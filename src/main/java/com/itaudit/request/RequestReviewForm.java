package com.itaudit.request;

import com.itaudit.inspection.InspectionResult;

/** 감사부서 판정 입력값 */
public class RequestReviewForm {

    /** 판정 */
    public enum Decision {
        /** 미흡 확정 → 개선사항 관리대장 등록 대상 */
        FINDING,
        /** 이상 없음 */
        NO_FINDING,
        /** 보완 요청 → 담당자에게 다시 요청 */
        SUPPLEMENT
    }

    private Decision decision;
    private InspectionResult confirmedResult;
    private String findingDetail;
    private String deptComment;

    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }
    public InspectionResult getConfirmedResult() { return confirmedResult; }
    public void setConfirmedResult(InspectionResult confirmedResult) { this.confirmedResult = confirmedResult; }
    public String getFindingDetail() { return findingDetail; }
    public void setFindingDetail(String findingDetail) { this.findingDetail = findingDetail; }
    public String getDeptComment() { return deptComment; }
    public void setDeptComment(String deptComment) { this.deptComment = deptComment; }
}
