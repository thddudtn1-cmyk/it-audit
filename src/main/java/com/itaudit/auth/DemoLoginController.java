package com.itaudit.auth;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.itaudit.common.ActivityLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 시연용 역할 선택 로그인. 로그인 화면에서 역할을 고르면 해당 데모 계정으로 바로 로그인한다.
 * app.demo-login.enabled=false 이면 이 컨트롤러가 등록되지 않아 /demo-login 은 404 가 된다.
 */
@Controller
@ConditionalOnProperty(name = "app.demo-login.enabled", havingValue = "true")
public class DemoLoginController {

    private final AppUserMapper userMapper;
    private final ActivityLogService activityLog;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public DemoLoginController(AppUserMapper userMapper, ActivityLogService activityLog) {
        this.userMapper = userMapper;
        this.activityLog = activityLog;
    }

    @PostMapping("/demo-login")
    public String demoLogin(@RequestParam Role role, HttpServletRequest request, HttpServletResponse response,
                            RedirectAttributes ra) {
        DemoAccounts demo = DemoAccounts.of(role);
        AppUser user = userMapper.findByLoginId(demo.getLoginId());
        // 관리자가 데모 계정을 중지했거나 역할을 바꿨으면 쓰지 않는다
        if (user == null || !user.isActive() || user.getRoleCode() != role) {
            ra.addFlashAttribute("demoError", role.getLabel() + " 데모 계정을 사용할 수 없습니다. 관리자에게 문의하세요.");
            return "redirect:/login";
        }

        // 세션 고정 공격 방지: 로그인 시 세션 ID 교체
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        LoginUser loginUser = new LoginUser(user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                loginUser, null, loginUser.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        userMapper.updateLastLogin(user.getUserId(), LocalDateTime.now());
        activityLog.write(user.getUserId(), user.getLoginId(), ActivityLogService.LOGIN_SUCCESS,
                "USER", user.getUserId(), "데모 로그인");
        return "redirect:" + role.getLandingPath();
    }
}
