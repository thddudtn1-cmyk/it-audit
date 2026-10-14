package com.itaudit.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.itaudit.auth.AppUser;
import com.itaudit.auth.AppUserMapper;
import com.itaudit.auth.LoginUser;
import com.itaudit.improvement.ActionResult;
import com.itaudit.improvement.Improvement;
import com.itaudit.improvement.ImprovementFilter;
import com.itaudit.improvement.ImprovementOrigin;
import com.itaudit.improvement.ImprovementService;
import com.itaudit.request.AuditRequestService;
import com.itaudit.request.ReviewState;
import com.itaudit.round.AuditRound;
import com.itaudit.round.RoundMapper;
import com.itaudit.round.RoundStatus;
import com.itaudit.round.SubmitStatus;

/** 빈 DB 로 뜰 때 시연용 데이터가 만들어지고, 데이터가 있으면 다시 만들지 않는다 (자체 인메모리 DB 사용) */
@SpringBootTest(properties = {"app.demo-login.enabled=true", "app.demo-data.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:itaudit-demo-seed;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoDataSeederTest {

    @Autowired DemoDataSeeder seeder;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RoundMapper roundMapper;
    @Autowired AppUserMapper userMapper;
    @Autowired ImprovementService improvementService;
    @Autowired AuditRequestService requestService;

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private LoginUser login(String loginId) {
        AppUser u = userMapper.findByLoginId(loginId);
        return new LoginUser(u);
    }

    @Test
    void 빈_DB에서_체크리스트와_회차_3개가_의도한_상태로_만들어진다() {
        int year = LocalDate.now().getYear();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checklist_item WHERE apply_year = ?", Integer.class, year)).isEqualTo(43);
        // #34~#40 의 감사 항목은 위 행 값을 이어받는다
        assertThat(jdbc.queryForObject("SELECT audit_item FROM checklist_item WHERE apply_year = ? AND seq_no = 38", String.class, year))
                .isEqualTo("프로그램 등록·변경·폐기 절차 수립 및 운용");

        List<AuditRound> rounds = roundMapper.findRounds(null);
        assertThat(rounds).extracting(AuditRound::getQuarter).containsExactly(3, 2, 1);
        Map<Integer, AuditRound> byQuarter = Map.of(1, rounds.get(2), 2, rounds.get(1), 3, rounds.get(0));
        assertThat(byQuarter.get(1).getStatus()).isEqualTo(RoundStatus.DONE);
        assertThat(byQuarter.get(2).getStatus()).isEqualTo(RoundStatus.SUBMITTED);
        assertThat(byQuarter.get(3).getStatus()).isEqualTo(RoundStatus.DRAFT);
        assertThat(byQuarter.get(1).getTargetCount()).isEqualTo(11);
        assertThat(byQuarter.get(2).getTargetCount()).isEqualTo(18);
        assertThat(byQuarter.get(3).getTargetCount()).as("#25 제외").isEqualTo(14);
        assertThat(byQuarter.get(3).getExcludedCount()).isEqualTo(1);
        assertThat(byQuarter.get(1).getNoReplyCount()).isEqualTo(1);

        // 3분기: 확인·제출·반려·임시저장·미입력이 섞여 있다
        List<SubmitStatus> q3 = roundMapper.findItems(byQuarter.get(3).getRoundId()).stream()
                .filter(i -> !i.isExcluded()).map(i -> i.getSubmitStatus()).toList();
        assertThat(q3).contains(SubmitStatus.CONFIRMED, SubmitStatus.SUBMITTED, SubmitStatus.REJECTED, SubmitStatus.NOT_SUBMITTED);
    }

    @Test
    void 개선사항은_제출_시점에_자동_등록되고_여러_상태로_만들어진다() {
        ImprovementFilter f = new ImprovementFilter();
        f.setKind(com.itaudit.improvement.ImprovementKind.IMPROVEMENT);
        f.setClosed(ImprovementFilter.Closed.ALL);
        List<Improvement> all = improvementService.findList(f, login("demo-lead"));
        assertThat(all).hasSize(6);
        assertThat(all).allMatch(m -> m.getOrigin() == ImprovementOrigin.ROUND_SUBMIT);

        Map<Integer, Improvement> bySeq = new java.util.HashMap<>();
        all.forEach(m -> bySeq.put(m.getItems().get(0).getSeqNo(), m));
        assertThat(bySeq.get(4).isClosed()).as("1분기 #4 최종확정").isTrue();
        assertThat(bySeq.get(7).getActionResult()).isEqualTo(ActionResult.IN_PROGRESS);
        assertThat(bySeq.get(7).isOverdue()).as("1분기 #7 예정일 경과").isTrue();
        assertThat(bySeq.get(11).isDueSoon()).as("2분기 #11 예정일 임박").isTrue();
        assertThat(bySeq.get(14).isReturned()).as("2분기 #14 반려됨").isTrue();
        assertThat(bySeq.get(14).getReturnComment()).contains("임계치 목록");
        assertThat(bySeq.get(19).isReviewPending()).as("2분기 #19 완료 검토 대기").isTrue();
        assertThat(bySeq.get(19).isOverdue()).isTrue();
        assertThat(bySeq.get(19).getEvidenceCount()).isEqualTo(1);
        assertThat(bySeq.get(34).getActionResult()).as("2분기 #34 예정").isEqualTo(ActionResult.PLANNED);

        f.setKind(com.itaudit.improvement.ImprovementKind.RECOMMENDATION);
        assertThat(improvementService.findList(f, login("demo-lead"))).hasSize(1);
    }

    @Test
    void 적정성_점검은_여러_상태가_섞여_있다() {
        AuditRound q2 = roundMapper.findRounds(null).stream().filter(r -> r.getQuarter() == 2).findFirst().orElseThrow();
        List<AuditRequestService.Target> targets = requestService.findTargets(q2.getRoundId());
        Map<ReviewState, Long> counts = new java.util.EnumMap<>(ReviewState.class);
        targets.forEach(t -> counts.merge(t.state(), 1L, Long::sum));
        assertThat(counts).containsEntry(ReviewState.PASSED, 5L).containsEntry(ReviewState.FINDING, 1L)
                .containsEntry(ReviewState.REQUESTED, 1L).containsEntry(ReviewState.SUBMITTED, 1L)
                .containsEntry(ReviewState.NOT_REVIEWED, 9L);
        assertThat(targets).as("18건 중 #16 회신불필요 제외").hasSize(17);
    }

    @Test
    void 팀과_담당자가_만들어지고_데모_담당자는_인프라운영팀에_배정되어_할_일이_보인다() throws Exception {
        assertThat(count("team")).isGreaterThanOrEqualTo(5);
        AppUser demo = userMapper.findByLoginId("demo-inspector");
        assertThat(demo.getTeamCode()).isEqualTo("INFRA");
        assertThat(userMapper.findByLoginId("demo-lee").getTeamCode()).isEqualTo("SEC");

        LoginUser inspector = new LoginUser(demo);
        mvc.perform(get("/my-tasks").with(user(inspector)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("적정성 점검 보완 요청 1건")))
                .andExpect(content().string(Matchers.containsString("내 개선사항")));
    }

    @Test
    void 대시보드와_주요_화면이_오류없이_열린다() throws Exception {
        LoginUser lead = login("demo-lead");
        for (String path : List.of("/dashboard", "/rounds", "/improvements", "/improvements?reviewPendingOnly=true", "/checklist",
                "/requests", "/audit-log")) {
            mvc.perform(get(path).with(user(lead))).andExpect(status().isOk());
        }
        mvc.perform(get("/dashboard").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("완료 검토 대기")));
        mvc.perform(get("/audit-log").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("점검 제출")));
    }

    @Test
    void 이미_데이터가_있으면_다시_만들지_않는다() throws Exception {
        int rounds = roundMapper.findRounds(null).size();
        int improvements = count("improvement");
        int checklist = count("checklist_item");
        ApplicationArguments args = new DefaultApplicationArguments();
        seeder.run(args);
        seeder.run(args);
        assertThat(roundMapper.findRounds(null)).hasSize(rounds);
        assertThat(count("improvement")).isEqualTo(improvements);
        assertThat(count("checklist_item")).isEqualTo(checklist);
    }
}
