package com.itaudit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.itaudit.auth.Menu;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> {
                // /demo-login 은 데모 로그인이 꺼져 있으면 컨트롤러가 없어 404
                auth.requestMatchers("/login", "/demo-login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error").permitAll();
                // 메뉴 정의(Menu)의 역할로 URL 접근을 제한한다
                for (Menu menu : Menu.values()) {
                    auth.requestMatchers(menu.getPath() + "/**").hasAnyRole(menu.roleNames());
                }
                auth.anyRequest().authenticated();
            })
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("loginId")
                .passwordParameter("password")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            // 세션 타임아웃(30분, application.yml) 후 요청하면 안내 문구와 함께 로그인 화면으로
            .sessionManagement(session -> session.invalidSessionUrl("/login?expired"));
        return http.build();
    }
}
