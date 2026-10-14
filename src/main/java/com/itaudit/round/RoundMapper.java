package com.itaudit.round;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoundMapper {

    /** 집계 포함 회차 목록. statuses 가 null 이면 전체 */
    List<AuditRound> findRounds(@Param("statuses") List<RoundStatus> statuses);

    AuditRound findRound(@Param("roundId") Long roundId);

    AuditRound findRoundByQuarter(@Param("year") int year, @Param("quarter") int quarter);

    void insertRound(AuditRound round);

    List<RoundItem> findItems(@Param("roundId") Long roundId);

    RoundItem findItem(@Param("roundItemId") Long roundItemId);

    /** 기준 회차보다 앞선 회차들의 항목 (순번별 최신값을 고르기 위해 최신 회차부터) */
    List<RoundItem> findItemsBefore(@Param("year") int year, @Param("quarter") int quarter);

    void insertItem(RoundItem item);

    List<RoundItem> findByAssignee(@Param("userId") Long userId);

    List<RoundItem> findHistory(@Param("seqNo") int seqNo, @Param("year") int year, @Param("quarter") int quarter);

    /** 점검결과·점검 내용 상세·비고1·조치내용·개선 예정일 */
    int updateInspection(RoundItem item);

    /** 제출 상태와 제출자·제출일시 */
    int updateSubmitStatus(RoundItem item);

    /** 배정 값(담당팀·담당자·사전요청자료·표본 수·회신불필요·제외) 수정 */
    int updateAssignment(RoundItem item);
}
