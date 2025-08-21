package com.hmdp.utils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hmdp.dto.UserDTO;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

public class FirstInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public FirstInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从请求头中获取token
        String token = request.getHeader("authorization");
        if (StrUtil.isNotBlank(token)) {
            // 2.从redis中获取用户信息
            String key = RedisConstants.LOGIN_USER_KEY + token;
            Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
            if (!map.isEmpty()) {
                // 3.将用户信息保存到ThreadLocal
                UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
                UserHolder.saveUser(userDTO);
                // 4.刷新token有效期
                stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
            }
        }
        // 无论是否有用户信息，都放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理ThreadLocal
        UserHolder.removeUser();
    }
}