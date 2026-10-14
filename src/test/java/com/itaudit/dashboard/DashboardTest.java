package com.itaudit.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

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
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestRounds;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired TestRounds testRounds;
    @Autowired DashboardService service;
    @Autowired JdbcTemplate jdbc;

    private String page(LoginUser u, String query) throws Exception {
        return mvc.perform(get("/dashboard" + query).with(user(u))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 회차가_없어도_대시보드가_열린다() throws Exception {
        LoginUser lead = testUsers.user("d-lead0", Role.AUDIT_LEAD);
        assertThat(page(lead, "")).contains("현황 대시보드").contains("회차가 없습니다")
                .contains("개선예정일이 지났거나 임박한 건이 없습니다");
    }

    @Test
    void 회차_진척률_담당자_제출률_점검결과_조치결과_경과_목록을_보여준다() throws Exception {
        LoginUser lead = testUsers.user("d-lead", Role.AUDIT_LEAD);
        LoginUser dept = testUsers.user("d-dept", Role.AUDIT_DEPT);
        testUsers.team("INFRA", "인프라팀");
        LoginUser kim = testUsers.inspector("d-kim", "INFRA");
        testChecklist.load(2026, lead);
        Long q2 = testRounds.create(2026, 2, lead);
        Long q3 = testRounds.create(2026, 3, lead);

        // 3분기: #23 확인(미흡), #24 미제출(배정), 나머지는 회신불필요
        Long i23 = testRounds.confirmItem(q3, 23, kim, "INFRA", "INSUFFICIENT", "미회수", "보완", "2026-12-31");
        Long i24 = testRounds.itemId(q3, 24);
        jdbc.update("UPDATE round_item SET team_code = 'INFRA', assignee_id = ? WHERE round_item_id = ?", kim.getUserId(), i24);
        jdbc.update("UPDATE round_item SET no_reply_yn = 'Y' WHERE round_id = ? AND round_item_id NOT IN (?, ?)", q3, i23, i24);

        // 개선사항: 경과 1건, 임박 1건, 종결 1건
        insertImprovement(q3, kim, LocalDate.now().minusDays(10), "IN_PROGRESS", "N");
        insertImprovement(q3, kim, LocalDate.now().plusDays(2), "PLANNED", "N");
        insertImprovement(q3, kim, LocalDate.now().minusDays(30), "DONE", "Y");

        String html = page(lead, "?roundId=" + q3);
        assertThat(html)
                .contains("2026년 3분기 제출률").contains("50%").contains("1 / 2건")
                .contains("담당자별 제출 현황 · 2026년 3분기").contains("d-kim 사용자").contains("1 / 2")
                .contains("점검결과별 건수").contains("미흡")
                .contains("개선예정일 경과 1건 · 7일 이내 임박 1건").contains("10일 경과").contains("D-2")
                .contains("kpi--critical");

        assertThat(service.resultStats(q3)).filteredOn(r -> r.getKey().equals("INSUFFICIENT"))
                .extracting(CountRow::getValue).containsExactly(1);
        assertThat(service.actionStats()).extracting(CountRow::getKey, CountRow::getValue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PLANNED", 1), org.assertj.core.groups.Tuple.tuple("IN_PROGRESS", 1),
                        org.assertj.core.groups.Tuple.tuple("DONE", 0), org.assertj.core.groups.Tuple.tuple("CLOSED", 1));

        // 기본 선택 = 가장 최근의 진행 중 회차
        assertThat(service.pickRound(service.findRounds(lead), null).getRoundId()).isEqualTo(q3);

        // 감사부서는 제출된 회차만 고를 수 있다
        jdbc.update("UPDATE audit_round SET status = 'SUBMITTED' WHERE round_id = ?", q2);
        List<Long> deptRounds = service.findRounds(dept).stream().map(r -> r.getRoundId()).toList();
        assertThat(deptRounds).containsExactly(q2);
        assertThat(page(dept, "")).contains("2026년 2분기").doesNotContain("2026년 3분기 제출률");
    }

    private void insertImprovement(Long roundId, LoginUser assignee, LocalDate planned, String action, String closed) {
        jdbc.update("INSERT INTO improvement (kind, round_id, team_code, assignee_id, inspection_result, detail, improvement_plan, "
                        + "planned_date, action_result, closed_yn, created_by, created_at, updated_at) VALUES "
                        + "('IMPROVEMENT', ?, 'INFRA', ?, 'INSUFFICIENT', '내용', '방안', ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                roundId, assignee.getUserId(), java.sql.Date.valueOf(planned), action, closed, assignee.getUserId());
    }

    @Test
    void 대시보드는_주관자_감사부서_조회자만_본다() throws Exception {
        for (Role role : List.of(Role.AUDIT_LEAD, Role.AUDIT_DEPT, Role.VIEWER)) {
            mvc.perform(get("/dashboard").with(user(testUsers.user("d-" + role.name().toLowerCase(), role))))
                    .andExpect(status().isOk());
        }
        for (Role role : List.of(Role.ADMIN, Role.INSPECTOR)) {
            mvc.perform(get("/dashboard").with(user(testUsers.user("dx-" + role.name().toLowerCase(), role))))
                    .andExpect(status().isForbidden());
        }
    }
}
