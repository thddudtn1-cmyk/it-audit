package com.itaudit.home;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.itaudit.auth.LoginUser;

@Controller
public class HomeController {

    /** 역할별 첫 화면으로 이동 */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal LoginUser loginUser) {
        return "redirect:" + loginUser.getRole().getLandingPath();
    }
}
