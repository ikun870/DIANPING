package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        // 使用单节点配置
        SingleServerConfig serverConfig = config.useSingleServer();
        // 设置节点地址
        serverConfig.setAddress("redis://127.0.0.1:6379")
        //.setPassword("")
        ;
        // 创建Redisson客户端
        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;
    }
}
