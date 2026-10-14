package com.itaudit.support;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.itaudit.admin.Team;
import com.itaudit.admin.TeamMapper;
import com.itaudit.auth.AppUser;
import com.itaudit.auth.AppUserMapper;
import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;

/** 테스트용 사용자·팀 생성 도우미 */
@Component
public class TestUsers {

    public static final String PASSWORD = "Test1234!";

    private final AppUserMapper userMapper;
    private final TeamMapper teamMapper;
    private final PasswordEncoder passwordEncoder;

    public TestUsers(AppUserMapper userMapper, TeamMapper teamMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.teamMapper = teamMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public void team(String code, String name) {
        Team team = new Team();
        team.setTeamCode(code);
        team.setTeamName(name);
        team.setSortOrder(0);
        team.setUseYn("Y");
        team.setCreatedAt(LocalDateTime.now());
        team.setUpdatedAt(LocalDateTime.now());
        teamMapper.insert(team);
    }

    public LoginUser user(String loginId, Role role) {
        return user(loginId, role, false, true);
    }

    public LoginUser user(String loginId, Role role, boolean pwChangeRequired, boolean active) {
        AppUser u = new AppUser();
        u.setLoginId(loginId);
        u.setUserName(loginId + " 사용자");
        u.setRoleCode(role);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setPwChangeRequired(pwChangeRequired ? "Y" : "N");
        u.setUseYn(active ? "Y" : "N");
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(u);
        return new LoginUser(userMapper.findById(u.getUserId()));
    }
}
