package com.itaudit.improvement;

/** 구분: 개선사항(⑤ '개선사항' 시트) · 권고사항(⑤ '권고사항' 시트) */
public enum ImprovementKind {
    IMPROVEMENT("개선사항"),
    RECOMMENDATION("권고사항");

    private final String label;

    ImprovementKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
