package com.itaudit.common;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActivityLogMapper {

    void insert(ActivityLog log);

    List<ActivityLog> findByAction(@Param("action") String action);
}
