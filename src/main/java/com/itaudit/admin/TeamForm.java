package com.itaudit.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class TeamForm {

    @NotBlank(message = "팀 코드를 입력하세요.")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,20}", message = "팀 코드는 영문·숫자·_- 20자 이하입니다.")
    private String teamCode;

    @NotBlank(message = "팀 이름을 입력하세요.")
    @Size(max = 100, message = "팀 이름은 100자 이하입니다.")
    private String teamName;

    private Integer sortOrder = 0;

    private boolean active = true;

    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
