package com.itaudit.inspection;

/** 점검결과 코드 (4단계 고정, PRD 9장) */
public enum InspectionResult {
    NORMAL("정상", "법규/규정 준수"),
    NEEDS_IMPROVEMENT("개선필요", "정상이나 일부 개선 필요, 긴급 아님"),
    INSUFFICIENT("미흡", "위반은 아니나 개선 필요"),
    RISK("위험", "법규/규정 위반");

    private final String label;
    private final String description;

    InspectionResult(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** 정상이 아니면 조치내용·개선 예정일이 필요하다 */
    public boolean requiresAction() {
        return this != NORMAL;
    }
}
