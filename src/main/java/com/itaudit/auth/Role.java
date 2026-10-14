package com.itaudit.auth;

/**
 * 역할 5개 (PRD 5장). DB의 app_user.role_code 에 이름(name) 그대로 저장한다.
 */
public enum Role {
    ADMIN("시스템 관리자", "/admin/users"),
    AUDIT_LEAD("IT자체감사 담당자", "/dashboard"),
    INSPECTOR("점검 담당자", "/my-tasks"),
    AUDIT_DEPT("감사부서", "/dashboard"),
    VIEWER("조회자", "/dashboard");

    private final String label;
    /** 로그인 직후 이동할 첫 화면 */
    private final String landingPath;

    Role(String label, String landingPath) {
        this.label = label;
        this.landingPath = landingPath;
    }

    public String getLabel() {
        return label;
    }

    public String getLandingPath() {
        return landingPath;
    }

    /** Spring Security 권한명 (ROLE_ 접두어) */
    public String authority() {
        return "ROLE_" + name();
    }
}
