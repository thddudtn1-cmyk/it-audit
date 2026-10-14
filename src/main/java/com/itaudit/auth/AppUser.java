package com.itaudit.auth;

import java.time.LocalDateTime;

/** 사용자 (app_user) */
public class AppUser {

    private Long userId;
    private String loginId;
    private String userName;
    private String teamCode;
    /** 조회 시 team 조인 결과 */
    private String teamName;
    private Role roleCode;
    private String passwordHash;
    private String pwChangeRequired;
    private String useYn;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return "Y".equals(useYn);
    }

    public boolean isPasswordChangeRequired() {
        return "Y".equals(pwChangeRequired);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public Role getRoleCode() { return roleCode; }
    public void setRoleCode(Role roleCode) { this.roleCode = roleCode; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPwChangeRequired() { return pwChangeRequired; }
    public void setPwChangeRequired(String pwChangeRequired) { this.pwChangeRequired = pwChangeRequired; }
    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
