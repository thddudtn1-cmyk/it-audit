package com.itaudit.dashboard;

/** 대시보드 막대 1행: 이름표 + 값 (+ 분모). 막대 길이는 max 대비 비율 */
public class CountRow {

    private String key;
    private String label;
    private String subLabel;
    private int value;
    private int total;

    public CountRow() {
    }

    public CountRow(String key, String label, int value) {
        this.key = key;
        this.label = label;
        this.value = value;
    }

    /** 분모가 있는 행의 비율 (%) */
    public int getRate() {
        return total <= 0 ? 0 : (int) Math.round(value * 100.0 / total);
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSubLabel() { return subLabel; }
    public void setSubLabel(String subLabel) { this.subLabel = subLabel; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
}
