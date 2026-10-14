package com.itaudit.round;

import java.time.LocalDateTime;

/** 분기 점검 회차 (audit_round). 목록 조회 시에는 항목 집계 값도 채운다. */
public class AuditRound {

    private Long roundId;
    private Integer roundYear;
    private Integer quarter;
    private RoundStatus status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    /** 감사부서 제출 (F-05) */
    private Long submittedBy;
    private LocalDateTime submittedAt;

    // 집계 (목록·상세 요약)
    private int totalCount;
    private int excludedCount;
    private int noReplyCount;
    private int assignedCount;
    private int submittedCount;
    private int confirmedCount;
    /**
     * 회차 삭제를 막는 개선사항·권고사항 수: 직접 등록했거나(미흡 확정 등록·권고사항), 조치가 진행·최종확정됐거나,
     * 증빙·코멘트가 있는 건. 0보다 크면 삭제할 수 없다. (아무도 손대지 않은 자동 등록 건은 회차와 함께 지운다)
     */
    private int improvementCount;

    public String getLabel() {
        return roundYear + "년 " + quarter + "분기";
    }

    /** ⑤ 관리대장의 '감사 시기' 표기 (예: 2026 3Q) */
    public String getShortLabel() {
        return roundYear + " " + quarter + "Q";
    }

    /** 점검 대상 = 제외하지 않은 항목 */
    public int getTargetCount() {
        return totalCount - excludedCount;
    }

    /**
     * 제출 대상 = 점검 대상 중 회신불필요가 아닌 항목.
     * [가정] '회신불필요' 항목은 담당자 회신 없이 끝나므로 제출률 분모에서 뺀다.
     */
    public int getSubmitTargetCount() {
        return getTargetCount() - noReplyCount;
    }

    /** 제출 대상 항목이 모두 제출(또는 확인)되었는지 → 검토중 */
    public boolean isAllCollected() {
        return getTargetCount() > 0 && submittedCount == getSubmitTargetCount();
    }

    /** 감사부서 제출 가능: 검토중이고 제출 대상이 모두 확인됨 */
    public boolean isReadyToSubmit() {
        return status == RoundStatus.REVIEW && confirmedCount == getSubmitTargetCount();
    }

    /** 확인을 기다리는 항목 수 (제출됐지만 미확인) */
    public int getAwaitingConfirmCount() {
        return submittedCount - confirmedCount;
    }

    public int getSubmitRate() {
        int target = getSubmitTargetCount();
        return target <= 0 ? 0 : (int) Math.round(submittedCount * 100.0 / target);
    }

    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public Integer getRoundYear() { return roundYear; }
    public void setRoundYear(Integer roundYear) { this.roundYear = roundYear; }
    public Integer getQuarter() { return quarter; }
    public void setQuarter(Integer quarter) { this.quarter = quarter; }
    public RoundStatus getStatus() { return status; }
    public void setStatus(RoundStatus status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public int getImprovementCount() { return improvementCount; }
    public void setImprovementCount(int improvementCount) { this.improvementCount = improvementCount; }
    public int getConfirmedCount() { return confirmedCount; }
    public void setConfirmedCount(int confirmedCount) { this.confirmedCount = confirmedCount; }
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getExcludedCount() { return excludedCount; }
    public void setExcludedCount(int excludedCount) { this.excludedCount = excludedCount; }
    public int getNoReplyCount() { return noReplyCount; }
    public void setNoReplyCount(int noReplyCount) { this.noReplyCount = noReplyCount; }
    public int getAssignedCount() { return assignedCount; }
    public void setAssignedCount(int assignedCount) { this.assignedCount = assignedCount; }
    public int getSubmittedCount() { return submittedCount; }
    public void setSubmittedCount(int submittedCount) { this.submittedCount = submittedCount; }
}
