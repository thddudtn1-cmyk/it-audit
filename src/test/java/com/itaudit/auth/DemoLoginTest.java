package com.itaudit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.common.ActivityLogMapper;
import com.itaudit.common.ActivityLogService;

@SpringBootTest(properties = "app.demo-login.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoLoginTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserMapper userMapper;
    @Autowired ActivityLogMapper activityLogMapper;
    @Autowired DemoAccountInitializer initializer;

    @Test
    void 로그인_화면에_역할_5개_선택_버튼이_보인다() throws Exception {
        String html = mvc.perform(get("/login")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (Role role : Role.values()) {
            assertThat(html).contains("value=\"" + role.name() + "\"").contains(role.getLabel());
        }
        assertThat(html).contains("아이디로 로그인");
    }

    @Test
    void 데모_계정과_데모팀이_만들어지고_초기_관리자도_따로_있다() {
        for (DemoAccounts d : DemoAccounts.values()) {
            AppUser u = userMapper.findByLoginId(d.getLoginId());
            assertThat(u.getRoleCode()).isEqualTo(d.getRole());
            assertThat(u.isPasswordChangeRequired()).isFalse();
        }
        assertThat(userMapper.findByLoginId("demo-inspector").getTeamName()).isEqualTo("데모팀");
        assertThat(userMapper.findByLoginId("admin")).as("데모 관리자와 별개로 초기 관리자 생성").isNotNull();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void 역할을_고르면_바로_로그인되어_첫_화면으로_간다(Role role) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/demo-login").param("role", role.name()).session(session).with(csrf()))
                .andExpect(redirectedUrl(role.getLandingPath()));

        // 같은 세션으로 이어서 화면 접근 가능
        String html = mvc.perform(get(role.getLandingPath()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains(DemoAccounts.of(role).getUserName()).contains(role.getLabel());

        assertThat(activityLogMapper.findByAction(ActivityLogService.LOGIN_SUCCESS))
                .anyMatch(l -> DemoAccounts.of(role).getLoginId().equals(l.getLoginId())
                        && "데모 로그인".equals(l.getDetail()));
    }

    @Test
    void 중지된_데모_계정은_쓸_수_없다() throws Exception {
        AppUser viewer = userMapper.findByLoginId("demo-viewer");
        viewer.setUseYn("N");
        userMapper.update(viewer);

        mvc.perform(post("/demo-login").param("role", "VIEWER").with(csrf()))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    void CSRF_토큰_없이는_데모_로그인할_수_없다() throws Exception {
        mvc.perform(post("/demo-login").param("role", "ADMIN").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 데모_계정은_비밀번호_로그인이_안_된다() throws Exception {
        mvc.perform(formLogin("/login").userParameter("loginId").user("demo-admin").password(""))
                .andExpect(unauthenticated());
    }

    @Test
    void 아이디_비밀번호_로그인도_그대로_쓸_수_있다() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(Matchers.containsString("name=\"loginId\"")))
                .andExpect(content().string(Matchers.containsString("name=\"password\"")));
    }

    @Test
    void 역할_명칭이_바뀌기_전에_만든_데모_계정은_옛_기본_이름일_때만_새_이름으로_맞춘다() throws Exception {
        AppUser admin = userMapper.findByLoginId("demo-admin");
        AppUser lead = userMapper.findByLoginId("demo-lead");
        AppUser viewer = userMapper.findByLoginId("demo-viewer");
        admin.setUserName("데모 관리자");
        userMapper.update(admin);
        lead.setUserName("데모 주관자");
        userMapper.update(lead);
        viewer.setUserName("내가 바꾼 이름");   // 직접 바꾼 이름은 건드리지 않는다
        userMapper.update(viewer);

        initializer.run(new org.springframework.boot.DefaultApplicationArguments());

        assertThat(userMapper.findByLoginId("demo-admin").getUserName()).isEqualTo("데모 시스템 관리자");
        assertThat(userMapper.findByLoginId("demo-lead").getUserName()).isEqualTo("데모 IT자체감사 담당자");
        assertThat(userMapper.findByLoginId("demo-viewer").getUserName()).isEqualTo("내가 바꾼 이름");
        assertThat(Role.ADMIN.getLabel()).isEqualTo("시스템 관리자");
        assertThat(Role.AUDIT_LEAD.getLabel()).isEqualTo("IT자체감사 담당자");
    }
}
