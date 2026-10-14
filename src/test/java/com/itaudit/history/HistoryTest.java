package com.itaudit.history;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
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
import com.itaudit.checklist.ChecklistMapper;
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestRounds;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HistoryTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired TestRounds testRounds;
    @Autowired ChecklistMapper checklistMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 항목_이력에_회차별_점검결과와_개선사항이_보인다() throws Exception {
        LoginUser lead = testUsers.user("h-lead", Role.AUDIT_LEAD);
        testUsers.team("INFRA", "인프라팀");
        LoginUser kim = testUsers.inspector("h-kim", "INFRA");
        testChecklist.load(2026, lead);
        Long roundId = testRounds.create(2026, 3, lead);
        Long item23 = testRounds.confirmItem(roundId, 23, kim, "INFRA", "INSUFFICIENT", "권한 미회수 1건", "보완", "2026-12-31");
        jdbc.update("INSERT INTO improvement (kind, round_id, team_code, assignee_id, inspection_result, detail, improvement_plan, "
                + "action_result, closed_yn, created_by, created_at, updated_at) VALUES ('IMPROVEMENT', ?, 'INFRA', ?, 'INSUFFICIENT', "
                + "'퇴사자 계정 미삭제', '계정 정리 절차', 'PLANNED', 'N', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                roundId, kim.getUserId(), lead.getUserId());
        Long impId = jdbc.queryForObject("SELECT MAX(improvement_id) FROM improvement", Long.class);
        jdbc.update("INSERT INTO improvement_item (improvement_id, round_item_id) VALUES (?, ?)", impId, item23);

        Long checklistId = checklistMapper.findBySeqNo(2026, 23).getItemId();
        mvc.perform(get("/checklist/items/" + checklistId + "/history").with(user(lead)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("2026년 3분기")))
                .andExpect(content().string(Matchers.containsString("권한 미회수 1건")))
                .andExpect(content().string(Matchers.containsString("퇴사자 계정 미삭제")));
        // 점검 입력 화면에도 관련 개선사항 표시
        mvc.perform(get("/inspections/" + item23).with(user(kim)))
                .andExpect(content().string(Matchers.containsString("관련 개선사항·권고사항")))
                .andExpect(content().string(Matchers.containsString("계정 정리 절차")));
    }
}
