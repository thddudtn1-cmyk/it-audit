package com.itaudit.inspection;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.itaudit.round.RoundItem;

/** 담당자 점검 입력값 (③ 총괄표의 담당자 기입 칸) */
public class InspectionForm {

    private InspectionResult inspectionResult;
    private String inspectionDetail;
    private String remark1;
    private String actionPlan;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate plannedDate;

    public static InspectionForm of(RoundItem item) {
        InspectionForm f = new InspectionForm();
        f.inspectionResult = item.getInspectionResult();
        f.inspectionDetail = item.getInspectionDetail();
        f.remark1 = item.getRemark1();
        f.actionPlan = item.getActionPlan();
        f.plannedDate = item.getPlannedDate();
        return f;
    }

    public InspectionResult getInspectionResult() { return inspectionResult; }
    public void setInspectionResult(InspectionResult inspectionResult) { this.inspectionResult = inspectionResult; }
    public String getInspectionDetail() { return inspectionDetail; }
    public void setInspectionDetail(String inspectionDetail) { this.inspectionDetail = inspectionDetail; }
    public String getRemark1() { return remark1; }
    public void setRemark1(String remark1) { this.remark1 = remark1; }
    public String getActionPlan() { return actionPlan; }
    public void setActionPlan(String actionPlan) { this.actionPlan = actionPlan; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(LocalDate plannedDate) { this.plannedDate = plannedDate; }
}
