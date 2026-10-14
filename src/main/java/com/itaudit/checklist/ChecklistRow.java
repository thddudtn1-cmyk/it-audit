package com.itaudit.checklist;

/**
 * 목록 화면 1행. ② 엑셀처럼 같은 감사 영역·감사 항목이 이어지면 셀을 합쳐 보여주기 위한 rowspan 을 담는다.
 * rowspan 이 0 이면 위 행과 합쳐진 칸이라 그리지 않는다.
 */
public record ChecklistRow(ChecklistItem item, int areaRowspan, int auditItemRowspan) {
}
