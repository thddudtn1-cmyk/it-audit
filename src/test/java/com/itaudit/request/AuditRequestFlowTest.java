package com.itaudit.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.inspection.InspectionResult;
import com.itaudit.round.RoundMapper;
import com.itaudit.round.RoundStatus;
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestRounds;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditRequestFlowTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired TestRounds testRounds;
    @Autowired AuditRequestMapper requestMapper;
    @Autowired RoundMapper roundMapper;
    @Autowired ActivityLogMapper activityLogMapper;

    LoginUser lead;
    LoginUser dept;
    LoginUser kim;
    LoginUser lee;
    Long roundId;
    Long item23;
    Long item24;

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("q-lead", Role.AUDIT_LEAD);
        dept = testUsers.user("q-dept", Role.AUDIT_DEPT);
        testUsers.team("INFRA", "인프라팀");
        kim = testUsers.inspector("q-kim", "INFRA");
        lee = testUsers.inspector("q-lee", "INFRA");
        testChecklist.load(2026, lead);
        roundId = testRounds.create(2026, 3, lead);
        jdbcMaterial(23, "인사이동자 권한 회수 대장");
        item23 = testRounds.confirmItem(roundId, 23, kim, "INFRA", "INSUFFICIENT", "1건 미회수", "회수 절차 보완", "2026-12-31");
        item24 = testRounds.confirmItem(roundId, 24, lee, "INFRA", "NORMAL", "이상 없음", null, null);
        testRounds.submitToDept(roundId);
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void jdbcMaterial(int seqNo, String material) {
        jdbc.update("UPDATE round_item SET request_material = ?, sample_size = 3 WHERE round_item_id = ?",
                material, testRounds.itemId(roundId, seqNo));
    }

    private MockHttpServletRequestBuilder requestItems(LoginUser u, Long... itemIds) {
        MockHttpServletRequestBuilder req = post("/requests").with(user(u)).with(csrf()).param("roundId", String.valueOf(roundId));
        for (int i = 0; i < itemIds.length; i++) {
            req.param("rows[" + i + "].roundItemId", String.valueOf(itemIds[i]))
                    .param("rows[" + i + "].selected", "true")
                    .param("rows[" + i + "].requestMaterial", "요청자료 " + itemIds[i])
                    .param("rows[" + i + "].sampleSize", "3");
        }
        return req;
    }

    private Long request(Long itemId) throws Exception {
        mvc.perform(requestItems(dept, itemId)).andExpect(redirectedUrl("/requests?roundId=" + roundId));
        return requestMapper.findByRoundItem(itemId).getRequestId();
    }

    private void submitByInspector(Long requestId, LoginUser u) throws Exception {
        mvc.perform(multipart("/requests/" + requestId + "/submission")
                        .file(new MockMultipartFile("files", "권한회수대장.xlsx", null, "data".getBytes(StandardCharsets.UTF_8)))
                        .with(user(u)).with(csrf())
                        .param("remark", "26년 4분기 내 조치").param("action", "submit"))
                .andExpect(redirectedUrl("/requests/" + requestId));
    }

    private MockHttpServletRequestBuilder review(Long requestId, String decision) {
        return post("/requests/" + requestId + "/review").with(user(dept)).with(csrf()).param("decision", decision);
    }

    // ---------- 요청 ----------

    @Test
    void 감사부서는_제출된_총괄표에서_항목을_골라_요청한다() throws Exception {
        mvc.perform(get("/requests/new").param("roundId", String.valueOf(roundId)).with(user(dept)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("인사이동자 권한 회수 대장")));

        mvc.perform(requestItems(dept, item23, item24))
                .andExpect(flash().attribute("message", "2건을 요청했습니다. 담당자 '내 할 일'에 표시됩니다."));
        AuditRequest r = requestMapper.findByRoundItem(item23);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.REQUESTED);
        assertThat(r.getRequestMaterial()).isEqualTo("요청자료 " + item23);
        assertThat(r.getSampleSize()).isEqualTo(3);
        assertThat(r.getRequestedBy()).isEqualTo(dept.getUserId());

        mvc.perform(get("/requests").param("roundId", String.valueOf(roundId)).with(user(dept)))
                .andExpect(content().string(Matchers.containsString("요청자료 " + item23)))
                .andExpect(content().string(Matchers.containsString("판정 대기</strong> <span>2건")));
        assertThat(activityLogMapper.findByAction(ActivityLogService.REQUEST_CREATE)).hasSize(2);

        // 같은 항목 중복 요청 불가
        mvc.perform(requestItems(dept, item23))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("이미 요청한 항목")));
    }

    @Test
    void 선택이_없거나_요청자료가_비면_요청하지_않는다() throws Exception {
        mvc.perform(post("/requests").with(user(dept)).with(csrf()).param("roundId", String.valueOf(roundId)))
                .andExpect(flash().attribute("errorMessage", "요청할 항목을 하나 이상 고르세요."));
        mvc.perform(post("/requests").with(user(dept)).with(csrf()).param("roundId", String.valueOf(roundId))
                        .param("rows[0].roundItemId", String.valueOf(item23)).param("rows[0].selected", "true")
                        .param("rows[0].requestMaterial", " "))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("요청자료를 입력하세요")));
        assertThat(requestMapper.findByRound(roundId)).isEmpty();
    }

    @Test
    void 감사부서_제출_전_회차에는_요청할_수_없다() throws Exception {
        Long draft = testRounds.create(2026, 4, lead);
        mvc.perform(get("/requests/new").param("roundId", String.valueOf(draft)).with(user(dept)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString("감사부서에 제출된 회차가 아닙니다")));
    }

    // ---------- 담당자 제출 ----------

    @Test
    void 담당자는_내_할_일에서_요청을_보고_자료와_비고를_제출한다() throws Exception {
        Long requestId = request(item23);
        mvc.perform(get("/my-tasks").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("받은 사전요청자료 1건")));
        mvc.perform(get("/requests").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("/requests/" + requestId)));
        mvc.perform(get("/requests/" + requestId).with(user(lee))).andExpect(status().isForbidden());

        submitByInspector(requestId, kim);
        AuditRequest r = requestMapper.findById(requestId);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.SUBMITTED);
        assertThat(r.getRemark()).isEqualTo("26년 4분기 내 조치");
        assertThat(r.getEvidenceCount()).isEqualTo(1);
        assertThat(r.getSubmittedBy()).isEqualTo(kim.getUserId());

        // 제출 후 다시 제출 불가
        mvc.perform(multipart("/requests/" + requestId + "/submission").with(user(kim)).with(csrf())
                        .param("remark", "변경").param("action", "save"))
                .andExpect(content().string(Matchers.containsString("이미 제출한 요청입니다")));
    }

    @Test
    void 파일도_비고도_없으면_제출할_수_없다() throws Exception {
        Long requestId = request(item23);
        mvc.perform(multipart("/requests/" + requestId + "/submission").with(user(kim)).with(csrf()).param("action", "submit"))
                .andExpect(content().string(Matchers.containsString("제출할 자료 파일을 올리거나 비고를 입력하세요")));
        assertThat(requestMapper.findById(requestId).getStatus()).isEqualTo(RequestStatus.REQUESTED);
    }

    // ---------- 판정 ----------

    @Test
    void 미흡_확정에는_확정_점검결과와_미흡_내용이_필요하다() throws Exception {
        Long requestId = request(item23);
        submitByInspector(requestId, kim);

        mvc.perform(review(requestId, "FINDING"))
                .andExpect(content().string(Matchers.containsString("확정 점검결과(개선필요·미흡·위험)를 고르세요")));
        mvc.perform(review(requestId, "FINDING").param("confirmedResult", "INSUFFICIENT"))
                .andExpect(content().string(Matchers.containsString("미흡 내용을 입력하세요")));

        mvc.perform(review(requestId, "FINDING").param("confirmedResult", "INSUFFICIENT")
                        .param("findingDetail", "퇴사자 1명 계정 미삭제"))
                .andExpect(flash().attribute("message", "미흡으로 확정했습니다."));
        AuditRequest r = requestMapper.findById(requestId);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.REVIEWED);
        assertThat(r.isFindingConfirmed()).isTrue();
        assertThat(r.getConfirmedResult()).isEqualTo(InspectionResult.INSUFFICIENT);
        assertThat(r.getFindingDetail()).isEqualTo("퇴사자 1명 계정 미삭제");
        assertThat(requestMapper.findConfirmedFindings(roundId)).hasSize(1);
        assertThat(activityLogMapper.findByAction(ActivityLogService.REQUEST_REVIEW))
                .anyMatch(l -> l.getDetail().contains("제출/- → 검토완료/미흡 확정(미흡)"));
    }

    @Test
    void 보완_요청하면_담당자가_다시_제출할_수_있다() throws Exception {
        Long requestId = request(item23);
        submitByInspector(requestId, kim);

        mvc.perform(review(requestId, "SUPPLEMENT"))
                .andExpect(content().string(Matchers.containsString("보완 요청 시 요청 내용을 검토 의견에 입력하세요")));
        mvc.perform(review(requestId, "SUPPLEMENT").param("deptComment", "최근 3개월분으로 다시 제출"))
                .andExpect(flash().attribute("message", "담당자에게 보완을 요청했습니다."));
        assertThat(requestMapper.findById(requestId).getStatus()).isEqualTo(RequestStatus.REQUESTED);
        mvc.perform(get("/my-tasks").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("보완 요청: 최근 3개월분으로 다시 제출")));

        submitByInspector(requestId, kim);
        assertThat(requestMapper.findById(requestId).getStatus()).isEqualTo(RequestStatus.SUBMITTED);
    }

    @Test
    void 제출_전에는_판정할_수_없다() throws Exception {
        Long requestId = request(item23);
        mvc.perform(review(requestId, "NO_FINDING"))
                .andExpect(content().string(Matchers.containsString("아직 자료를 제출하지 않았습니다")));
    }

    // ---------- 완료 ----------

    @Test
    void 모든_요청을_판정해야_적정성_점검을_완료할_수_있다() throws Exception {
        Long r23 = request(item23);
        Long r24 = request(item24);
        submitByInspector(r23, kim);
        submitByInspector(r24, lee);
        mvc.perform(review(r23, "FINDING").param("confirmedResult", "RISK").param("findingDetail", "위반"));

        mvc.perform(post("/requests/rounds/" + roundId + "/complete").with(user(dept)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "판정하지 않은 사전요청자료가 1건 있습니다."));
        mvc.perform(review(r24, "NO_FINDING"));
        mvc.perform(post("/requests/rounds/" + roundId + "/complete").with(user(dept)).with(csrf()))
                .andExpect(flash().attribute("message", Matchers.containsString("적정성 점검을 완료했습니다")));

        assertThat(roundMapper.findRound(roundId).getStatus()).isEqualTo(RoundStatus.DONE);
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_DONE))
                .anyMatch(l -> l.getDetail().contains("요청 2건 중 미흡 확정 1건"));
        // 완료 후 판정 변경 불가
        mvc.perform(review(r24, "FINDING").param("confirmedResult", "RISK").param("findingDetail", "x"))
                .andExpect(content().string(Matchers.containsString("적정성 점검이 끝난 회차")));
    }

    // ---------- 권한 ----------

    @Test
    void 요청_판정_완료는_감사부서만_한다() throws Exception {
        Long requestId = request(item23);
        submitByInspector(requestId, kim);
        for (LoginUser u : List.of(lead, kim)) {
            mvc.perform(get("/requests/new").param("roundId", String.valueOf(roundId)).with(user(u))).andExpect(status().isForbidden());
            mvc.perform(requestItems(u, item24)).andExpect(status().isForbidden());
            mvc.perform(post("/requests/" + requestId + "/review").with(user(u)).with(csrf()).param("decision", "NO_FINDING"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/requests/rounds/" + roundId + "/complete").with(user(u)).with(csrf())).andExpect(status().isForbidden());
        }
        // 주관자는 열람 가능, 조회자는 메뉴 밖
        mvc.perform(get("/requests").with(user(lead))).andExpect(status().isOk());
        mvc.perform(get("/requests/" + requestId).with(user(lead))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("판정 저장"))));
        mvc.perform(get("/requests").with(user(testUsers.user("q-viewer", Role.VIEWER)))).andExpect(status().isForbidden());
    }
}
