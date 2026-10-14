package com.itaudit.evidence;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EvidenceMapper {

    /** 삭제되지 않은 파일 (올린 순) */
    List<Evidence> findActive(@Param("ownerType") EvidenceOwnerType ownerType, @Param("ownerId") Long ownerId);

    Evidence findById(@Param("evidenceId") Long evidenceId);

    void insert(Evidence evidence);

    int markDeleted(@Param("evidenceId") Long evidenceId, @Param("deletedBy") Long deletedBy,
                    @Param("deletedAt") LocalDateTime deletedAt);
}
