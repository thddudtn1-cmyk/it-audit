package com.itaudit.improvement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.support.TestChecklist;
import com.itaudit.support.TestRounds;
import com.itaudit.support.TestUsers;

/** 조치 결과가 '완료'로 올라오면 주관자가 검토해 최종확정하거나 이전 단계로 되돌린다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CompletionReviewTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired TestChecklist testChecklist;
    @Autowired TestRounds testRounds;
    @Autowired ImprovementService service;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired JdbcTemplate jdbc;

    LoginUser lead;
    LoginUser kim;
    LoginUser dept;
    Long id;

    @BeforeEach
    void setUp() throws Exception {
        lead = testUsers.user("cr-lead", Role.AUDIT_LEAD);
        dept = testUsers.user("cr-dept", Role.AUDIT_DEPT);
        testUsers.team("INFRA", "인프라팀");
        kim = testUsers.inspector("cr-kim", "INFRA");
        testChecklist.load(2026, lead);
        Long roundId = testRounds.create(2026, 3, lead);
        jdbc.update("INSERT INTO improvement (kind, origin, round_id, team_code, assignee_id, inspection_result, detail, improvement_plan, "
                + "action_result, closed_yn, created_by, created_at, updated_at) VALUES ('IMPROVEMENT', 'ROUND_SUBMIT', ?, 'INFRA', ?, "
                + "'INSUFFICIENT', '미회수', '절차 보완', 'IN_PROGRESS', 'N', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                roundId, kim.getUserId(), lead.getUserId());
        id = jdbc.queryForObject("SELECT MAX(improvement_id) FROM improvement", Long.class);
    }

    private MockHttpServletRequestBuilder complete(LoginUser u) {
        return post("/improvements/" + id).with(user(u)).with(csrf()).param("improvementPlan", "절차 보완")
                .param("actionResult", "DONE").param("actionStatus", "절차서 개정 완료");
    }

    private Improvement current() {
        return service.get(id, lead);
    }

    @Test
    void 담당자가_완료로_올리면_검토_대기가_되고_담당자는_더_수정할_수_없다() throws Exception {
        mvc.perform(complete(kim));
        Improvement m = current();
        assertThat(m.getActionResult()).isEqualTo(ActionResult.DONE);
        assertThat(m.isReviewPending()).isTrue();
        assertThat(m.isClosed()).isFalse();

        // 담당자: 검토 대기 안내, 수정 불가
        mvc.perform(get("/improvements/" + id).with(user(kim)))
                .andExpect(content().string(Matchers.containsString("완료 · 주관자 검토 대기")))
                .andExpect(content().string(Matchers.containsString("주관자가 검토 중이라 수정할 수 없고")))
                .andExpect(content().string(Matchers.not(Matchers.containsString(">저장</button>"))));
        mvc.perform(post("/improvements/" + id).with(user(kim)).with(csrf()).param("actionResult", "IN_PROGRESS"))
                .andExpect(content().string(Matchers.containsString("주관자가 검토 중입니다")));
        assertThat(current().getActionResult()).isEqualTo(ActionResult.DONE);
        mvc.perform(get("/my-tasks").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("주관자 검토 중")));

        // 주관자: 검토 패널(최종확정·되돌리기), 목록 태그, 대시보드 대기 건수, 필터
        mvc.perform(get("/improvements/" + id).with(user(lead)))
                .andExpect(content().string(Matchers.containsString("완료 검토")))
                .andExpect(content().string(Matchers.containsString("이전 단계로 되돌리기")));
        mvc.perform(get("/improvements").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("검토 대기")));
        mvc.perform(get("/improvements").param("reviewPendingOnly", "true").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("절차서 개정 완료")));
        mvc.perform(get("/dashboard").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("완료 검토 대기")))
                .andExpect(content().string(Matchers.containsString("kpi--attention")));
    }

    @Test
    void 진행중인_건에는_검토_필터에_나오지_않는다() throws Exception {
        mvc.perform(get("/improvements").param("reviewPendingOnly", "true").with(user(lead)))
                .andExpect(content().string(Matchers.containsString("해당하는 건이 없습니다")));
        mvc.perform(get("/improvements/" + id).with(user(lead)))
                .andExpect(content().string(Matchers.containsString("담당자가 조치 결과를 '완료'로 올리면 여기서 검토")));
    }

    @Test
    void 주관자가_최종확정하면_확정되고_수정할_수_없다() throws Exception {
        mvc.perform(complete(kim));
        mvc.perform(post("/improvements/" + id + "/close").param("comment", "증빙 확인").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("message", "최종확정했습니다."));
        Improvement m = current();
        assertThat(m.isClosed()).isTrue();
        assertThat(m.isReviewPending()).isFalse();
        assertThat(m.getClosedBy()).isEqualTo(lead.getUserId());
        assertThat(m.getCloseComment()).isEqualTo("증빙 확인");
        assertThat(activityLogMapper.findByAction(ActivityLogService.IMPROVEMENT_CLOSE))
                .anyMatch(l -> l.getDetail().contains("최종 확정") && l.getDetail().contains("증빙 확인"));
        mvc.perform(get("/improvements/" + id).with(user(kim)))
                .andExpect(content().string(Matchers.containsString("최종확정")));
        mvc.perform(post("/improvements/" + id).with(user(kim)).with(csrf()).param("actionResult", "IN_PROGRESS"))
                .andExpect(content().string(Matchers.containsString("최종확정된 건은 수정할 수 없습니다")));

        // 최종확정 취소 → 완료 검토 대기로 복귀
        mvc.perform(post("/improvements/" + id + "/reopen").param("reason", "증빙 보완 필요").with(user(lead)).with(csrf()))
                .andExpect(flash().attribute("message", "최종확정을 취소했습니다. 완료 검토 대기 상태로 돌아갑니다."));
        assertThat(current().isReviewPending()).isTrue();
    }

    @Test
    void 주관자가_이전_단계로_되돌리면_사유가_담당자에게_보이고_다시_수정할_수_있다() throws Exception {
        mvc.perform(complete(kim));
        mvc.perform(post("/improvements/" + id + "/return").with(user(lead)).with(csrf())
                        .param("target", "IN_PROGRESS").param("reason", "증빙 캡처가 부족합니다"))
                .andExpect(flash().attribute("message", Matchers.containsString("이전 단계(진행중)로 되돌렸습니다")));
        Improvement m = current();
        assertThat(m.getActionResult()).isEqualTo(ActionResult.IN_PROGRESS);
        assertThat(m.isClosed()).isFalse();
        assertThat(m.isReturned()).isTrue();
        assertThat(m.getReturnComment()).isEqualTo("증빙 캡처가 부족합니다");
        assertThat(m.getReturnedBy()).isEqualTo(lead.getUserId());
        assertThat(activityLogMapper.findByAction(ActivityLogService.IMPROVEMENT_RETURN))
                .anyMatch(l -> l.getDetail().contains("완료 반려 → 진행중") && l.getDetail().contains("증빙 캡처가 부족합니다"));

        // 담당자 화면(상세·내 할 일)에 반려 사유, 다시 수정 가능
        mvc.perform(get("/improvements/" + id).with(user(kim)))
                .andExpect(content().string(Matchers.containsString("주관자가 완료를 반려하고 진행중로 되돌렸습니다")))
                .andExpect(content().string(Matchers.containsString("사유: 증빙 캡처가 부족합니다")));
        mvc.perform(get("/my-tasks").with(user(kim)))
                .andExpect(content().string(Matchers.containsString("반려 사유: 증빙 캡처가 부족합니다")));
        mvc.perform(post("/improvements/" + id).with(user(kim)).with(csrf()).param("improvementPlan", "절차 보완")
                .param("actionResult", "IN_PROGRESS").param("actionStatus", "캡처 보완 중"));
        assertThat(current().getActionStatus()).isEqualTo("캡처 보완 중");
        assertThat(current().isReturned()).as("완료로 다시 올리기 전까지 사유 유지").isTrue();

        // 다시 완료로 올리면 반려 사유는 지워진다
        mvc.perform(complete(kim));
        Improvement again = current();
        assertThat(again.isReviewPending()).isTrue();
        assertThat(again.getReturnComment()).isNull();
        assertThat(again.isReturned()).isFalse();
    }

    @Test
    void 예정_단계로도_되돌릴_수_있다() throws Exception {
        mvc.perform(complete(kim));
        mvc.perform(post("/improvements/" + id + "/return").with(user(lead)).with(csrf())
                .param("target", "PLANNED").param("reason", "착수 전 확인 필요"));
        assertThat(current().getActionResult()).isEqualTo(ActionResult.PLANNED);
    }

    @Test
    void 완료가_아니거나_사유_단계가_없으면_되돌릴_수_없다() throws Exception {
        // 진행중인 건
        mvc.perform(post("/improvements/" + id + "/return").with(user(lead)).with(csrf())
                        .param("target", "PLANNED").param("reason", "x"))
                .andExpect(flash().attribute("errorMessage", "조치 결과가 '완료'인 건만 검토할 수 있습니다."));
        mvc.perform(complete(kim));
        mvc.perform(post("/improvements/" + id + "/return").with(user(lead)).with(csrf()).param("target", "IN_PROGRESS"))
                .andExpect(flash().attribute("errorMessage", "반려 사유을(를) 입력하세요."));
        mvc.perform(post("/improvements/" + id + "/return").with(user(lead)).with(csrf()).param("reason", "사유"))
                .andExpect(flash().attribute("errorMessage", "되돌릴 이전 단계(진행중·예정)를 고르세요."));
        mvc.perform(post("/improvements/" + id + "/return").with(user(lead)).with(csrf())
                        .param("target", "DONE").param("reason", "사유"))
                .andExpect(flash().attribute("errorMessage", "되돌릴 이전 단계(진행중·예정)를 고르세요."));
        assertThat(current().isReviewPending()).isTrue();

        // 최종확정된 건은 먼저 취소해야 한다
        mvc.perform(post("/improvements/" + id + "/close").with(user(lead)).with(csrf()));
        mvc.perform(post("/improvements/" + id + "/return").with(user(lead)).with(csrf())
                        .param("target", "IN_PROGRESS").param("reason", "사유"))
                .andExpect(flash().attribute("errorMessage", "이미 최종확정된 건입니다. 최종확정을 취소한 뒤 검토하세요."));
    }

    @Test
    void 검토는_주관자만_할_수_있다() throws Exception {
        mvc.perform(complete(kim));
        for (LoginUser u : List.of(kim, dept, testUsers.user("cr-viewer", Role.VIEWER))) {
            mvc.perform(post("/improvements/" + id + "/return").with(user(u)).with(csrf())
                    .param("target", "IN_PROGRESS").param("reason", "x")).andExpect(status().isForbidden());
            mvc.perform(post("/improvements/" + id + "/close").with(user(u)).with(csrf())).andExpect(status().isForbidden());
        }
        assertThat(current().isReviewPending()).isTrue();
    }

    // ---------- 관리대장 목록 → 상세에서 검토 ----------

    @Test
    void 목록_행을_누르면_상세로_이동하고_검토_대기_건은_검토하기_링크가_보인다() throws Exception {
        // 진행중: 행은 클릭 가능하지만 검토하기 링크는 없다
        String before = mvc.perform(get("/improvements").with(user(lead))).andReturn().getResponse().getContentAsString();
        assertThat(before).contains("완료 검토").contains("class=\"row--clickable\"")
                .contains("data-href=\"/improvements/" + id + "\"").doesNotContain("검토하기");

        mvc.perform(complete(kim));
        String html = mvc.perform(get("/improvements").with(user(lead))).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-href=\"/improvements/" + id + "\"").contains("검토하기").contains("row--review");
        // 목록에는 인라인 최종확정·반려 폼이 없다 (검토는 상세 화면에서)
        assertThat(html).doesNotContain("action=\"/improvements/" + id + "/close\"")
                .doesNotContain("action=\"/improvements/" + id + "/return\"").doesNotContain("검토완료(최종확정)");
        assertThat(html).contains("round.js");

        // 다른 역할 목록에도 행 이동은 되지만 검토 열·링크는 없다 (담당자는 본인 건 상세로)
        for (LoginUser u : List.of(kim, dept, testUsers.user("cr-viewer2", Role.VIEWER))) {
            String other = mvc.perform(get("/improvements").with(user(u))).andReturn().getResponse().getContentAsString();
            assertThat(other).contains("data-href=\"/improvements/" + id + "\"")
                    .doesNotContain("검토하기").doesNotContain("완료 검토</th>");
        }
    }

    @Test
    void 상세로_들어가_최종확정할_수_있다() throws Exception {
        mvc.perform(complete(kim));
        String detail = mvc.perform(get("/improvements/" + id).with(user(lead))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("action=\"/improvements/" + id + "/close\"")
                .contains("action=\"/improvements/" + id + "/return\"").contains(">최종확정</button>").contains(">되돌리기</button>");

        mvc.perform(post("/improvements/" + id + "/close").with(user(lead)).with(csrf()).param("comment", "확인"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/improvements/" + id))
                .andExpect(flash().attribute("message", "최종확정했습니다."));
        assertThat(current().isClosed()).isTrue();
        // 최종확정된 건의 목록 행에는 '검토하기'가 없고 '최종확정' 표시
        mvc.perform(get("/improvements").param("closed", "ALL").with(user(lead)))
                .andExpect(content().string(Matchers.not(Matchers.containsString("검토하기"))))
                .andExpect(content().string(Matchers.containsString("최종확정")));
    }

    @Test
    void 상세로_들어가_반려할_수_있다() throws Exception {
        mvc.perform(complete(kim));
        mvc.perform(post("/improvements/" + id + "/return").with(user(lead)).with(csrf())
                        .param("target", "IN_PROGRESS").param("reason", "상세에서 반려"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/improvements/" + id))
                .andExpect(flash().attribute("message", Matchers.containsString("이전 단계(진행중)로 되돌렸습니다")));
        assertThat(current().getReturnComment()).isEqualTo("상세에서 반려");
        mvc.perform(get("/improvements").with(user(lead))).andExpect(content().string(Matchers.containsString("반려됨")));
    }
}
