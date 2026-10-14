package com.itaudit.checklist;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 엑셀 업로드 미리보기 1행. 확정 전까지 세션에 보관한다. */
public class ChecklistUploadRow implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 엑셀 행 번호 (1부터) */
    private int excelRow;
    private Integer seqNo;
    private String seqText;
    private String areaText;
    private AuditArea auditArea;
    private String auditItem;
    /** 빈 칸이라 위 행의 감사 항목을 이어받았는지 */
    private boolean auditItemInherited;
    private String itemDetail;
    private String periodText;
    private AuditPeriod auditPeriod;
    private String regulation;
    private final List<String> errors = new ArrayList<>();

    public boolean hasError() {
        return !errors.isEmpty();
    }

    public void addError(String message) {
        errors.add(message);
    }

    public int getExcelRow() { return excelRow; }
    public void setExcelRow(int excelRow) { this.excelRow = excelRow; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public String getSeqText() { return seqText; }
    public void setSeqText(String seqText) { this.seqText = seqText; }
    public String getAreaText() { return areaText; }
    public void setAreaText(String areaText) { this.areaText = areaText; }
    public AuditArea getAuditArea() { return auditArea; }
    public void setAuditArea(AuditArea auditArea) { this.auditArea = auditArea; }
    public String getAuditItem() { return auditItem; }
    public void setAuditItem(String auditItem) { this.auditItem = auditItem; }
    public boolean isAuditItemInherited() { return auditItemInherited; }
    public void setAuditItemInherited(boolean auditItemInherited) { this.auditItemInherited = auditItemInherited; }
    public String getItemDetail() { return itemDetail; }
    public void setItemDetail(String itemDetail) { this.itemDetail = itemDetail; }
    public String getPeriodText() { return periodText; }
    public void setPeriodText(String periodText) { this.periodText = periodText; }
    public AuditPeriod getAuditPeriod() { return auditPeriod; }
    public void setAuditPeriod(AuditPeriod auditPeriod) { this.auditPeriod = auditPeriod; }
    public String getRegulation() { return regulation; }
    public void setRegulation(String regulation) { this.regulation = regulation; }
    public List<String> getErrors() { return errors; }
}
