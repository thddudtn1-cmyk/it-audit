package com.itaudit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired AppUserMapper userMapper;
    @Autowired ActivityLogMapper activityLogMapper;

    @Test
    void 비로그인_사용자는_로그인_화면으로_이동한다() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/admin/users")).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("로그인")));
    }

    @Test
    void 초기_관리자는_비밀번호_변경이_필요한_상태로_생성된다() {
        AppUser admin = userMapper.findByLoginId("admin");
        assertThat(admin).isNotNull();
        assertThat(admin.getRoleCode()).isEqualTo(Role.ADMIN);
        assertThat(admin.isPasswordChangeRequired()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2a$");
    }

    @Test
    void 올바른_비밀번호로_로그인하면_첫_화면으로_이동하고_이력이_남는다() throws Exception {
        LoginUser u = testUsers.user("lead1", Role.AUDIT_LEAD);

        mvc.perform(formLogin("/login").userParameter("loginId").user("lead1").password(TestUsers.PASSWORD))
                .andExpect(authenticated().withRoles("AUDIT_LEAD"))
                .andExpect(redirectedUrl("/"));

        assertThat(userMapper.findById(u.getUserId()).getLastLoginAt()).isNotNull();
        assertThat(activityLogMapper.findByAction(ActivityLogService.LOGIN_SUCCESS))
                .anyMatch(l -> "lead1".equals(l.getLoginId()));
    }

    @Test
    void 틀린_비밀번호는_로그인에_실패하고_실패_이력이_남는다() throws Exception {
        testUsers.user("lead2", Role.AUDIT_LEAD);

        mvc.perform(formLogin("/login").userParameter("loginId").user("lead2").password("wrong-pass1!"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));

        assertThat(activityLogMapper.findByAction(ActivityLogService.LOGIN_FAIL))
                .anyMatch(l -> "lead2".equals(l.getLoginId()));
    }

    @Test
    void 사용중지된_계정은_로그인할_수_없다() throws Exception {
        testUsers.user("stopped", Role.VIEWER, false, false);

        mvc.perform(formLogin("/login").userParameter("loginId").user("stopped").password(TestUsers.PASSWORD))
                .andExpect(unauthenticated());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void 로그인하면_역할별_첫_화면으로_이동한다(Role role) throws Exception {
        LoginUser u = testUsers.user("landing-" + role.name().toLowerCase(), role);
        mvc.perform(get("/").with(user(u))).andExpect(redirectedUrl(role.getLandingPath()));
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void 역할별로_허용된_메뉴만_접근할_수_있다(Role role) throws Exception {
        LoginUser u = testUsers.user("access-" + role.name().toLowerCase(), role);
        for (Menu menu : Menu.values()) {
            int expected = menu.allows(role) ? 200 : 403;
            int actual = mvc.perform(get(menu.getPath()).with(user(u)))
                    .andReturn().getResponse().getStatus();
            // /admin 은 /admin/users 로 리다이렉트
            if (menu == Menu.ADMIN && menu.allows(role)) {
                expected = 302;
            }
            assertThat(actual).as("%s → %s", role, menu).isEqualTo(expected);
        }
    }

    @Test
    void 화면_메뉴는_역할에_맞는_항목만_보인다() throws Exception {
        LoginUser inspector = testUsers.user("menu-inspector", Role.INSPECTOR);
        mvc.perform(get("/my-tasks").with(user(inspector)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/my-tasks\"")))
                .andExpect(content().string(Matchers.containsString("href=\"/improvements\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/admin\""))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("href=\"/checklist\""))));
    }

    @Test
    void 비밀번호_변경이_필요하면_다른_화면에_들어갈_수_없다() throws Exception {
        LoginUser u = testUsers.user("must-change", Role.ADMIN, true, true);

        mvc.perform(get("/admin/users").with(user(u))).andExpect(redirectedUrl("/account/password"));
        mvc.perform(get("/account/password").with(user(u))).andExpect(status().isOk());
    }

    @Test
    void 비밀번호를_변경하면_강제_변경_상태가_해제된다() throws Exception {
        LoginUser u = testUsers.user("changer", Role.VIEWER, true, true);

        mvc.perform(post("/account/password").with(user(u)).with(csrf())
                        .param("currentPassword", TestUsers.PASSWORD)
                        .param("newPassword", "NewPass123!")
                        .param("confirmPassword", "NewPass123!"))
                .andExpect(redirectedUrl("/"));

        assertThat(userMapper.findById(u.getUserId()).isPasswordChangeRequired()).isFalse();
        assertThat(u.isPasswordChangeRequired()).isFalse();
        mvc.perform(formLogin("/login").userParameter("loginId").user("changer").password("NewPass123!"))
                .andExpect(authenticated());
    }

    @Test
    void 규칙에_맞지_않는_새_비밀번호는_거부된다() throws Exception {
        LoginUser u = testUsers.user("weak", Role.VIEWER, true, true);

        mvc.perform(post("/account/password").with(user(u)).with(csrf())
                        .param("currentPassword", TestUsers.PASSWORD)
                        .param("newPassword", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("비밀번호는 8~64자")));

        assertThat(userMapper.findById(u.getUserId()).isPasswordChangeRequired()).isTrue();
    }

    @Test
    void CSRF_토큰이_없거나_틀린_POST는_거부된다() throws Exception {
        LoginUser u = testUsers.user("nocsrf", Role.VIEWER, true, true);

        mvc.perform(post("/account/password").with(user(u)).with(csrf().useInvalidToken())
                        .param("currentPassword", TestUsers.PASSWORD)
                        .param("newPassword", "NewPass123!")
                        .param("confirmPassword", "NewPass123!"))
                .andExpect(status().isForbidden());

        // 토큰이 아예 없으면(세션 만료 후 제출 등) 만료 안내와 함께 로그인 화면으로 보낸다
        mvc.perform(post("/account/password").with(user(u))
                        .param("currentPassword", TestUsers.PASSWORD)
                        .param("newPassword", "NewPass123!")
                        .param("confirmPassword", "NewPass123!"))
                .andExpect(redirectedUrl("/login?expired"));

        assertThat(userMapper.findById(u.getUserId()).isPasswordChangeRequired()).isTrue();
    }
}
