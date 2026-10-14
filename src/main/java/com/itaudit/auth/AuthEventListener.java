package com.itaudit.auth;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

import com.itaudit.common.ActivityLogService;

/** 로그인 성공·실패·로그아웃을 활동 이력에 남긴다 */
@Component
public class AuthEventListener {

    private final ActivityLogService activityLog;
    private final AppUserMapper userMapper;

    public AuthEventListener(ActivityLogService activityLog, AppUserMapper userMapper) {
        this.activityLog = activityLog;
        this.userMapper = userMapper;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof LoginUser user) {
            userMapper.updateLastLogin(user.getUserId(), LocalDateTime.now());
            activityLog.write(user.getUserId(), user.getLoginId(), ActivityLogService.LOGIN_SUCCESS,
                    "USER", user.getUserId(), null);
        }
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String loginId = String.valueOf(event.getAuthentication().getPrincipal());
        activityLog.write(null, loginId, ActivityLogService.LOGIN_FAIL, "USER", null,
                event.getException().getClass().getSimpleName());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof LoginUser user) {
            activityLog.write(user.getUserId(), user.getLoginId(), ActivityLogService.LOGOUT,
                    "USER", user.getUserId(), null);
        }
    }
}
