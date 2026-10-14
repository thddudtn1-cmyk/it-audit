package com.itaudit.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 초기·재설정 비밀번호로 로그인한 사용자는 비밀번호를 바꾸기 전까지 다른 화면에 들어갈 수 없다 */
public class PasswordChangeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user && user.isPasswordChangeRequired()) {
            response.sendRedirect(request.getContextPath() + "/account/password");
            return false;
        }
        return true;
    }
}
