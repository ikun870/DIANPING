package com.hmdp.common.annotation;

import com.hmdp.common.enums.LimitType;
import java.lang.annotation.*;

/**
 * 自定义限流注解
 * - 1.
@Target({ElementType.METHOD})

- 作用：指定当前注解只能应用于方法上
- 使用场景：确保 @RateLimiter 注解只能标记在Controller或Service的方法上，限制其使用范围
- 2.
@Retention(RetentionPolicy.RUNTIME)

- 作用：指定注解在运行时仍然保留，不会被编译器丢弃
- 使用场景：AOP切面 RateLimiterAspect 需要在运行时通过反射获取方法上的注解信息，从而执行限流逻辑
- 3.
@Documented

- 作用：指定该注解会被包含在Javadoc文档中
- 使用场景：生成API文档时会自动包含该注解的说明，提高代码可维护性
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {
    /**
     * 限流key前缀
     */
    String keyPrefix() default "rate_limit:";

    /**
     * 时间窗口大小(秒)
     */
    int windowSize() default 60;

    /**
     * 时间窗口内允许的最大请求数
     */
    int maxCount() default 100;

    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 限流维度
     */
    LimitType limitType() default LimitType.GLOBAL;
}