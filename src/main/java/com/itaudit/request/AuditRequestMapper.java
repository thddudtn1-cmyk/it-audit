package com.itaudit.request;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditRequestMapper {

    List<AuditRequest> findByRound(@Param("roundId") Long roundId);

    /** 담당자에게 온 요청 (최신 회차부터) */
    List<AuditRequest> findByAssignee(@Param("userId") Long userId);

    AuditRequest findById(@Param("requestId") Long requestId);

    AuditRequest findByRoundItem(@Param("roundItemId") Long roundItemId);

    /** 미흡 확정된 요청 (개선사항 등록 후보, F-07). roundId 가 null 이면 전체 */
    List<AuditRequest> findConfirmedFindings(@Param("roundId") Long roundId);

    void insert(AuditRequest request);

    /** 담당자: 비고 저장·제출 */
    int updateSubmission(AuditRequest request);

    /** 감사부서: 판정·보완 요청 */
    int updateReview(AuditRequest request);

    int completeRound(@Param("roundId") Long roundId, @Param("completedBy") Long completedBy,
                      @Param("completedAt") LocalDateTime completedAt);
}
