package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public Long generateId(String keyPrefix){
        LocalDateTime now = LocalDateTime.now();
        Long id =  stringRedisTemplate.opsForValue().increment("inc:"+keyPrefix+":"+
                now.format(DateTimeFormatter.ofPattern("yyyy:MM::dd")));
        long currentTimeSecond = now.toEpochSecond(ZoneOffset.UTC);

        assert id != null;
        return currentTimeSecond<<32 | id;
    }
}
