package com.itaudit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.itaudit.auth.PasswordChangeInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PasswordChangeInterceptor())
                .excludePathPatterns("/account/password", "/login", "/logout", "/error",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico");
    }
}
