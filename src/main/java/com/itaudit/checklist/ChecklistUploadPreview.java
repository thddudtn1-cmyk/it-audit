package com.itaudit.checklist;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 엑셀 업로드 파싱 결과. 화면에서 확인한 뒤 확정하면 저장한다. */
public class ChecklistUploadPreview implements Serializable {

    private static final long serialVersionUID = 1L;

    private int applyYear;
    private String fileName;
    private String sheetName;
    /** 파일 전체에 대한 오류 (헤더 없음, 읽기 실패 등) */
    private final List<String> fileErrors = new ArrayList<>();
    private final List<ChecklistUploadRow> rows = new ArrayList<>();
    /** 같은 연도에 이미 있는 항목 수 (확정 시 대체됨) */
    private int existingCount;
    /** 같은 연도 항목으로 만든 분기 회차가 있으면 대체 불가 */
    private boolean usedInRounds;

    public boolean isValid() {
        return fileErrors.isEmpty() && !rows.isEmpty() && rows.stream().noneMatch(ChecklistUploadRow::hasError);
    }

    /** 확정 가능 여부 = 오류 없음 + 회차에서 쓰지 않는 연도 */
    public boolean isSavable() {
        return isValid() && !usedInRounds;
    }

    public long getErrorRowCount() {
        return rows.stream().filter(ChecklistUploadRow::hasError).count();
    }

    public long getInheritedCount() {
        return rows.stream().filter(ChecklistUploadRow::isAuditItemInherited).count();
    }

    /** 감사 시기별 건수 (화면 요약용) */
    public Map<AuditPeriod, Long> getPeriodCounts() {
        Map<AuditPeriod, Long> counts = new EnumMap<>(AuditPeriod.class);
        for (AuditPeriod p : AuditPeriod.values()) {
            counts.put(p, rows.stream().filter(r -> r.getAuditPeriod() == p).count());
        }
        return counts;
    }

    public int getApplyYear() { return applyYear; }
    public void setApplyYear(int applyYear) { this.applyYear = applyYear; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    public List<String> getFileErrors() { return fileErrors; }
    public List<ChecklistUploadRow> getRows() { return rows; }
    public int getExistingCount() { return existingCount; }
    public void setExistingCount(int existingCount) { this.existingCount = existingCount; }
    public boolean isUsedInRounds() { return usedInRounds; }
    public void setUsedInRounds(boolean usedInRounds) { this.usedInRounds = usedInRounds; }
}
