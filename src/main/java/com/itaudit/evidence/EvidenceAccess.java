package com.itaudit.evidence;

import com.itaudit.auth.LoginUser;

/**
 * 증적 소유 대상별 접근 규칙. 소유 대상을 가진 기능(F-04, F-06, F-07)이 구현한다.
 * 볼 수 없으면 AccessDeniedException, 바꿀 수 없으면 BusinessException 을 던진다.
 */
public interface EvidenceAccess {

    EvidenceOwnerType ownerType();

    /** 파일 내려받기 */
    void checkView(Long ownerId, LoginUser user);

    /** 파일 추가·삭제 */
    void checkModify(Long ownerId, LoginUser user);
}
