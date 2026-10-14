package com.itaudit.checklist;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChecklistMapper {

    List<Integer> findYears();

    /** area·period 가 null 이면 조건 없음 */
    List<ChecklistItem> findByYear(@Param("year") int year,
                                   @Param("area") AuditArea area,
                                   @Param("period") AuditPeriod period);

    ChecklistItem findById(@Param("itemId") Long itemId);

    ChecklistItem findBySeqNo(@Param("year") int year, @Param("seqNo") int seqNo);

    int countByYear(@Param("year") int year);

    List<String> findAuditItems(@Param("year") int year);

    List<String> findRegulations(@Param("year") int year);

    void insert(ChecklistItem item);

    int update(ChecklistItem item);

    int deleteByYear(@Param("year") int year);

    int deleteById(@Param("itemId") Long itemId);

    /** 해당 연도 항목을 쓰는 회차 점검항목 수 */
    int countUsedInRounds(@Param("year") int year);
}
