package com.itaudit.round;

/** 분기 점검 회차 상태 (PRD 9장 AuditRound) */
public enum RoundStatus {
    DRAFT("작성중"),
    REVIEW("검토중"),
    SUBMITTED("감사부서 검토중"),
    DONE("완료");

    private final String label;

    RoundStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 감사부서 제출 시 회차 잠금 (F-05) — 그 전까지는 배정을 고칠 수 있다 */
    public boolean isEditable() {
        return this == DRAFT || this == REVIEW;
    }

    /** 감사부서는 제출된 회차만 본다 */
    public boolean isVisibleToAuditDept() {
        return this == SUBMITTED || this == DONE;
    }
}
