package com.itaudit.improvement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import java.time.LocalDate;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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

/** 분기 점검을 감사부서에 제출하는 시점에 점검결과가 정상이 아닌 항목이 개선사항 관리대장에 확정 등록된다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutoRegistrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired TestRounds testRounds;
    @Autowired ImprovementMapper improvementMapper;
    @Autowired ImprovementService improvementService;
    @Autowired RoundMapper roundMapper;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired JdbcTemplate jdbc;

    LoginUser lead;
    LoginUser dept;
    LoginUser kim;
    Long roundId;
    Long item23;   // 미흡
    Long item24;   // 정상
    Long item26;   // 위험
    Long item27;   // 개선필요
    Long item30;   // 위험 이지만 제외

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("a-lead", Role.AUDIT_LEAD);
        dept = testUsers.user("a-dept", Role.AUDIT_DEPT);
        testUsers.team("INFRA", "인프라팀");
        kim = testUsers.inspector("a-kim", "INFRA");
        testChecklist.load(2026, lead);
        roundId = testRounds.create(2026, 3, lead);
        item23 = testRounds.confirmItem(roundId, 23, kim, "INFRA", "INSUFFICIENT", "인사이동 1건 미회수", "회수 절차 보완", "2026-12-31");
        item24 = testRounds.confirmItem(roundId, 24, kim, "INFRA", "NORMAL", "이상 없음", null, null);
        item26 = testRounds.confirmItem(roundId, 26, kim, "INFRA", "RISK", "반출 승인 없음", "승인 절차 수립", "2026-11-30");
        item27 = testRounds.confirmItem(roundId, 27, kim, "INFRA", "NEEDS_IMPROVEMENT", "승인 문서 보완", "문서 서식 개선", "2027-01-31");
        item30 = testRounds.confirmItem(roundId, 30, kim, "INFRA", "RISK", "제외될 항목", "x", "2026-12-31");
        jdbc.update("UPDATE round_item SET excluded_yn = 'Y', exclude_reason = '그룹 IT감사로 대체' WHERE round_item_id = ?", item30);
        // 나머지는 회신불필요 (확인된 항목만 제출 대상)
        jdbc.update("UPDATE round_item SET no_reply_yn = 'Y' WHERE round_id = ? AND submit_status <> 'CONFIRMED'", roundId);
    }

    private void submitToDept() throws Exception {
        mvc.perform(post("/rounds/" + roundId + "/submit").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("message", Matchers.containsString("감사부서에 제출했습니다")));
        assertThat(roundMapper.findRound(roundId).getStatus()).isEqualTo(RoundStatus.SUBMITTED);
    }

    private List<Improvement> registry() {
        ImprovementFilter f = new ImprovementFilter();
        f.setKind(ImprovementKind.IMPROVEMENT);
        f.setClosed(ImprovementFilter.Closed.ALL);
        return improvementService.findList(f, lead);
    }

    @Test
    void 제출_안내에_자동_등록될_건수를_보여주고_제출_전에는_등록되지_않는다() throws Exception {
        mvc.perform(get("/rounds/" + roundId).with(user(lead)))
                .andExpect(content().string(Matchers.containsString("점검결과가 정상이 아닌 3건이 감사부서 검토와 관계없이 개선사항 관리대장에 자동 등록됩니다")));
        assertThat(registry()).isEmpty();
    }

    @Test
    void 제출하면_정상이_아닌_항목이_점검_시점_값으로_자동_등록된다() throws Exception {
        submitToDept();

        List<Improvement> list = registry();
        assertThat(list).hasSize(3);   // 정상(#24)·제외(#30)·회신불필요 항목은 등록되지 않음
        assertThat(list).allSatisfy(m -> {
            assertThat(m.getKind()).isEqualTo(ImprovementKind.IMPROVEMENT);
            assertThat(m.getOrigin()).isEqualTo(ImprovementOrigin.ROUND_SUBMIT);
            assertThat(m.isAutoRegistered()).isTrue();
            assertThat(m.getActionResult()).isEqualTo(ActionResult.PLANNED);
            assertThat(m.getRoundShortLabel()).isEqualTo("2026 3Q");
            assertThat(m.getOwnerLabel()).isEqualTo("인프라팀 / a-kim 사용자");
            assertThat(m.getCreatedBy()).isEqualTo(lead.getUserId());
        });
        assertThat(list).extracting(m -> m.getItems().get(0).getSeqNo()).containsExactlyInAnyOrder(23, 26, 27);
        Improvement m23 = list.stream().filter(m -> m.getItems().get(0).getSeqNo() == 23).findFirst().orElseThrow();
        assertThat(m23.getInspectionResult()).isEqualTo(InspectionResult.INSUFFICIENT);
        assertThat(m23.getDetail()).isEqualTo("인사이동 1건 미회수");
        assertThat(m23.getImprovementPlan()).isEqualTo("회수 절차 보완");
        assertThat(m23.getPlannedDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(list).extracting(Improvement::getInspectionResult)
                .containsExactlyInAnyOrder(InspectionResult.INSUFFICIENT, InspectionResult.RISK, InspectionResult.NEEDS_IMPROVEMENT);

        assertThat(activityLogMapper.findByAction(ActivityLogService.IMPROVEMENT_CREATE)).hasSize(3)
                .allMatch(l -> l.getDetail().startsWith("분기 점검 제출로 자동 등록"));
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_SUBMIT))
                .anyMatch(l -> l.getDetail().contains("개선사항 관리대장 자동 등록 3건"));
        // 관리대장 화면과 담당자 내 할 일에 바로 보인다
        mvc.perform(get("/improvements").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("인사이동 1건 미회수")))
                .andExpect(content().string(Matchers.containsString("승인 절차 수립")));
        mvc.perform(get("/my-tasks").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("내 개선사항 3건")));
    }

    @Test
    void 감사부서_검토와_관계없이_등록되어_판정을_바꿔도_유지된다() throws Exception {
        submitToDept();
        // 감사부서가 '적정'으로 판정해도 관리대장 건은 그대로
        mvc.perform(post("/requests/items/" + item23 + "/review").with(user(dept)).with(csrf()).param("decision", "NO_FINDING"));
        assertThat(registry()).hasSize(3);
        // 미흡 확정으로 바꿨다가 다시 적정으로 되돌릴 수 있다 (자동 등록 건은 판정 변경을 막지 않는다)
        mvc.perform(post("/requests/items/" + item23 + "/review").with(user(dept)).with(csrf())
                .param("decision", "FINDING").param("confirmedResult", "RISK").param("findingDetail", "위반"));
        mvc.perform(post("/requests/items/" + item23 + "/review").with(user(dept)).with(csrf()).param("decision", "NO_FINDING"))
                .andExpect(flash().attribute("message", "적정으로 판정했습니다."));
        assertThat(registry()).hasSize(3);
        // 이미 등록된 항목은 미흡 확정 등록 후보에 나오지 않고, 정상으로 제출했지만 감사부서가 미흡 확정한 건만 나온다
        mvc.perform(post("/requests/items/" + item23 + "/review").with(user(dept)).with(csrf())
                .param("decision", "FINDING").param("confirmedResult", "INSUFFICIENT").param("findingDetail", "x"));
        mvc.perform(post("/requests/items/" + item24 + "/review").with(user(dept)).with(csrf())
                .param("decision", "FINDING").param("confirmedResult", "INSUFFICIENT").param("findingDetail", "정상이라 했지만 미흡"));
        assertThat(improvementService.findOpenFindings()).extracting(r -> r.getSeqNo()).containsExactly(24);
    }

    @Test
    void 자동_등록_건은_점검_결과와_내용을_고칠_수_없지만_담당과_조치는_바꿀_수_있다() throws Exception {
        submitToDept();
        Improvement m = registry().stream().filter(x -> x.getItems().get(0).getSeqNo() == 23).findFirst().orElseThrow();

        String html = mvc.perform(get("/improvements/" + m.getImprovementId()).with(user(lead)))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("분기 점검 제출 시 자동 등록").contains("고칠 수 없습니다");
        // 속성 순서와 관계없이 점검 결과 select 와 내용 textarea 가 비활성
        assertThat(html).containsPattern("<select[^>]*(name=\"inspectionResult\"[^>]*disabled|disabled[^>]*name=\"inspectionResult\")");
        assertThat(html).containsPattern("<textarea[^>]*(name=\"detail\"[^>]*disabled|disabled[^>]*name=\"detail\")");

        // 주관자가 점검 결과·내용을 바꾸려 해도 무시, 개선 방안·예정일은 수정 가능
        mvc.perform(post("/improvements/" + m.getImprovementId()).with(user(lead)).with(csrf())
                .param("inspectionResult", "NEEDS_IMPROVEMENT").param("detail", "바꾼 내용")
                .param("teamCode", "INFRA").param("assigneeId", String.valueOf(kim.getUserId()))
                .param("improvementPlan", "분기별 점검").param("plannedDate", "2027-03-31")
                .param("actionResult", "PLANNED"));
        Improvement after = improvementService.get(m.getImprovementId(), lead);
        assertThat(after.getInspectionResult()).isEqualTo(InspectionResult.INSUFFICIENT);
        assertThat(after.getDetail()).isEqualTo("인사이동 1건 미회수");
        assertThat(after.getImprovementPlan()).isEqualTo("분기별 점검");
        assertThat(after.getPlannedDate()).isEqualTo(LocalDate.of(2027, 3, 31));

        // 담당자는 조치를 갱신하고, 완료 후 주관자가 종결한다
        mvc.perform(post("/improvements/" + m.getImprovementId()).with(user(kim)).with(csrf())
                .param("improvementPlan", "분기별 점검").param("plannedDate", "2027-03-31")
                .param("actionResult", "DONE").param("actionStatus", "절차서 개정 완료"));
        mvc.perform(post("/improvements/" + m.getImprovementId() + "/close").with(user(lead)).with(csrf()));
        assertThat(improvementService.get(m.getImprovementId(), lead).isClosed()).isTrue();
    }

    @Test
    void 아무도_손대지_않은_자동_등록_개선사항은_회차와_함께_삭제된다() throws Exception {
        submitToDept();
        assertThat(roundMapper.findRound(roundId).getImprovementCount()).as("삭제를 막는 건수").isZero();

        mvc.perform(post("/rounds/delete").with(user(lead)).with(csrf()).param("roundIds", String.valueOf(roundId)))
                .andExpect(flash().attribute("message", Matchers.containsString("회차 1건")));
        assertThat(roundMapper.findRound(roundId)).isNull();
        assertThat(registry()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM improvement_item", Integer.class)).isZero();
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_DELETE))
                .anyMatch(l -> l.getDetail().contains("자동 등록 개선사항 3건 함께 삭제"));
    }

    @Test
    void 조치가_진행된_자동_등록_개선사항이_있으면_회차를_삭제할_수_없다() throws Exception {
        submitToDept();
        Improvement m = registry().get(0);
        mvc.perform(post("/improvements/" + m.getImprovementId()).with(user(kim)).with(csrf())
                .param("improvementPlan", "x").param("plannedDate", "2027-03-31")
                .param("actionResult", "IN_PROGRESS").param("actionStatus", "착수"));
        assertThat(roundMapper.findRound(roundId).getImprovementCount()).isEqualTo(1);

        mvc.perform(post("/rounds/delete").with(user(lead)).with(csrf()).param("roundIds", String.valueOf(roundId)))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("조치가 진행·최종확정되었거나")));
        assertThat(roundMapper.findRound(roundId)).isNotNull();
        assertThat(registry()).hasSize(3);

        // 코멘트가 달린 건도 막는다
        Improvement other = registry().stream().filter(x -> !x.getImprovementId().equals(m.getImprovementId())).findFirst().orElseThrow();
        mvc.perform(post("/comments").with(user(lead)).with(csrf()).param("targetType", "IMPROVEMENT")
                .param("targetId", String.valueOf(other.getImprovementId())).param("body", "의견"));
        assertThat(roundMapper.findRound(roundId).getImprovementCount()).isEqualTo(2);
    }
}
