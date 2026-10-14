package com.itaudit.round;

import java.time.LocalDateTime;

import com.itaudit.checklist.AuditArea;
import com.itaudit.checklist.AuditPeriod;

/**
 * 회차 점검항목 (round_item). 조회 시 체크리스트 마스터·팀·담당자 이름을 조인해 채운다.
 * [가정] 오픈 이슈 '#42 문구 차이'가 정해지기 전까지 항목 문구는 회차에 복사하지 않고 마스터를 참조한다.
 */
public class RoundItem {

    private Long roundItemId;
    private Long roundId;
    private Long checklistItemId;
    private String teamCode;
    private Long assigneeId;
    /** 사전요청자료 (③ 총괄표: 주관자가 담당자에게 준비시키는 증적) */
    private String requestMaterial;
    private Integer sampleSize;
    private String noReplyYn;
    private String excludedYn;
    private String excludeReason;
    private SubmitStatus submitStatus;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    // 조인 값
    private Integer seqNo;
    private AuditArea auditArea;
    private String auditItem;
    private String itemDetail;
    private AuditPeriod auditPeriod;
    private String regulation;
    private String teamName;
    private String assigneeName;
    /** 지난 회차 조회(findItemsBefore) 때만 채운다 */
    private Integer roundYear;
    private Integer quarter;

    public String getRoundLabel() {
        return roundYear == null ? null : roundYear + "년 " + quarter + "분기";
    }

    public boolean isExcluded() {
        return "Y".equals(excludedYn);
    }

    public boolean isNoReply() {
        return "Y".equals(noReplyYn);
    }

    public Long getRoundItemId() { return roundItemId; }
    public void setRoundItemId(Long roundItemId) { this.roundItemId = roundItemId; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public Long getChecklistItemId() { return checklistItemId; }
    public void setChecklistItemId(Long checklistItemId) { this.checklistItemId = checklistItemId; }
    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public String getRequestMaterial() { return requestMaterial; }
    public void setRequestMaterial(String requestMaterial) { this.requestMaterial = requestMaterial; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    public String getNoReplyYn() { return noReplyYn; }
    public void setNoReplyYn(String noReplyYn) { this.noReplyYn = noReplyYn; }
    public String getExcludedYn() { return excludedYn; }
    public void setExcludedYn(String excludedYn) { this.excludedYn = excludedYn; }
    public String getExcludeReason() { return excludeReason; }
    public void setExcludeReason(String excludeReason) { this.excludeReason = excludeReason; }
    public SubmitStatus getSubmitStatus() { return submitStatus; }
    public void setSubmitStatus(SubmitStatus submitStatus) { this.submitStatus = submitStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
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
    public String getRegulation() { return regulation; }
    public void setRegulation(String regulation) { this.regulation = regulation; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
    public Integer getRoundYear() { return roundYear; }
    public void setRoundYear(Integer roundYear) { this.roundYear = roundYear; }
    public Integer getQuarter() { return quarter; }
    public void setQuarter(Integer quarter) { this.quarter = quarter; }
}
