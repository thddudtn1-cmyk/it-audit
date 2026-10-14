package com.itaudit.config;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Menu;

import jakarta.servlet.http.HttpServletRequest;

/** 모든 화면 공통 모델: 로그인 사용자, 역할별 메뉴, 현재 경로 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("loginUser")
    public LoginUser loginUser(@AuthenticationPrincipal LoginUser loginUser) {
        return loginUser;
    }

    @ModelAttribute("menus")
    public List<Menu> menus(@AuthenticationPrincipal LoginUser loginUser) {
        return loginUser == null ? List.of() : Menu.forRole(loginUser.getRole());
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
