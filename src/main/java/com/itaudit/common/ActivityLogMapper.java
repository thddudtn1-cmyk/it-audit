package com.itaudit.common;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActivityLogMapper {

    void insert(ActivityLog log);

    List<ActivityLog> findByAction(@Param("action") String action);

    /** 감사 로그 화면 검색 (최신순). 조건이 null 이면 제외 */
    List<ActivityLog> search(@Param("action") String action, @Param("loginId") String loginId,
                             @Param("keyword") String keyword, @Param("from") java.time.LocalDateTime from,
                             @Param("to") java.time.LocalDateTime to,
                             @Param("offset") int offset, @Param("limit") int limit);

    int count(@Param("action") String action, @Param("loginId") String loginId,
              @Param("keyword") String keyword, @Param("from") java.time.LocalDateTime from,
              @Param("to") java.time.LocalDateTime to);
}
