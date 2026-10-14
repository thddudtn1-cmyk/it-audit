package com.itaudit.auditlog;

import java.util.LinkedHashMap;
import java.util.Map;

import com.itaudit.common.ActivityLogService;

/** 활동 코드 → 화면 표시 이름. 목록 순서가 필터 드롭다운 순서다. */
public final class ActionLabels {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        put(ActivityLogService.LOGIN_SUCCESS, "로그인");
        put(ActivityLogService.LOGIN_FAIL, "로그인 실패");
        put(ActivityLogService.LOGOUT, "로그아웃");
        put(ActivityLogService.PASSWORD_CHANGE, "비밀번호 변경");
        put(ActivityLogService.USER_CREATE, "사용자 등록");
        put(ActivityLogService.USER_UPDATE, "사용자 수정");
        put(ActivityLogService.USER_PASSWORD_RESET, "비밀번호 재설정");
        put(ActivityLogService.TEAM_CREATE, "담당팀 등록");
        put(ActivityLogService.TEAM_UPDATE, "담당팀 수정");
        put(ActivityLogService.CHECKLIST_UPLOAD, "체크리스트 엑셀 업로드");
        put(ActivityLogService.CHECKLIST_COPY, "체크리스트 연도 복사");
        put(ActivityLogService.CHECKLIST_CREATE, "체크리스트 항목 등록");
        put(ActivityLogService.CHECKLIST_UPDATE, "체크리스트 항목 수정");
        put(ActivityLogService.CHECKLIST_DELETE, "체크리스트 항목 삭제");
        put(ActivityLogService.ROUND_CREATE, "회차 생성");
        put(ActivityLogService.ROUND_DELETE, "회차 삭제");
        put(ActivityLogService.ROUND_ITEM_ASSIGN, "담당 배정");
        put(ActivityLogService.ROUND_STATUS, "회차 상태 변경");
        put(ActivityLogService.INSPECTION_SAVE, "점검 임시저장");
        put(ActivityLogService.INSPECTION_SUBMIT, "점검 제출");
        put(ActivityLogService.ROUND_ITEM_CONFIRM, "점검 확인");
        put(ActivityLogService.ROUND_ITEM_CONFIRM_CANCEL, "점검 확인 취소");
        put(ActivityLogService.ROUND_ITEM_REJECT, "점검 반려");
        put(ActivityLogService.ROUND_SUBMIT, "감사부서 제출");
        put(ActivityLogService.REQUEST_CREATE, "사전요청자료 요청");
        put(ActivityLogService.REQUEST_SAVE, "사전요청자료 임시저장");
        put(ActivityLogService.REQUEST_SUBMIT, "사전요청자료 제출");
        put(ActivityLogService.REQUEST_REVIEW, "미흡 확정·판정");
        put(ActivityLogService.ROUND_DONE, "적정성 점검 완료");
        put(ActivityLogService.IMPROVEMENT_CREATE, "개선·권고 등록");
        put(ActivityLogService.IMPROVEMENT_UPDATE, "개선·권고 수정");
        put(ActivityLogService.IMPROVEMENT_CLOSE, "종결");
        put(ActivityLogService.IMPROVEMENT_REOPEN, "종결 취소");
        put(ActivityLogService.EVIDENCE_UPLOAD, "파일 업로드");
        put(ActivityLogService.EVIDENCE_DELETE, "파일 삭제");
        put(ActivityLogService.EXCEL_EXPORT, "엑셀 내보내기");
        put(ActivityLogService.COMMENT_ADD, "코멘트 작성");
        put(ActivityLogService.COMMENT_DELETE, "코멘트 삭제");
    }

    private ActionLabels() {
    }

    private static void put(String code, String label) {
        LABELS.put(code, label);
    }

    public static Map<String, String> all() {
        return LABELS;
    }

    /** 모르는 코드는 코드 그대로 보여준다 */
    public static String label(String code) {
        return LABELS.getOrDefault(code, code);
    }
}
