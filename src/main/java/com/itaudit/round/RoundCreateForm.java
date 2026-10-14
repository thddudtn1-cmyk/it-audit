package com.itaudit.round;

import java.util.ArrayList;
import java.util.List;

/** 회차 생성 입력값: 연도·분기, 직전 회차 배정 복사 여부, 항목별 제외 */
public class RoundCreateForm {

    private Integer year;
    private Integer quarter;
    private boolean copyPrevious = true;
    private List<Row> rows = new ArrayList<>();

    public static class Row {
        private Long checklistItemId;
        private boolean excluded;
        private String excludeReason;

        public Long getChecklistItemId() { return checklistItemId; }
        public void setChecklistItemId(Long checklistItemId) { this.checklistItemId = checklistItemId; }
        public boolean isExcluded() { return excluded; }
        public void setExcluded(boolean excluded) { this.excluded = excluded; }
        public String getExcludeReason() { return excludeReason; }
        public void setExcludeReason(String excludeReason) { this.excludeReason = excludeReason; }
    }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getQuarter() { return quarter; }
    public void setQuarter(Integer quarter) { this.quarter = quarter; }
    public boolean isCopyPrevious() { return copyPrevious; }
    public void setCopyPrevious(boolean copyPrevious) { this.copyPrevious = copyPrevious; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }
}
