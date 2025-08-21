package com.hmdp.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 限流维度枚举
 */
@Getter
@AllArgsConstructor
public enum LimitType {
    /**
     * 全局限流
     */
    GLOBAL("global", "全局限流"),
    
    /**
     * IP限流
     */
    IP("ip", "IP维度限流"),
    
    /**
     * 用户限流
     */
    USER("user", "用户维度限流");
    
    private final String code;
    private final String description;
}