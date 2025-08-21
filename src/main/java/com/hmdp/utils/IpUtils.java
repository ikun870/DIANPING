package com.hmdp.utils;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP地址工具类
 */
public class IpUtils {

    /**
     * 获取客户端真实IP地址
     * 支持代理服务器、负载均衡等场景
     */
    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        //代码采用 优先级链式检查 策略，依次从不同来源获取IP地址
        //举例 ：Nginx配置中添加 proxy_set_header X-Forwarded-For $remote_addr; 即可传递真实IP
        //先检查各种代理头信息，解析代理服务器添加的 x-forwarded-for 等头字段，还原客户端真实IP，再检查RemoteAddr
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        //代理头信息检查完毕，若仍未获取到IP，再检查RemoteAddr
        /*
        获取与服务器直接建立TCP连接的IP
        无代理时 = 用户真实IP
        有代理时 = 最后一个代理服务器的IP
         */
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 对于通过多个代理的情况，第一个IP为客户端真实IP，多个IP按照','分割
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        // 本地IP处理
        //通过Java的网络API获取本机在局域网中的 实际IP地址
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                ip = "unknown";
            }
        }

        return ip;
    }
}