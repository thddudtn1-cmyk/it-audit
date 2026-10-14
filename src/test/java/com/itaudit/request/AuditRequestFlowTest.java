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
import org.springframework.jdbc.core.JdbcTemplate;
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

/** 적정성 점검: 제출된 분기 점검 내역을 감사부서가 항목별로 검토하고 보완자료를 요청한다 */
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
    @Autowired AuditRequestService service;
    @Autowired RoundMapper roundMapper;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired JdbcTemplate jdbc;

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
        item23 = testRounds.confirmItem(roundId, 23, kim, "INFRA", "INSUFFICIENT", "1건 미회수", "회수 절차 보완", "2026-12-31");
        item24 = testRounds.confirmItem(roundId, 24, lee, "INFRA", "NORMAL", "이상 없음", null, null);
        testRounds.submitToDept(roundId);   // 나머지 항목은 회신불필요 → 적정성 점검 대상은 #23, #24
    }

    private MockHttpServletRequestBuilder review(Long itemId, String decision, LoginUser u) {
        return post("/requests/items/" + itemId + "/review").with(user(u)).with(csrf()).param("decision", decision);
    }

    private void supplement(Long itemId, String material) throws Exception {
        mvc.perform(review(itemId, "SUPPLEMENT", dept).param("requestMaterial", material).param("deptComment", "3개월분으로"))
                .andExpect(redirectedUrl("/requests/items/" + itemId));
    }

    private void submitByInspector(Long requestId, Long itemId, LoginUser u) throws Exception {
        mvc.perform(multipart("/requests/" + requestId + "/submission")
                        .file(new MockMultipartFile("files", "권한회수대장.xlsx", null, "data".getBytes(StandardCharsets.UTF_8)))
                        .with(user(u)).with(csrf())
                        .param("remark", "26년 4분기 내 조치").param("action", "submit"))
                .andExpect(redirectedUrl("/requests/items/" + itemId));
    }

    // ---------- 검토 대상 ----------

    @Test
    void 제출된_점검_내역이_항목별로_나오고_회신불필요_항목은_대상이_아니다() throws Exception {
        List<AuditRequestService.Target> targets = service.findTargets(roundId);
        assertThat(targets).extracting(t -> t.item().getSeqNo()).containsExactly(23, 24);
        assertThat(targets).allMatch(t -> t.state() == ReviewState.NOT_REVIEWED);

        mvc.perform(get("/requests").param("roundId", String.valueOf(roundId)).with(user(dept)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("적정성 점검")))
                .andExpect(content().string(Matchers.containsString("1건 미회수")))
                .andExpect(content().string(Matchers.containsString("점검 항목</strong> <span>2건")))
                .andExpect(content().string(Matchers.containsString("미검토</strong> <span>2건")));
        // 상세에는 담당자가 제출한 분기 점검 내역이 보인다
        mvc.perform(get("/requests/items/" + item23).with(user(dept)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("분기 점검 내역")))
                .andExpect(content().string(Matchers.containsString("1건 미회수")))
                .andExpect(content().string(Matchers.containsString("회수 절차 보완")));
        Long noReply = testRounds.itemId(roundId, 25);
        mvc.perform(get("/requests/items/" + noReply).with(user(dept))).andExpect(status().isBadRequest());
    }

    @Test
    void 감사부서_제출_전_회차는_검토할_수_없다() throws Exception {
        Long draft = testRounds.create(2026, 4, lead);
        mvc.perform(get("/requests/items/" + testRounds.itemId(draft, 33)).with(user(dept))).andExpect(status().isForbidden());
        mvc.perform(get("/requests").param("roundId", String.valueOf(draft)).with(user(dept)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.containsString("감사부서에 제출된 회차가 아닙니다")));
    }

    // ---------- 판정 ----------

    @Test
    void 요청_없이_바로_적정으로_판정한다() throws Exception {
        mvc.perform(review(item24, "NO_FINDING", dept))
                .andExpect(flash().attribute("message", "적정으로 판정했습니다."));
        AuditRequest r = requestMapper.findByRoundItem(item24);
        assertThat(r.getStatus()).isEqualTo(RequestStatus.REVIEWED);
        assertThat(r.isFindingConfirmed()).isFalse();
        assertThat(r.getRequestMaterial()).as("보완 요청 없이 판정한 행").isNull();
        assertThat(ReviewState.of(r)).isEqualTo(ReviewState.PASSED);
        assertThat(r.getReviewedBy()).isEqualTo(dept.getUserId());
        assertThat(activityLogMapper.findByAction(ActivityLogService.REQUEST_REVIEW))
                .anyMatch(l -> l.getDetail().contains("#24 판정 미검토 → 적정"));
    }

    @Test
    void 미흡_확정에는_확정_점검결과와_미흡_내용이_필요하고_실패하면_행을_만들지_않는다() throws Exception {
        mvc.perform(review(item23, "FINDING", dept))
                .andExpect(content().string(Matchers.containsString("확정 점검결과(개선필요·미흡·위험)를 고르세요")));
        mvc.perform(review(item23, "FINDING", dept).param("confirmedResult", "INSUFFICIENT"))
                .andExpect(content().string(Matchers.containsString("미흡 내용을 입력하세요")));
        assertThat(requestMapper.findByRoundItem(item23)).isNull();

        mvc.perform(review(item23, "FINDING", dept).param("confirmedResult", "INSUFFICIENT")
                        .param("findingDetail", "퇴사자 1명 계정 미삭제"))
                .andExpect(flash().attribute("message", "미흡으로 확정했습니다."));
        AuditRequest r = requestMapper.findByRoundItem(item23);
        assertThat(ReviewState.of(r)).isEqualTo(ReviewState.FINDING);
        assertThat(r.getConfirmedResult()).isEqualTo(InspectionResult.INSUFFICIENT);
        assertThat(requestMapper.findConfirmedFindings(roundId)).hasSize(1);
    }

    @Test
    void 판정을_고르지_않으면_거부한다() throws Exception {
        mvc.perform(post("/requests/items/" + item23 + "/review").with(user(dept)).with(csrf()))
                .andExpect(content().string(Matchers.containsString("판정을 선택하세요")));
    }

    // ---------- 보완자료 요청 → 제출 → 재검토 ----------

    @Test
    void 보완자료를_요청하면_담당자가_제출하고_감사부서가_다시_판정한다() throws Exception {
        mvc.perform(review(item23, "SUPPLEMENT", dept))
                .andExpect(content().string(Matchers.containsString("보완 요청 시 요청할 자료를 입력하세요")));
        assertThat(requestMapper.findByRoundItem(item23)).isNull();

        supplement(item23, "인사이동자 권한 회수 대장 (최근 3개월)");
        AuditRequest r = requestMapper.findByRoundItem(item23);
        assertThat(ReviewState.of(r)).isEqualTo(ReviewState.REQUESTED);
        assertThat(r.getRequestMaterial()).isEqualTo("인사이동자 권한 회수 대장 (최근 3개월)");
        assertThat(activityLogMapper.findByAction(ActivityLogService.REQUEST_CREATE))
                .anyMatch(l -> l.getDetail().contains("#23 보완자료 요청"));

        // 담당자: 내 할 일에 표시, 다른 담당자는 못 봄
        mvc.perform(get("/my-tasks").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("적정성 점검 보완 요청 1건")))
                .andExpect(content().string(Matchers.containsString("감사부서 의견: 3개월분으로")));
        mvc.perform(get("/requests").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("/requests/items/" + item23)));
        mvc.perform(get("/requests/items/" + item23).with(user(lee))).andExpect(status().isForbidden());
        mvc.perform(get("/requests/" + r.getRequestId()).with(user(kim))).andExpect(redirectedUrl("/requests/items/" + item23));

        // 요청 중에는 감사부서가 판정할 수 없다
        mvc.perform(review(item23, "NO_FINDING", dept))
                .andExpect(content().string(Matchers.containsString("보완자료를 제출하기를 기다리는 중입니다")));

        submitByInspector(r.getRequestId(), item23, kim);
        AuditRequest submitted = requestMapper.findByRoundItem(item23);
        assertThat(ReviewState.of(submitted)).isEqualTo(ReviewState.SUBMITTED);
        assertThat(submitted.getRemark()).isEqualTo("26년 4분기 내 조치");
        assertThat(submitted.getEvidenceCount()).isEqualTo(1);

        // 제출 후에는 다시 제출 불가, 감사부서가 재검토 (보완 제출 → 미흡 확정)
        mvc.perform(multipart("/requests/" + r.getRequestId() + "/submission").with(user(kim)).with(csrf())
                        .param("remark", "변경").param("action", "save"))
                .andExpect(content().string(Matchers.containsString("이미 제출했습니다")));
        mvc.perform(review(item23, "FINDING", dept).param("confirmedResult", "RISK").param("findingDetail", "위반 확인"))
                .andExpect(flash().attribute("message", "미흡으로 확정했습니다."));
        assertThat(ReviewState.of(requestMapper.findByRoundItem(item23))).isEqualTo(ReviewState.FINDING);
    }

    @Test
    void 제출할_자료도_비고도_없으면_보완자료를_제출할_수_없다() throws Exception {
        supplement(item23, "대장");
        Long requestId = requestMapper.findByRoundItem(item23).getRequestId();
        mvc.perform(multipart("/requests/" + requestId + "/submission").with(user(kim)).with(csrf()).param("action", "submit"))
                .andExpect(content().string(Matchers.containsString("제출할 보완자료 파일을 올리거나 비고를 입력하세요")));
        assertThat(requestMapper.findById(requestId).getStatus()).isEqualTo(RequestStatus.REQUESTED);
    }

    @Test
    void 한번_판정한_항목도_완료_전에는_보완자료를_요청할_수_있다() throws Exception {
        mvc.perform(review(item24, "NO_FINDING", dept));
        supplement(item24, "추가 증적");
        assertThat(ReviewState.of(requestMapper.findByRoundItem(item24))).isEqualTo(ReviewState.REQUESTED);
        assertThat(requestMapper.findByRoundItem(item24).getFindingYn()).as("이전 판정은 지워짐").isNull();
    }

    // ---------- 완료 ----------

    @Test
    void 모든_항목을_검토해야_적정성_점검을_완료할_수_있다() throws Exception {
        mvc.perform(post("/requests/rounds/" + roundId + "/complete").with(user(dept)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "검토하지 않은 항목이 2건 있습니다 (미검토·보완 요청 중 포함)."));

        mvc.perform(review(item23, "FINDING", dept).param("confirmedResult", "RISK").param("findingDetail", "위반"));
        supplement(item24, "자료");
        mvc.perform(post("/requests/rounds/" + roundId + "/complete").with(user(dept)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "검토하지 않은 항목이 1건 있습니다 (미검토·보완 요청 중 포함)."));

        Long requestId = requestMapper.findByRoundItem(item24).getRequestId();
        submitByInspector(requestId, item24, lee);
        mvc.perform(review(item24, "NO_FINDING", dept));
        mvc.perform(post("/requests/rounds/" + roundId + "/complete").with(user(dept)).with(csrf()))
                .andExpect(flash().attribute("message", Matchers.containsString("적정성 점검을 완료했습니다")));

        assertThat(roundMapper.findRound(roundId).getStatus()).isEqualTo(RoundStatus.DONE);
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_DONE))
                .anyMatch(l -> l.getDetail().contains("점검 항목 2건 중 미흡 확정 1건"));
        // 완료 후 판정 변경 불가
        mvc.perform(review(item24, "FINDING", dept).param("confirmedResult", "RISK").param("findingDetail", "x"))
                .andExpect(content().string(Matchers.containsString("적정성 점검이 끝난 회차")));
    }

    // ---------- 권한 ----------

    @Test
    void 검토_판정_완료는_감사부서만_한다() throws Exception {
        for (LoginUser u : List.of(lead, kim)) {
            mvc.perform(post("/requests/items/" + item23 + "/review").with(user(u)).with(csrf()).param("decision", "NO_FINDING"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/requests/rounds/" + roundId + "/complete").with(user(u)).with(csrf())).andExpect(status().isForbidden());
        }
        assertThat(requestMapper.findByRoundItem(item23)).isNull();
        // 주관자는 열람만, 조회자는 메뉴 밖
        mvc.perform(get("/requests").with(user(lead))).andExpect(status().isOk());
        mvc.perform(get("/requests/items/" + item23).with(user(lead))).andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("적정성 검토"))));
        mvc.perform(get("/requests").with(user(testUsers.user("q-viewer", Role.VIEWER)))).andExpect(status().isForbidden());
        // 기존 '요청 추가' 화면은 없어졌다
        mvc.perform(get("/requests/new").param("roundId", String.valueOf(roundId)).with(user(dept)))
                .andExpect(status().is4xxClientError());
    }
}
