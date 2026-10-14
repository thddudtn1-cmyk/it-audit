package com.itaudit.checklist;

/**
 * 감사 영역 (PRD 항목 정의서: 선택 3개).
 * [가정] 오픈 이슈 '감사 영역 분류 확정'은 ② 체크리스트의 분류를 원본으로 삼는다.
 *        ③ 총괄표의 '시스템 개발 및 변경 관리'처럼 띄어쓰기만 다른 표기는 같은 영역으로 인식한다.
 */
public enum AuditArea {
    IT_OPS("IT시스템 운영 및 유지보수"),
    SECURITY("정보보호 및 개인정보보호"),
    DEV_CHANGE("시스템 개발 및 변경관리");

    private final String label;

    AuditArea(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 엑셀 표기 → 코드. 공백을 무시하고 비교하며, 일치하는 영역이 없으면 null */
    public static AuditArea fromLabel(String text) {
        if (text == null) {
            return null;
        }
        String key = text.replaceAll("\\s+", "");
        for (AuditArea area : values()) {
            if (area.label.replaceAll("\\s+", "").equals(key)) {
                return area;
            }
        }
        return null;
    }
}
