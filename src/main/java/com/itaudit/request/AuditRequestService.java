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
 * 감사부서 적정성 점검 (F-06): 사전요청자료 요청 → 담당자 자료·비고 제출 → 미흡 확정·이상 없음·보완 요청 → 적정성 점검 완료.
 * [가정] 오픈 이슈 '감사부서 직접 로그인 여부'는 감사부서가 시스템에 직접 로그인해 요청·판정하는 쪽으로 구현했다.
 *        주관자·조회자는 열람만 한다.
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

    /** 사전요청자료를 다룰 수 있는 회차 = 감사부서에 제출된 회차 */
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

    public List<AuditRequest> findByRound(Long roundId) {
        return mapper.findByRound(roundId);
    }

    public List<AuditRequest> findMine(LoginUser user) {
        return mapper.findByAssignee(user.getUserId());
    }

    /** 요청 후보: 회차의 점검 대상 항목 (이미 요청한 항목 표시용 맵 포함) */
    public record Candidate(RoundItem item, AuditRequest existing) {
    }

    public List<Candidate> findCandidates(Long roundId) {
        Map<Long, AuditRequest> existing = mapper.findByRound(roundId).stream()
                .collect(Collectors.toMap(AuditRequest::getRoundItemId, Function.identity()));
        List<Candidate> result = new ArrayList<>();
        for (RoundItem item : roundMapper.findItems(roundId)) {
            if (!item.isExcluded()) {
                result.add(new Candidate(item, existing.get(item.getRoundItemId())));
            }
        }
        return result;
    }

    public AuditRequest getForView(Long requestId, LoginUser user) {
        AuditRequest r = load(requestId);
        checkViewable(r, user);
        return r;
    }

    // ---------- 요청 (감사부서) ----------

    @Transactional
    public int create(RequestCreateForm form, LoginUser actor) {
        AuditRound round = getRound(form.getRoundId());
        if (round.getStatus() != RoundStatus.SUBMITTED) {
            throw new BusinessException("적정성 점검이 끝난 회차에는 요청을 추가할 수 없습니다.");
        }
        Map<Long, RoundItem> items = roundMapper.findItems(round.getRoundId()).stream()
                .collect(Collectors.toMap(RoundItem::getRoundItemId, Function.identity()));
        List<RequestCreateForm.Row> selected = form.getRows().stream().filter(RequestCreateForm.Row::isSelected).toList();
        if (selected.isEmpty()) {
            throw new BusinessException("요청할 항목을 하나 이상 고르세요.");
        }
        List<String> errors = new ArrayList<>();
        for (RequestCreateForm.Row row : selected) {
            RoundItem item = items.get(row.getRoundItemId());
            if (item == null || item.isExcluded()) {
                throw new BusinessException("이 회차의 점검 대상 항목이 아닙니다.");
            }
            if (mapper.findByRoundItem(item.getRoundItemId()) != null) {
                errors.add("#" + item.getSeqNo() + " 이미 요청한 항목입니다.");
            } else if (!StringUtils.hasText(row.getRequestMaterial())) {
                errors.add("#" + item.getSeqNo() + " 요청자료를 입력하세요.");
            } else if (row.getRequestMaterial().trim().length() > 1000) {
                errors.add("#" + item.getSeqNo() + " 요청자료는 1000자 이하여야 합니다.");
            } else if (row.getSampleSize() != null && row.getSampleSize() < 0) {
                errors.add("#" + item.getSeqNo() + " 표본 수는 0 이상이어야 합니다.");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join(" / ", errors));
        }
        LocalDateTime now = LocalDateTime.now();
        for (RequestCreateForm.Row row : selected) {
            RoundItem item = items.get(row.getRoundItemId());
            AuditRequest r = new AuditRequest();
            r.setRoundItemId(item.getRoundItemId());
            r.setRequestMaterial(row.getRequestMaterial().trim());
            r.setSampleSize(row.getSampleSize());
            r.setStatus(RequestStatus.REQUESTED);
            r.setRequestedBy(actor.getUserId());
            r.setRequestedAt(now);
            r.setUpdatedBy(actor.getUserId());
            r.setUpdatedAt(now);
            mapper.insert(r);
            activityLog.log(ActivityLogService.REQUEST_CREATE, "AUDIT_REQUEST", r.getRequestId(),
                    round.getLabel() + " #" + item.getSeqNo() + " 요청: " + r.getRequestMaterial());
        }
        return selected.size();
    }

    // ---------- 자료 제출 (담당자) ----------

    @Transactional
    public void saveSubmission(Long requestId, String remark, List<MultipartFile> files, boolean submit, LoginUser actor) {
        AuditRequest r = load(requestId);
        checkViewable(r, actor);
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
            errors.add("제출할 자료 파일을 올리거나 비고를 입력하세요.");
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
                requestId, r.getRoundLabel() + " #" + r.getSeqNo() + (submit ? " 자료 제출" : " 임시저장")
                        + (newFiles.isEmpty() ? "" : ", 파일 " + newFiles.size() + "건"));
    }

    /** 담당자가 자료를 낼 수 없는 이유 (가능하면 null) */
    public String submitBlockReason(AuditRequest r, LoginUser user) {
        if (user.getRole() != Role.INSPECTOR || !Objects.equals(r.getAssigneeId(), user.getUserId())) {
            return "해당 항목의 점검 담당자만 자료를 제출할 수 있습니다.";
        }
        if (r.getRoundStatus() != RoundStatus.SUBMITTED) {
            return "적정성 점검이 끝난 회차입니다.";
        }
        if (r.getStatus() != RequestStatus.REQUESTED) {
            return "이미 제출한 요청입니다. 감사부서가 보완을 요청하면 다시 제출할 수 있습니다.";
        }
        return null;
    }

    // ---------- 판정 (감사부서) ----------

    @Transactional
    public void review(Long requestId, RequestReviewForm form, LoginUser actor) {
        AuditRequest r = load(requestId);
        String blocked = reviewBlockReason(r);
        if (blocked != null) {
            throw new BusinessException(blocked);
        }
        if (form.getDecision() == null) {
            throw new BusinessException("판정을 선택하세요.");
        }
        String comment = trimToNull(form.getDeptComment());
        String detail = trimToNull(form.getFindingDetail());
        if (comment != null && comment.length() > 1000) {
            throw new BusinessException("검토 의견은 1000자 이하여야 합니다.");
        }
        if (detail != null && detail.length() > 2000) {
            throw new BusinessException("미흡 내용은 2000자 이하여야 합니다.");
        }

        // 이미 개선사항에 연결된 미흡 확정은 판정을 바꿀 수 없다
        if (r.isFindingConfirmed() && form.getDecision() != RequestReviewForm.Decision.FINDING) {
            for (FindingLinkCheck check : findingLinkChecks.orderedStream().toList()) {
                String reason = check.blockReason(r.getRoundItemId());
                if (reason != null) {
                    throw new BusinessException(reason);
                }
            }
        }

        String before = r.getStatus().getLabel() + "/" + r.getFindingLabel()
                + (r.getConfirmedResult() == null ? "" : "(" + r.getConfirmedResult().getLabel() + ")");
        switch (form.getDecision()) {
            case FINDING -> {
                if (form.getConfirmedResult() == null || form.getConfirmedResult() == InspectionResult.NORMAL) {
                    throw new BusinessException("미흡 확정 시 확정 점검결과(개선필요·미흡·위험)를 고르세요.");
                }
                if (detail == null) {
                    throw new BusinessException("미흡 확정 시 미흡 내용을 입력하세요.");
                }
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
                if (comment == null) {
                    throw new BusinessException("보완 요청 시 요청 내용을 검토 의견에 입력하세요.");
                }
                r.setStatus(RequestStatus.REQUESTED);
                r.setFindingYn(null);
                r.setConfirmedResult(null);
                r.setFindingDetail(null);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        r.setDeptComment(comment);
        r.setReviewedBy(actor.getUserId());
        r.setReviewedAt(now);
        r.setUpdatedBy(actor.getUserId());
        r.setUpdatedAt(now);
        mapper.updateReview(r);
        String after = r.getStatus().getLabel() + "/" + r.getFindingLabel()
                + (r.getConfirmedResult() == null ? "" : "(" + r.getConfirmedResult().getLabel() + ")");
        activityLog.log(ActivityLogService.REQUEST_REVIEW, "AUDIT_REQUEST", requestId,
                r.getRoundLabel() + " #" + r.getSeqNo() + " 판정 " + before + " → " + after
                        + (comment == null ? "" : ", 의견=" + comment));
    }

    /** 감사부서가 판정할 수 없는 이유 (가능하면 null) */
    public static String reviewBlockReason(AuditRequest r) {
        if (r.getRoundStatus() != RoundStatus.SUBMITTED) {
            return "적정성 점검이 끝난 회차라 판정을 바꿀 수 없습니다.";
        }
        if (r.getStatus() == RequestStatus.REQUESTED) {
            return "담당자가 아직 자료를 제출하지 않았습니다.";
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
        List<AuditRequest> requests = mapper.findByRound(roundId);
        long open = requests.stream().filter(r -> r.getStatus() != RequestStatus.REVIEWED).count();
        if (open > 0) {
            throw new BusinessException("판정하지 않은 사전요청자료가 " + open + "건 있습니다.");
        }
        mapper.completeRound(roundId, actor.getUserId(), LocalDateTime.now());
        long findings = requests.stream().filter(AuditRequest::isFindingConfirmed).count();
        activityLog.log(ActivityLogService.ROUND_DONE, "ROUND", roundId,
                round.getLabel() + " 적정성 점검 완료, 요청 " + requests.size() + "건 중 미흡 확정 " + findings + "건");
    }

    // ---------- 권한 ----------

    private AuditRequest load(Long requestId) {
        AuditRequest r = mapper.findById(requestId);
        if (r == null) {
            throw new BusinessException("사전요청자료를 찾을 수 없습니다.");
        }
        return r;
    }

    /** 열람: 감사부서·주관자 전체, 점검 담당자는 본인 항목 */
    private static void checkViewable(AuditRequest r, LoginUser user) {
        boolean ok = switch (user.getRole()) {
            case AUDIT_DEPT, AUDIT_LEAD, VIEWER -> true;
            case INSPECTOR -> Objects.equals(r.getAssigneeId(), user.getUserId());
            case ADMIN -> false;
        };
        if (!ok) {
            throw new AccessDeniedException("사전요청자료 열람 권한 없음");
        }
    }

    @Override
    public EvidenceOwnerType ownerType() {
        return EvidenceOwnerType.AUDIT_REQUEST;
    }

    @Override
    public void checkView(Long ownerId, LoginUser user) {
        checkViewable(load(ownerId), user);
    }

    @Override
    public void checkModify(Long ownerId, LoginUser user) {
        AuditRequest r = load(ownerId);
        checkViewable(r, user);
        String blocked = submitBlockReason(r, user);
        if (blocked != null) {
            throw new BusinessException(blocked);
        }
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
