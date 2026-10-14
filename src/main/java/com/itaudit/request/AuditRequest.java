package com.itaudit.request;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.itaudit.checklist.AuditArea;
import com.itaudit.checklist.AuditPeriod;
import com.itaudit.inspection.InspectionResult;
import com.itaudit.round.RoundStatus;

/** 적정성 점검 기록·보완자료 요청 (audit_request). 조회 시 회차 점검항목·체크리스트·회차 정보를 조인해 채운다. */
public class AuditRequest {

    private Long requestId;
    private Long roundItemId;
    /** 감사부서가 요청한 보완자료 (보완 요청 없이 바로 판정한 행은 null) */
    private String requestMaterial;
    private Integer sampleSize;
    /** 비고 (④, 담당자 입력) */
    private String remark;
    private RequestStatus status;
    /** 보완 요청·검토 의견 (감사부서) */
    private String deptComment;
    /** Y=미흡 확정, N=이상 없음, null=미검토 */
    private String findingYn;
    /** 확정 점검결과 (⑤ '점검 결과') */
    private InspectionResult confirmedResult;
    /** 미흡 내용 */
    private String findingDetail;
    private Long requestedBy;
    private LocalDateTime requestedAt;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    // 조인 값
    private Long roundId;
    private Integer roundYear;
    private Integer quarter;
    private RoundStatus roundStatus;
    private Integer seqNo;
    private AuditArea auditArea;
    private String auditItem;
    private String itemDetail;
    private AuditPeriod auditPeriod;
    private String teamCode;
    private String teamName;
    private Long assigneeId;
    private String assigneeName;
    /** ③ 담당자 점검 입력 (감사부서 참고·개선사항 기본값) */
    private InspectionResult inspectionResult;
    private String inspectionDetail;
    private String actionPlan;
    private LocalDate plannedDate;
    private int evidenceCount;

    public boolean isFindingConfirmed() {
        return "Y".equals(findingYn);
    }

    public boolean isNoFinding() {
        return "N".equals(findingYn);
    }

    public String getRoundLabel() {
        return roundYear == null ? null : roundYear + "년 " + quarter + "분기";
    }

    /** 미흡 확정 표시 문구 */
    public String getFindingLabel() {
        if (findingYn == null) {
            return "-";
        }
        return isFindingConfirmed() ? "미흡 확정" : "이상 없음";
    }

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getRoundItemId() { return roundItemId; }
    public void setRoundItemId(Long roundItemId) { this.roundItemId = roundItemId; }
    public String getRequestMaterial() { return requestMaterial; }
    public void setRequestMaterial(String requestMaterial) { this.requestMaterial = requestMaterial; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
    public String getDeptComment() { return deptComment; }
    public void setDeptComment(String deptComment) { this.deptComment = deptComment; }
    public String getFindingYn() { return findingYn; }
    public void setFindingYn(String findingYn) { this.findingYn = findingYn; }
    public InspectionResult getConfirmedResult() { return confirmedResult; }
    public void setConfirmedResult(InspectionResult confirmedResult) { this.confirmedResult = confirmedResult; }
    public String getFindingDetail() { return findingDetail; }
    public void setFindingDetail(String findingDetail) { this.findingDetail = findingDetail; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public Integer getRoundYear() { return roundYear; }
    public void setRoundYear(Integer roundYear) { this.roundYear = roundYear; }
    public Integer getQuarter() { return quarter; }
    public void setQuarter(Integer quarter) { this.quarter = quarter; }
    public RoundStatus getRoundStatus() { return roundStatus; }
    public void setRoundStatus(RoundStatus roundStatus) { this.roundStatus = roundStatus; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public AuditArea getAuditArea() { return auditArea; }
    public void setAuditArea(AuditArea auditArea) { this.auditArea = auditArea; }
    public String getAuditItem() { return auditItem; }
    public void setAuditItem(String auditItem) { this.auditItem = auditItem; }
    public String getItemDetail() { return itemDetail; }
    public void setItemDetail(String itemDetail) { this.itemDetail = itemDetail; }
    public AuditPeriod getAuditPeriod() { return auditPeriod; }
    public void setAuditPeriod(AuditPeriod auditPeriod) { this.auditPeriod = auditPeriod; }
    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
    public InspectionResult getInspectionResult() { return inspectionResult; }
    public void setInspectionResult(InspectionResult inspectionResult) { this.inspectionResult = inspectionResult; }
    public String getInspectionDetail() { return inspectionDetail; }
    public void setInspectionDetail(String inspectionDetail) { this.inspectionDetail = inspectionDetail; }
    public String getActionPlan() { return actionPlan; }
    public void setActionPlan(String actionPlan) { this.actionPlan = actionPlan; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(LocalDate plannedDate) { this.plannedDate = plannedDate; }
    public int getEvidenceCount() { return evidenceCount; }
    public void setEvidenceCount(int evidenceCount) { this.evidenceCount = evidenceCount; }
}
