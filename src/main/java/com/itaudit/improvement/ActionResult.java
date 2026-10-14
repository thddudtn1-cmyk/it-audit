package com.itaudit.improvement;

/** 조치 결과 코드: 예정 · 진행중 · 완료 */
public enum ActionResult {
    PLANNED("예정"),
    IN_PROGRESS("진행중"),
    DONE("완료");

    private final String label;

    ActionResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
