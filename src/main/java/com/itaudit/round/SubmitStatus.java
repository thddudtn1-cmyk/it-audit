package com.itaudit.round;

/** 회차 점검항목 제출 상태. 제출은 F-04, 확인·반려는 F-05 에서 쓴다. */
public enum SubmitStatus {
    NOT_SUBMITTED("미제출"),
    SUBMITTED("제출"),
    CONFIRMED("확인"),
    REJECTED("반려");

    private final String label;

    SubmitStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
