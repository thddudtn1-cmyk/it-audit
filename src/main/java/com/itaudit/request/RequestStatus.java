package com.itaudit.request;

/** 보완자료 요청 상태 (audit_request.status). 화면에는 ReviewState 로 보여준다. */
public enum RequestStatus {
    /** 감사부서가 보완자료를 요청함 (담당자 제출 대기) */
    REQUESTED("보완 요청"),
    /** 담당자가 보완자료를 제출함 */
    SUBMITTED("보완 제출"),
    /** 감사부서가 판정함 (적정 또는 미흡 확정) */
    REVIEWED("검토완료");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
