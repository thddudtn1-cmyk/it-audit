package com.itaudit.round;

import com.itaudit.checklist.ChecklistItem;

/** 회차 생성 미리보기 1행: 추출된 체크리스트 항목 + 지난 회차의 같은 순번 항목(없으면 null) */
public record RoundCandidate(ChecklistItem item, RoundItem previous) {
}
