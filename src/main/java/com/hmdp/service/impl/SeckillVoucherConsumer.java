package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * Kafka消费者，处理秒杀订单消息
 */
@Component
@Slf4j
public class SeckillVoucherConsumer {

    @Resource
    @Qualifier("voucherOrderServiceImpl")
    private IVoucherOrderService voucherOrderService;

    /**
     * 监听秒杀订单主题
     */
    @KafkaListener(topics = "seckill_topic")//// 默认消费者组（使用yaml中的group-id）
    //配置优先级 ：注解配置 > yaml全局配置，当注解中显式指定 groupId 时，会覆盖yaml中的默认配置。
    public void listenSeckillTopic(VoucherOrder voucherOrder, ConsumerRecord<?, ?> consumerRecord, Acknowledgment acknowledgment) {
        log.info("接收到秒杀订单消息: {}", voucherOrder);
        try {
            // 调用订单处理方法
            voucherOrderService.handleVoucherOrder(voucherOrder);
            /*
             * - Spring容器注入的是 代理对象 （包含事务增强逻辑）
- 直接调用 voucherOrderService.handleVoucherOrder() 相当于通过代理对象调用，会自动触发AOP增强
- 无需手动获取 AopContext.currentProxy() ，因为注入时已完成代理初始化
SeckillVoucherConsumer 中调用 ❌ 不需要 通过Spring注入的 voucherOrderService 本身就是 代理对象 
VoucherOrderServiceImpl.seckillVoucher() 中调用 ✅ 需要 存在 自调用问题 （ this.createVoucherOrder() 会绕过代理）
             */
            // 手动提交偏移量
            acknowledgment.acknowledge();
            /*
             * ### . 消费语义保障
                Kafka默认提供三种消费语义：

                - 至少一次(at-least-once) ：消息可能被重复消费，但不会丢失
                - 最多一次(at-most-once) ：消息可能丢失，但不会重复消费
                - 精确一次(exactly-once) ：消息只被消费一次
                手动提交偏移量是实现 至少一次 语义的关键，确保只有在订单处理成功后才提交偏移量。

                ### 2. 业务场景必要性
                在秒杀系统中：

                - 如果使用自动提交(默认5秒)，可能出现消息已提交但订单处理失败的情况
                - 手动提交确保 handleVoucherOrder(voucherOrder) 执行成功后才标记消息为已消费
                - 避免库存扣减但订单未创建的业务异常
                - 确保订单处理失败时，消息不会被重复消费
             */
        } catch (Exception e) {
            log.error("处理秒杀订单异常,topic = {},offset = {},原因 = {}", consumerRecord.topic(), consumerRecord.offset(), e.getMessage());
        }
    }
}