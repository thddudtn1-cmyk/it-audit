package com.itaudit.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** 세션에 보관되는 로그인 사용자 정보 */
public class LoginUser implements UserDetails {

    private final Long userId;
    private final String loginId;
    private final String userName;
    private final String teamCode;
    private final Role role;
    private final String passwordHash;
    private final boolean enabled;
    /** 비밀번호 변경 후 같은 세션에서 해제되므로 가변 */
    private volatile boolean passwordChangeRequired;

    public LoginUser(AppUser user) {
        this(user.getUserId(), user.getLoginId(), user.getUserName(), user.getTeamCode(),
                user.getRoleCode(), user.getPasswordHash(), user.isActive(), user.isPasswordChangeRequired());
    }

    public LoginUser(Long userId, String loginId, String userName, String teamCode, Role role,
                     String passwordHash, boolean enabled, boolean passwordChangeRequired) {
        this.userId = userId;
        this.loginId = loginId;
        this.userName = userName;
        this.teamCode = teamCode;
        this.role = role;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public Long getUserId() { return userId; }
    public String getLoginId() { return loginId; }
    public String getUserName() { return userName; }
    public String getTeamCode() { return teamCode; }
    public Role getRole() { return role; }
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public void passwordChanged() { this.passwordChangeRequired = false; }
}
