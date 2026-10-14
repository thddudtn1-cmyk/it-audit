package com.itaudit.auth;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;

/** 본인 계정 관련 처리 */
@Service
public class AccountService {

    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLog;

    public AccountService(AppUserMapper userMapper, PasswordEncoder passwordEncoder, ActivityLogService activityLog) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.activityLog = activityLog;
    }

    @Transactional
    public void changePassword(LoginUser loginUser, String currentPassword, String newPassword, String confirmPassword) {
        AppUser user = userMapper.findById(loginUser.getUserId());
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("현재 비밀번호가 올바르지 않습니다.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException("새 비밀번호와 확인 값이 다릅니다.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException("현재 비밀번호와 다른 비밀번호를 입력하세요.");
        }
        PasswordPolicy.validate(newPassword);
        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(newPassword), "N", LocalDateTime.now());
        loginUser.passwordChanged();
        activityLog.log(ActivityLogService.PASSWORD_CHANGE, "USER", user.getUserId(), null);
    }
}
