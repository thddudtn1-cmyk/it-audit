package com.itaudit.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.auth.AppUser;
import com.itaudit.auth.AppUserMapper;
import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired TestUsers testUsers;
    @Autowired AppUserMapper userMapper;
    @Autowired TeamMapper teamMapper;
    @Autowired ActivityLogMapper activityLogMapper;

    LoginUser admin;

    @BeforeEach
    void setUp() {
        admin = testUsers.user("admin-test", Role.ADMIN);
    }

    @Test
    void 담당팀을_등록하고_수정한다() throws Exception {
        mvc.perform(post("/admin/teams").with(user(admin)).with(csrf())
                        .param("teamCode", "INFRA").param("teamName", "인프라운영팀").param("sortOrder", "1"))
                .andExpect(redirectedUrl("/admin/teams"));
        assertThat(teamMapper.findByCode("INFRA").getTeamName()).isEqualTo("인프라운영팀");

        // 체크박스 미선택 = 사용 중지 (삭제 대신)
        mvc.perform(post("/admin/teams/INFRA").with(user(admin)).with(csrf())
                        .param("teamName", "인프라팀").param("sortOrder", "2"))
                .andExpect(redirectedUrl("/admin/teams"));
        Team team = teamMapper.findByCode("INFRA");
        assertThat(team.getTeamName()).isEqualTo("인프라팀");
        assertThat(team.isActive()).isFalse();
        assertThat(activityLogMapper.findByAction(ActivityLogService.TEAM_UPDATE))
                .anyMatch(l -> "INFRA".equals(l.getTargetId()));
    }

    @Test
    void 사용자를_등록하면_BCrypt로_저장되고_첫_로그인에_비밀번호_변경이_강제된다() throws Exception {
        testUsers.team("DEV", "개발팀");

        mvc.perform(post("/admin/users").with(user(admin)).with(csrf())
                        .param("loginId", "kim").param("userName", "김담당")
                        .param("teamCode", "DEV").param("roleCode", "INSPECTOR")
                        .param("initialPassword", "Init1234!").param("active", "true"))
                .andExpect(redirectedUrl("/admin/users"));

        AppUser kim = userMapper.findByLoginId("kim");
        assertThat(kim.getRoleCode()).isEqualTo(Role.INSPECTOR);
        assertThat(kim.getTeamName()).isEqualTo("개발팀");
        assertThat(kim.getPasswordHash()).startsWith("$2a$").doesNotContain("Init1234!");
        assertThat(kim.isPasswordChangeRequired()).isTrue();

        mvc.perform(formLogin("/login").userParameter("loginId").user("kim").password("Init1234!"))
                .andExpect(authenticated().withRoles("INSPECTOR"));
        assertThat(activityLogMapper.findByAction(ActivityLogService.USER_CREATE))
                .anyMatch(l -> String.valueOf(kim.getUserId()).equals(l.getTargetId()));
    }

    @Test
    void 점검_담당자는_담당팀_없이_등록할_수_없다() throws Exception {
        mvc.perform(post("/admin/users").with(user(admin)).with(csrf())
                        .param("loginId", "noteam").param("userName", "팀없음")
                        .param("roleCode", "INSPECTOR").param("initialPassword", "Init1234!"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("담당팀을 지정해야 합니다")));
        assertThat(userMapper.findByLoginId("noteam")).isNull();
    }

    @Test
    void 중복_아이디와_약한_초기_비밀번호는_거부된다() throws Exception {
        mvc.perform(post("/admin/users").with(user(admin)).with(csrf())
                        .param("loginId", "admin-test").param("userName", "중복")
                        .param("roleCode", "VIEWER").param("initialPassword", "Init1234!"))
                .andExpect(content().string(Matchers.containsString("이미 사용 중인 아이디")));

        mvc.perform(post("/admin/users").with(user(admin)).with(csrf())
                        .param("loginId", "weakpw").param("userName", "약한비번")
                        .param("roleCode", "VIEWER").param("initialPassword", "12345678"))
                .andExpect(content().string(Matchers.containsString("비밀번호는 8~64자")));
        assertThat(userMapper.findByLoginId("weakpw")).isNull();
    }

    @Test
    void 사용자_역할과_사용여부를_수정한다() throws Exception {
        LoginUser target = testUsers.user("target", Role.VIEWER);

        mvc.perform(post("/admin/users/" + target.getUserId()).with(user(admin)).with(csrf())
                        .param("loginId", "ignored").param("userName", "변경된 이름")
                        .param("roleCode", "AUDIT_DEPT").param("active", "false"))
                .andExpect(redirectedUrl("/admin/users"));

        AppUser updated = userMapper.findById(target.getUserId());
        assertThat(updated.getLoginId()).isEqualTo("target");
        assertThat(updated.getUserName()).isEqualTo("변경된 이름");
        assertThat(updated.getRoleCode()).isEqualTo(Role.AUDIT_DEPT);
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void 관리자는_본인_계정을_중지하거나_역할을_바꿀_수_없다() throws Exception {
        mvc.perform(post("/admin/users/" + admin.getUserId()).with(user(admin)).with(csrf())
                        .param("userName", "나").param("roleCode", "VIEWER").param("active", "true"))
                .andExpect(content().string(Matchers.containsString("본인 계정의 관리자 역할")));
        assertThat(userMapper.findById(admin.getUserId()).getRoleCode()).isEqualTo(Role.ADMIN);
    }

    @Test
    void 비밀번호를_재설정하면_다음_로그인에_변경이_강제된다() throws Exception {
        LoginUser target = testUsers.user("reset-me", Role.VIEWER);

        mvc.perform(post("/admin/users/" + target.getUserId() + "/reset-password").with(user(admin)).with(csrf())
                        .param("newPassword", "Reset123!"))
                .andExpect(redirectedUrl("/admin/users/" + target.getUserId() + "/edit"));

        assertThat(userMapper.findById(target.getUserId()).isPasswordChangeRequired()).isTrue();
        mvc.perform(formLogin("/login").userParameter("loginId").user("reset-me").password("Reset123!"))
                .andExpect(authenticated());
    }

    @Test
    void 관리_화면_목록이_표시된다() throws Exception {
        mvc.perform(get("/admin/users").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("admin-test")));
        mvc.perform(get("/admin/teams").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/users/new").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/users/" + admin.getUserId() + "/edit").with(user(admin))).andExpect(status().isOk());
    }

    @Test
    void 관리자가_아니면_관리_기능을_쓸_수_없다() throws Exception {
        LoginUser lead = testUsers.user("lead-x", Role.AUDIT_LEAD);
        mvc.perform(post("/admin/teams").with(user(lead)).with(csrf())
                        .param("teamCode", "X").param("teamName", "X팀"))
                .andExpect(status().isForbidden());
        assertThat(teamMapper.findByCode("X")).isNull();
    }
}
