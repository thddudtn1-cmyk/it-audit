package com.itaudit.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogService;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuditLogTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired ActivityLogService activityLog;

    private String page(LoginUser u, String query) throws Exception {
        return mvc.perform(get("/audit-log" + query).with(user(u))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void 행위_사용자_키워드로_걸러_보고_한글_이름으로_표시한다() throws Exception {
        LoginUser lead = testUsers.user("al-lead", Role.AUDIT_LEAD);
        activityLog.write(lead.getUserId(), "al-lead", ActivityLogService.IMPROVEMENT_CLOSE, "IMPROVEMENT", 7, "개선사항 종결, 의견=증빙 확인");
        activityLog.write(lead.getUserId(), "al-lead", ActivityLogService.EVIDENCE_DELETE, "ROUND_ITEM", 3, "파일=old_100%.pdf");
        activityLog.write(null, "hacker", ActivityLogService.LOGIN_FAIL, "USER", null, "BadCredentialsException");

        String all = page(lead, "");
        assertThat(all).contains("감사 로그").contains("종결").contains("파일 삭제").contains("로그인 실패");

        String closed = page(lead, "?action=" + ActivityLogService.IMPROVEMENT_CLOSE);
        assertThat(closed).contains("의견=증빙 확인").doesNotContain("old_100%.pdf");

        assertThat(page(lead, "?loginId=hacker")).contains("BadCredentialsException").doesNotContain("의견=증빙 확인");
        // % 는 문자 그대로 검색 (모든 행에 맞는 와일드카드가 아님)
        for (String kw : List.of("100%", "%")) {
            String html = mvc.perform(get("/audit-log").param("keyword", kw).with(user(lead))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).contains("old_100%.pdf").doesNotContain("의견=증빙 확인");
        }
        assertThat(page(lead, "?from=2999-01-01")).contains("조건에 맞는 기록이 없습니다");
    }

    @Test
    void 쪽을_나누어_보여준다() throws Exception {
        LoginUser admin = testUsers.user("al-admin", Role.ADMIN);
        for (int i = 0; i < 120; i++) {
            activityLog.write(admin.getUserId(), "al-admin", ActivityLogService.EXCEL_EXPORT, "EXPORT", i, "내보내기 " + i);
        }
        assertThat(page(admin, "?action=EXCEL_EXPORT")).contains("총 120건 · 1 / 3 쪽").contains("내보내기 119").doesNotContain("내보내기 69\n");
        assertThat(page(admin, "?action=EXCEL_EXPORT&page=3")).contains("3 / 3 쪽").contains("내보내기 0<");
        assertThat(page(admin, "?action=EXCEL_EXPORT&page=99")).contains("3 / 3 쪽");
    }

    @Test
    void 관리자와_주관자만_볼_수_있다() throws Exception {
        for (Role role : List.of(Role.ADMIN, Role.AUDIT_LEAD)) {
            mvc.perform(get("/audit-log").with(user(testUsers.user("ok-" + role, role)))).andExpect(status().isOk());
        }
        for (Role role : List.of(Role.INSPECTOR, Role.AUDIT_DEPT, Role.VIEWER)) {
            mvc.perform(get("/audit-log").with(user(testUsers.user("no-" + role, role)))).andExpect(status().isForbidden());
        }
    }
}
