package com.itaudit.request;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
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
import com.itaudit.inspection.InspectionResult;
import com.itaudit.round.AuditRound;
import com.itaudit.round.RoundItem;
import com.itaudit.round.RoundMapper;
import com.itaudit.round.RoundStatus;

/**
 * 감사부서 적정성 점검 (F-06).
 *
 * 흐름: 주관자가 분기 점검을 감사부서에 제출 → 감사부서가 제출된 분기 점검 내역(점검결과·내용·증적)을 항목별로 검토 →
 *       적정 / 미흡 확정 / 보완자료 요청 → (보완자료 요청 시) 담당자가 보완자료·비고 제출 → 감사부서 재검토 →
 *       모든 항목 검토 후 적정성 점검 완료.
 *
 * 적정성 점검 대상 = 회차의 점검 대상 항목 중 회신불필요가 아닌 항목 (점검 내역이 있는 항목).
 * audit_request 행은 감사부서가 항목에 처음 손댈 때(판정 또는 보완 요청) 만들어진다. 행이 없으면 '미검토'다.
 * [가정] 오픈 이슈 '감사부서 직접 로그인 여부'는 감사부서가 시스템에 직접 로그인해 검토하는 쪽으로 구현했다.
 */
@Service
public class AuditRequestService implements EvidenceAccess {

    private final AuditRequestMapper mapper;
    private final RoundMapper roundMapper;
    private final EvidenceService evidenceService;
    private final ActivityLogService activityLog;
    private final ObjectProvider<FindingLinkCheck> findingLinkChecks;

    /** 미흡 확정을 되돌릴 수 없는 경우(개선사항에 연결됨 등)를 알려 주는 확인 (F-07 이 구현) */
    public interface FindingLinkCheck {
        /** 되돌릴 수 없으면 이유, 괜찮으면 null */
        String blockReason(Long roundItemId);
    }

    /** 적정성 점검 대상 항목 1건: 분기 점검 내역(item) + 감사부서 검토 행(review, 없으면 null) */
    public record Target(RoundItem item, AuditRequest review) {
        public ReviewState state() {
            return ReviewState.of(review);
        }
    }

    /** 상세 화면 묶음 */
    public record TargetView(RoundItem item, AuditRound round, AuditRequest review) {
        public ReviewState state() {
            return ReviewState.of(review);
        }
    }

    public AuditRequestService(AuditRequestMapper mapper, RoundMapper roundMapper,
                               @Lazy EvidenceService evidenceService, ActivityLogService activityLog,
                               ObjectProvider<FindingLinkCheck> findingLinkChecks) {
        this.mapper = mapper;
        this.roundMapper = roundMapper;
        this.evidenceService = evidenceService;
        this.activityLog = activityLog;
        this.findingLinkChecks = findingLinkChecks;
    }

    // ---------- 조회 ----------

    /** 적정성 점검을 할 수 있는 회차 = 감사부서에 제출된 회차 */
    public List<AuditRound> findRounds() {
        return roundMapper.findRounds(List.of(RoundStatus.SUBMITTED, RoundStatus.DONE));
    }

    public AuditRound getRound(Long roundId) {
        AuditRound round = roundMapper.findRound(roundId);
        if (round == null || !round.getStatus().isVisibleToAuditDept()) {
            throw new BusinessException("감사부서에 제출된 회차가 아닙니다.");
        }
        return round;
    }

    /** 이 회차의 적정성 점검 대상 항목과 검토 상태 (순번 순) */
    public List<Target> findTargets(Long roundId) {
        Map<Long, AuditRequest> reviews = mapper.findByRound(roundId).stream()
                .collect(Collectors.toMap(AuditRequest::getRoundItemId, Function.identity()));
        List<Target> result = new ArrayList<>();
        for (RoundItem item : roundMapper.findItems(roundId)) {
            if (isTarget(item)) {
                result.add(new Target(item, reviews.get(item.getRoundItemId())));
            }
        }
        return result;
    }

    private static boolean isTarget(RoundItem item) {
        return !item.isExcluded() && !item.isNoReply();
    }

    public List<AuditRequest> findByRound(Long roundId) {
        return mapper.findByRound(roundId);
    }

    /** 담당자에게 온 보완자료 요청 */
    public List<AuditRequest> findMine(LoginUser user) {
        return mapper.findByAssignee(user.getUserId());
    }

    public AuditRequest getForView(Long requestId, LoginUser user) {
        AuditRequest r = load(requestId);
        checkViewable(r.getRoundId(), r.getAssigneeId(), r.getRoundStatus(), user);
        return r;
    }

    public TargetView getTarget(Long roundItemId, LoginUser user) {
        RoundItem item = roundMapper.findItem(roundItemId);
        AuditRound round = item == null ? null : roundMapper.findRound(item.getRoundId());
        if (item == null || round == null) {
            throw new BusinessException("점검항목을 찾을 수 없습니다.");
        }
        checkViewable(round.getRoundId(), item.getAssigneeId(), round.getStatus(), user);
        if (!isTarget(item)) {
            throw new BusinessException("적정성 점검 대상이 아닌 항목입니다 (제외 또는 회신불필요).");
        }
        return new TargetView(item, round, mapper.findByRoundItem(roundItemId));
    }

    // ---------- 보완자료 제출 (담당자) ----------

    @Transactional
    public void saveSubmission(Long requestId, String remark, List<MultipartFile> files, boolean submit, LoginUser actor) {
        AuditRequest r = getForView(requestId, actor);
        String blocked = submitBlockReason(r, actor);
        if (blocked != null) {
            throw new BusinessException(blocked);
        }
        List<MultipartFile> newFiles = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        List<String> errors = new ArrayList<>();
        String trimmed = StringUtils.hasText(remark) ? remark.trim() : null;
        if (trimmed != null && trimmed.length() > 1000) {
            errors.add("비고는 1000자 이하여야 합니다.");
        }
        for (MultipartFile f : newFiles) {
            try {
                evidenceService.validate(f);
            } catch (BusinessException e) {
                errors.add(e.getMessage());
            }
        }
        if (submit && trimmed == null && r.getEvidenceCount() + newFiles.size() == 0) {
            errors.add("제출할 보완자료 파일을 올리거나 비고를 입력하세요.");
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join(" / ", errors));
        }

        LocalDateTime now = LocalDateTime.now();
        r.setRemark(trimmed);
        if (submit) {
            r.setStatus(RequestStatus.SUBMITTED);
            r.setSubmittedBy(actor.getUserId());
            r.setSubmittedAt(now);
        }
        r.setUpdatedBy(actor.getUserId());
        r.setUpdatedAt(now);
        mapper.updateSubmission(r);
        for (MultipartFile f : newFiles) {
            evidenceService.store(EvidenceOwnerType.AUDIT_REQUEST, requestId, f, actor);
        }
        activityLog.log(submit ? ActivityLogService.REQUEST_SUBMIT : ActivityLogService.REQUEST_SAVE, "AUDIT_REQUEST",
                requestId, r.getRoundLabel() + " #" + r.getSeqNo() + (submit ? " 보완자료 제출" : " 보완자료 임시저장")
                        + (newFiles.isEmpty() ? "" : ", 파일 " + newFiles.size() + "건"));
    }

    /** 담당자가 보완자료를 낼 수 없는 이유 (가능하면 null) */
    public String submitBlockReason(AuditRequest r, LoginUser user) {
        if (user.getRole() != Role.INSPECTOR || !Objects.equals(r.getAssigneeId(), user.getUserId())) {
            return "해당 항목의 점검 담당자만 보완자료를 제출할 수 있습니다.";
        }
        if (r.getRoundStatus() != RoundStatus.SUBMITTED) {
            return "적정성 점검이 끝난 회차입니다.";
        }
        if (r.getStatus() != RequestStatus.REQUESTED) {
            return "보완자료를 이미 제출했습니다. 감사부서가 다시 보완을 요청하면 제출할 수 있습니다.";
        }
        return null;
    }

    // ---------- 검토·판정 (감사부서) ----------

    /**
     * 항목 검토. 감사부서가 항목에 처음 손대면 검토 행을 만든다.
     * 보완 요청은 요청자료를 담당자에게 보내고 제출을 기다린다. 적정·미흡 확정은 바로 판정을 끝낸다.
     */
    @Transactional
    public void review(Long roundItemId, RequestReviewForm form, LoginUser actor) {
        TargetView t = getTarget(roundItemId, actor);
        String blocked = reviewBlockReason(t.round(), t.review());
        if (blocked != null) {
            throw new BusinessException(blocked);
        }
        if (form.getDecision() == null) {
            throw new BusinessException("판정을 선택하세요.");
        }
        String comment = trimToNull(form.getDeptComment());
        String detail = trimToNull(form.getFindingDetail());
        String material = trimToNull(form.getRequestMaterial());
        if (comment != null && comment.length() > 1000) {
            throw new BusinessException("검토 의견은 1000자 이하여야 합니다.");
        }
        if (detail != null && detail.length() > 2000) {
            throw new BusinessException("미흡 내용은 2000자 이하여야 합니다.");
        }
        if (material != null && material.length() > 1000) {
            throw new BusinessException("보완 요청 자료는 1000자 이하여야 합니다.");
        }
        // 값 검증은 저장 전에 모두 끝낸다 (실패하면 행도 만들지 않는다)
        switch (form.getDecision()) {
            case FINDING -> {
                if (form.getConfirmedResult() == null || form.getConfirmedResult() == InspectionResult.NORMAL) {
                    throw new BusinessException("미흡 확정 시 확정 점검결과(개선필요·미흡·위험)를 고르세요.");
                }
                if (detail == null) {
                    throw new BusinessException("미흡 확정 시 미흡 내용을 입력하세요.");
                }
            }
            case SUPPLEMENT -> {
                if (material == null) {
                    throw new BusinessException("보완 요청 시 요청할 자료를 입력하세요.");
                }
            }
            default -> { }
        }

        AuditRequest r = t.review();
        // 이미 개선사항에 연결된 미흡 확정은 판정을 바꿀 수 없다
        if (r != null && r.isFindingConfirmed() && form.getDecision() != RequestReviewForm.Decision.FINDING) {
            for (FindingLinkCheck check : findingLinkChecks.orderedStream().toList()) {
                String reason = check.blockReason(roundItemId);
                if (reason != null) {
                    throw new BusinessException(reason);
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        String where = t.round().getLabel() + " #" + t.item().getSeqNo();
        String before = ReviewState.of(r).getLabel()
                + (r != null && r.getConfirmedResult() != null ? "(" + r.getConfirmedResult().getLabel() + ")" : "");
        if (r == null) {
            AuditRequest created = new AuditRequest();
            created.setRoundItemId(roundItemId);
            created.setStatus(RequestStatus.REVIEWED);
            created.setRequestedBy(actor.getUserId());
            created.setRequestedAt(now);
            created.setUpdatedBy(actor.getUserId());
            created.setUpdatedAt(now);
            mapper.insert(created);
            r = mapper.findById(created.getRequestId());
        }

        switch (form.getDecision()) {
            case FINDING -> {
                r.setStatus(RequestStatus.REVIEWED);
                r.setFindingYn("Y");
                r.setConfirmedResult(form.getConfirmedResult());
                r.setFindingDetail(detail);
            }
            case NO_FINDING -> {
                r.setStatus(RequestStatus.REVIEWED);
                r.setFindingYn("N");
                r.setConfirmedResult(null);
                r.setFindingDetail(null);
            }
            case SUPPLEMENT -> {
                r.setStatus(RequestStatus.REQUESTED);
                r.setRequestMaterial(material);
                r.setFindingYn(null);
                r.setConfirmedResult(null);
                r.setFindingDetail(null);
            }
        }
        r.setDeptComment(comment);
        r.setReviewedBy(actor.getUserId());
        r.setReviewedAt(now);
        r.setUpdatedBy(actor.getUserId());
        r.setUpdatedAt(now);
        mapper.updateReview(r);

        String after = ReviewState.of(r).getLabel()
                + (r.getConfirmedResult() == null ? "" : "(" + r.getConfirmedResult().getLabel() + ")");
        if (form.getDecision() == RequestReviewForm.Decision.SUPPLEMENT) {
            activityLog.log(ActivityLogService.REQUEST_CREATE, "AUDIT_REQUEST", r.getRequestId(),
                    where + " 보완자료 요청: " + material + (comment == null ? "" : ", 의견=" + comment));
        } else {
            activityLog.log(ActivityLogService.REQUEST_REVIEW, "AUDIT_REQUEST", r.getRequestId(),
                    where + " 판정 " + before + " → " + after + (comment == null ? "" : ", 의견=" + comment));
        }
    }

    /** 감사부서가 판정할 수 없는 이유 (가능하면 null) */
    public static String reviewBlockReason(AuditRound round, AuditRequest review) {
        if (round.getStatus() != RoundStatus.SUBMITTED) {
            return "적정성 점검이 끝난 회차라 판정을 바꿀 수 없습니다.";
        }
        if (review != null && review.getStatus() == RequestStatus.REQUESTED) {
            return "담당자가 보완자료를 제출하기를 기다리는 중입니다.";
        }
        return null;
    }

    // ---------- 적정성 점검 완료 (감사부서) ----------

    @Transactional
    public void completeRound(Long roundId, LoginUser actor) {
        AuditRound round = getRound(roundId);
        if (round.getStatus() != RoundStatus.SUBMITTED) {
            throw new BusinessException("이미 적정성 점검이 끝난 회차입니다.");
        }
        List<Target> targets = findTargets(roundId);
        long open = targets.stream().filter(t -> !t.state().isDone()).count();
        if (open > 0) {
            throw new BusinessException("검토하지 않은 항목이 " + open + "건 있습니다 (미검토·보완 요청 중 포함).");
        }
        mapper.completeRound(roundId, actor.getUserId(), LocalDateTime.now());
        long findings = targets.stream().filter(t -> t.state() == ReviewState.FINDING).count();
        activityLog.log(ActivityLogService.ROUND_DONE, "ROUND", roundId,
                round.getLabel() + " 적정성 점검 완료, 점검 항목 " + targets.size() + "건 중 미흡 확정 " + findings + "건");
    }

    // ---------- 권한 ----------

    private AuditRequest load(Long requestId) {
        AuditRequest r = mapper.findById(requestId);
        if (r == null) {
            throw new BusinessException("적정성 점검 기록을 찾을 수 없습니다.");
        }
        return r;
    }

    /**
     * 열람: 감사부서·주관자는 감사부서에 제출된 회차, 점검 담당자는 본인 항목.
     * 조회자·관리자는 적정성 점검을 보지 않는다.
     */
    private static void checkViewable(Long roundId, Long assigneeId, RoundStatus roundStatus, LoginUser user) {
        boolean ok = switch (user.getRole()) {
            case AUDIT_DEPT, AUDIT_LEAD -> roundStatus != null && roundStatus.isVisibleToAuditDept();
            case INSPECTOR -> Objects.equals(assigneeId, user.getUserId());
            case VIEWER, ADMIN -> false;
        };
        if (!ok) {
            throw new AccessDeniedException("적정성 점검 열람 권한 없음");
        }
    }

    @Override
    public EvidenceOwnerType ownerType() {
        return EvidenceOwnerType.AUDIT_REQUEST;
    }

    @Override
    public void checkView(Long ownerId, LoginUser user) {
        getForView(ownerId, user);
    }

    @Override
    public void checkModify(Long ownerId, LoginUser user) {
        AuditRequest r = getForView(ownerId, user);
        String blocked = submitBlockReason(r, user);
        if (blocked != null) {
            throw new BusinessException(blocked);
        }
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
