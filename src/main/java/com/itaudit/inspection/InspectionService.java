package com.itaudit.inspection;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;
import com.itaudit.evidence.EvidenceAccess;
import com.itaudit.evidence.EvidenceOwnerType;
import com.itaudit.evidence.EvidenceService;
import com.itaudit.round.AuditRound;
import com.itaudit.round.ReviewService;
import com.itaudit.round.RoundItem;
import com.itaudit.round.RoundMapper;
import com.itaudit.round.SubmitStatus;

/**
 * 담당자 점검 입력·증적·제출 (F-04).
 * 입력은 배정된 점검 담당자만, 회차가 감사부서에 제출되기 전이고 항목이 미제출·반려 상태일 때만 할 수 있다.
 */
@Service
public class InspectionService implements EvidenceAccess {

    private final RoundMapper roundMapper;
    private final EvidenceService evidenceService;
    private final ActivityLogService activityLog;
    private final ReviewService reviewService;

    // EvidenceService 가 접근 규칙(EvidenceAccess)으로 이 서비스를 받으므로 순환 참조를 @Lazy 로 끊는다
    public InspectionService(RoundMapper roundMapper, @Lazy EvidenceService evidenceService,
                             ActivityLogService activityLog, ReviewService reviewService) {
        this.roundMapper = roundMapper;
        this.evidenceService = evidenceService;
        this.activityLog = activityLog;
        this.reviewService = reviewService;
    }

    /** 화면에 필요한 항목·회차 묶음 */
    public record Target(RoundItem item, AuditRound round) {
    }

    // ---------- 조회 ----------

    public List<RoundItem> findMyItems(LoginUser user) {
        return roundMapper.findByAssignee(user.getUserId());
    }

    public Target getForView(Long roundItemId, LoginUser user) {
        Target t = load(roundItemId);
        checkViewable(t, user);
        return t;
    }

    public List<RoundItem> findHistory(Target t) {
        return roundMapper.findHistory(t.item().getSeqNo(), t.round().getRoundYear(), t.round().getQuarter());
    }

    /** 이 사용자가 지금 입력·제출할 수 있는지 (화면 버튼 표시용) */
    public boolean canEdit(Target t, LoginUser user) {
        return editBlockReason(t, user) == null;
    }

    /** 입력할 수 없는 이유 (입력 가능하면 null) */
    public String editBlockReason(Target t, LoginUser user) {
        if (user.getRole() != Role.INSPECTOR || !Objects.equals(t.item().getAssigneeId(), user.getUserId())) {
            return "배정된 점검 담당자만 입력할 수 있습니다.";
        }
        if (t.item().isExcluded()) {
            return "이번 회차에서 제외된 항목입니다.";
        }
        if (!t.round().getStatus().isEditable()) {
            return "감사부서에 제출된 회차라 수정할 수 없습니다.";
        }
        if (!t.item().isInputOpen()) {
            return "이미 제출한 항목입니다. IT자체감사 담당자가 반려하면 다시 수정할 수 있습니다.";
        }
        return null;
    }

    // ---------- 저장·제출 ----------

    /**
     * 점검 입력을 저장하고 새 증적 파일을 올린다. submit 이면 필수 값을 확인한 뒤 제출 상태로 바꾼다.
     * 검증에 실패하면 아무것도 저장하지 않는다.
     */
    @Transactional
    public void save(Long roundItemId, InspectionForm form, List<MultipartFile> files, boolean submit, LoginUser actor) {
        Target t = load(roundItemId);
        checkViewable(t, actor);
        String blocked = editBlockReason(t, actor);
        if (blocked != null) {
            throw new BusinessException(blocked);
        }

        List<MultipartFile> newFiles = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        List<String> errors = validate(form, submit, t.item().getEvidenceCount() + newFiles.size());
        for (MultipartFile f : newFiles) {
            try {
                evidenceService.validate(f);
            } catch (BusinessException e) {
                errors.add(e.getMessage());
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join(" / ", errors));
        }

        RoundItem item = t.item();
        InspectionResult before = item.getInspectionResult();
        item.setInspectionResult(form.getInspectionResult());
        item.setInspectionDetail(trimToNull(form.getInspectionDetail()));
        item.setRemark1(trimToNull(form.getRemark1()));
        item.setActionPlan(trimToNull(form.getActionPlan()));
        item.setPlannedDate(form.getPlannedDate());
        LocalDateTime now = LocalDateTime.now();
        item.setUpdatedBy(actor.getUserId());
        item.setUpdatedAt(now);
        roundMapper.updateInspection(item);

        for (MultipartFile f : newFiles) {
            evidenceService.store(EvidenceOwnerType.ROUND_ITEM, roundItemId, f, actor);
        }

        String where = t.round().getLabel() + " #" + item.getSeqNo();
        // 판정(점검결과) 변경은 항상 이력을 남긴다
        String resultChange = before == item.getInspectionResult() ? ""
                : ", 점검결과 " + label(before) + " → " + label(item.getInspectionResult());
        if (submit) {
            item.setSubmitStatus(SubmitStatus.SUBMITTED);
            item.setSubmittedBy(actor.getUserId());
            item.setSubmittedAt(now);
            roundMapper.updateSubmitStatus(item);
            activityLog.log(ActivityLogService.INSPECTION_SUBMIT, "ROUND_ITEM", roundItemId,
                    where + " 제출, 점검결과=" + label(item.getInspectionResult()) + resultChange);
            reviewService.refreshStatus(item.getRoundId(), actor);
        } else {
            activityLog.log(ActivityLogService.INSPECTION_SAVE, "ROUND_ITEM", roundItemId,
                    where + " 임시저장" + resultChange + (newFiles.isEmpty() ? "" : ", 파일 " + newFiles.size() + "건"));
        }
    }

    /** 임시저장은 길이만, 제출은 필수 값까지 확인 */
    private static List<String> validate(InspectionForm f, boolean submit, int evidenceCount) {
        List<String> errors = new ArrayList<>();
        if (length(f.getInspectionDetail()) > 4000) {
            errors.add("점검 내용 상세는 4000자 이하여야 합니다.");
        }
        if (length(f.getRemark1()) > 1000) {
            errors.add("비고1은 1000자 이하여야 합니다.");
        }
        if (length(f.getActionPlan()) > 2000) {
            errors.add("조치내용은 2000자 이하여야 합니다.");
        }
        if (submit) {
            if (f.getInspectionResult() == null) {
                errors.add("점검결과를 선택하세요.");
            }
            if (!StringUtils.hasText(f.getInspectionDetail())) {
                errors.add("점검 내용 상세를 입력하세요.");
            }
            if (f.getInspectionResult() != null && f.getInspectionResult().requiresAction()) {
                if (!StringUtils.hasText(f.getActionPlan())) {
                    errors.add("점검결과가 정상이 아니면 조치내용을 입력해야 합니다.");
                }
                if (f.getPlannedDate() == null) {
                    errors.add("점검결과가 정상이 아니면 개선 예정일을 입력해야 합니다.");
                }
            }
        }
        return errors;
    }

    // ---------- 권한 ----------

    private Target load(Long roundItemId) {
        RoundItem item = roundMapper.findItem(roundItemId);
        if (item == null) {
            throw new BusinessException("점검항목을 찾을 수 없습니다.");
        }
        return new Target(item, roundMapper.findRound(item.getRoundId()));
    }

    /**
     * 열람 권한: 점검 담당자는 본인 배정 항목, 주관자·조회자는 전체, 감사부서는 제출된 회차만.
     * 관리자는 점검 업무 데이터를 보지 않는다.
     */
    private static void checkViewable(Target t, LoginUser user) {
        boolean ok = switch (user.getRole()) {
            case INSPECTOR -> Objects.equals(t.item().getAssigneeId(), user.getUserId());
            case AUDIT_LEAD, VIEWER -> true;
            case AUDIT_DEPT -> t.round().getStatus().isVisibleToAuditDept();
            case ADMIN -> false;
        };
        if (!ok) {
            throw new AccessDeniedException("점검항목 열람 권한 없음");
        }
    }

    @Override
    public EvidenceOwnerType ownerType() {
        return EvidenceOwnerType.ROUND_ITEM;
    }

    @Override
    public void checkView(Long ownerId, LoginUser user) {
        checkViewable(load(ownerId), user);
    }

    @Override
    public void checkModify(Long ownerId, LoginUser user) {
        Target t = load(ownerId);
        checkViewable(t, user);
        String blocked = editBlockReason(t, user);
        if (blocked != null) {
            throw new BusinessException(blocked);
        }
    }

    private static String label(InspectionResult r) {
        return r == null ? "없음" : r.getLabel();
    }

    private static int length(String s) {
        return s == null ? 0 : s.trim().length();
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
