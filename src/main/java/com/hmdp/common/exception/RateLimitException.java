package com.hmdp.common.exception;

import lombok.Getter;

/**
 * 自定义限流异常
 */
@Getter
public class RateLimitException extends RuntimeException {

    private final String message;

    public RateLimitException(String message) {
       
        this.message = message;
    }

    public RateLimitException(String message, Throwable cause) {
        super(cause);
        this.message = message;
    }
}