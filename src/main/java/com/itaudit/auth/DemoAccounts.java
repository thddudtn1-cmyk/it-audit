package com.itaudit.auth;

import java.util.Arrays;
import java.util.List;

/**
 * 시연용 역할별 데모 계정 (app.demo-login.enabled=true 일 때만 생성·사용).
 * 비밀번호는 무작위 값으로 저장해 아이디/비밀번호 로그인에는 쓸 수 없다.
 */
public enum DemoAccounts {
    ADMIN(Role.ADMIN, "demo-admin", "데모 관리자",
            "사용자·권한, 담당팀 코드 관리"),
    AUDIT_LEAD(Role.AUDIT_LEAD, "demo-lead", "데모 주관자",
            "체크리스트 개정, 분기 회차 생성·배정, 총괄표 검토·제출, 개선사항 완료 검토(최종확정·반려)"),
    INSPECTOR(Role.INSPECTOR, "demo-inspector", "데모 담당자",
            "배정 항목 점검결과 입력·증적 첨부, 적정성 점검 보완자료 제출, 조치 현황 입력"),
    AUDIT_DEPT(Role.AUDIT_DEPT, "demo-dept", "데모 감사부서",
            "제출된 점검 내역 적정성 검토, 보완자료 요청, 미흡 확정·권고사항 등록"),
    VIEWER(Role.VIEWER, "demo-viewer", "데모 조회자",
            "회차 진행 현황과 개선사항 조회");

    /** 데모 담당자가 속하는 팀 (점검 담당자는 담당팀 필수) */
    public static final String DEMO_TEAM_CODE = "DEMO";
    public static final String DEMO_TEAM_NAME = "데모팀";

    private final Role role;
    private final String loginId;
    private final String userName;
    private final String description;

    DemoAccounts(Role role, String loginId, String userName, String description) {
        this.role = role;
        this.loginId = loginId;
        this.userName = userName;
        this.description = description;
    }

    public static DemoAccounts of(Role role) {
        return Arrays.stream(values()).filter(d -> d.role == role).findFirst().orElseThrow();
    }

    public static List<DemoAccounts> all() {
        return List.of(values());
    }

    public Role getRole() { return role; }
    public String getLoginId() { return loginId; }
    public String getUserName() { return userName; }
    public String getDescription() { return description; }
}
