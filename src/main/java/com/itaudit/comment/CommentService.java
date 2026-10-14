package com.itaudit.comment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;
import com.itaudit.improvement.ImprovementService;
import com.itaudit.inspection.InspectionService;

/**
 * 코멘트 (F-12): 항목·개선사항 단위로 주관자·담당자·감사부서가 의견을 주고받는다.
 * 대상을 볼 수 있는 사람만 코멘트를 읽고(대상별 열람 규칙 재사용), 조회자는 읽기만 한다.
 * 삭제는 본인 글(주관자는 모든 글)을 논리 삭제하고 활동 이력에 남긴다.
 */
@Service
public class CommentService {

    static final int MAX_LENGTH = 2000;
    private static final Set<Role> WRITERS = Set.of(Role.AUDIT_LEAD, Role.INSPECTOR, Role.AUDIT_DEPT);

    private final CommentMapper mapper;
    private final InspectionService inspectionService;
    private final ImprovementService improvementService;
    private final ActivityLogService activityLog;

    public CommentService(CommentMapper mapper, InspectionService inspectionService,
                          ImprovementService improvementService, ActivityLogService activityLog) {
        this.mapper = mapper;
        this.inspectionService = inspectionService;
        this.improvementService = improvementService;
        this.activityLog = activityLog;
    }

    /** 열람 권한이 없으면 AccessDeniedException */
    public List<Comment> find(CommentTarget type, Long targetId, LoginUser user) {
        checkViewable(type, targetId, user);
        return mapper.findActive(type, targetId);
    }

    public boolean canWrite(LoginUser user) {
        return WRITERS.contains(user.getRole());
    }

    public boolean canDelete(Comment c, LoginUser user) {
        return user.getUserId().equals(c.getAuthorId()) || user.getRole() == Role.AUDIT_LEAD;
    }

    @Transactional
    public Comment add(CommentTarget type, Long targetId, String body, LoginUser user) {
        checkViewable(type, targetId, user);
        if (!canWrite(user)) {
            throw new BusinessException("코멘트는 주관자·담당자·감사부서만 쓸 수 있습니다.");
        }
        if (!StringUtils.hasText(body)) {
            throw new BusinessException("코멘트 내용을 입력하세요.");
        }
        String text = body.trim();
        if (text.length() > MAX_LENGTH) {
            throw new BusinessException("코멘트는 " + MAX_LENGTH + "자 이하여야 합니다.");
        }
        Comment c = new Comment();
        c.setTargetType(type);
        c.setTargetId(targetId);
        c.setBody(text);
        c.setAuthorId(user.getUserId());
        c.setCreatedAt(LocalDateTime.now());
        mapper.insert(c);
        activityLog.log(ActivityLogService.COMMENT_ADD, type.name(), targetId, abbreviate(text));
        return c;
    }

    @Transactional
    public Comment delete(Long commentId, LoginUser user) {
        Comment c = mapper.findById(commentId);
        if (c == null || "Y".equals(c.getDeletedYn())) {
            throw new BusinessException("코멘트를 찾을 수 없습니다.");
        }
        checkViewable(c.getTargetType(), c.getTargetId(), user);
        if (!canDelete(c, user)) {
            throw new BusinessException("본인이 쓴 코멘트만 삭제할 수 있습니다.");
        }
        mapper.markDeleted(commentId, user.getUserId(), LocalDateTime.now());
        activityLog.log(ActivityLogService.COMMENT_DELETE, c.getTargetType().name(), c.getTargetId(),
                "작성자=" + c.getAuthorName() + ", 내용=" + abbreviate(c.getBody()));
        return c;
    }

    /** 대상별 열람 규칙: 점검항목은 InspectionService, 개선사항은 ImprovementService 가 판단한다 */
    private void checkViewable(CommentTarget type, Long targetId, LoginUser user) {
        switch (type) {
            case ROUND_ITEM -> inspectionService.getForView(targetId, user);
            case IMPROVEMENT -> improvementService.get(targetId, user);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }
}
