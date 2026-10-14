package com.itaudit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 운영 설정 (app.demo-login.enabled=false) */
@SpringBootTest(properties = {"app.demo-login.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:itaudit-nodemo;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoLoginDisabledTest {

    @Autowired MockMvc mvc;
    @Autowired AppUserMapper userMapper;

    @Test
    void 데모_로그인을_끄면_역할_버튼이_없고_요청도_막힌다() throws Exception {
        String html = mvc.perform(get("/login")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("/demo-login").doesNotContain("시연용").contains("name=\"loginId\"");

        mvc.perform(post("/demo-login").param("role", "ADMIN").with(csrf())).andExpect(status().isNotFound());
        assertThat(userMapper.findByLoginId("demo-admin")).isNull();
    }
}
