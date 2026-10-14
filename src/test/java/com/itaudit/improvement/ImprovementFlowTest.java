package com.itaudit.improvement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.inspection.InspectionResult;
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestRounds;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImprovementFlowTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired TestRounds testRounds;
    @Autowired ImprovementService service;
    @Autowired ImprovementMapper mapper;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired JdbcTemplate jdbc;

    LoginUser lead;
    LoginUser dept;
    LoginUser kim;
    LoginUser lee;
    Long roundId;
    Long item23;
    Long item24;
    Long item26;
    Long request23;

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("m-lead", Role.AUDIT_LEAD);
        dept = testUsers.user("m-dept", Role.AUDIT_DEPT);
        testUsers.team("INFRA", "인프라팀");
        kim = testUsers.inspector("m-kim", "INFRA");
        lee = testUsers.inspector("m-lee", "INFRA");
        testChecklist.load(2026, lead);
        roundId = testRounds.create(2026, 3, lead);
        item23 = testRounds.confirmItem(roundId, 23, kim, "INFRA", "INSUFFICIENT", "1건 미회수", "회수 절차 보완", "2026-12-31");
        item24 = testRounds.confirmItem(roundId, 24, lee, "INFRA", "NORMAL", "이상 없음", null, null);
        item26 = testRounds.confirmItem(roundId, 26, kim, "INFRA", "NEEDS_IMPROVEMENT", "반출 대장 누락", "대장 서식 개선", "2026-11-30");
        testRounds.submitToDept(roundId);
        request23 = testRounds.confirmFinding(item23, dept.getUserId(), "INSUFFICIENT", "퇴사자 계정 미삭제", "26년 4분기 내 조치");
        testRounds.confirmFinding(item26, dept.getUserId(), "RISK", "반출 승인 없음", null);
    }

    private Long createImprovement() throws Exception {
        String location = mvc.perform(post("/improvements").with(user(lead)).with(csrf())
                        .param("roundItemIds", String.valueOf(item23), String.valueOf(item26))
                        .param("teamCode", "INFRA").param("assigneeId", String.valueOf(kim.getUserId()))
                        .param("inspectionResult", "RISK").param("detail", "퇴사자 계정 미삭제, 반출 승인 없음")
                        .param("improvementPlan", "계정 정리 절차 수립").param("plannedDate", "2026-12-31"))
                .andExpect(redirectedUrlPattern("/improvements/*"))
                .andReturn().getResponse().getRedirectedUrl();
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    // ---------- 개선사항 등록 ----------

    @Test
    void 미흡_확정_건을_고르면_기본값을_채워_준다() {
        assertThat(service.findOpenFindings()).extracting(r -> r.getSeqNo()).containsExactly(23, 26);

        ImprovementForm f = service.prepareFromFindings(List.of(item23, item26));
        assertThat(f.getInspectionResult()).as("가장 심각한 확정 결과").isEqualTo(InspectionResult.RISK);
        assertThat(f.getDetail()).isEqualTo("퇴사자 계정 미삭제\n반출 승인 없음");
        // ④ 비고 → 없으면 ③ 조치내용
        assertThat(f.getImprovementPlan()).isEqualTo("26년 4분기 내 조치\n대장 서식 개선");
        assertThat(f.getPlannedDate()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(f.getAssigneeId()).isEqualTo(kim.getUserId());
    }

    @Test
    void 여러_항목을_하나의_개선사항으로_묶어_등록한다() throws Exception {
        mvc.perform(get("/improvements/new").param("roundItemIds", String.valueOf(item23), String.valueOf(item26))
                        .with(user(lead)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("26년 4분기 내 조치")));

        Long id = createImprovement();
        Improvement m = service.get(id, lead);
        assertThat(m.getKind()).isEqualTo(ImprovementKind.IMPROVEMENT);
        assertThat(m.getItems()).extracting(LinkedItem::getSeqNo).containsExactly(23, 26);
        assertThat(m.getActionResult()).isEqualTo(ActionResult.PLANNED);
        assertThat(m.getRoundShortLabel()).isEqualTo("2026 3Q");
        assertThat(m.getOwnerLabel()).isEqualTo("인프라팀 / m-kim 사용자");
        assertThat(service.findOpenFindings()).isEmpty();
        assertThat(activityLogMapper.findByAction(ActivityLogService.IMPROVEMENT_CREATE))
                .anyMatch(l -> l.getDetail().contains("항목 #23 #26"));

        String list = mvc.perform(get("/improvements").with(user(lead))).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("감사 시기").contains("담당부서/담당자").contains("2026 3Q").contains("계정 정리 절차 수립");
    }

    @Test
    void 미확정이거나_이미_등록된_건은_등록할_수_없다() throws Exception {
        mvc.perform(post("/improvements").with(user(lead)).with(csrf())
                        .param("roundItemIds", String.valueOf(item24)).param("inspectionResult", "RISK").param("detail", "x"))
                .andExpect(content().string(Matchers.containsString("미흡으로 확정한 항목만")));
        createImprovement();
        mvc.perform(get("/improvements/new").param("roundItemIds", String.valueOf(item23)).with(user(lead)))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("이미 개선사항에 등록")));
    }

    @Test
    void 개선사항에_연결된_미흡_확정은_감사부서가_판정을_바꿀_수_없다() throws Exception {
        createImprovement();
        mvc.perform(post("/requests/items/" + item23 + "/review").with(user(dept)).with(csrf()).param("decision", "NO_FINDING"))
                .andExpect(content().string(Matchers.containsString("개선사항(번호")));
    }

    // ---------- 담당자 조치 ----------

    @Test
    void 담당자는_본인_건만_보고_조치_정보만_갱신한다() throws Exception {
        Long id = createImprovement();
        mvc.perform(get("/my-tasks").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("내 개선사항 1건")));
        mvc.perform(get("/improvements").with(user(lee)))
                .andExpect(content().string(Matchers.containsString("해당하는 건이 없습니다")));
        mvc.perform(get("/improvements/" + id).with(user(lee))).andExpect(status().isForbidden());

        mvc.perform(multipart("/improvements/" + id)
                        .file(new MockMultipartFile("files", "계정정리.pdf", null, "pdf".getBytes()))
                        .with(user(kim)).with(csrf())
                        .param("detail", "담당자가 바꾸려는 내용")
                        .param("improvementPlan", "분기별 계정 점검").param("plannedDate", "2027-01-31")
                        .param("actionResult", "IN_PROGRESS").param("actionStatus", "1차 정리 완료"))
                .andExpect(flash().attribute("message", "저장했습니다."));

        Improvement m = service.get(id, lead);
        assertThat(m.getDetail()).as("관리 정보는 담당자가 바꿀 수 없음").isEqualTo("퇴사자 계정 미삭제, 반출 승인 없음");
        assertThat(m.getImprovementPlan()).isEqualTo("분기별 계정 점검");
        assertThat(m.getPlannedDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(m.getActionResult()).isEqualTo(ActionResult.IN_PROGRESS);
        assertThat(m.getEvidenceCount()).isEqualTo(1);
        assertThat(activityLogMapper.findByAction(ActivityLogService.IMPROVEMENT_UPDATE))
                .anyMatch(l -> l.getDetail().contains("조치결과=진행중"));
    }

    @Test
    void 완료로_바꾸려면_조치_현황이_필요하다() throws Exception {
        Long id = createImprovement();
        mvc.perform(post("/improvements/" + id).with(user(kim)).with(csrf()).param("actionResult", "DONE"))
                .andExpect(content().string(Matchers.containsString("조치 현황을 입력하세요")));
        assertThat(service.get(id, lead).getActionResult()).isEqualTo(ActionResult.PLANNED);
    }

    // ---------- 종결 ----------

    @Test
    void 완료된_건만_종결하고_종결_취소에는_사유가_필요하다() throws Exception {
        Long id = createImprovement();
        mvc.perform(post("/improvements/" + id + "/close").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "조치 결과가 '완료'인 건만 최종확정할 수 있습니다."));

        mvc.perform(post("/improvements/" + id).with(user(kim)).with(csrf())
                .param("actionResult", "DONE").param("actionStatus", "전 계정 정리 완료"));
        mvc.perform(post("/improvements/" + id + "/close").param("comment", "증빙 확인").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("message", "최종확정했습니다."));
        Improvement closed = service.get(id, lead);
        assertThat(closed.isClosed()).isTrue();
        assertThat(closed.getClosedBy()).isEqualTo(lead.getUserId());

        // 종결 후 수정 불가, 미종결 목록에서 빠짐
        mvc.perform(post("/improvements/" + id).with(user(kim)).with(csrf()).param("actionResult", "IN_PROGRESS"))
                .andExpect(content().string(Matchers.containsString("최종확정된 건은 수정할 수 없습니다")));
        mvc.perform(get("/improvements").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("해당하는 건이 없습니다")));
        mvc.perform(get("/improvements").param("closed", "CLOSED").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("전 계정 정리 완료")));

        mvc.perform(post("/improvements/" + id + "/reopen").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "최종확정 취소 사유을(를) 입력하세요."));
        mvc.perform(post("/improvements/" + id + "/reopen").param("reason", "추가 증빙 필요").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("message", "최종확정을 취소했습니다. 완료 검토 대기 상태로 돌아갑니다."));
        assertThat(service.get(id, lead).isClosed()).isFalse();
        assertThat(activityLogMapper.findByAction(ActivityLogService.IMPROVEMENT_CLOSE)).hasSize(1);
        assertThat(activityLogMapper.findByAction(ActivityLogService.IMPROVEMENT_REOPEN))
                .anyMatch(l -> l.getDetail().contains("추가 증빙 필요"));
    }

    // ---------- 권고사항 ----------

    @Test
    void 감사부서는_권고사항을_등록하고_내용을_고칠_수_있다() throws Exception {
        mvc.perform(get("/improvements/recommendations/new").param("roundId", String.valueOf(roundId)).with(user(dept)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("권고 대상 항목")));
        String location = mvc.perform(post("/improvements/recommendations").with(user(dept)).with(csrf())
                        .param("roundId", String.valueOf(roundId)).param("roundItemIds", String.valueOf(item24))
                        .param("assigneeId", String.valueOf(lee.getUserId()))
                        .param("detail", "접근권한 검토 주기를 분기로 단축 권고"))
                .andExpect(redirectedUrlPattern("/improvements/*"))
                .andReturn().getResponse().getRedirectedUrl();
        Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
        Improvement rec = service.get(id, dept);
        assertThat(rec.getKind()).isEqualTo(ImprovementKind.RECOMMENDATION);
        assertThat(rec.getInspectionResult()).isNull();
        assertThat(rec.getTeamCode()).as("담당자 팀 자동").isEqualTo("INFRA");

        mvc.perform(get("/improvements").param("kind", "RECOMMENDATION").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("상세 내용")))
                .andExpect(content().string(Matchers.containsString("접근권한 검토 주기를 분기로 단축 권고")));

        mvc.perform(post("/improvements/" + id).with(user(dept)).with(csrf())
                .param("assigneeId", String.valueOf(lee.getUserId())).param("detail", "권고 내용 수정"));
        assertThat(service.get(id, dept).getDetail()).isEqualTo("권고 내용 수정");

        // 감사부서는 개선사항(주관자 관리)은 고칠 수 없다
        Long imp = createImprovement();
        mvc.perform(post("/improvements/" + imp).with(user(dept)).with(csrf()).param("detail", "x"))
                .andExpect(content().string(Matchers.containsString("수정 권한이 없습니다")));
    }

    // ---------- 조회 ----------

    @Test
    void 개선예정일_경과_건을_골라_볼_수_있다() throws Exception {
        Long id = createImprovement();
        jdbc.update("UPDATE improvement SET planned_date = ? WHERE improvement_id = ?",
                java.sql.Date.valueOf(LocalDate.now().minusDays(5)), id);
        assertThat(service.get(id, lead).isOverdue()).isTrue();
        mvc.perform(get("/improvements").param("overdueOnly", "true").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("5일 경과")));

        jdbc.update("UPDATE improvement SET planned_date = ? WHERE improvement_id = ?",
                java.sql.Date.valueOf(LocalDate.now().plusDays(3)), id);
        assertThat(service.get(id, lead).isDueSoon()).isTrue();
        mvc.perform(get("/improvements").param("overdueOnly", "true").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("해당하는 건이 없습니다")));
        mvc.perform(get("/improvements").with(user(lead))).andExpect(content().string(Matchers.containsString("D-3")));
    }

    @Test
    void 조회자는_보기만_하고_관리자는_볼_수_없다() throws Exception {
        Long id = createImprovement();
        LoginUser viewer = testUsers.user("m-viewer", Role.VIEWER);
        mvc.perform(get("/improvements/" + id).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString(">저장</button>"))));
        mvc.perform(post("/improvements/" + id).with(user(viewer)).with(csrf()).param("actionResult", "DONE"))
                .andExpect(content().string(Matchers.containsString("수정 권한이 없습니다")));
        mvc.perform(post("/improvements/" + id + "/close").with(user(viewer)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/improvements/findings").with(user(dept))).andExpect(status().isForbidden());
        mvc.perform(get("/improvements").with(user(testUsers.user("m-admin", Role.ADMIN)))).andExpect(status().isForbidden());
    }
}
