package com.hmdp.utils;



import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CacheClient {
    
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // Caffeine本地缓存配置(5分钟TTL+最大10000条目)
    private final LoadingCache<String, String> localCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10000l)
            .build(key -> null);

    public final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //写入redis
    //写本地缓存
    public void set(String key, Object value, Long ttl,TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), ttl, timeUnit);
        localCache.put(key,JSONUtil.toJsonStr(value));
    }
    public void setWithLogicalExpire(String key, Object value, Long ttl, TimeUnit timeUnit){
        // 1. 封装逻辑过期数据
        RedisData redisData = new RedisData();
        redisData.setData(value);//这里value可能是null，但是不影响将其包装的redis，通过key查到的redisdata一定不是null
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(ttl)));
        // 2. 写入 Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
        //写入本地缓存
        localCache.put(key, JSONUtil.toJsonStr(redisData));

    }
    //完整流程：本地缓存 → Redis → 数据库 → 双缓存更新
    //本地缓存和redis一样都会缓存空值
    public <T,ID> T queryWithPassThrough(String prefix, ID id, Class<T> type,Function<ID,T> dbFallback,Long ttl,TimeUnit timeUnit){
            // 缓存数据
            String key = prefix + id;
            // 1.查询本地缓存
            String localValue = localCache.getIfPresent(key);
            if (StrUtil.isNotBlank(localValue)) {
               T t = JSONUtil.toBean(localValue, type);
               return t;
            }
            if(localValue!=null){

                return null;
            }

            // 2.本地未命中，查询Redis
            String str = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(str)) {
                // 3.Redis命中，更新本地缓存
                T t = JSONUtil.toBean(str, type);
                localCache.put(key, str);
                return t;
            }
            //判断数据库中是否存在（命中的是否是空值）
            if(str != null){//等价于shop_str!=null&&shop_str==""
                return null;
            }
            //redis不存在，数据库查询数据
            T t = dbFallback.apply(id);
            if (t == null) {
                //数据库不存在，将空值写入redis,为了防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                //写入本地缓存
                localCache.put(key, "");

                return null;
            }
            // 4.数据库查询后更新双缓存
            this.set(key, t, ttl, timeUnit);
            return t;
        }
        //需要提前对热点key预热，但是没法对本地缓存进行预热
        //存入本地缓存的数据也包含逻辑过期时间，同时其本身也会过期
        //先看本地缓存有没有，有，看过没过期，逻辑过期则删除，没有，查redis，redis没过期，更新本地缓存，redis过期，返回过期数据，同时异步线程
        //查询数据库数据，更新redis和本地缓存
        public <T,ID> T queryWithLogicalExpire(String prefix, ID id, Class<T> type,Function<ID,T> dbFallback,Long ttl,TimeUnit timeUnit){
            // 缓存数据
            String key = prefix + id;
            // 1.查询本地缓存
              // 1.查询本地缓存
              String localValue = localCache.getIfPresent(key);
              if (localValue != null) {
                  RedisData redisData = JSONUtil.toBean(localValue, RedisData.class);
                  LocalDateTime expireTime = redisData.getExpireTime();
                  // 2.检查本地缓存是否过期
                  if (expireTime == null || expireTime.isAfter(LocalDateTime.now())) {
                      // 未过期，直接返回数据
                      JSONObject data = (JSONObject) redisData.getData();
                      return JSONUtil.toBean(data, type);
                  } else {
                      // 已过期，清除本地缓存
                      localCache.invalidate(key);
                  }
              }
            // 2.本地未命中，查询Redis
            String str = stringRedisTemplate.opsForValue().get(key);
             //命中
            RedisData redisData = JSONUtil.toBean(str, RedisData.class);
            // 反序列化
            JSONObject data = (JSONObject) redisData.getData();
            T t = JSONUtil.toBean(data, type);//如果data为null，会返回null，不会报错
            //判断是否过期
            LocalDateTime expireTime = redisData.getExpireTime();
            if (expireTime != null && expireTime.isAfter(LocalDateTime.now())){
                  // 未过期，更新本地缓存
                  localCache.put(key, JSONUtil.toJsonStr(redisData));
                  return t;
              }

            //过期了。需要缓存重建，数据库查询店铺数据
            //1.获取互斥锁
            String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
            //2.判断是否获取锁成功
            boolean tryLock = tryLock(lockKey);
            //3.成功，开启独立线程，实现缓存重建
            if(tryLock){
                // 开启独立线程
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        // 缓存重建
                        T t1 = dbFallback.apply(id);
                        //即使t1为null，也会写入redis，为了防止缓存穿透.
                        this.setWithLogicalExpire(key,t1,ttl,timeUnit);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    finally{
                        //释放锁
                        unLock(lockKey);
                    }
                });
            }

            return t;
 
        }

        private boolean tryLock(String key){
            Boolean setIfAbsent = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10, TimeUnit.SECONDS);
            return BooleanUtil.isTrue(setIfAbsent);

        }
        private void unLock(String key){
            stringRedisTemplate.delete(key);
        }


}
