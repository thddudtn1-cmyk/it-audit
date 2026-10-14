package com.itaudit.admin;

import com.itaudit.auth.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 사용자 등록·수정 입력값. 비밀번호는 등록 시에만 받는다. */
public class UserForm {

    @NotBlank(message = "아이디를 입력하세요.")
    @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "아이디는 영문·숫자·._- 3~50자입니다.")
    private String loginId;

    @NotBlank(message = "이름을 입력하세요.")
    @Size(max = 100, message = "이름은 100자 이하입니다.")
    private String userName;

    private String teamCode;

    @NotNull(message = "역할을 선택하세요.")
    private Role roleCode;

    private String initialPassword;

    private boolean active = true;

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
    public Role getRoleCode() { return roleCode; }
    public void setRoleCode(Role roleCode) { this.roleCode = roleCode; }
    public String getInitialPassword() { return initialPassword; }
    public void setInitialPassword(String initialPassword) { this.initialPassword = initialPassword; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
