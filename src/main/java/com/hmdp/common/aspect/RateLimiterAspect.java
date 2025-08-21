package com.hmdp.common.aspect;

import com.hmdp.common.annotation.RateLimiter;
import com.hmdp.common.enums.LimitType;
import com.hmdp.common.exception.RateLimitException;
import com.hmdp.utils.IpUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Collections;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流切面实现
 */
@Aspect
@Component
@Slf4j
public class RateLimiterAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 加载Lua脚本
     */
    private DefaultRedisScript<Long> rateLimitScript;

    
    //之前写的是static代码块
    //static静态代码块总是先于 @PostConstruct方法执行
    //static：类加载期（JVM控制）每个类加载器加载类时执行一次一定只执行一次
    //  @PostConstruct：对象初始化期（容器控制），每个 Bean 实例创建时执行，执行次数取决于Bean的作用域
    /*在默认的单例作用域下：

    static 代码块执行 1 次（类加载时）

    @PostConstruct 执行 1 次（Bean 初始化时）

    在原型作用域下：

    static 代码块执行 1 次（类加载时）

    @PostConstruct 执行 N 次（每次创建新实例时） */
    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setLocation(new ClassPathResource("rateLimiter.lua"));
        rateLimitScript.setResultType(Long.class);
    }

    /**
     * 拦截所有带@RateLimiter注解的方法
     * @Around ：环绕通知
     * 类型：AOP环绕通知注解
     * 作用：可以在目标方法执行前后插入自定义逻辑，拥有完全控制目标方法执行的能力（包括是否执行、何时执行、异常处理等）
     * 特点：是功能最强大的AOP通知类型，能实现前置通知（@Before）、后置通知（@After）、返回通知（@AfterReturning）和异常通知（@AfterThrowing）的全部功能
     */
    @Around("@annotation(com.hmdp.common.annotation.RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimiter rateLimiter = method.getAnnotation(RateLimiter.class);

        // 获取限流key
        String key = generateKey(rateLimiter, joinPoint);
        int windowSize = rateLimiter.windowSize();
        int maxCount = rateLimiter.maxCount();

        // 执行Lua脚本
        Long result = stringRedisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(windowSize * 1000), // 窗口大小(毫秒)
                String.valueOf(maxCount),
                String.valueOf(System.currentTimeMillis()) // 当前时间戳(毫秒)
        );

        if (result != null && result == 1) {
            // 允许访问
            return joinPoint.proceed();//执行目标方法
        } else {
            // 限流处理
            log.warn("接口限流: {}.{}，key: {}", method.getDeclaringClass().getName(), method.getName(), key);
            throw new RateLimitException(rateLimiter.message());
        }
    }

    /**
     * 生成限流key
     */
    private String generateKey(RateLimiter rateLimiter, ProceedingJoinPoint joinPoint) {
        StringBuilder key = new StringBuilder(rateLimiter.keyPrefix());
        LimitType limitType = rateLimiter.limitType();

        switch (limitType) {
            case IP:
                // IP维度限流
                ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (requestAttributes == null) {
                    throw new RuntimeException("无法获取请求上下文，无法使用IP维度限流");
                }
                HttpServletRequest request = requestAttributes.getRequest();
                key.append("ip:").append(IpUtils.getIpAddr(request));
                break;
            case USER:
                // 用户维度限流
                Long userId = UserHolder.getUser().getId();
                if (userId == null) {
                    throw new RuntimeException("用户未登录，无法使用用户维度限流");
                }
                key.append("user:").append(userId);
                break;
            case GLOBAL:
            default:
                // 全局限流
                key.append("global");
                break;
        }

        // 添加方法签名作为key的一部分，确保不同方法的限流key不冲突
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        key.append(":").append(signature.getDeclaringTypeName()).append(":").append(signature.getName());

        return key.toString();
    }


    
}