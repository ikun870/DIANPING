package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFuture;

import org.springframework.util.concurrent.ListenableFutureCallback;
 
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    @Qualifier("seckillVoucherServiceImpl")
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    /* 
     @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("优惠券不存在");
        }
        //2.判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        //3.判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        Long userId = UserHolder.getUser().getId();
        //方式1，使用synchronized锁，synchronized锁是JVM级别的锁，它只在单个JVM进程内有效，但是在分布式的情况下，无法解决一人一单并发问题
        // synchronized(userId.toString().intern()){//intern()方法会返回字符串池中的字符串，避免重复创建字符串对象,保证只要用户id的值相同，就会进入同一个锁
        //     //要锁到直到事务提交
        //     IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();  
        //     return proxy.createVoucherOrder(voucherId);
        // }

        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
        //创建Redisson锁对象
        RLock lock = redissonClient.getLock("rlock:order:" + userId);
        //获取锁
        //boolean isLock = lock.tryLock(5L);
        boolean isLock = lock.tryLock();;
        if (!isLock) {
            return Result.fail("请勿重复下单！");
        }
        try{
            //5.使用Spring AOP代理对象来创建订单，确保事务注解@Transactional生效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();  
            return proxy.createVoucherOrder(voucherId);
        }finally{
            lock.unlock();
        }
    }
    */
    //使用静态代码块提前加载脚本，提高响应速度
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /* 
    private BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    //1.获取订单信息
                    VoucherOrder voucherOrder = orderQueue.take();
                    //2.创建订单
                    // 使用构造函数传入的代理执行订单创建
                    handleVoucherOrder(voucherOrder, proxy);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }
    // 添加代理参数确保事务注解生效
    private void handleVoucherOrder(VoucherOrder voucherOrder, IVoucherOrderService proxy) {
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
        //创建Redisson锁对象
        RLock lock = redissonClient.getLock("rlock:order:" + voucherOrder.getUserId());
        //获取锁
        //boolean isLock = lock.tryLock(5L);
        boolean isLock = lock.tryLock();;
        if (!isLock) {
            log.error("获取锁失败");
        }
        try{
            proxy.createVoucherOrder(voucherOrder);
        }finally{
            lock.unlock();
        }
    }
     */
   // private IVoucherOrderService proxy; 
    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */
    /* 
   @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,Collections.emptyList(),voucherId.toString(), userId.toString());
        //2.判断优惠券库存
        if (result == 1) {
            return Result.fail("优惠券库存不足");
        }
        //判断用户是否下单
        if (result == 2) {
            return Result.fail("您已购买过一次");
        }
        //可以购买，生成订单id
        Long orderId = redisIdWorker.nextId("order");
        //获取代理对象
        //5.使用Spring AOP代理对象来创建订单，确保事务注解@Transactional生效
        proxy = (IVoucherOrderService) AopContext.currentProxy();  

        //3.把订单信息保存到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orderQueue.add(voucherOrder);
        //4.返回订单id
       return Result.ok(orderId);
    }
    */
     private IVoucherOrderService proxy;

    /* 
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.执行lua脚本
        Long userId = UserHolder.getUser().getId();
         //可以购买，生成订单id
        Long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,Collections.emptyList(),voucherId.toString(), userId.toString(),orderId.toString());

        //2.判断优惠券库存
        if (result == 1) {
            return Result.fail("优惠券库存不足");
        }
        //判断用户是否下单
        if (result == 2) {
            return Result.fail("您已购买过一次");
        }
       
        // 获取代理对象
        // 3.使用Spring AOP代理对象来创建订单，确保事务注解@Transactional生效
        proxy = (IVoucherOrderService) AopContext.currentProxy();  
        if(proxy == null){
            return Result.fail("代理为空，~订单创建失败");
        }
        //4.返回订单id
       return Result.ok(orderId);
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
      private void init(){
          
          // 提交任务时传递已初始化的代理
          SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
      }
    
    private class VoucherOrderHandler implements Runnable{
        private final String streamQueueKey = "stream.orders";

        @Override
        public void run() {
            while(true){
                try {
                    //0.初始化stream和group

                    initStream();

                    //1. 获取消息队列中的订单消息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(streamQueueKey, ReadOffset.lastConsumed())

                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        continue; // 2.1 失败，说明没有消息，继续下一次循环
                    }
                    log.info("___________________执行了VoucherOrderHandler");


                    //3.成功，可以下单
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //4.创建订单
                    handleVoucherOrder(voucherOrder);
                    //5.ack确认 SACK stream orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(streamQueueKey, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while(true){
                try {
                    //1.获取pending-list中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(streamQueueKey, ReadOffset.from("0"))
                    );
                    //2.判断消息获取是否成功
                    if (list == null || list.isEmpty()) {
                        break;//说明pendingList中没有消息，直接结束循环
                    }
                    //3.成功，可以下单
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //4.创建订单
                    handleVoucherOrder(voucherOrder);
                    //5.ack确认 SACK stream orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(streamQueueKey, "g1", record.getId());

                } catch (Exception e) {
                    log.error("处理pendingList订单异常",e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e1) {
                         e1.printStackTrace();
                    }
                }
            }
        }

        public void initStream(){
            Boolean exists = stringRedisTemplate.hasKey(streamQueueKey);
            if (BooleanUtil.isFalse(exists)) {
                log.info("stream不存在,开始创建stream");
                // 不存在，需要创建
                stringRedisTemplate.opsForStream().createGroup(streamQueueKey, ReadOffset.latest(), "g1");
                log.info("stream和group创建完毕");
                return;
            }
            // stream存在，判断group是否存在
            StreamInfo.XInfoGroups groups = stringRedisTemplate.opsForStream().groups(streamQueueKey);
            if(groups.isEmpty()){
                log.info("group不存在,开始创建group");
                // group不存在，创建group
                stringRedisTemplate.opsForStream().createGroup(streamQueueKey, ReadOffset.latest(), "g1");
                log.info("group创建完毕");
            }
        }
    }

    */
    public void handleVoucherOrder(VoucherOrder voucherOrder) {
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
        //创建Redisson锁对象
        RLock lock = redissonClient.getLock("rlock:order:" + voucherOrder.getUserId());
        //获取锁
        //boolean isLock = lock.tryLock(5L);
        boolean isLock = lock.tryLock();;
        if (!isLock) {
            log.error("获取锁失败");
        }
        try{
            proxy.createVoucherOrder(voucherOrder);
        }finally{
            lock.unlock();
        }
    }

    /**
     * 通过订单id创建订单
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
       //4.一人一单
        // 查询订单
        Long userId = UserHolder.getUser().getId();
        
        VoucherOrder had_voucherOrder = query().eq("user_id", userId).eq("voucher_id", voucherId).one();
        if (had_voucherOrder != null) {
            return Result.fail("您已购买过一次");
        }

        //6.1 扣除库存
        //加上乐观锁 解决了库存超卖问题
        //Sql: update seckill_voucher set stock = stock - 1 where id = ? and stock = ?
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足");
        }

        //6.2.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //7.返回订单id
        return Result.ok(voucherOrder.getId());
    }

    /**
     * 通过订单对象创建订单
     * @param voucherOrder
     * @return
     */
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //以防万一

         Long userId = voucherOrder.getUserId();
        
        VoucherOrder had_voucherOrder = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).one();
        if (had_voucherOrder != null) {
            throw new RuntimeException("您已购买过一次");
        }

        //扣除库存
        //加上乐观锁 解决了库存超卖问题
        //Sql: update seckill_voucher set stock = stock - 1 where id = ? and stock = ?
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
        if (!success) {
            throw new RuntimeException("库存不足");
        }

        //最需要执行的一步
        save(voucherOrder);
        
    }

    /*
     * 使用kafka消息队列

     */
        //使用静态代码块提前加载脚本，提高响应速度
    private static final DefaultRedisScript<Long> SECKILL_KAFKA_SCRIPT;
    static {
        SECKILL_KAFKA_SCRIPT = new DefaultRedisScript<>();
        SECKILL_KAFKA_SCRIPT.setLocation(new ClassPathResource("seckill_kafka.lua"));
        SECKILL_KAFKA_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private KafkaTemplate<String, VoucherOrder> kafkaTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long orderId = redisIdWorker.nextId("order");
        // 2.判断用户是否具有下单资格，操作redis

        Long result = stringRedisTemplate.execute(
                SECKILL_KAFKA_SCRIPT,
                Collections.emptyList(),
                userId.toString(), voucherId.toString(), orderId.toString()
        );
        int r = result.intValue();
        if (r != 0) {
            // 3.不为0，代表没有购买资格
            return Result.fail(r == 1 ? "不能重复下单" : "库存不足");
        }
        // 4.为0，有购买资格，把下单信息保存到kafka
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        proxy = (IVoucherOrderService) AopContext.currentProxy();  
        if(proxy == null){
            return Result.fail("代理为空，~订单创建失败");
        }

        // 5.发送到kafka
        ListenableFuture<SendResult<String,VoucherOrder>> send = kafkaTemplate.send("seckill_topic", voucherOrder);
        //执行回调函数
        send.addCallback(new ListenableFutureCallback<SendResult<String, VoucherOrder>>() {
            @Override
            public void onFailure(Throwable ex) {
                // 发送失败
                log.error("kafka发送消息失败",ex);

            }

            @Override
            public void onSuccess(SendResult<String, VoucherOrder> result) {
                // 发送成功
                log.info("kafka发送消息成功,消息内容:{}",result);

            }
        });

        // 6.返回订单id
        return Result.ok(orderId);
    }

}
