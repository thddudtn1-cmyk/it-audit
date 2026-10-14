package com.itaudit.round;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.itaudit.auth.LoginUser;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;
import com.itaudit.improvement.ActionResult;
import com.itaudit.improvement.Improvement;
import com.itaudit.improvement.ImprovementKind;
import com.itaudit.improvement.ImprovementMapper;
import com.itaudit.improvement.ImprovementOrigin;

/**
 * 총괄표 검토·감사부서 제출 (F-05).
 * 회차 상태: 작성중(담당자 입력) → 검토중(제출 대상이 모두 제출됨) → 감사부서 제출(잠금) → 완료(F-06 적정성 점검 완료).
 * 작성중·검토중은 항목 제출 상태로 자동 결정한다. 반려가 생기면 다시 작성중이 된다.
 */
@Service
public class ReviewService {

    private final RoundMapper mapper;
    private final ActivityLogService activityLog;
    private final ImprovementMapper improvementMapper;

    public ReviewService(RoundMapper mapper, ActivityLogService activityLog, ImprovementMapper improvementMapper) {
        this.mapper = mapper;
        this.activityLog = activityLog;
        this.improvementMapper = improvementMapper;
    }

    /** 항목 제출·검토·배정 변경 뒤 호출: 작성중 ↔ 검토중 자동 전환 */
    @Transactional
    public void refreshStatus(Long roundId, LoginUser actor) {
        AuditRound round = mapper.findRound(roundId);
        if (round == null || !round.getStatus().isEditable()) {
            return;
        }
        RoundStatus next = round.isAllCollected() ? RoundStatus.REVIEW : RoundStatus.DRAFT;
        if (next != round.getStatus()) {
            mapper.updateRoundStatus(roundId, next, actor.getUserId(), LocalDateTime.now());
            activityLog.log(ActivityLogService.ROUND_STATUS, "ROUND", roundId,
                    round.getLabel() + " " + round.getStatus().getLabel() + " → " + next.getLabel());
        }
    }

    @Transactional
    public void confirm(Long roundItemId, LoginUser actor) {
        RoundItem item = editableItem(roundItemId);
        if (item.getSubmitStatus() != SubmitStatus.SUBMITTED) {
            throw new BusinessException("#" + item.getSeqNo() + " 제출된 항목만 확인할 수 있습니다.");
        }
        review(item, SubmitStatus.CONFIRMED, null, actor);
        activityLog.log(ActivityLogService.ROUND_ITEM_CONFIRM, "ROUND_ITEM", roundItemId, label(item) + " 확인");
        refreshStatus(item.getRoundId(), actor);
    }

    /** 제출된 항목을 한 번에 확인 */
    @Transactional
    public int confirmAll(Long roundId, LoginUser actor) {
        editableRound(roundId);
        int count = 0;
        for (RoundItem item : mapper.findItems(roundId)) {
            if (!item.isExcluded() && item.getSubmitStatus() == SubmitStatus.SUBMITTED) {
                review(item, SubmitStatus.CONFIRMED, null, actor);
                activityLog.log(ActivityLogService.ROUND_ITEM_CONFIRM, "ROUND_ITEM", item.getRoundItemId(),
                        label(item) + " 확인 (일괄)");
                count++;
            }
        }
        refreshStatus(roundId, actor);
        return count;
    }

    @Transactional
    public void cancelConfirm(Long roundItemId, LoginUser actor) {
        RoundItem item = editableItem(roundItemId);
        if (item.getSubmitStatus() != SubmitStatus.CONFIRMED) {
            throw new BusinessException("#" + item.getSeqNo() + " 확인된 항목이 아닙니다.");
        }
        review(item, SubmitStatus.SUBMITTED, null, actor);
        activityLog.log(ActivityLogService.ROUND_ITEM_CONFIRM_CANCEL, "ROUND_ITEM", roundItemId, label(item) + " 확인 취소");
        refreshStatus(item.getRoundId(), actor);
    }

    /** 반려: 담당자가 다시 입력·제출해야 한다. 사유 필수. */
    @Transactional
    public void reject(Long roundItemId, String reason, LoginUser actor) {
        RoundItem item = editableItem(roundItemId);
        if (item.getSubmitStatus() != SubmitStatus.SUBMITTED && item.getSubmitStatus() != SubmitStatus.CONFIRMED) {
            throw new BusinessException("#" + item.getSeqNo() + " 제출된 항목만 반려할 수 있습니다.");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("반려 사유를 입력하세요.");
        }
        if (reason.trim().length() > 1000) {
            throw new BusinessException("반려 사유는 1000자 이하여야 합니다.");
        }
        review(item, SubmitStatus.REJECTED, reason.trim(), actor);
        activityLog.log(ActivityLogService.ROUND_ITEM_REJECT, "ROUND_ITEM", roundItemId,
                label(item) + " 반려, 사유=" + reason.trim());
        refreshStatus(item.getRoundId(), actor);
    }

    /** 감사부서 제출: 이후 배정·점검 입력·증적 변경이 모두 잠긴다 */
    @Transactional
    public void submitToAuditDept(Long roundId, LoginUser actor) {
        refreshStatus(roundId, actor);
        AuditRound round = mapper.findRound(roundId);
        if (round == null) {
            throw new BusinessException("회차를 찾을 수 없습니다.");
        }
        if (!round.getStatus().isEditable()) {
            throw new BusinessException("이미 감사부서에 제출된 회차입니다.");
        }
        if (!round.isReadyToSubmit()) {
            throw new BusinessException(notReadyReason(round));
        }
        mapper.submitRound(roundId, actor.getUserId(), LocalDateTime.now());
        int registered = registerImprovements(round, actor);
        activityLog.log(ActivityLogService.ROUND_SUBMIT, "ROUND", roundId,
                round.getLabel() + " 감사부서 제출, 점검 대상 " + round.getTargetCount() + "건"
                        + ", 개선사항 관리대장 자동 등록 " + registered + "건");
    }

    /** 점검결과가 정상이 아닌 항목 = 개선사항 관리대장에 자동 등록될 항목 (제외·회신불필요 항목은 대상 아님) */
    public static boolean isImprovementTarget(RoundItem item) {
        return !item.isExcluded() && !item.isNoReply() && item.getInspectionResult() != null
                && item.getInspectionResult().requiresAction();
    }

    /**
     * 분기 점검을 감사부서에 제출하는 시점에 점검결과가 정상이 아닌 항목(개선필요·미흡·위험)을 개선사항 관리대장에 확정 등록한다.
     * 감사부서의 적정성 점검(검토·판정) 결과와 관계없이 제출 시점의 점검결과를 그대로 쓰며, 항목 1건당 개선사항 1건을 만든다.
     * 개선 방안·개선예정일은 담당자가 입력한 조치내용·개선 예정일을 기본값으로 쓴다.
     */
    private int registerImprovements(AuditRound round, LoginUser actor) {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (RoundItem item : mapper.findItems(round.getRoundId())) {
            if (!isImprovementTarget(item)
                    || !improvementMapper.findIdsByRoundItem(item.getRoundItemId(), ImprovementKind.IMPROVEMENT).isEmpty()) {
                continue;
            }
            Improvement m = new Improvement();
            m.setKind(ImprovementKind.IMPROVEMENT);
            m.setOrigin(ImprovementOrigin.ROUND_SUBMIT);
            m.setRoundId(round.getRoundId());
            m.setTeamCode(item.getTeamCode());
            m.setAssigneeId(item.getAssigneeId());
            m.setInspectionResult(item.getInspectionResult());
            m.setDetail(StringUtils.hasText(item.getInspectionDetail()) ? item.getInspectionDetail() : "(점검 내용 없음)");
            m.setImprovementPlan(item.getActionPlan());
            m.setPlannedDate(item.getPlannedDate());
            m.setActionResult(ActionResult.PLANNED);
            m.setCreatedBy(actor.getUserId());
            m.setCreatedAt(now);
            m.setUpdatedBy(actor.getUserId());
            m.setUpdatedAt(now);
            improvementMapper.insert(m);
            improvementMapper.insertItem(m.getImprovementId(), item.getRoundItemId());
            activityLog.log(ActivityLogService.IMPROVEMENT_CREATE, "IMPROVEMENT", m.getImprovementId(),
                    "분기 점검 제출로 자동 등록: " + round.getLabel() + " #" + item.getSeqNo() + ", 점검 결과="
                            + item.getInspectionResult().getLabel());
            count++;
        }
        return count;
    }

    /** 제출할 수 없는 이유 (화면 안내용) */
    public static String notReadyReason(AuditRound round) {
        int notSubmitted = round.getSubmitTargetCount() - round.getSubmittedCount();
        if (notSubmitted > 0) {
            return "아직 제출되지 않은 항목이 " + notSubmitted + "건 있습니다 (미제출·반려 포함).";
        }
        if (round.getAwaitingConfirmCount() > 0) {
            return "확인하지 않은 제출 항목이 " + round.getAwaitingConfirmCount() + "건 있습니다.";
        }
        if (round.getTargetCount() == 0) {
            return "점검 대상 항목이 없습니다.";
        }
        return "제출할 수 없는 상태입니다.";
    }

    private void review(RoundItem item, SubmitStatus status, String comment, LoginUser actor) {
        LocalDateTime now = LocalDateTime.now();
        item.setSubmitStatus(status);
        // 반려 사유는 다음 반려 전까지 남겨 두어 담당자가 볼 수 있게 한다 (확인 시에는 지움)
        item.setReviewComment(status == SubmitStatus.REJECTED ? comment
                : status == SubmitStatus.CONFIRMED ? null : item.getReviewComment());
        item.setReviewedBy(actor.getUserId());
        item.setReviewedAt(now);
        item.setUpdatedBy(actor.getUserId());
        item.setUpdatedAt(now);
        mapper.updateReview(item);
    }

    private RoundItem editableItem(Long roundItemId) {
        RoundItem item = mapper.findItem(roundItemId);
        if (item == null) {
            throw new BusinessException("점검항목을 찾을 수 없습니다.");
        }
        if (item.isExcluded()) {
            throw new BusinessException("#" + item.getSeqNo() + " 제외된 항목입니다.");
        }
        editableRound(item.getRoundId());
        return item;
    }

    private AuditRound editableRound(Long roundId) {
        AuditRound round = mapper.findRound(roundId);
        if (round == null) {
            throw new BusinessException("회차를 찾을 수 없습니다.");
        }
        if (!round.getStatus().isEditable()) {
            throw new BusinessException("감사부서에 제출된 회차는 검토 상태를 바꿀 수 없습니다.");
        }
        return round;
    }

    private static String label(RoundItem item) {
        return "#" + item.getSeqNo() + " " + item.getAuditItem();
    }
}
