package com.itaudit.common;

import java.time.LocalDateTime;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.itaudit.auth.LoginUser;

/** 주요 행위를 activity_log 에 남긴다. 행위자는 현재 로그인 사용자로 채운다. */
@Service
public class ActivityLogService {

    // 행위 코드
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String TEAM_CREATE = "TEAM_CREATE";
    public static final String TEAM_UPDATE = "TEAM_UPDATE";
    public static final String CHECKLIST_UPLOAD = "CHECKLIST_UPLOAD";
    public static final String CHECKLIST_COPY = "CHECKLIST_COPY";
    public static final String CHECKLIST_CREATE = "CHECKLIST_CREATE";
    public static final String CHECKLIST_UPDATE = "CHECKLIST_UPDATE";
    public static final String ROUND_CREATE = "ROUND_CREATE";
    public static final String ROUND_ITEM_ASSIGN = "ROUND_ITEM_ASSIGN";
    public static final String ROUND_STATUS = "ROUND_STATUS";
    public static final String ROUND_SUBMIT = "ROUND_SUBMIT";
    public static final String ROUND_ITEM_CONFIRM = "ROUND_ITEM_CONFIRM";
    public static final String ROUND_ITEM_CONFIRM_CANCEL = "ROUND_ITEM_CONFIRM_CANCEL";
    public static final String ROUND_ITEM_REJECT = "ROUND_ITEM_REJECT";
    public static final String INSPECTION_SAVE = "INSPECTION_SAVE";
    public static final String INSPECTION_SUBMIT = "INSPECTION_SUBMIT";
    public static final String EVIDENCE_UPLOAD = "EVIDENCE_UPLOAD";
    public static final String EVIDENCE_DELETE = "EVIDENCE_DELETE";

    private final ActivityLogMapper mapper;

    public ActivityLogService(ActivityLogMapper mapper) {
        this.mapper = mapper;
    }

    public void log(String action, String targetType, Object targetId, String detail) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            write(user.getUserId(), user.getLoginId(), action, targetType, targetId, detail);
        } else {
            write(null, null, action, targetType, targetId, detail);
        }
    }

    /** 로그인 실패처럼 인증 정보가 아직 없는 시점에 행위자를 직접 지정 */
    public void write(Long userId, String loginId, String action, String targetType, Object targetId, String detail) {
        ActivityLog log = new ActivityLog();
        log.setUserId(userId);
        log.setLoginId(loginId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : String.valueOf(targetId));
        log.setDetail(truncate(detail, 1000));
        log.setClientIp(currentClientIp());
        log.setCreatedAt(LocalDateTime.now());
        mapper.insert(log);
    }

    private static String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
