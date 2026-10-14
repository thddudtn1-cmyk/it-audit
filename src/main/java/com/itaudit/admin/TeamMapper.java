package com.itaudit.admin;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeamMapper {

    List<Team> findAll();

    List<Team> findActive();

    Team findByCode(@Param("teamCode") String teamCode);

    void insert(Team team);

    int update(Team team);
}
