package com.itaudit.round;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.itaudit.auth.LoginUser;
import com.itaudit.common.ActivityLogService;
import com.itaudit.common.BusinessException;
import com.itaudit.evidence.EvidenceService;

/**
 * 분기 회차 삭제 (주관자). 상태와 관계없이 삭제할 수 있다.
 * 회차와 그 안의 점검 입력·증적(파일 포함)·적정성 점검 기록·코멘트를 완전히 지우고 되돌릴 수 없다.
 * [결정] CLAUDE.md 의 '판정·파일 삭제는 논리 삭제' 규칙의 예외로 사용자가 물리 삭제를 선택했다.
 *        대신 지운 내용의 요약을 활동 이력(감사 로그)에 남긴다.
 * 조치가 진행·최종확정됐거나 직접 등록한 개선사항·권고사항이 연결된 회차는 관리대장이 깨지므로 삭제할 수 없다.
 * 분기 점검 제출 시 자동 등록되어 아무도 손대지 않은 개선사항은 회차와 함께 지운다.
 */
@Service
public class RoundDeleteService {

    private final RoundMapper roundMapper;
    private final RoundPurgeMapper purgeMapper;
    private final EvidenceService evidenceService;
    private final ActivityLogService activityLog;

    public RoundDeleteService(RoundMapper roundMapper, RoundPurgeMapper purgeMapper, EvidenceService evidenceService,
                              ActivityLogService activityLog) {
        this.roundMapper = roundMapper;
        this.purgeMapper = purgeMapper;
        this.evidenceService = evidenceService;
        this.activityLog = activityLog;
    }

    /** 선택한 회차 삭제. 한 건이라도 삭제할 수 없으면 아무것도 삭제하지 않는다. */
    @Transactional
    public int delete(List<Long> roundIds, LoginUser actor) {
        if (roundIds == null || roundIds.isEmpty()) {
            throw new BusinessException("삭제할 회차를 선택하세요.");
        }
        List<AuditRound> rounds = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(roundIds)) {
            AuditRound r = id == null ? null : roundMapper.findRound(id);
            if (r == null) {
                throw new BusinessException("선택한 회차를 찾을 수 없습니다. 목록을 새로 고친 뒤 다시 선택하세요.");
            }
            rounds.add(r);
        }
        List<String> linked = rounds.stream()
                .filter(r -> purgeMapper.countBlockingImprovements(r.getRoundId()) > 0)
                .map(AuditRound::getLabel).toList();
        if (!linked.isEmpty()) {
            throw new BusinessException("조치가 진행·최종확정되었거나 직접 등록한 개선사항·권고사항이 연결된 회차는 삭제할 수 없습니다: "
                    + String.join(", ", linked));
        }

        List<String> allPaths = new ArrayList<>();
        for (AuditRound r : rounds) {
            Long id = r.getRoundId();
            long inspected = roundMapper.findItems(id).stream().filter(i -> i.getInspectionResult() != null).count();
            List<String> paths = purgeMapper.findEvidencePaths(id);
            int comments = purgeMapper.deleteComments(id);
            // 분기 점검 제출 시 자동 등록되어 아무도 손대지 않은 개선사항은 회차와 함께 지운다
            purgeMapper.deleteImprovementLinks(id);
            int improvements = purgeMapper.deleteImprovements(id);
            purgeMapper.deleteEvidence(id);
            int requests = purgeMapper.deleteRequests(id);
            int items = purgeMapper.deleteItems(id);
            purgeMapper.deleteRound(id);
            allPaths.addAll(paths);
            activityLog.log(ActivityLogService.ROUND_DELETE, "ROUND", id,
                    r.getLabel() + " 삭제 (상태 " + r.getStatus().getLabel() + "): 점검항목 " + items + "건, 점검 입력 "
                            + inspected + "건, 적정성 점검 기록 " + requests + "건, 증적 파일 " + paths.size() + "개, 코멘트 "
                            + comments + "건, 자동 등록 개선사항 " + improvements + "건 함께 삭제");
        }
        // 파일은 DB 삭제가 확정(커밋)된 뒤에 지운다. 롤백되면 파일은 그대로 둔다.
        if (!allPaths.isEmpty()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evidenceService.deleteStoredFiles(allPaths);
                    }
                });
            } else {
                evidenceService.deleteStoredFiles(allPaths);
            }
        }
        return rounds.size();
    }
}
