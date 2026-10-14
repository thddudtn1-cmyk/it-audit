package com.itaudit.request;

/** 사전요청자료 상태 */
public enum RequestStatus {
    /** 감사부서가 요청함 (보완 요청 시에도 이 상태로 돌아감) */
    REQUESTED("요청"),
    /** 담당자가 자료를 제출함 */
    SUBMITTED("제출"),
    /** 감사부서가 미흡 확정 또는 이상 없음으로 판정함 */
    REVIEWED("검토완료");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
