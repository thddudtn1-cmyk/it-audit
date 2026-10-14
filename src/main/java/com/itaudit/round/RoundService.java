package com.itaudit.round;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.itaudit.admin.Team;
import com.itaudit.admin.TeamMapper;
import com.itaudit.auth.AppUser;
import com.itaudit.auth.AppUserMapper;
import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.checklist.AuditPeriod;
import com.itaudit.checklist.ChecklistItem;
import com.itaudit.checklist.ChecklistMapper;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;

/**
 * 분기 점검 회차 생성·담당 배정 (F-03).
 * 회차 대상 = 회차 연도 체크리스트 중 사용 중이고 감사 시기가 해당 분기이거나 '매분기'인 항목.
 */
@Service
public class RoundService {

    private final RoundMapper roundMapper;
    private final ChecklistMapper checklistMapper;
    private final TeamMapper teamMapper;
    private final AppUserMapper userMapper;
    private final ActivityLogService activityLog;

    public RoundService(RoundMapper roundMapper, ChecklistMapper checklistMapper, TeamMapper teamMapper,
                        AppUserMapper userMapper, ActivityLogService activityLog) {
        this.roundMapper = roundMapper;
        this.checklistMapper = checklistMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.activityLog = activityLog;
    }

    // ---------- 조회 ----------

    /** 감사부서는 감사부서에 제출된 회차만 본다 */
    public List<AuditRound> findRounds(LoginUser user) {
        if (user.getRole() == Role.AUDIT_DEPT) {
            return roundMapper.findRounds(List.of(RoundStatus.SUBMITTED, RoundStatus.DONE));
        }
        return roundMapper.findRounds(null);
    }

    public AuditRound getRound(Long roundId, LoginUser user) {
        AuditRound round = roundMapper.findRound(roundId);
        if (round == null) {
            throw new BusinessException("회차를 찾을 수 없습니다.");
        }
        if (user.getRole() == Role.AUDIT_DEPT && !round.getStatus().isVisibleToAuditDept()) {
            throw new AccessDeniedException("감사부서에 제출되지 않은 회차");
        }
        return round;
    }

    public List<RoundItem> findItems(Long roundId) {
        return roundMapper.findItems(roundId);
    }

    public List<Team> findActiveTeams() {
        return teamMapper.findActive();
    }

    public List<AppUser> findInspectors() {
        return userMapper.findActiveByRole(Role.INSPECTOR);
    }

    // ---------- 회차 생성 ----------

    /** 오늘 기준 분기 */
    public static int currentQuarter() {
        return (LocalDate.now().getMonthValue() - 1) / 3 + 1;
    }

    /** 회차 대상 항목 추출 + 지난 회차의 같은 순번 항목 */
    public List<RoundCandidate> extractCandidates(int year, int quarter) {
        checkQuarter(year, quarter);
        AuditPeriod period = AuditPeriod.valueOf("Q" + quarter);
        Map<Integer, RoundItem> previous = latestPreviousBySeqNo(year, quarter);
        List<RoundCandidate> result = new ArrayList<>();
        for (ChecklistItem item : checklistMapper.findByYear(year, null, null)) {
            if (item.isActive() && (item.getAuditPeriod() == period || item.getAuditPeriod() == AuditPeriod.EVERY)) {
                result.add(new RoundCandidate(item, previous.get(item.getSeqNo())));
            }
        }
        return result;
    }

    /**
     * 순번별로 가장 최근 지난 회차의 항목.
     * [가정] '직전 회차 배정 복사'는 항목마다 같은 순번이 있었던 가장 최근 회차에서 가져온다.
     *        매분기 항목은 바로 앞 분기에서, 분기 항목은 지난해 같은 분기에서 가져오게 된다.
     *        순번은 체크리스트 마스터 코드이므로 연도가 달라도 같은 항목으로 본다.
     */
    private Map<Integer, RoundItem> latestPreviousBySeqNo(int year, int quarter) {
        Map<Integer, RoundItem> map = new HashMap<>();
        for (RoundItem item : roundMapper.findItemsBefore(year, quarter)) {
            map.putIfAbsent(item.getSeqNo(), item); // 최신 회차부터 정렬되어 있음
        }
        return map;
    }

    public AuditRound findRoundByQuarter(int year, int quarter) {
        return roundMapper.findRoundByQuarter(year, quarter);
    }

    @Transactional
    public Long create(RoundCreateForm form, LoginUser actor) {
        if (form.getYear() == null || form.getQuarter() == null) {
            throw new BusinessException("연도와 분기를 선택하세요.");
        }
        int year = form.getYear();
        int quarter = form.getQuarter();
        checkQuarter(year, quarter);
        if (roundMapper.findRoundByQuarter(year, quarter) != null) {
            throw new BusinessException(year + "년 " + quarter + "분기 회차가 이미 있습니다.");
        }
        List<RoundCandidate> candidates = extractCandidates(year, quarter);
        if (candidates.isEmpty()) {
            throw new BusinessException(year + "년 체크리스트에 " + quarter + "분기 점검 대상 항목이 없습니다. 체크리스트를 먼저 등록하세요.");
        }

        // 제외 여부는 화면 입력값을 쓰되, 대상 항목은 서버에서 다시 추출한 것만 인정한다
        Map<Long, RoundCreateForm.Row> inputs = form.getRows().stream()
                .filter(r -> r.getChecklistItemId() != null)
                .collect(Collectors.toMap(RoundCreateForm.Row::getChecklistItemId, Function.identity(), (a, b) -> a));
        List<String> errors = new ArrayList<>();
        for (RoundCandidate c : candidates) {
            RoundCreateForm.Row in = inputs.get(c.item().getItemId());
            if (in != null && in.isExcluded() && !StringUtils.hasText(in.getExcludeReason())) {
                errors.add("#" + c.item().getSeqNo());
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("제외하는 항목은 제외 사유를 입력하세요: " + String.join(", ", errors));
        }

        LocalDateTime now = LocalDateTime.now();
        AuditRound round = new AuditRound();
        round.setRoundYear(year);
        round.setQuarter(quarter);
        round.setStatus(RoundStatus.DRAFT);
        round.setCreatedBy(actor.getUserId());
        round.setCreatedAt(now);
        round.setUpdatedBy(actor.getUserId());
        round.setUpdatedAt(now);
        roundMapper.insertRound(round);

        int copied = 0;
        List<String> excluded = new ArrayList<>();
        for (RoundCandidate c : candidates) {
            RoundItem item = new RoundItem();
            item.setRoundId(round.getRoundId());
            item.setChecklistItemId(c.item().getItemId());
            item.setNoReplyYn("N");
            item.setExcludedYn("N");
            item.setSubmitStatus(SubmitStatus.NOT_SUBMITTED);
            if (form.isCopyPrevious() && c.previous() != null) {
                copyAssignment(c.previous(), item);
                copied++;
            }
            RoundCreateForm.Row in = inputs.get(c.item().getItemId());
            if (in != null && in.isExcluded()) {
                item.setExcludedYn("Y");
                item.setExcludeReason(in.getExcludeReason().trim());
                excluded.add("#" + c.item().getSeqNo());
            }
            item.setCreatedAt(now);
            item.setUpdatedBy(actor.getUserId());
            item.setUpdatedAt(now);
            roundMapper.insertItem(item);
        }
        activityLog.log(ActivityLogService.ROUND_CREATE, "ROUND", round.getRoundId(),
                round.getLabel() + ", 추출 " + candidates.size() + "건"
                        + (excluded.isEmpty() ? "" : ", 제외 " + String.join(" ", excluded))
                        + (form.isCopyPrevious() ? ", 지난 회차 배정 복사 " + copied + "건" : ""));
        return round.getRoundId();
    }

    /** 지난 회차 배정 값 복사. 지금은 쓸 수 없는 팀·담당자는 비운다. */
    private void copyAssignment(RoundItem prev, RoundItem item) {
        Team team = prev.getTeamCode() == null ? null : teamMapper.findByCode(prev.getTeamCode());
        item.setTeamCode(team != null && team.isActive() ? team.getTeamCode() : null);
        AppUser assignee = prev.getAssigneeId() == null ? null : userMapper.findById(prev.getAssigneeId());
        if (assignee != null && assignee.isActive() && assignee.getRoleCode() == Role.INSPECTOR
                && Objects.equals(assignee.getTeamCode(), item.getTeamCode())) {
            item.setAssigneeId(assignee.getUserId());
        }
        item.setRequestMaterial(prev.getRequestMaterial());
        item.setSampleSize(prev.getSampleSize());
        item.setNoReplyYn(prev.isNoReply() ? "Y" : "N");
    }

    // ---------- 배정 편집 ----------

    @Transactional
    public int saveAssignment(Long roundId, AssignmentForm form, LoginUser actor) {
        AuditRound round = getRound(roundId, actor);
        if (!round.getStatus().isEditable()) {
            throw new BusinessException("감사부서에 제출된 회차는 배정을 바꿀 수 없습니다.");
        }
        Map<Long, RoundItem> items = new LinkedHashMap<>();
        for (RoundItem item : roundMapper.findItems(roundId)) {
            items.put(item.getRoundItemId(), item);
        }

        List<String> errors = new ArrayList<>();
        List<RoundItem> changed = new ArrayList<>();
        List<String> diffs = new ArrayList<>();
        for (AssignmentForm.Row row : form.getRows()) {
            RoundItem item = items.get(row.getRoundItemId());
            if (item == null) {
                throw new BusinessException("이 회차의 항목이 아닙니다.");
            }
            String label = "#" + item.getSeqNo();

            String teamCode = StringUtils.hasText(row.getTeamCode()) ? row.getTeamCode() : null;
            AppUser assignee = row.getAssigneeId() == null ? null : userMapper.findById(row.getAssigneeId());
            if (row.getAssigneeId() != null
                    && (assignee == null || !assignee.isActive() || assignee.getRoleCode() != Role.INSPECTOR)) {
                errors.add(label + " 담당자는 사용 중인 점검 담당자여야 합니다.");
                continue;
            }
            if (assignee != null && teamCode == null) {
                teamCode = assignee.getTeamCode();
            }
            if (teamCode != null && !Objects.equals(teamCode, item.getTeamCode())) {
                Team team = teamMapper.findByCode(teamCode);
                if (team == null || !team.isActive()) {
                    errors.add(label + " 사용할 수 없는 담당팀입니다.");
                    continue;
                }
            }
            if (assignee != null && !Objects.equals(assignee.getTeamCode(), teamCode)) {
                errors.add(label + " 담당자 " + assignee.getUserName() + "은(는) 선택한 담당팀 소속이 아닙니다.");
                continue;
            }
            if (row.getSampleSize() != null && row.getSampleSize() < 0) {
                errors.add(label + " 표본 수는 0 이상이어야 합니다.");
                continue;
            }
            String material = trimToNull(row.getRequestMaterial());
            if (material != null && material.length() > 1000) {
                errors.add(label + " 사전요청자료는 1000자 이하여야 합니다.");
                continue;
            }
            String reason = trimToNull(row.getExcludeReason());
            if (row.isExcluded() && reason == null) {
                errors.add(label + " 제외 사유를 입력하세요.");
                continue;
            }
            if (reason != null && reason.length() > 500) {
                errors.add(label + " 제외 사유는 500자 이하여야 합니다.");
                continue;
            }

            String before = describe(item);
            item.setTeamCode(teamCode);
            item.setAssigneeId(assignee == null ? null : assignee.getUserId());
            item.setAssigneeName(assignee == null ? null : assignee.getUserName());
            item.setRequestMaterial(material);
            item.setSampleSize(row.getSampleSize());
            item.setNoReplyYn(row.isNoReply() ? "Y" : "N");
            item.setExcludedYn(row.isExcluded() ? "Y" : "N");
            // 포함으로 되돌리면 사유는 지운다 (변경 이력에는 남음)
            item.setExcludeReason(row.isExcluded() ? reason : null);
            String after = describe(item);
            if (!before.equals(after)) {
                changed.add(item);
                diffs.add(before + " → " + after);
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join(" / ", errors));
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < changed.size(); i++) {
            RoundItem item = changed.get(i);
            item.setUpdatedBy(actor.getUserId());
            item.setUpdatedAt(now);
            roundMapper.updateAssignment(item);
            activityLog.log(ActivityLogService.ROUND_ITEM_ASSIGN, "ROUND_ITEM", item.getRoundItemId(),
                    round.getLabel() + " #" + item.getSeqNo() + " " + diffs.get(i));
        }
        return changed.size();
    }

    private static String describe(RoundItem i) {
        return "[팀=" + i.getTeamCode() + ", 담당자=" + i.getAssigneeId() + ", 사전요청자료=" + i.getRequestMaterial()
                + ", 표본=" + i.getSampleSize() + ", 회신불필요=" + i.getNoReplyYn()
                + ", 제외=" + i.getExcludedYn() + (i.isExcluded() ? "(" + i.getExcludeReason() + ")" : "") + "]";
    }

    private static void checkQuarter(int year, int quarter) {
        if (year < 2000 || year > 2100 || quarter < 1 || quarter > 4) {
            throw new BusinessException("연도(2000~2100)와 분기(1~4)를 확인하세요.");
        }
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
