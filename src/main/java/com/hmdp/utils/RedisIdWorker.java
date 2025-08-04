package com.hmdp.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Resource;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.experimental.var;

/**
 * 基于Redis的id生成器
 * 生成的id是一个64位的长整数
 * 1.时间戳：31位
 * 2.序列号：32位
 * 3.符号位：1位
 * 为什么使用Redis生成id：Redis的原子性操作（ INCR 命令）确保多实例环境下ID生成的全局唯一性
64位ID结构（1位符号+31位秒级时间+32位序列号）支持到2085年（代码第19行 startTime 基准）
 */
@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //2025年1月1日 00:00:00
    private static final LocalDateTime startTime = LocalDateTime.of(2025,1,1,0,0,0);
    //向左移动的位数
    private static final int COUNT_BITS = 32;

    public long nextId(String keyPrefix){
      //1.生成时间戳

      // 单位：秒
      long start_seconds = startTime.toEpochSecond(ZoneOffset.UTC);
      // 单位：毫秒
      long current_seconds = System.currentTimeMillis() / 1000;
      // 单位：秒
      long gap_seconds = current_seconds - start_seconds;

      //2.生成id=时间戳 拼上 序列号
      LocalDateTime now = LocalDateTime.now();
      String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
      String key = "icr:"+ keyPrefix + ":" + date;
      //设置key确定的序号自增长++，当天的总请求数
      long sequence = stringRedisTemplate.opsForValue().increment(key);

      gap_seconds <<= COUNT_BITS;
      //时间戳拼上序列号，这里是符号位1位 时间戳31位 序列号32位
      long id = gap_seconds | sequence;
      
      return id;
    }
          /*id分别代表的是某一秒的请求是当天的第几个请求，
      为什么不是某一天的请求是当天的第几个请求，这样高31位能够使用更长的时间
       * ### 为什么不采用天级时间戳
        1. 1.
          业务排序需求 ：
          - 电商订单需要按秒级时间排序（如秒杀场景）
          - 天级时间戳无法满足精确到秒的排序需求
        2. 2.
          分布式ID特性 ：
          
          - 秒级时间戳+序列号组合保证ID全局唯一且时间有序
          - 天级时间戳会导致大量ID时间部分重复
        3. 3.
          实际时间覆盖 ：
          
          - 31位秒级时间戳覆盖68年（从2025年基准）
          - 已足够满足系统生命周期需求
       */


}
