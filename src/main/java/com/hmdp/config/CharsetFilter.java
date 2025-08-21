package com.hmdp.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 全局过滤器：确保所有响应默认 UTF-8，并防止某些容器/代理回退 ISO-8859-1。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CharsetFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        response.setCharacterEncoding("UTF-8");
        if (response instanceof HttpServletResponse resp) {
            String ct = resp.getContentType();
            if (ct != null && ct.startsWith("text/")) {
                if (!ct.toLowerCase().contains("charset")) {
                    resp.setContentType(ct + ";charset=UTF-8");
                }
            }
        }
        chain.doFilter(request, response);
    }
}
