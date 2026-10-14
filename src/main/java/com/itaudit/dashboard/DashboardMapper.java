package com.itaudit.dashboard;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {

    /** 담당자별 제출 대상·제출 건수 (label=담당자, subLabel=담당팀, value=제출, total=대상) */
    List<CountRow> findAssigneeStats(@Param("roundId") Long roundId);

    /** 점검결과별 건수 (key=점검결과 코드, null=미입력) */
    List<CountRow> findResultStats(@Param("roundId") Long roundId);

    /** 개선사항·권고사항 조치 결과별 건수 (key=조치 결과 코드, label=종결 여부 Y/N) */
    List<CountRow> findActionStats();
}
