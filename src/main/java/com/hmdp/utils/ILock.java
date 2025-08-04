package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取分布式锁
     * @param expireSeconds 过期时间
     * @return true 成功 false 失败
     */
    boolean tryLock(Long expireSeconds);

    /**
     * 释放锁
     * 
     */
    void unlock();

}
