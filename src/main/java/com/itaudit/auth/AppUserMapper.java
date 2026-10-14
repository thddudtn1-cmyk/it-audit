package com.itaudit.auth;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserMapper {

    AppUser findByLoginId(@Param("loginId") String loginId);

    AppUser findById(@Param("userId") Long userId);

    List<AppUser> findAll();

    int countByRole(@Param("role") Role role);

    /** 사용 중인 해당 역할 사용자 (담당팀·이름 순) */
    List<AppUser> findActiveByRole(@Param("role") Role role);

    void insert(AppUser user);

    /** 이름·담당팀·역할·사용여부 수정 */
    int update(AppUser user);

    int updatePassword(@Param("userId") Long userId,
                       @Param("passwordHash") String passwordHash,
                       @Param("pwChangeRequired") String pwChangeRequired,
                       @Param("updatedAt") LocalDateTime updatedAt);

    int updateLastLogin(@Param("userId") Long userId, @Param("lastLoginAt") LocalDateTime lastLoginAt);
}
