package com.itaudit.common;

/** 화면에 그대로 보여줄 업무 규칙 위반 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
