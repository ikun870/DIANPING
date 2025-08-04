package com.hmdp.utils;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.fasterxml.jackson.databind.ser.std.StdKeySerializers.Default;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * 简单的Redis锁
 * 目的：为了防止重复下单，保证一人一单
 */
public class SimpleRedisLock implements ILock{
    private String name;//与userId有关
    private StringRedisTemplate stringRedisTemplate;
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    private static final String KEY_NAME_PREFIX = "lock:";
    private static final String THREAD_ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    @Override
    public boolean tryLock(Long expireSeconds) {
        //获取当前线程id,使用UUID的好处是,每个线程的id都是唯一的,一定不会重复，而直接获取线程id的话，在不同的jvm中，不同的线程可能获取到相同的id
        String threadId = THREAD_ID_PREFIX+Thread.currentThread().getId();
        // 获取锁
        String lockKey = KEY_NAME_PREFIX + name;

        //使用的set nx ex 命令，确保了只有一个线程可以获取到锁
        Boolean setIfAbsent = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, threadId , expireSeconds, TimeUnit.SECONDS);
        //setIfAbsent可能是空指针
        return BooleanUtil.isTrue(setIfAbsent);
    }

    /* 
    @Override
    public void unlock() {
        // 释放锁

        //获取线程的id
        String threadId = THREAD_ID_PREFIX+Thread.currentThread().getId();
        //获取redis中存入的线程id
        String lockKey = KEY_NAME_PREFIX + name;
        String lockThreadId = stringRedisTemplate.opsForValue().get(lockKey);
        //判断线程id是否一致
        if(threadId.equals(lockThreadId)){
            //一致，删除锁

            //这里如果被阻塞，锁就会超时释放，若被其他线程获取到锁，就会删除其他线程的锁
            stringRedisTemplate.delete(lockKey);
        }
    }
    */
    /*
     * 为什么使用lua脚本：
     * 1. 确保原子性：Lua脚本在Redis服务器端执行，保证了操作的原子性，避免了在执行解锁操作时，可能存在的阻塞
     * 2. 减少网络往返：使用Lua脚本可以将多个Redis命令合并为一个请求，减少了网络往返次数，提高了效率。
     * 3. 通过Lua脚本实现的解锁操作，确保了只有当前线程持有的锁才能被释放
     */
    private static final DefaultRedisScript<Long> UNLOCK_LUA_SCRIPT;
    static {
        UNLOCK_LUA_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_LUA_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_LUA_SCRIPT.setResultType(Long.class);
    }
    @Override
    public void unlock() {
        // 释放锁

        //获取线程的id
        String threadId = THREAD_ID_PREFIX+Thread.currentThread().getId();
        //获取redis中存入的线程id
        String lockKey = KEY_NAME_PREFIX + name;

        //调用lua脚本判断是否是当前线程的锁并释放锁
        stringRedisTemplate.execute(UNLOCK_LUA_SCRIPT,
            Collections.singletonList(lockKey),
            threadId
        );
        //log.info("+++++++++++++++++unlock lockKey:{} threadId:{}",lockKey,threadId);
        
    }
    
}
