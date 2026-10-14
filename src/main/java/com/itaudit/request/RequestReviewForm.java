package com.itaudit.request;

import com.itaudit.inspection.InspectionResult;

/** 감사부서 적정성 판정 입력값 */
public class RequestReviewForm {

    /** 판정 */
    public enum Decision {
        /** 미흡 확정 → 개선사항 관리대장 등록 대상 */
        FINDING,
        /** 적정 (이상 없음) */
        NO_FINDING,
        /** 보완자료 요청 → 담당자가 자료를 다시 제출 */
        SUPPLEMENT
    }

    private Decision decision;
    private InspectionResult confirmedResult;
    private String findingDetail;
    /** 보완 요청할 자료 (보완 요청 시 필수) */
    private String requestMaterial;
    private String deptComment;

    public Decision getDecision() { return decision; }
    public void setDecision(Decision decision) { this.decision = decision; }
    public InspectionResult getConfirmedResult() { return confirmedResult; }
    public void setConfirmedResult(InspectionResult confirmedResult) { this.confirmedResult = confirmedResult; }
    public String getFindingDetail() { return findingDetail; }
    public void setFindingDetail(String findingDetail) { this.findingDetail = findingDetail; }
    public String getRequestMaterial() { return requestMaterial; }
    public void setRequestMaterial(String requestMaterial) { this.requestMaterial = requestMaterial; }
    public String getDeptComment() { return deptComment; }
    public void setDeptComment(String deptComment) { this.deptComment = deptComment; }
}
