package com.itaudit.auth;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 역할별 메뉴와 URL 접근 권한의 단일 정의 (PRD 10장 화면 구성의 '주 사용자' 기준).
 * SecurityConfig 가 이 정의로 URL 접근 규칙을 만들고, 화면 상단 메뉴도 같은 정의로 그린다.
 * 화면 안의 세부 동작(예: 회차 생성은 주관자만)은 각 기능 구현 시 별도로 제한한다.
 */
public enum Menu {
    DASHBOARD("대시보드", "/dashboard", "F-08",
            Role.AUDIT_LEAD, Role.AUDIT_DEPT, Role.VIEWER),
    MY_TASKS("내 할 일", "/my-tasks", "F-04",
            Role.INSPECTOR),
    CHECKLIST("체크리스트 관리", "/checklist", "F-02",
            Role.ADMIN, Role.AUDIT_LEAD),
    // 조회자는 회차 진행 현황 열람 (PRD 5장)
    ROUNDS("분기 점검 회차", "/rounds", "F-03",
            Role.AUDIT_LEAD, Role.AUDIT_DEPT, Role.VIEWER),
    // [가정] 오픈 이슈 '감사부서 직접 로그인 여부'는 감사부서가 직접 로그인해 작성하는 쪽으로 가정한다.
    //        주관자도 진행 상황 확인을 위해 열람할 수 있게 둔다.
    REQUESTS("사전요청자료", "/requests", "F-06",
            Role.AUDIT_LEAD, Role.AUDIT_DEPT, Role.INSPECTOR),
    IMPROVEMENTS("개선사항 관리대장", "/improvements", "F-07",
            Role.AUDIT_LEAD, Role.INSPECTOR, Role.AUDIT_DEPT, Role.VIEWER),
    // [가정] 감사 로그는 관리자와 자체감사 주관자만 본다 (로그인 IP·사용자 행적이 담겨 있어 담당자·조회자는 제외)
    AUDIT_LOG("감사 로그", "/audit-log", "F-11",
            Role.ADMIN, Role.AUDIT_LEAD),
    ADMIN("관리", "/admin", "F-01",
            Role.ADMIN);

    private final String label;
    private final String path;
    private final String featureId;
    private final Set<Role> roles;

    Menu(String label, String path, String featureId, Role... roles) {
        this.label = label;
        this.path = path;
        this.featureId = featureId;
        this.roles = EnumSet.copyOf(Arrays.asList(roles));
    }

    public static List<Menu> forRole(Role role) {
        return Arrays.stream(values()).filter(m -> m.roles.contains(role)).toList();
    }

    public boolean allows(Role role) {
        return roles.contains(role);
    }

    public String[] roleNames() {
        return roles.stream().map(Role::name).toArray(String[]::new);
    }

    public String getLabel() {
        return label;
    }

    public String getPath() {
        return path;
    }

    public String getFeatureId() {
        return featureId;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
