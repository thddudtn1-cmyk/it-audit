package com.itaudit.improvement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.itaudit.admin.Team;
import com.itaudit.admin.TeamMapper;
import com.itaudit.auth.AppUser;
import com.itaudit.auth.AppUserMapper;
import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;
import com.itaudit.evidence.EvidenceAccess;
import com.itaudit.evidence.EvidenceOwnerType;
import com.itaudit.evidence.EvidenceService;
import com.itaudit.inspection.InspectionResult;
import com.itaudit.request.AuditRequest;
import com.itaudit.request.AuditRequestMapper;
import com.itaudit.request.AuditRequestService;
import com.itaudit.round.AuditRound;
import com.itaudit.round.RoundItem;
import com.itaudit.round.RoundMapper;
import com.itaudit.round.RoundStatus;

/**
 * 개선사항·권고사항 관리 (F-07).
 * 개선사항은 주관자가 감사부서 '미흡 확정' 건에서 만들고, 권고사항은 감사부서·주관자가 회차 항목을 골라 만든다.
 * 담당자는 조치 정보(개선 방안·개선예정일·조치 결과·조치 현황·증빙자료)를 갱신한다.
 * 조치 결과가 '완료'가 되면 주관자가 검토해 최종확정(= 종결)하거나 이전 단계(진행중·예정)로 되돌린다.
 * 검토를 기다리는 동안 담당자는 수정할 수 없고, 되돌려지면 사유를 보며 다시 수정한다.
 * 최종확정하지 않은 건은 분기가 바뀌어도 대장에 남아 다음 분기로 이어진다.
 * [가정] 오픈 이슈 '개선필요 등급도 관리대장에 올릴지'는 감사부서가 개선필요로 미흡 확정한 건이면 올릴 수 있게 했다
 *        (등급으로 자동 제외하지 않고, 확정 여부로만 판단).
 */
@Service
public class ImprovementService implements EvidenceAccess, AuditRequestService.FindingLinkCheck {

    private final ImprovementMapper mapper;
    private final AuditRequestMapper requestMapper;
    private final RoundMapper roundMapper;
    private final TeamMapper teamMapper;
    private final AppUserMapper userMapper;
    private final EvidenceService evidenceService;
    private final ActivityLogService activityLog;

    public ImprovementService(ImprovementMapper mapper, AuditRequestMapper requestMapper, RoundMapper roundMapper,
                              TeamMapper teamMapper, AppUserMapper userMapper, @Lazy EvidenceService evidenceService,
                              ActivityLogService activityLog) {
        this.mapper = mapper;
        this.requestMapper = requestMapper;
        this.roundMapper = roundMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.evidenceService = evidenceService;
        this.activityLog = activityLog;
    }

    // ---------- 조회 ----------

    public List<Improvement> findList(ImprovementFilter filter, LoginUser user) {
        if (user.getRole() == Role.INSPECTOR) {
            filter.setAssigneeId(user.getUserId());
        }
        return withItems(mapper.findByFilter(filter, LocalDate.now()));
    }

    /** 담당자 본인의 최종확정 전 건 (내 할 일, 개선예정일 순) */
    public List<Improvement> findMyOpen(LoginUser user) {
        List<Improvement> result = new ArrayList<>();
        for (ImprovementKind kind : ImprovementKind.values()) {
            ImprovementFilter f = new ImprovementFilter();
            f.setKind(kind);
            f.setAssigneeId(user.getUserId());
            result.addAll(mapper.findByFilter(f, LocalDate.now()));
        }
        result.sort(Comparator.comparing(Improvement::getPlannedDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return withItems(result);
    }

    /** 같은 항목(순번)의 개선사항·권고사항 이력 */
    public List<Improvement> findHistory(int seqNo) {
        return mapper.findBySeqNo(seqNo);
    }

    /** 이 사용자가 상세 화면을 열 수 있는지 (이력 목록의 링크 표시용) */
    public boolean canView(Improvement m, LoginUser user) {
        return switch (user.getRole()) {
            case AUDIT_LEAD, AUDIT_DEPT, VIEWER -> true;
            case INSPECTOR -> Objects.equals(m.getAssigneeId(), user.getUserId());
            case ADMIN -> false;
        };
    }

    public Improvement get(Long id, LoginUser user) {
        Improvement m = load(id);
        checkViewable(m, user);
        return m;
    }

    private List<Improvement> withItems(List<Improvement> list) {
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, List<LinkedItem>> items = mapper.findLinkedItems(list.stream().map(Improvement::getImprovementId).toList())
                .stream().collect(Collectors.groupingBy(LinkedItem::getImprovementId));
        list.forEach(m -> m.setItems(items.getOrDefault(m.getImprovementId(), List.of())));
        return list;
    }

    public List<AuditRound> findRoundsForRecommendation() {
        return roundMapper.findRounds(List.of(RoundStatus.SUBMITTED, RoundStatus.DONE));
    }

    public List<RoundItem> findRoundItems(Long roundId) {
        return roundMapper.findItems(roundId).stream().filter(i -> !i.isExcluded()).toList();
    }

    public List<Team> findActiveTeams() {
        return teamMapper.findActive();
    }

    public List<AppUser> findInspectors() {
        return userMapper.findActiveByRole(Role.INSPECTOR);
    }

    // ---------- 개선사항 등록 (주관자) ----------

    /** 아직 개선사항에 연결되지 않은 미흡 확정 건 */
    public List<AuditRequest> findOpenFindings() {
        return requestMapper.findConfirmedFindings(null).stream()
                .filter(r -> mapper.findIdsByRoundItem(r.getRoundItemId(), ImprovementKind.IMPROVEMENT).isEmpty())
                .toList();
    }

    /** 선택한 미흡 확정 건으로 등록 화면 기본값을 만든다 */
    public ImprovementForm prepareFromFindings(List<Long> roundItemIds) {
        List<AuditRequest> findings = loadFindings(roundItemIds);
        ImprovementForm f = new ImprovementForm();
        f.setKind(ImprovementKind.IMPROVEMENT);
        f.setRoundId(findings.get(0).getRoundId());
        f.setRoundItemIds(findings.stream().map(AuditRequest::getRoundItemId).toList());
        // 가장 심각한 확정 결과
        f.setInspectionResult(findings.stream().map(AuditRequest::getConfirmedResult)
                .max(Comparator.naturalOrder()).orElse(InspectionResult.INSUFFICIENT));
        f.setDetail(joinDistinct(findings.stream().map(AuditRequest::getFindingDetail).toList()));
        // 개선 방안 기본값: ④ 비고(조치계획) → 없으면 ③ 조치내용 (PRD 3장·항목 정의서)
        f.setImprovementPlan(joinDistinct(findings.stream()
                .map(r -> StringUtils.hasText(r.getRemark()) ? r.getRemark() : r.getActionPlan()).toList()));
        f.setPlannedDate(findings.stream().map(AuditRequest::getPlannedDate).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null));
        AuditRequest first = findings.get(0);
        f.setTeamCode(first.getTeamCode());
        f.setAssigneeId(first.getAssigneeId());
        f.setActionResult(ActionResult.PLANNED);
        return f;
    }

    public List<AuditRequest> loadFindings(List<Long> roundItemIds) {
        if (roundItemIds == null || roundItemIds.isEmpty()) {
            throw new BusinessException("개선사항으로 등록할 미흡 확정 건을 고르세요.");
        }
        List<AuditRequest> findings = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(roundItemIds)) {
            AuditRequest r = requestMapper.findByRoundItem(id);
            if (r == null || !r.isFindingConfirmed()) {
                throw new BusinessException("감사부서가 미흡으로 확정한 항목만 개선사항으로 등록할 수 있습니다.");
            }
            if (!mapper.findIdsByRoundItem(id, ImprovementKind.IMPROVEMENT).isEmpty()) {
                throw new BusinessException("#" + r.getSeqNo() + " 은(는) 이미 개선사항에 등록되었습니다.");
            }
            findings.add(r);
        }
        Set<Long> rounds = findings.stream().map(AuditRequest::getRoundId).collect(Collectors.toSet());
        if (rounds.size() > 1) {
            throw new BusinessException("같은 회차의 미흡 확정 건만 하나로 묶을 수 있습니다.");
        }
        return findings;
    }

    @Transactional
    public Long createImprovement(ImprovementForm form, LoginUser actor) {
        List<AuditRequest> findings = loadFindings(form.getRoundItemIds());
        form.setKind(ImprovementKind.IMPROVEMENT);
        form.setRoundId(findings.get(0).getRoundId());
        if (form.getInspectionResult() == null || form.getInspectionResult() == InspectionResult.NORMAL) {
            throw new BusinessException("점검 결과(개선필요·미흡·위험)를 고르세요.");
        }
        return insert(form, findings.stream().map(AuditRequest::getRoundItemId).toList(), actor,
                findings.stream().map(r -> "#" + r.getSeqNo()).collect(Collectors.joining(" ")));
    }

    // ---------- 권고사항 등록 (감사부서·주관자) ----------

    @Transactional
    public Long createRecommendation(ImprovementForm form, LoginUser actor) {
        AuditRound round = form.getRoundId() == null ? null : roundMapper.findRound(form.getRoundId());
        if (round == null || !round.getStatus().isVisibleToAuditDept()) {
            throw new BusinessException("감사부서에 제출된 회차를 고르세요.");
        }
        if (form.getRoundItemIds() == null || form.getRoundItemIds().isEmpty()) {
            throw new BusinessException("권고 대상 항목을 하나 이상 고르세요.");
        }
        List<String> labels = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(form.getRoundItemIds())) {
            RoundItem item = roundMapper.findItem(id);
            if (item == null || !item.getRoundId().equals(round.getRoundId()) || item.isExcluded()) {
                throw new BusinessException("이 회차의 점검 대상 항목이 아닙니다.");
            }
            labels.add("#" + item.getSeqNo());
        }
        form.setKind(ImprovementKind.RECOMMENDATION);
        form.setInspectionResult(null);
        return insert(form, new ArrayList<>(new LinkedHashSet<>(form.getRoundItemIds())), actor, String.join(" ", labels));
    }

    private Long insert(ImprovementForm form, List<Long> roundItemIds, LoginUser actor, String itemLabels) {
        String detail = required(form.getDetail(), form.getKind() == ImprovementKind.IMPROVEMENT ? "점검 내용 상세" : "상세 내용", 4000);
        AppUser assignee = checkAssignee(form);
        LocalDateTime now = LocalDateTime.now();
        Improvement m = new Improvement();
        m.setKind(form.getKind());
        m.setOrigin(form.getKind() == ImprovementKind.RECOMMENDATION ? ImprovementOrigin.RECOMMENDATION
                : ImprovementOrigin.AUDIT_FINDING);
        m.setRoundId(form.getRoundId());
        m.setTeamCode(teamOf(form, assignee));
        m.setAssigneeId(assignee == null ? null : assignee.getUserId());
        m.setInspectionResult(form.getInspectionResult());
        m.setDetail(detail);
        m.setImprovementPlan(optional(form.getImprovementPlan(), "개선 방안", 2000));
        m.setPlannedDate(form.getPlannedDate());
        m.setActionResult(ActionResult.PLANNED);
        m.setCreatedBy(actor.getUserId());
        m.setCreatedAt(now);
        m.setUpdatedBy(actor.getUserId());
        m.setUpdatedAt(now);
        mapper.insert(m);
        for (Long id : roundItemIds) {
            mapper.insertItem(m.getImprovementId(), id);
        }
        activityLog.log(ActivityLogService.IMPROVEMENT_CREATE, "IMPROVEMENT", m.getImprovementId(),
                m.getKind().getLabel() + " 등록, 항목 " + itemLabels
                        + (m.getInspectionResult() == null ? "" : ", 점검 결과=" + m.getInspectionResult().getLabel()));
        return m.getImprovementId();
    }

    // ---------- 수정 ----------

    /** 화면에서 어떤 칸을 고칠 수 있는지 */
    public record Permissions(boolean manage, boolean action, boolean close) {
        public boolean any() {
            return manage || action;
        }
    }

    /**
     * 주관자: 전체 수정·종결 / 감사부서: 권고사항의 관리 정보 / 담당자(본인 건): 조치 정보.
     * 관리 정보 = 담당부서/담당자·점검 결과·내용, 조치 정보 = 개선 방안·개선예정일·조치 결과·조치 현황·증빙자료.
     * 최종확정된 건은 아무도 고칠 수 없다 (최종확정 취소 후 수정).
     * 조치 결과가 '완료'로 올라와 주관자 검토를 기다리는 동안은 담당자도 고칠 수 없다 (주관자가 되돌리면 다시 수정).
     */
    public Permissions permissions(Improvement m, LoginUser user) {
        if (m.isClosed()) {
            return new Permissions(false, false, user.getRole() == Role.AUDIT_LEAD);
        }
        return switch (user.getRole()) {
            case AUDIT_LEAD -> new Permissions(true, true, true);
            case AUDIT_DEPT -> new Permissions(m.getKind() == ImprovementKind.RECOMMENDATION, false, false);
            case INSPECTOR -> new Permissions(false,
                    Objects.equals(m.getAssigneeId(), user.getUserId()) && !m.isReviewPending(), false);
            default -> new Permissions(false, false, false);
        };
    }

    @Transactional
    public void update(Long id, ImprovementForm form, List<MultipartFile> files, LoginUser actor) {
        Improvement m = get(id, actor);
        Permissions p = permissions(m, actor);
        if (!p.any()) {
            throw new BusinessException(m.isClosed() ? "최종확정된 건은 수정할 수 없습니다."
                    : m.isReviewPending() && actor.getRole() == Role.INSPECTOR
                    ? "완료로 보고되어 주관자가 검토 중입니다. 주관자가 이전 단계로 되돌리면 다시 수정할 수 있습니다."
                    : "수정 권한이 없습니다.");
        }
        List<MultipartFile> newFiles = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (!newFiles.isEmpty() && !p.action()) {
            throw new BusinessException("증빙자료는 담당자나 주관자만 올릴 수 있습니다.");
        }
        newFiles.forEach(evidenceService::validate);

        String before = describe(m);
        if (p.manage()) {
            AppUser assignee = checkAssignee(form);
            m.setTeamCode(teamOf(form, assignee));
            m.setAssigneeId(assignee == null ? null : assignee.getUserId());
            // 분기 점검 제출 시 자동 등록된 건의 점검 결과·점검 내용은 제출 시점 값으로 확정되어 고치지 않는다
            if (!m.isAutoRegistered()) {
                m.setDetail(required(form.getDetail(), m.getKind() == ImprovementKind.IMPROVEMENT ? "점검 내용 상세" : "상세 내용", 4000));
            }
            if (m.getKind() == ImprovementKind.IMPROVEMENT && !m.isAutoRegistered()) {
                if (form.getInspectionResult() == null || form.getInspectionResult() == InspectionResult.NORMAL) {
                    throw new BusinessException("점검 결과(개선필요·미흡·위험)를 고르세요.");
                }
                m.setInspectionResult(form.getInspectionResult());
            }
        }
        if (p.action()) {
            m.setImprovementPlan(optional(form.getImprovementPlan(), "개선 방안", 2000));
            m.setPlannedDate(form.getPlannedDate());
            if (form.getActionResult() == null) {
                throw new BusinessException("조치 결과를 고르세요.");
            }
            ActionResult previous = m.getActionResult();
            m.setActionResult(form.getActionResult());
            m.setActionStatus(optional(form.getActionStatus(), "조치 현황", 2000));
            if (m.getActionResult() == ActionResult.DONE && previous != ActionResult.DONE) {
                // 다시 완료로 올리면 이전 반려 사유는 지운다 (반려 이력은 감사 로그에 남아 있다)
                m.setReturnComment(null);
                m.setReturnedBy(null);
                m.setReturnedAt(null);
            }
            if (m.getActionResult() == ActionResult.DONE && !StringUtils.hasText(m.getActionStatus())) {
                throw new BusinessException("조치 결과를 '완료'로 하려면 조치 현황을 입력하세요.");
            }
        }
        m.setUpdatedBy(actor.getUserId());
        m.setUpdatedAt(LocalDateTime.now());
        mapper.update(m);
        for (MultipartFile f : newFiles) {
            evidenceService.store(EvidenceOwnerType.IMPROVEMENT, id, f, actor);
        }
        String after = describe(m);
        if (!before.equals(after)) {
            activityLog.log(ActivityLogService.IMPROVEMENT_UPDATE, "IMPROVEMENT", id, before + " → " + after);
        }
    }

    // ---------- 완료 검토: 최종확정 또는 이전 단계로 되돌림 (주관자) ----------

    @Transactional
    public void close(Long id, String comment, LoginUser actor) {
        Improvement m = get(id, actor);
        if (m.isClosed()) {
            throw new BusinessException("이미 최종확정된 건입니다.");
        }
        if (m.getActionResult() != ActionResult.DONE) {
            throw new BusinessException("조치 결과가 '완료'인 건만 최종확정할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.updateClosed(id, "Y", actor.getUserId(), now, optional(comment, "최종확정 의견", 1000), now);
        activityLog.log(ActivityLogService.IMPROVEMENT_CLOSE, "IMPROVEMENT", id,
                m.getKind().getLabel() + " 최종 확정" + (StringUtils.hasText(comment) ? ", 의견=" + comment.trim() : ""));
    }

    /** 완료 검토에서 고를 수 있는 처리 상태 */
    public enum ReviewOutcome {
        /** 최종확정 (종결) */
        CLOSED,
        /** 진행중으로 되돌림 */
        IN_PROGRESS,
        /** 예정으로 되돌림 */
        PLANNED
    }

    /**
     * 완료 검토 처리: 선택한 상태로 변경한다. 최종확정이면 코멘트는 선택(최종확정 의견),
     * 이전 단계로 되돌리면 코멘트가 필수(반려 사유)이며 담당자에게 보인다.
     */
    @Transactional
    public ReviewOutcome review(Long id, ReviewOutcome outcome, String comment, LoginUser actor) {
        if (outcome == null) {
            throw new BusinessException("변경할 상태를 선택하세요.");
        }
        if (outcome == ReviewOutcome.CLOSED) {
            close(id, comment, actor);
        } else {
            sendBack(id, ActionResult.valueOf(outcome.name()), comment, actor);
        }
        return outcome;
    }

    /**
     * 완료 반려: 조치 결과를 이전 단계(진행중·예정)로 되돌린다. 사유는 필수이며 담당자에게 보인다.
     * 담당자는 되돌려진 뒤 다시 수정해 완료로 올릴 수 있다.
     */
    @Transactional
    public void sendBack(Long id, ActionResult target, String reason, LoginUser actor) {
        Improvement m = get(id, actor);
        if (m.isClosed()) {
            throw new BusinessException("이미 최종확정된 건입니다. 최종확정을 취소한 뒤 검토하세요.");
        }
        if (m.getActionResult() != ActionResult.DONE) {
            throw new BusinessException("조치 결과가 '완료'인 건만 검토할 수 있습니다.");
        }
        if (target == null || target == ActionResult.DONE) {
            throw new BusinessException("되돌릴 이전 단계(진행중·예정)를 고르세요.");
        }
        String r = required(reason, "반려 사유", 1000);
        LocalDateTime now = LocalDateTime.now();
        m.setActionResult(target);
        m.setReturnComment(r);
        m.setReturnedBy(actor.getUserId());
        m.setReturnedAt(now);
        m.setUpdatedBy(actor.getUserId());
        m.setUpdatedAt(now);
        mapper.update(m);
        activityLog.log(ActivityLogService.IMPROVEMENT_RETURN, "IMPROVEMENT", id,
                m.getKind().getLabel() + " 완료 반려 → " + target.getLabel() + ", 사유=" + r);
    }

    /** 최종확정 취소: 사유 필수, 최종확정 이력은 활동 이력에 남는다. 취소하면 '완료(검토 대기)'로 돌아간다 */
    @Transactional
    public void reopen(Long id, String reason, LoginUser actor) {
        Improvement m = get(id, actor);
        if (!m.isClosed()) {
            throw new BusinessException("최종확정된 건이 아닙니다.");
        }
        String r = required(reason, "최종확정 취소 사유", 1000);
        LocalDateTime now = LocalDateTime.now();
        mapper.updateClosed(id, "N", null, null, null, now);
        activityLog.log(ActivityLogService.IMPROVEMENT_REOPEN, "IMPROVEMENT", id,
                m.getKind().getLabel() + " 최종 확정 취소, 사유=" + r + " (기존 최종확정일 " + m.getClosedAt() + ")");
    }

    // ---------- 다른 기능과의 연결 ----------

    /**
     * 감사부서 미흡 확정에서 등록한 개선사항이 있으면 그 확정은 판정을 바꿀 수 없다 (F-06).
     * 분기 점검 제출 시 자동 등록된 개선사항은 감사부서 판정과 무관하므로 판정 변경을 막지 않는다.
     */
    @Override
    public String blockReason(Long roundItemId) {
        List<Long> ids = mapper.findIdsByRoundItemAndOrigin(roundItemId, ImprovementOrigin.AUDIT_FINDING);
        return ids.isEmpty() ? null : "개선사항(번호 " + ids.get(0) + ")에 등록된 미흡 확정이라 판정을 바꿀 수 없습니다.";
    }

    @Override
    public EvidenceOwnerType ownerType() {
        return EvidenceOwnerType.IMPROVEMENT;
    }

    @Override
    public void checkView(Long ownerId, LoginUser user) {
        get(ownerId, user);
    }

    @Override
    public void checkModify(Long ownerId, LoginUser user) {
        if (!permissions(get(ownerId, user), user).action()) {
            throw new BusinessException("증빙자료를 바꿀 수 없습니다 (담당자·주관자만, 완료 검토 전까지).");
        }
    }

    // ---------- 내부 ----------

    private Improvement load(Long id) {
        Improvement m = mapper.findById(id);
        if (m == null) {
            throw new BusinessException("개선사항을 찾을 수 없습니다.");
        }
        withItems(new ArrayList<>(List.of(m)));
        return m;
    }

    /** 점검 담당자는 본인 건만, 관리자는 보지 않는다 */
    private static void checkViewable(Improvement m, LoginUser user) {
        boolean ok = switch (user.getRole()) {
            case AUDIT_LEAD, AUDIT_DEPT, VIEWER -> true;
            case INSPECTOR -> Objects.equals(m.getAssigneeId(), user.getUserId());
            case ADMIN -> false;
        };
        if (!ok) {
            throw new AccessDeniedException("개선사항 열람 권한 없음");
        }
    }

    private AppUser checkAssignee(ImprovementForm form) {
        if (form.getAssigneeId() == null) {
            return null;
        }
        AppUser u = userMapper.findById(form.getAssigneeId());
        if (u == null || !u.isActive() || u.getRoleCode() != Role.INSPECTOR) {
            throw new BusinessException("담당자는 사용 중인 점검 담당자여야 합니다.");
        }
        if (StringUtils.hasText(form.getTeamCode()) && !form.getTeamCode().equals(u.getTeamCode())) {
            throw new BusinessException("담당자 " + u.getUserName() + "은(는) 선택한 담당부서 소속이 아닙니다.");
        }
        return u;
    }

    private String teamOf(ImprovementForm form, AppUser assignee) {
        if (assignee != null) {
            return assignee.getTeamCode();
        }
        if (!StringUtils.hasText(form.getTeamCode())) {
            return null;
        }
        Team t = teamMapper.findByCode(form.getTeamCode());
        if (t == null) {
            throw new BusinessException("없는 담당부서입니다.");
        }
        return t.getTeamCode();
    }

    private static String describe(Improvement m) {
        return "[담당=" + m.getTeamCode() + "/" + m.getAssigneeId()
                + ", 결과=" + (m.getInspectionResult() == null ? "-" : m.getInspectionResult().getLabel())
                + ", 개선예정일=" + m.getPlannedDate() + ", 조치결과=" + m.getActionResult().getLabel()
                + ", 개선방안=" + abbreviate(m.getImprovementPlan()) + ", 조치현황=" + abbreviate(m.getActionStatus())
                + ", 내용=" + abbreviate(m.getDetail()) + "]";
    }

    private static String abbreviate(String s) {
        return s == null ? "-" : s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private static String joinDistinct(List<String> values) {
        return values.stream().filter(StringUtils::hasText).map(String::trim).distinct().collect(Collectors.joining("\n"));
    }

    private static String required(String value, String name, int max) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(name + "을(를) 입력하세요.");
        }
        return optional(value, name, max);
    }

    private static String optional(String value, String name, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String v = value.trim();
        if (v.length() > max) {
            throw new BusinessException(name + "은(는) " + max + "자 이하여야 합니다.");
        }
        return v;
    }
}
