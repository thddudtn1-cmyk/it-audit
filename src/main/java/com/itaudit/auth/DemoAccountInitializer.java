package com.itaudit.auth;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itaudit.admin.Team;
import com.itaudit.admin.TeamMapper;

/** 데모 로그인이 켜져 있으면 역할별 데모 계정과 데모팀을 없을 때만 만든다 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.demo-login.enabled", havingValue = "true")
public class DemoAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitializer.class);

    private final AppUserMapper userMapper;
    private final TeamMapper teamMapper;
    private final PasswordEncoder passwordEncoder;

    public DemoAccountInitializer(AppUserMapper userMapper, TeamMapper teamMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.teamMapper = teamMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        if (teamMapper.findByCode(DemoAccounts.DEMO_TEAM_CODE) == null) {
            Team team = new Team();
            team.setTeamCode(DemoAccounts.DEMO_TEAM_CODE);
            team.setTeamName(DemoAccounts.DEMO_TEAM_NAME);
            team.setSortOrder(999);
            team.setUseYn("Y");
            team.setCreatedAt(now);
            team.setUpdatedAt(now);
            teamMapper.insert(team);
        }
        for (DemoAccounts demo : DemoAccounts.values()) {
            if (userMapper.findByLoginId(demo.getLoginId()) != null) {
                continue;
            }
            AppUser user = new AppUser();
            user.setLoginId(demo.getLoginId());
            user.setUserName(demo.getUserName());
            user.setTeamCode(demo.getRole() == Role.INSPECTOR ? DemoAccounts.DEMO_TEAM_CODE : null);
            user.setRoleCode(demo.getRole());
            // 아이디/비밀번호로는 로그인할 수 없도록 무작위 비밀번호
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setPwChangeRequired("N");
            user.setUseYn("Y");
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            userMapper.insert(user);
            log.info("데모 계정 '{}'({})을(를) 생성했습니다.", demo.getLoginId(), demo.getRole().getLabel());
        }
    }
}
