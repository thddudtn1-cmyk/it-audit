package com.itaudit.improvement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImprovementMapper {

    List<Improvement> findByFilter(@Param("f") ImprovementFilter filter, @Param("today") LocalDate today);

    Improvement findById(@Param("improvementId") Long improvementId);

    /** 같은 체크리스트 순번 항목에 연결된 개선사항·권고사항 (F-10 이력, 최신 회차부터) */
    List<Improvement> findBySeqNo(@Param("seqNo") int seqNo);

    List<LinkedItem> findLinkedItems(@Param("ids") List<Long> improvementIds);

    /** 이 점검항목이 연결된 개선사항 ID (kind 가 null 이면 전체) */
    List<Long> findIdsByRoundItem(@Param("roundItemId") Long roundItemId, @Param("kind") ImprovementKind kind);

    /** 이 점검항목이 연결된, 해당 경위로 등록된 개선사항 ID */
    List<Long> findIdsByRoundItemAndOrigin(@Param("roundItemId") Long roundItemId, @Param("origin") ImprovementOrigin origin);

    void insert(Improvement improvement);

    void insertItem(@Param("improvementId") Long improvementId, @Param("roundItemId") Long roundItemId);

    /** 관리 정보 + 조치 정보 수정 */
    int update(Improvement improvement);

    int updateClosed(@Param("improvementId") Long improvementId, @Param("closedYn") String closedYn,
                     @Param("closedBy") Long closedBy, @Param("closedAt") LocalDateTime closedAt,
                     @Param("closeComment") String closeComment, @Param("updatedAt") LocalDateTime updatedAt);
}
