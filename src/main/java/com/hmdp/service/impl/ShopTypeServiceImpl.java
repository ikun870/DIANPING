package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public Result queryTypeListWithCache() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        // 从Redis查询缓存
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (shopTypeJsonList != null && !shopTypeJsonList.isEmpty()) {
              log.info("缓存命中————————————————————————————————————————————————————————————————————————————");
            // 缓存命中
            try {
                // 将JSON字符串列表转换为ShopType对象列表
                List<ShopType> typeList = new ArrayList<>();
                for (String json : shopTypeJsonList) {
                    typeList.add(objectMapper.readValue(json, ShopType.class));
                }
                return Result.ok(typeList);
            } catch (Exception e) {
                // 解析失败，继续查询数据库
                log.error("缓存解析失败，继续查询数据库", e);
            }
        }
        
        // 查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        
        // 写入缓存
        try {
            // 清空原有缓存
            stringRedisTemplate.delete(key);
            // 将每个ShopType对象序列化为JSON字符串并存入Redis List
            for (ShopType shopType : typeList) {
                String shopTypeJson = JSONUtil.toJsonStr(shopType);
                stringRedisTemplate.opsForList().rightPush(key, shopTypeJson);
            }
            // 设置过期时间
            stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 序列化失败，不影响主流程
        }
        
        return Result.ok(typeList);
    }
}
