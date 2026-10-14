package com.itaudit.auth;

import com.itaudit.common.BusinessException;

/** 비밀번호 규칙: 8~64자, 영문·숫자·특수문자 각 1자 이상 */
public final class PasswordPolicy {

    public static final String DESCRIPTION = "8~64자, 영문·숫자·특수문자를 각각 1자 이상 포함";

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 64
                || !password.matches(".*[A-Za-z].*")
                || !password.matches(".*[0-9].*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new BusinessException("비밀번호는 " + DESCRIPTION + "해야 합니다.");
        }
    }
}
