package com.itaudit.auth;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 계정이 하나도 없으면 초기 관리자를 만든다. 첫 로그인 시 비밀번호 변경이 강제된다. */
@Component
public class InitAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitAdminRunner.class);

    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String loginId;
    private final String password;

    public InitAdminRunner(AppUserMapper userMapper, PasswordEncoder passwordEncoder,
                           @Value("${app.init-admin.login-id}") String loginId,
                           @Value("${app.init-admin.password}") String password) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.loginId = loginId;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userMapper.countByRole(Role.ADMIN) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AppUser admin = new AppUser();
        admin.setLoginId(loginId);
        admin.setUserName("시스템 관리자");
        admin.setRoleCode(Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setPwChangeRequired("Y");
        admin.setUseYn("Y");
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userMapper.insert(admin);
        log.warn("초기 관리자 계정 '{}'을(를) 생성했습니다. 첫 로그인 후 비밀번호를 변경하세요.", loginId);
    }
}
