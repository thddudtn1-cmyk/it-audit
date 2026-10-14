package com.itaudit.checklist;

import java.time.LocalDateTime;

/** 체크리스트 마스터 1건 = 항목 상세 1개 (checklist_item) */
public class ChecklistItem {

    private Long itemId;
    private Integer applyYear;
    /** 순번 (② 체크리스트의 순번, 연도 안에서 유일한 마스터 코드) */
    private Integer seqNo;
    private AuditArea auditArea;
    private String auditItem;
    private String itemDetail;
    private AuditPeriod auditPeriod;
    private String regulation;
    private String useYn;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return "Y".equals(useYn);
    }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Integer getApplyYear() { return applyYear; }
    public void setApplyYear(Integer applyYear) { this.applyYear = applyYear; }
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
    public String getUseYn() { return useYn; }
    public void setUseYn(String useYn) { this.useYn = useYn; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
