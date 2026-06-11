package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private final String lockName;
    private static final String prefix = "lock:";
    //集群环境下不同JVM的线程号可能是一样的
    //因此需要UUID标识不同JVM
    private static final String idPrefix = UUID.randomUUID().toString()+"-";
    private final StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> redisScript;

    @Resource
    private RedissonClient redissonClient;

    static {
        redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("unlock.lua"));
    }


    public SimpleRedisLock(String lockName,StringRedisTemplate stringRedisTemplate) {
        this.lockName = lockName;
        this.stringRedisTemplate = stringRedisTemplate;

    }

    @Override
    public boolean tryLock(long timeout) {


        long threadId = Thread.currentThread().getId();
        String value = idPrefix + threadId;
        Boolean res = stringRedisTemplate.opsForValue().setIfAbsent(prefix + lockName, idPrefix,timeout, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(res);
    }

    //Lua脚本解决Redis不一致问题
    @Override
    public void unlock() {
        stringRedisTemplate.execute(
                redisScript,
                Collections.singletonList(prefix+lockName),
                idPrefix+Thread.currentThread().getId()
        );
    }


/*
    @Override
    public void unlock() {
        //避免释放其他线程的锁,需要查看锁中保存的值，即线程ID
        String threadId = idPrefix + Thread.currentThread().getId();
        String uuid = stringRedisTemplate.opsForValue().get(prefix + lockName);
        if (threadId.equals(uuid)) {
            stringRedisTemplate.delete(prefix + lockName);
        }
    }
*/
}
