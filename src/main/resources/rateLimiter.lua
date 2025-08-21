-- 滑动窗口限流Lua脚本
-- KEYS[1]: 限流key
-- ARGV[1]: 窗口大小(毫秒)
-- ARGV[2]: 最大请求数
-- ARGV[3]: 当前时间戳(毫秒)

local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- 移除窗口之外的请求记录
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 统计当前窗口内的请求数
local count = redis.call('ZCARD', key)

if count < limit then
    -- 添加当前请求记录 (时间戳+随机数防止重复)
    -- 确保每次调用都有不同的随机数
    math.randomseed(now)
    local random = math.random(1000000)
    --local random = tostring(now)
    redis.call('ZADD', key, now, now .. '-' .. random)
    -- 设置过期时间(窗口大小+5s，确保窗口外的key能被自动清理)
    redis.call('EXPIRE', key, window / 1000 + 5)
    return 1
end

return 0