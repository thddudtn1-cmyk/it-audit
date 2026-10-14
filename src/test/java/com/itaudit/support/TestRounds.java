package com.itaudit.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.itaudit.auth.LoginUser;
import com.itaudit.round.RoundCreateForm;
import com.itaudit.round.RoundMapper;
import com.itaudit.round.RoundService;

/** 회차 상태를 빠르게 만들어 주는 테스트 도우미 */
@Component
public class TestRounds {

    private final RoundService roundService;
    private final RoundMapper roundMapper;
    private final JdbcTemplate jdbc;

    public TestRounds(RoundService roundService, RoundMapper roundMapper, JdbcTemplate jdbc) {
        this.roundService = roundService;
        this.roundMapper = roundMapper;
        this.jdbc = jdbc;
    }

    public Long create(int year, int quarter, LoginUser lead) {
        RoundCreateForm form = new RoundCreateForm();
        form.setYear(year);
        form.setQuarter(quarter);
        return roundService.create(form, lead);
    }

    public Long itemId(Long roundId, int seqNo) {
        return roundMapper.findItems(roundId).stream().filter(i -> i.getSeqNo() == seqNo)
                .findFirst().orElseThrow().getRoundItemId();
    }

    /** 담당자 배정 + 점검결과 입력·확인까지 끝난 상태로 만든다 */
    public Long confirmItem(Long roundId, int seqNo, LoginUser inspector, String teamCode, String result,
                            String detail, String actionPlan, String plannedDate) {
        Long itemId = itemId(roundId, seqNo);
        jdbc.update("UPDATE round_item SET team_code = ?, assignee_id = ?, inspection_result = ?, inspection_detail = ?, "
                        + "action_plan = ?, planned_date = ?, submit_status = 'CONFIRMED' WHERE round_item_id = ?",
                teamCode, inspector.getUserId(), result, detail, actionPlan,
                plannedDate == null ? null : java.sql.Date.valueOf(plannedDate), itemId);
        return itemId;
    }

    /** 확인되지 않은 나머지 항목을 회신불필요로 돌리고 감사부서 제출 상태로 */
    public void submitToDept(Long roundId) {
        jdbc.update("UPDATE round_item SET no_reply_yn = 'Y' WHERE round_id = ? AND submit_status <> 'CONFIRMED'", roundId);
        jdbc.update("UPDATE audit_round SET status = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP WHERE round_id = ?", roundId);
    }
}
