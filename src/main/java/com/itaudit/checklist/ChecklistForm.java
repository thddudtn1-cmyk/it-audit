package com.itaudit.checklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 체크리스트 항목 개별 등록·수정 입력값 */
public class ChecklistForm {

    /** 화면 표시 전용. 순번은 자동 채번되며 입력값은 쓰지 않는다 */
    private Integer seqNo;

    @NotNull(message = "감사 영역을 선택하세요.")
    private AuditArea auditArea;

    @NotBlank(message = "감사 항목을 입력하세요.")
    @Size(max = 200, message = "감사 항목은 200자 이하입니다.")
    private String auditItem;

    @NotBlank(message = "항목 상세를 입력하세요.")
    @Size(max = 2000, message = "항목 상세는 2000자 이하입니다.")
    private String itemDetail;

    @NotNull(message = "감사 시기를 선택하세요.")
    private AuditPeriod auditPeriod;

    @Size(max = 500, message = "법규/규정은 500자 이하입니다.")
    private String regulation;

    private boolean active = true;

    public static ChecklistForm of(ChecklistItem item) {
        ChecklistForm f = new ChecklistForm();
        f.setSeqNo(item.getSeqNo());
        f.setAuditArea(item.getAuditArea());
        f.setAuditItem(item.getAuditItem());
        f.setItemDetail(item.getItemDetail());
        f.setAuditPeriod(item.getAuditPeriod());
        f.setRegulation(item.getRegulation());
        f.setActive(item.isActive());
        return f;
    }

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
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
