package com.itaudit.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewFlowTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired RoundService roundService;
    @Autowired RoundMapper roundMapper;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired JdbcTemplate jdbc;

    LoginUser lead;
    LoginUser kim;
    LoginUser lee;
    Long roundId;
    Long item23;
    Long item24;

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("v-lead", Role.AUDIT_LEAD);
        testUsers.team("INFRA", "인프라팀");
        kim = testUsers.inspector("v-kim", "INFRA");
        lee = testUsers.inspector("v-lee", "INFRA");
        testChecklist.load(2026, lead);
        RoundCreateForm form = new RoundCreateForm();
        form.setYear(2026);
        form.setQuarter(3);
        roundId = roundService.create(form, lead);
        item23 = itemId(23);
        item24 = itemId(24);
        jdbc.update("UPDATE round_item SET team_code = 'INFRA', assignee_id = ? WHERE round_item_id = ?", kim.getUserId(), item23);
        jdbc.update("UPDATE round_item SET team_code = 'INFRA', assignee_id = ? WHERE round_item_id = ?", lee.getUserId(), item24);
        // 검토 흐름만 보기 위해 나머지 항목은 회신불필요로
        jdbc.update("UPDATE round_item SET no_reply_yn = 'Y' WHERE round_id = ? AND round_item_id NOT IN (?, ?)",
                roundId, item23, item24);
    }

    private Long itemId(int seqNo) {
        return roundMapper.findItems(roundId).stream().filter(i -> i.getSeqNo() == seqNo).findFirst().orElseThrow().getRoundItemId();
    }

    private void submitInspection(Long itemId, LoginUser u) throws Exception {
        mvc.perform(multipart("/inspections/" + itemId).with(user(u)).with(csrf())
                .param("action", "submit").param("inspectionResult", "NORMAL").param("inspectionDetail", "확인 완료"));
    }

    private RoundStatus roundStatus() {
        return roundMapper.findRound(roundId).getStatus();
    }

    private String base() {
        return "/rounds/" + roundId;
    }

    @Test
    void 제출_대상이_모두_모이면_검토중이_되고_반려되면_작성중으로_돌아간다() throws Exception {
        assertThat(roundStatus()).isEqualTo(RoundStatus.DRAFT);
        submitInspection(item23, kim);
        assertThat(roundStatus()).isEqualTo(RoundStatus.DRAFT);
        submitInspection(item24, lee);
        assertThat(roundStatus()).isEqualTo(RoundStatus.REVIEW);

        mvc.perform(post(base() + "/items/" + item24 + "/reject").param("reason", "증적 캡처가 흐립니다")
                .with(user(lead)).with(csrf()));
        assertThat(roundStatus()).isEqualTo(RoundStatus.DRAFT);
        RoundItem rejected = roundMapper.findItem(item24);
        assertThat(rejected.getSubmitStatus()).isEqualTo(SubmitStatus.REJECTED);
        assertThat(rejected.getReviewComment()).isEqualTo("증적 캡처가 흐립니다");

        // 담당자 화면에 반려 사유가 보이고 다시 제출할 수 있다
        mvc.perform(get("/my-tasks").with(user(lee)))
                .andExpect(content().string(Matchers.containsString("반려 사유: 증적 캡처가 흐립니다")));
        mvc.perform(get("/inspections/" + item24).with(user(lee)))
                .andExpect(content().string(Matchers.containsString("IT자체감사 담당자가 반려했습니다")));
        submitInspection(item24, lee);
        assertThat(roundStatus()).isEqualTo(RoundStatus.REVIEW);
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_STATUS))
                .extracting(l -> l.getDetail()).anyMatch(d -> d.endsWith("검토중 → 작성중"));
    }

    @Test
    void 반려에는_사유가_필요하다() throws Exception {
        submitInspection(item23, kim);
        mvc.perform(post(base() + "/items/" + item23 + "/reject").param("reason", "  ").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "반려 사유를 입력하세요."));
        assertThat(roundMapper.findItem(item23).getSubmitStatus()).isEqualTo(SubmitStatus.SUBMITTED);
    }

    @Test
    void 제출된_항목만_확인할_수_있고_확인을_취소할_수_있다() throws Exception {
        mvc.perform(post(base() + "/items/" + item23 + "/confirm").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("제출된 항목만 확인")));

        submitInspection(item23, kim);
        mvc.perform(post(base() + "/items/" + item23 + "/confirm").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("message", "확인했습니다."));
        assertThat(roundMapper.findItem(item23).getSubmitStatus()).isEqualTo(SubmitStatus.CONFIRMED);
        assertThat(roundMapper.findItem(item23).getReviewedBy()).isEqualTo(lead.getUserId());

        mvc.perform(post(base() + "/items/" + item23 + "/cancel-confirm").with(user(lead)).with(csrf()));
        assertThat(roundMapper.findItem(item23).getSubmitStatus()).isEqualTo(SubmitStatus.SUBMITTED);
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_ITEM_CONFIRM_CANCEL)).hasSize(1);
    }

    @Test
    void 다른_회차_주소로는_검토할_수_없다() throws Exception {
        submitInspection(item23, kim);
        mvc.perform(post("/rounds/999999/items/" + item23 + "/confirm").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "이 회차의 항목이 아닙니다."));
        assertThat(roundMapper.findItem(item23).getSubmitStatus()).isEqualTo(SubmitStatus.SUBMITTED);
    }

    @Test
    void 모두_확인해야_감사부서에_제출할_수_있고_제출하면_잠긴다() throws Exception {
        mvc.perform(post(base() + "/submit").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("아직 제출되지 않은 항목이 2건")));

        submitInspection(item23, kim);
        submitInspection(item24, lee);
        mvc.perform(get(base()).with(user(lead)))
                .andExpect(content().string(Matchers.containsString("확인하지 않은 제출 항목이 2건")))
                .andExpect(content().string(Matchers.containsString("제출 항목 일괄 확인 (2건)")));
        mvc.perform(post(base() + "/submit").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("확인하지 않은 제출 항목이 2건")));

        mvc.perform(post(base() + "/confirm-all").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("message", "2건을 확인했습니다."));
        assertThat(roundMapper.findRound(roundId).isReadyToSubmit()).isTrue();

        mvc.perform(post(base() + "/submit").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("message", Matchers.containsString("감사부서에 제출했습니다")));
        AuditRound round = roundMapper.findRound(roundId);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.SUBMITTED);
        assertThat(round.getSubmittedBy()).isEqualTo(lead.getUserId());
        assertThat(round.getSubmittedAt()).isNotNull();
        assertThat(activityLogMapper.findByAction(ActivityLogService.ROUND_SUBMIT)).hasSize(1);

        // 잠금: 검토 상태 변경·배정 편집 불가, 감사부서 열람 가능
        mvc.perform(post(base() + "/items/" + item23 + "/reject").param("reason", "x").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", Matchers.containsString("검토 상태를 바꿀 수 없습니다")));
        mvc.perform(get(base()).with(user(lead)))
                .andExpect(content().string(Matchers.containsString("감사부서에 제출되어 잠긴 회차입니다")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("배정 편집"))));
        mvc.perform(get(base()).with(user(testUsers.user("v-dept", Role.AUDIT_DEPT)))).andExpect(status().isOk());
    }

    @Test
    void 배정에서_제외_회신불필요를_바꿔도_상태가_다시_계산된다() throws Exception {
        submitInspection(item23, kim);
        assertThat(roundStatus()).isEqualTo(RoundStatus.DRAFT);
        // #24 를 회신불필요로 바꾸면 제출 대상이 모두 모인 셈
        jdbc.update("UPDATE round_item SET no_reply_yn = 'N' WHERE round_item_id = ?", item24);
        List<RoundItem> items = roundMapper.findItems(roundId);
        var req = post(base() + "/assign").with(user(lead)).with(csrf());
        for (int i = 0; i < items.size(); i++) {
            RoundItem it = items.get(i);
            String p = "rows[" + i + "].";
            req.param(p + "roundItemId", String.valueOf(it.getRoundItemId()))
                    .param(p + "teamCode", it.getTeamCode() == null ? "" : it.getTeamCode())
                    .param(p + "assigneeId", it.getAssigneeId() == null ? "" : String.valueOf(it.getAssigneeId()));
            if (it.isNoReply() || it.getRoundItemId().equals(item24)) {
                req.param(p + "noReply", "true");
            }
        }
        mvc.perform(req);
        assertThat(roundStatus()).isEqualTo(RoundStatus.REVIEW);
    }

    @Test
    void 검토와_제출은_주관자만_한다() throws Exception {
        submitInspection(item23, kim);
        for (LoginUser u : List.of(kim, testUsers.user("v-viewer", Role.VIEWER), testUsers.user("v-dept2", Role.AUDIT_DEPT))) {
            mvc.perform(post(base() + "/items/" + item23 + "/confirm").with(user(u)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post(base() + "/items/" + item23 + "/reject").param("reason", "x").with(user(u)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(post(base() + "/confirm-all").with(user(u)).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post(base() + "/submit").with(user(u)).with(csrf())).andExpect(status().isForbidden());
        }
        assertThat(roundMapper.findItem(item23).getSubmitStatus()).isEqualTo(SubmitStatus.SUBMITTED);
    }
}
