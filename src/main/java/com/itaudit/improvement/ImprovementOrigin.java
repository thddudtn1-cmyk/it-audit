package com.itaudit.improvement;

/** 개선사항·권고사항이 관리대장에 오른 경위 */
public enum ImprovementOrigin {
    /** 분기 점검을 감사부서에 제출할 때 점검결과가 정상이 아닌 항목이 자동 등록됨 (감사부서 검토와 무관) */
    ROUND_SUBMIT("분기 점검 제출 시 자동 등록"),
    /** 감사부서가 미흡으로 확정한 건을 주관자가 등록함 */
    AUDIT_FINDING("감사부서 미흡 확정 건 등록"),
    /** 권고사항 */
    RECOMMENDATION("권고사항 등록");

    private final String label;

    ImprovementOrigin(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
