package com.itaudit.improvement;

import com.itaudit.checklist.AuditArea;
import com.itaudit.checklist.AuditPeriod;

/** 개선사항에 연결된 회차 점검항목 (표시용) */
public class LinkedItem {

    private Long improvementId;
    private Long roundItemId;
    private Integer seqNo;
    private AuditArea auditArea;
    private String auditItem;
    private String itemDetail;
    private AuditPeriod auditPeriod;

    public Long getImprovementId() { return improvementId; }
    public void setImprovementId(Long improvementId) { this.improvementId = improvementId; }
    public Long getRoundItemId() { return roundItemId; }
    public void setRoundItemId(Long roundItemId) { this.roundItemId = roundItemId; }
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
}
