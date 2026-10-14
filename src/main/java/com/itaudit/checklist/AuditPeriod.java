package com.itaudit.checklist;

/** 감사 시기 코드: 1분기 · 2분기 · 3분기 · 4분기 · 매분기 */
public enum AuditPeriod {
    Q1("1분기"),
    Q2("2분기"),
    Q3("3분기"),
    Q4("4분기"),
    EVERY("매분기");

    private final String label;

    AuditPeriod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 엑셀 표기 → 코드. 공백을 무시하고 '1Q'·'1/4분기' 같은 변형은 받지 않는다. 없으면 null */
    public static AuditPeriod fromLabel(String text) {
        if (text == null) {
            return null;
        }
        String key = text.replaceAll("\\s+", "");
        for (AuditPeriod p : values()) {
            if (p.label.equals(key)) {
                return p;
            }
        }
        return null;
    }
}
