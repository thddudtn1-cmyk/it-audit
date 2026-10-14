package com.itaudit.round;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 회차 삭제(F-03 확장)용 연쇄 삭제. 모든 메서드는 반환값으로 지운 행 수를 돌려준다. */
@Mapper
public interface RoundPurgeMapper {

    /**
     * 회차 삭제를 막는 개선사항·권고사항 수. 분기 점검 제출 시 자동 등록되었고 아무도 손대지 않은 건
     * (조치 결과 예정, 미종결, 조치 현황·증빙·코멘트 없음)은 막지 않고 회차와 함께 지운다.
     * 다른 회차의 개선사항이 이 회차 항목을 가리키는 경우도 막는다.
     */
    int countBlockingImprovements(@Param("roundId") Long roundId);

    int deleteImprovementLinks(@Param("roundId") Long roundId);

    /** 회차와 함께 지우는 자동 등록 개선사항 */
    int deleteImprovements(@Param("roundId") Long roundId);

    /** 이 회차·그 적정성 점검 기록의 증적 파일 경로 (논리 삭제된 것 포함, 물리 파일이 남아 있으므로) */
    List<String> findEvidencePaths(@Param("roundId") Long roundId);

    int deleteComments(@Param("roundId") Long roundId);

    int deleteEvidence(@Param("roundId") Long roundId);

    int deleteRequests(@Param("roundId") Long roundId);

    int deleteItems(@Param("roundId") Long roundId);

    int deleteRound(@Param("roundId") Long roundId);
}
