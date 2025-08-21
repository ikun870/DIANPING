package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
        @Resource
        private ShopMapper shopMapper;
        @Resource
        private StringRedisTemplate stringRedisTemplate;
        @Resource
        private CacheClient cacheClient;
        @Override
        public Result queryShopById(Long id) {
            //解决缓存穿透问题
            //Shop shop = queeryWithPassThrough(id);
            //Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

            //还解决了缓存击穿问题
            //Shop shop = queryWithMutex(id);(互斥锁)
            //Shop shop = queryWithLogicalExpire(id,30L);//逻辑过期
            //也是使用逻辑过期
            Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

            if(shop == null){
                return Result.fail("店铺不存在");
            }
            
            return Result.ok(shop);
        }
        //利用缓存空值解决了缓存穿透
        /*
        
        public Shop queeryWithPassThrough(Long id){
            // 缓存店铺数据
            String shop_str = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(shop_str)) {
                // 店铺存在，将店铺数据转换为Shop对象
              
                Shop shop1 = JSONUtil.toBean(shop_str, Shop.class);
                return shop1;
            }
            //判断数据库中是否存在（命中的是否是空值）
            if(shop_str != null){//等价于shop_str!=null&&shop_str==""
                return null;
            }
            //redis不存在，数据库查询店铺数据
            Shop shop = getById(id);
            if (shop == null) {
                //数据库不存在，返回店铺不存在
                //将空值写入redis,为了防止缓存穿透
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 缓存店铺数据
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
            // 缓存店铺数据，设置过期时间
            stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY + id, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return shop;
        }
         */
        //在解决缓存穿透的基础上，利用互斥锁解决缓存击穿问题
        public Shop queryWithMutex(Long id){
            // 缓存店铺数据
            String shop_str = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(shop_str)) {
                // 店铺存在，将店铺数据转换为Shop对象
                Shop shop1 = JSONUtil.toBean(shop_str, Shop.class);
                return shop1;
            }
            //判断数据库中是否存在（命中的是否是空值）
            if(shop_str != null){//等价于shop_str!=null&&shop_str==""
                return null;
            }

            String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
            Shop shop = null;
            try{
                //缓存重建
                //1.获取互斥锁
                boolean tryLock = tryLock(lockKey);
                //2.判断是否获取成功
                if(!tryLock){
                    //获取失败，休眠并重试
                    Thread.sleep(50);
                    return queryWithMutex(id);
                }
                
                //再次查询缓存，doublecheck因为有可能其他线程已经在在第一次查询后将数据写入缓存
                // shop_str = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
                // if (StrUtil.isNotBlank(shop_str)) {
                //     // 店铺存在，将店铺数据转换为Shop对象
                //     Shop shop1 = JSONUtil.toBean(shop_str, Shop.class);
                //     return shop1;
                // }
                //3.成功，根据id查询数据库
                //redis不存在，数据库查询店铺数据
                shop = getById(id);
                //模拟缓存重建的延时
                Thread.sleep(2000);
                if (shop == null) {
                    //数据库不存在，返回店铺不存在
                    //将空值写入redis,为了防止缓存穿透
                    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                // 缓存店铺数据
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
                // 缓存店铺数据，设置过期时间
                stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_KEY + id, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            finally{
                //释放互斥锁
                unLock(lockKey);
            }
            return shop;
        }
        private boolean tryLock(String key){
            Boolean setIfAbsent = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10, TimeUnit.SECONDS);
            return BooleanUtil.isTrue(setIfAbsent);

        }
        private void unLock(String key){
            stringRedisTemplate.delete(key);
        }
        //利用逻辑过期解决缓存击穿问题
        public Shop queryWithLogicalExpire(Long id,Long expireSeconds){
            // 缓存店铺数据
            String shop_str = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
            if (StrUtil.isBlank(shop_str)) {
                // redis中店铺不存在，我们认为数据库中也不存在
                return null;
            }
            //命中
            RedisData redisData = JSONUtil.toBean(shop_str, RedisData.class);
            // 反序列化
            JSONObject data = (JSONObject) redisData.getData();
            Shop shop = JSONUtil.toBean(data, Shop.class);

            LocalDateTime expireTime = redisData.getExpireTime();
            if(expireTime.isAfter(LocalDateTime.now())){
                //未过期，直接返回店铺数据
                return shop;
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
                        saveShopToRedis(id,expireSeconds);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    finally{
                        //释放锁
                        unLock(lockKey);
                    }
                });
            }

            return shop;

        }

        private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

        @Override
        @Transactional// 开启事务，保证更新数据库和删除缓存的原子性
        public Result updateShop(Shop shop) {
            // 校验参数
            if (shop.getId() == null) {
                return Result.fail("店铺id不能为空");
            }
            // 更新数据库
            updateById(shop);
            // 删除缓存
            stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
            return Result.ok();
        }

        // 缓存店铺数据（使用了逻辑过期时间）
        public void saveShopToRedis(Long id,Long expireSeconds) throws InterruptedException{

            //查询店铺数据
            Shop shop = getById(id);
            //模拟数据库查询延时
            //TODO 删除
            Thread.sleep(200);
            //封装
            RedisData redisData = new RedisData();
            redisData.setData(shop);
            //注意这里的过期时间是数据的逻辑过期时间，而不是缓存的过期时间
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
            //写入redis，注意这里不仅仅是写入了数据，还写入了过期时间，二者被统一封装到了RedisData中，并写入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
        }
        
        
}
