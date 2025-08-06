--1.参数列表
--1.1 优惠券id 
local voucherId = ARGV[1]
--1.2 用户id
local userId = ARGV[2]
--1.3 订单id
local orderId = ARGV[3]

--2.数据key
--2.1 优惠券key
local voucherKey = "seckill:stock:" .. voucherId
--2.2 订单key set的key
local orderKey = "seckill:order:" .. userId

--3.业务逻辑
--3.1 查询优惠券信息
if(tonumber(redis.call('get',voucherKey)) <= 0) then
    --优惠券库存不足
    return 1
end
--3.2 判断用户是否下单
if(redis.call('sismember',orderKey,userId) == 1) then
    --用户已经下单
    return 2
end
--3.3 扣减库存
redis.call('incrby',voucherKey,-1)
--3.4 下单
redis.call('sadd',orderKey,userId)
--3.6 发送消息到队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

return 0
