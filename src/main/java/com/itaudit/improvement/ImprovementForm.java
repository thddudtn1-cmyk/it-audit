package com.itaudit.improvement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.itaudit.inspection.InspectionResult;

/** 개선사항·권고사항 등록·수정 입력값 */
public class ImprovementForm {

    private ImprovementKind kind;
    private Long roundId;
    private List<Long> roundItemIds = new ArrayList<>();
    private String teamCode;
    private Long assigneeId;
    private InspectionResult inspectionResult;
    private String detail;
    private String improvementPlan;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate plannedDate;
    private ActionResult actionResult;
    private String actionStatus;

    public static ImprovementForm of(Improvement m) {
        ImprovementForm f = new ImprovementForm();
        f.kind = m.getKind();
        f.roundId = m.getRoundId();
        f.teamCode = m.getTeamCode();
        f.assigneeId = m.getAssigneeId();
        f.inspectionResult = m.getInspectionResult();
        f.detail = m.getDetail();
        f.improvementPlan = m.getImprovementPlan();
        f.plannedDate = m.getPlannedDate();
        f.actionResult = m.getActionResult();
        f.actionStatus = m.getActionStatus();
        return f;
    }

    public ImprovementKind getKind() { return kind; }
    public void setKind(ImprovementKind kind) { this.kind = kind; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public List<Long> getRoundItemIds() { return roundItemIds; }
    public void setRoundItemIds(List<Long> roundItemIds) { this.roundItemIds = roundItemIds; }
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
}
