package com.itaudit.request;

/** 적정성 점검 항목의 진행 상태 (화면 표시용). 감사부서가 아직 손대지 않은 항목은 행이 없다. */
public enum ReviewState {
    NOT_REVIEWED("미검토"),
    /** 감사부서가 보완자료를 요청하고 담당자의 제출을 기다리는 중 */
    REQUESTED("보완 요청"),
    /** 담당자가 보완자료를 제출해 감사부서의 재검토를 기다리는 중 */
    SUBMITTED("보완 제출"),
    /** 이상 없음으로 판정 */
    PASSED("적정"),
    /** 미흡으로 확정 (개선사항 등록 대상) */
    FINDING("미흡 확정");

    private final String label;

    ReviewState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 검토가 끝난 상태 (적정성 점검 완료의 조건) */
    public boolean isDone() {
        return this == PASSED || this == FINDING;
    }

    /** 감사부서가 지금 볼 차례 (미검토 또는 보완자료 제출됨) */
    public boolean needsReview() {
        return this == NOT_REVIEWED || this == SUBMITTED;
    }

    public static ReviewState of(AuditRequest r) {
        if (r == null) {
            return NOT_REVIEWED;
        }
        return switch (r.getStatus()) {
            case REQUESTED -> REQUESTED;
            case SUBMITTED -> SUBMITTED;
            case REVIEWED -> r.isFindingConfirmed() ? FINDING : PASSED;
        };
    }
}
