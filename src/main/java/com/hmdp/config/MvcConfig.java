package com.hmdp.config;

import jakarta.annotation.Resource;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hmdp.utils.FirstInterceptor;

import com.hmdp.utils.SecondInterceptor;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 拦截所有路径的拦截器
        registry.addInterceptor(new FirstInterceptor(stringRedisTemplate))
            .order(1);
        
        // 2. 拦截需要登录路径的拦截器
        registry.addInterceptor(new SecondInterceptor())
            .excludePathPatterns("/user/code","/user/login","blog/hot","/shop/**", "/voucher/**", "/shop-type/**", "/upload/**","/chat/**","/index.html")
            .order(2);
    }
}
