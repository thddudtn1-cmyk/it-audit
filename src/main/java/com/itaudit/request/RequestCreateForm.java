package com.itaudit.request;

import java.util.ArrayList;
import java.util.List;

/** 총괄표에서 항목을 골라 사전요청자료를 요청하는 입력값 */
public class RequestCreateForm {

    private Long roundId;
    private List<Row> rows = new ArrayList<>();

    public static class Row {
        private Long roundItemId;
        private boolean selected;
        private String requestMaterial;
        private Integer sampleSize;

        public Long getRoundItemId() { return roundItemId; }
        public void setRoundItemId(Long roundItemId) { this.roundItemId = roundItemId; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public String getRequestMaterial() { return requestMaterial; }
        public void setRequestMaterial(String requestMaterial) { this.requestMaterial = requestMaterial; }
        public Integer getSampleSize() { return sampleSize; }
        public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    }

    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public List<Row> getRows() { return rows; }
    public void setRows(List<Row> rows) { this.rows = rows; }
}
