local voucherId = ARGV[1]
local userId = ARGV[2]

--查询库存是否足够
if(tonumber(redis.call("get","seckill:stock:"..voucherId))<=0) then
    return 1
end

-- 查询用户是否已经购买过
if(redis.call("sisMember","seckill:order:"..voucherId,userId)==1) then
    return 2
end

--库存-1且新建订单
redis.call("incrby","seckill:stock:"..voucherId,"-1")
redis.call("sadd","seckill:order:"..voucherId,userId)
return 0
