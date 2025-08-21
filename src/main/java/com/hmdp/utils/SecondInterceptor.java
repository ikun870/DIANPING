package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SecondInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.查询ThreadLocal中的用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 2.不存在，返回错误结果
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().print(JSONUtil.toJsonStr(Result.fail("请先登录")));
            response.setStatus(401);
            return false;
        }
        // 3.存在，放行
        return true;
    }
}