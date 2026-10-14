package com.itaudit.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.itaudit.auth.LoginUser;
import com.itaudit.auth.Role;
import com.itaudit.improvement.ActionResult;
import com.itaudit.improvement.Improvement;
import com.itaudit.improvement.ImprovementFilter;
import com.itaudit.improvement.ImprovementMapper;
import com.itaudit.inspection.InspectionResult;
import com.itaudit.round.AuditRound;
import com.itaudit.round.RoundMapper;
import com.itaudit.round.RoundStatus;

/** 현황 대시보드 (F-08) 집계 */
@Service
public class DashboardService {

    private final DashboardMapper mapper;
    private final RoundMapper roundMapper;
    private final ImprovementMapper improvementMapper;

    public DashboardService(DashboardMapper mapper, RoundMapper roundMapper, ImprovementMapper improvementMapper) {
        this.mapper = mapper;
        this.roundMapper = roundMapper;
        this.improvementMapper = improvementMapper;
    }

    /** 감사부서는 제출된 회차만 */
    public List<AuditRound> findRounds(LoginUser user) {
        return user.getRole() == Role.AUDIT_DEPT
                ? roundMapper.findRounds(List.of(RoundStatus.SUBMITTED, RoundStatus.DONE))
                : roundMapper.findRounds(null);
    }

    /** 기본 선택 회차: 가장 최근의 진행 중(완료 아님) 회차, 없으면 가장 최근 회차 */
    public AuditRound pickRound(List<AuditRound> rounds, Long roundId) {
        if (roundId != null) {
            return rounds.stream().filter(r -> r.getRoundId().equals(roundId)).findFirst().orElse(null);
        }
        return rounds.stream().filter(r -> r.getStatus() != RoundStatus.DONE).findFirst()
                .orElse(rounds.isEmpty() ? null : rounds.get(0));
    }

    public List<CountRow> assigneeStats(Long roundId) {
        return mapper.findAssigneeStats(roundId);
    }

    /** 점검결과 4단계 + 미입력, 0건도 행으로 */
    public List<CountRow> resultStats(Long roundId) {
        Map<String, Integer> counts = mapper.findResultStats(roundId).stream()
                .collect(Collectors.toMap(r -> r.getKey() == null ? "NONE" : r.getKey(), CountRow::getValue));
        List<CountRow> rows = new ArrayList<>();
        for (InspectionResult r : InspectionResult.values()) {
            rows.add(new CountRow(r.name(), r.getLabel(), counts.getOrDefault(r.name(), 0)));
        }
        rows.add(new CountRow("NONE", "미입력", counts.getOrDefault("NONE", 0)));
        return rows;
    }

    /** 최종확정 전 건의 조치 결과별 건수 + 최종확정 건수 */
    public List<CountRow> actionStats() {
        List<CountRow> raw = mapper.findActionStats();
        List<CountRow> rows = new ArrayList<>();
        for (ActionResult a : ActionResult.values()) {
            int n = raw.stream().filter(r -> "N".equals(r.getLabel()) && a.name().equals(r.getKey()))
                    .mapToInt(CountRow::getValue).sum();
            rows.add(new CountRow(a.name(), a.getLabel() + (a == ActionResult.DONE ? " (IT자체감사 담당자 검토 대기)" : ""), n));
        }
        int closed = raw.stream().filter(r -> "Y".equals(r.getLabel())).mapToInt(CountRow::getValue).sum();
        rows.add(new CountRow("CLOSED", "최종확정", closed));
        return rows;
    }

    /** 최종확정 전 개선사항·권고사항 전체 (개선예정일 순) */
    public List<Improvement> openImprovements() {
        ImprovementFilter f = new ImprovementFilter();
        f.setKind(null);
        f.setClosed(ImprovementFilter.Closed.OPEN);
        return improvementMapper.findByFilter(f, LocalDate.now());
    }

    public static int max(List<CountRow> rows) {
        return Math.max(1, rows.stream().mapToInt(r -> Math.max(r.getValue(), r.getTotal())).max().orElse(1));
    }
}
