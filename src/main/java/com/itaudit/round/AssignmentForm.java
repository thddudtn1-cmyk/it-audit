package com.itaudit.round;

import java.util.ArrayList;
import java.util.List;

/** 총괄표 배정 편집 입력값 (회차의 모든 항목을 한 번에 저장) */
public class AssignmentForm {

    private List<Row> rows = new ArrayList<>();

    public static class Row {
        private Long roundItemId;
        private String teamCode;
        private Long assigneeId;
        private String requestMaterial;
        private Integer sampleSize;
        private boolean noReply;
        private boolean excluded;
        private String excludeReason;

        public static Row of(RoundItem item) {
            Row r = new Row();
            r.roundItemId = item.getRoundItemId();
            r.teamCode = item.getTeamCode();
            r.assigneeId = item.getAssigneeId();
            r.requestMaterial = item.getRequestMaterial();
            r.sampleSize = item.getSampleSize();
            r.noReply = item.isNoReply();
            r.excluded = item.isExcluded();
            r.excludeReason = item.getExcludeReason();
            return r;
        }

        public Long getRoundItemId() { return roundItemId; }
        public void setRoundItemId(Long roundItemId) { this.roundItemId = roundItemId; }
        public String getTeamCode() { return teamCode; }
        public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
        public Long getAssigneeId() { return assigneeId; }
        public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
        public String getRequestMaterial() { return requestMaterial; }
        public void setRequestMaterial(String requestMaterial) { this.requestMaterial = requestMaterial; }
        public Integer getSampleSize() { return sampleSize; }
        public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
        public boolean isNoReply() { return noReply; }
        public void setNoReply(boolean noReply) { this.noReply = noReply; }
        public boolean isExcluded() { return excluded; }
        public void setExcluded(boolean excluded) { this.excluded = excluded; }
        public String getExcludeReason() { return excludeReason; }
        public void setExcludeReason(String excludeReason) { this.excludeReason = excludeReason; }
    }

    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }
}
