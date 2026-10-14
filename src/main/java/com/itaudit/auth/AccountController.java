package com.itaudit.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.itaudit.common.BusinessException;

@Controller
public class AccountController {

    private final AccountService accountService;
    private final boolean demoLoginEnabled;

    public AccountController(AccountService accountService,
                             @Value("${app.demo-login.enabled:false}") boolean demoLoginEnabled) {
        this.accountService = accountService;
        this.demoLoginEnabled = demoLoginEnabled;
    }

    @GetMapping("/login")
    public String login(Model model) {
        if (demoLoginEnabled) {
            model.addAttribute("demoAccounts", DemoAccounts.all());
        }
        return "auth/login";
    }

    @GetMapping("/account/password")
    public String passwordForm(Model model) {
        model.addAttribute("policy", PasswordPolicy.DESCRIPTION);
        return "auth/password";
    }

    @PostMapping("/account/password")
    public String changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Model model, RedirectAttributes ra) {
        try {
            accountService.changePassword(loginUser, currentPassword, newPassword, confirmPassword);
        } catch (BusinessException e) {
            model.addAttribute("policy", PasswordPolicy.DESCRIPTION);
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/password";
        }
        ra.addFlashAttribute("message", "비밀번호를 변경했습니다.");
        return "redirect:/";
    }
}
