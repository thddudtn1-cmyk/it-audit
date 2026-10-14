package com.itaudit.evidence;

/** 증적이 붙는 대상 */
public enum EvidenceOwnerType {
    /** 회차 점검항목 (F-04 담당자 증적) */
    ROUND_ITEM,
    /** 적정성 점검 보완자료 (F-06 제출 자료) */
    AUDIT_REQUEST,
    /** 개선사항·권고사항 (F-07 증빙자료) */
    IMPROVEMENT
}
