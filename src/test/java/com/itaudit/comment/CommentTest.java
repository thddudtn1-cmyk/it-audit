package com.itaudit.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import com.itaudit.support.TestRounds;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommentTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired TestRounds testRounds;
    @Autowired CommentMapper mapper;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired JdbcTemplate jdbc;

    LoginUser lead;
    LoginUser dept;
    LoginUser kim;
    LoginUser lee;
    LoginUser viewer;
    Long roundId;
    Long item23;
    Long improvementId;

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("c-lead", Role.AUDIT_LEAD);
        dept = testUsers.user("c-dept", Role.AUDIT_DEPT);
        viewer = testUsers.user("c-viewer", Role.VIEWER);
        testUsers.team("INFRA", "인프라팀");
        kim = testUsers.inspector("c-kim", "INFRA");
        lee = testUsers.inspector("c-lee", "INFRA");
        testChecklist.load(2026, lead);
        roundId = testRounds.create(2026, 3, lead);
        item23 = testRounds.confirmItem(roundId, 23, kim, "INFRA", "INSUFFICIENT", "1건 미회수", "보완", "2026-12-31");
        testRounds.submitToDept(roundId);
        jdbc.update("INSERT INTO improvement (kind, round_id, team_code, assignee_id, inspection_result, detail, improvement_plan, "
                + "action_result, closed_yn, created_by, created_at, updated_at) VALUES ('IMPROVEMENT', ?, 'INFRA', ?, 'INSUFFICIENT', "
                + "'미삭제', '정리', 'PLANNED', 'N', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", roundId, kim.getUserId(), lead.getUserId());
        improvementId = jdbc.queryForObject("SELECT MAX(improvement_id) FROM improvement", Long.class);
        jdbc.update("INSERT INTO improvement_item (improvement_id, round_item_id) VALUES (?, ?)", improvementId, item23);
    }

    private void add(String type, Long id, String body, LoginUser u) throws Exception {
        mvc.perform(post("/comments").with(user(u)).with(csrf()).param("targetType", type)
                .param("targetId", String.valueOf(id)).param("body", body));
    }

    @Test
    void 점검항목에서_주관자_담당자_감사부서가_의견을_주고받는다() throws Exception {
        add("ROUND_ITEM", item23, "증적 캡처를 보완해 주세요", lead);
        add("ROUND_ITEM", item23, "보완 자료 업로드했습니다", kim);
        add("ROUND_ITEM", item23, "확인했습니다", dept);

        assertThat(mapper.findActive(CommentTarget.ROUND_ITEM, item23)).extracting(Comment::getBody)
                .containsExactly("증적 캡처를 보완해 주세요", "보완 자료 업로드했습니다", "확인했습니다");
        mvc.perform(get("/inspections/" + item23).with(user(kim)))
                .andExpect(content().string(Matchers.containsString("코멘트 3건")))
                .andExpect(content().string(Matchers.containsString("증적 캡처를 보완해 주세요")))
                .andExpect(content().string(Matchers.containsString("자체감사 주관자")));
        assertThat(activityLogMapper.findByAction(ActivityLogService.COMMENT_ADD)).hasSize(3);
    }

    @Test
    void 개선사항에도_코멘트를_남긴다() throws Exception {
        add("IMPROVEMENT", improvementId, "예정일을 앞당길 수 있나요?", lead);
        mvc.perform(get("/improvements/" + improvementId).with(user(kim)))
                .andExpect(content().string(Matchers.containsString("예정일을 앞당길 수 있나요?")));
    }

    @Test
    void 조회자는_읽기만_하고_쓸_수_없다() throws Exception {
        add("ROUND_ITEM", item23, "의견", lead);
        mvc.perform(get("/inspections/" + item23).with(user(viewer)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("의견")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("코멘트 남기기"))));
        mvc.perform(post("/comments").with(user(viewer)).with(csrf()).param("targetType", "ROUND_ITEM")
                        .param("targetId", String.valueOf(item23)).param("body", "x"))
                .andExpect(flash().attribute("errorMessage", "코멘트는 주관자·담당자·감사부서만 쓸 수 있습니다."));
        assertThat(mapper.findActive(CommentTarget.ROUND_ITEM, item23)).hasSize(1);
    }

    @Test
    void 볼_수_없는_대상에는_코멘트를_읽지도_쓰지도_못한다() throws Exception {
        add("ROUND_ITEM", item23, "비공개 의견", lead);
        // 다른 담당자 (본인 배정 아님)
        mvc.perform(post("/comments").with(user(lee)).with(csrf()).param("targetType", "ROUND_ITEM")
                .param("targetId", String.valueOf(item23)).param("body", "끼어들기")).andExpect(status().isForbidden());
        mvc.perform(post("/comments").with(user(lee)).with(csrf()).param("targetType", "IMPROVEMENT")
                .param("targetId", String.valueOf(improvementId)).param("body", "끼어들기")).andExpect(status().isForbidden());
        Long commentId = mapper.findActive(CommentTarget.ROUND_ITEM, item23).get(0).getCommentId();
        mvc.perform(post("/comments/" + commentId + "/delete").with(user(lee)).with(csrf())).andExpect(status().isForbidden());
        // 관리자도 점검 데이터를 보지 않는다
        mvc.perform(post("/comments").with(user(testUsers.user("c-admin", Role.ADMIN))).with(csrf())
                .param("targetType", "ROUND_ITEM").param("targetId", String.valueOf(item23)).param("body", "x"))
                .andExpect(status().isForbidden());
        assertThat(mapper.findActive(CommentTarget.ROUND_ITEM, item23)).hasSize(1);
    }

    @Test
    void 빈_내용과_너무_긴_내용은_거부한다() throws Exception {
        mvc.perform(post("/comments").with(user(lead)).with(csrf()).param("targetType", "ROUND_ITEM")
                        .param("targetId", String.valueOf(item23)).param("body", "   "))
                .andExpect(flash().attribute("errorMessage", "코멘트 내용을 입력하세요."));
        mvc.perform(post("/comments").with(user(lead)).with(csrf()).param("targetType", "ROUND_ITEM")
                        .param("targetId", String.valueOf(item23)).param("body", "가".repeat(2001)))
                .andExpect(flash().attribute("errorMessage", "코멘트는 2000자 이하여야 합니다."));
        assertThat(mapper.findActive(CommentTarget.ROUND_ITEM, item23)).isEmpty();
    }

    @Test
    void 삭제는_본인_글만_가능하고_주관자는_모두_삭제할_수_있으며_논리_삭제다() throws Exception {
        add("ROUND_ITEM", item23, "담당자 글", kim);
        add("ROUND_ITEM", item23, "감사부서 글", dept);
        Long kimComment = mapper.findActive(CommentTarget.ROUND_ITEM, item23).get(0).getCommentId();
        Long deptComment = mapper.findActive(CommentTarget.ROUND_ITEM, item23).get(1).getCommentId();

        mvc.perform(post("/comments/" + deptComment + "/delete").with(user(kim)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "본인이 쓴 코멘트만 삭제할 수 있습니다."));
        mvc.perform(post("/comments/" + kimComment + "/delete").with(user(kim)).with(csrf()))
                .andExpect(redirectedUrl("/inspections/" + item23 + "#comments"));
        mvc.perform(post("/comments/" + deptComment + "/delete").with(user(lead)).with(csrf()))
                .andExpect(redirectedUrl("/inspections/" + item23 + "#comments"));

        assertThat(mapper.findActive(CommentTarget.ROUND_ITEM, item23)).isEmpty();
        assertThat(mapper.findById(kimComment).getBody()).as("행은 남아 있음").isEqualTo("담당자 글");
        assertThat(mapper.findById(kimComment).getDeletedYn()).isEqualTo("Y");
        assertThat(activityLogMapper.findByAction(ActivityLogService.COMMENT_DELETE)).hasSize(2)
                .anyMatch(l -> l.getDetail().contains("담당자 글"));
    }

    @Test
    void 감사부서는_제출_전_회차_항목에는_코멘트할_수_없다() throws Exception {
        Long draftRound = testRounds.create(2026, 4, lead);
        Long draftItem = testRounds.itemId(draftRound, 33);
        mvc.perform(post("/comments").with(user(dept)).with(csrf()).param("targetType", "ROUND_ITEM")
                .param("targetId", String.valueOf(draftItem)).param("body", "x")).andExpect(status().isForbidden());
    }
}
