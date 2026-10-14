package com.itaudit.improvement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.itaudit.inspection.InspectionResult;

/** 개선사항·권고사항 (improvement) */
public class Improvement {

    /** 개선예정일 임박 기준 (일) */
    public static final int DUE_SOON_DAYS = 7;

    private Long improvementId;
    private ImprovementKind kind;
    /** 발생 회차 */
    private Long roundId;
    private String teamCode;
    private Long assigneeId;
    /** 점검 결과 (⑤ 확정값, 권고사항은 없음) */
    private InspectionResult inspectionResult;
    /** 점검 내용 상세 (권고사항: 상세 내용) */
    private String detail;
    /** 개선 방안 */
    private String improvementPlan;
    /** 개선예정일 */
    private LocalDate plannedDate;
    private ActionResult actionResult;
    /** 조치 현황 */
    private String actionStatus;
    private String closedYn;
    private Long closedBy;
    private LocalDateTime closedAt;
    private String closeComment;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    // 조인 값
    private Integer roundYear;
    private Integer quarter;
    private String teamName;
    private String assigneeName;
    private int evidenceCount;
    private List<LinkedItem> items = new ArrayList<>();

    public boolean isClosed() {
        return "Y".equals(closedYn);
    }

    /** ⑤ '감사 시기' = 발생 회차 (예: 2026 3Q) */
    public String getRoundShortLabel() {
        return roundYear == null ? null : roundYear + " " + quarter + "Q";
    }

    /** ⑤ '담당부서/담당자' — 저장은 나눠서, 화면에서만 합친다 (PRD 9장) */
    public String getOwnerLabel() {
        return (teamName == null ? "-" : teamName) + " / " + (assigneeName == null ? "-" : assigneeName);
    }

    /** 개선예정일까지 남은 일수 (음수 = 경과). 예정일이 없으면 null */
    public Long daysLeft(LocalDate today) {
        return plannedDate == null ? null : ChronoUnit.DAYS.between(today, plannedDate);
    }

    /** 오늘 기준 남은 일수 (화면 표시용) */
    public Long getDaysLeft() {
        return daysLeft(LocalDate.now());
    }

    /** 경과 일수 (화면 표시용, 경과가 아니면 0) */
    public long getOverdueDays() {
        Long d = getDaysLeft();
        return d == null || d >= 0 ? 0 : -d;
    }

    /** 미종결이면서 예정일이 지남 */
    public boolean isOverdue() {
        Long d = daysLeft(LocalDate.now());
        return !isClosed() && d != null && d < 0;
    }

    /** 미종결이면서 예정일이 7일 이내 */
    public boolean isDueSoon() {
        Long d = daysLeft(LocalDate.now());
        return !isClosed() && d != null && d >= 0 && d <= DUE_SOON_DAYS;
    }

    public Long getImprovementId() { return improvementId; }
    public void setImprovementId(Long improvementId) { this.improvementId = improvementId; }
    public ImprovementKind getKind() { return kind; }
    public void setKind(ImprovementKind kind) { this.kind = kind; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public String getTeamCode() { return teamCode; }
    public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public InspectionResult getInspectionResult() { return inspectionResult; }
    public void setInspectionResult(InspectionResult inspectionResult) { this.inspectionResult = inspectionResult; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getImprovementPlan() { return improvementPlan; }
    public void setImprovementPlan(String improvementPlan) { this.improvementPlan = improvementPlan; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(LocalDate plannedDate) { this.plannedDate = plannedDate; }
    public ActionResult getActionResult() { return actionResult; }
    public void setActionResult(ActionResult actionResult) { this.actionResult = actionResult; }
    public String getActionStatus() { return actionStatus; }
    public void setActionStatus(String actionStatus) { this.actionStatus = actionStatus; }
    public String getClosedYn() { return closedYn; }
    public void setClosedYn(String closedYn) { this.closedYn = closedYn; }
    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getCloseComment() { return closeComment; }
    public void setCloseComment(String closeComment) { this.closeComment = closeComment; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getRoundYear() { return roundYear; }
    public void setRoundYear(Integer roundYear) { this.roundYear = roundYear; }
    public Integer getQuarter() { return quarter; }
    public void setQuarter(Integer quarter) { this.quarter = quarter; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
    public int getEvidenceCount() { return evidenceCount; }
    public void setEvidenceCount(int evidenceCount) { this.evidenceCount = evidenceCount; }
    public List<LinkedItem> getItems() { return items; }
    public void setItems(List<LinkedItem> items) { this.items = items; }
}
