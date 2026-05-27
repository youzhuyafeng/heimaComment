package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.function.Function;

@Component
@Slf4j
public class CacheUtils {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);


    public void set(String key, Object value, Integer expireTime, TimeUnit timeUnit){
        String jsonObject = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key,jsonObject,expireTime,timeUnit);
    }

    public void setLogicalExpire(String key, Object value, Integer expireTime, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        redisData.setData(value);
        String jsonObject = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key,jsonObject);
    }

    //根据键查找缓存中的结果，如果没有就查数据库，数据库没有就缓存为空值，解决缓存穿透
    public <R,ID> R queryWithPassThrough(String prefix, ID id,Class<R> clazz,Function<ID,R> function,Integer expireTime,TimeUnit timeUnit){
        String key = prefix+id;
        String jsonObject= stringRedisTemplate.opsForValue().get(key);
        if(jsonObject != null && !jsonObject.isEmpty()){
            return JSONUtil.toBean(jsonObject,clazz);
        }
        //数据不存在于数据库，返回null
        if(jsonObject != null){
            return null;
        }

        R result = function.apply(id);
        if(result == null){
            set(key,"",expireTime,timeUnit);
            return null;
        }
        set(key,result,expireTime,timeUnit);
        return result;
    }

    //先看看键的逻辑过期时间有没有到期，如果到期新开线程拿锁更新键值对，解决缓存击穿
    public <R,ID> R queryWithBreakDown(String prefix, ID id,Class<R> clazz,Function<ID,R> function,Integer expireTime,TimeUnit timeUnit) {
        String key = prefix + id;
        String jsonObject = stringRedisTemplate.opsForValue().get(key);
        //键不存在，使用互斥锁避免缓存击穿
        if(jsonObject == null){
            return queryWithLock(prefix,id,clazz,function,expireTime,timeUnit);
        }
        //键存在但为空
        LocalDateTime time = JSONUtil.parseObj(jsonObject).get("expireTime",LocalDateTime.class);
        if(jsonObject.isEmpty() && !time.isAfter(LocalDateTime.now())) return null;
        //键存在且不为空
        if(time.isAfter(LocalDateTime.now())){
            return JSONUtil.parseObj(jsonObject).get("data",clazz);
        }

        boolean res = tryLock(key);
        if(res){
            executorService.submit(() -> {
                try {
                    R queryRes = function.apply(id);
                    if (queryRes == null) {
                        setLogicalExpire(key, "", expireTime, timeUnit);
                    }else{
                        setLogicalExpire(key, queryRes, expireTime, timeUnit);
                    }
                } finally {
                    unLock(key);
                }
            });
        }
        return JSONUtil.parseObj(jsonObject).get("data",clazz);
    }
    //缓存中不存在的解决方案
    private <R, ID> R queryWithLock(String prefix, ID id, Class<R> clazz, Function<ID,R> function, Integer expireTime, TimeUnit timeUnit) {
        String key = prefix + id;
        while(true){
            String jsonObject = stringRedisTemplate.opsForValue().get(key);
            if(jsonObject != null){
                if(jsonObject.isEmpty()) return null;
                return JSONUtil.parseObj(jsonObject).get("data",clazz);
            }
            boolean res = tryLock(key);
            //双重检查,获取锁之后重新看看缓存是不是已经更新了
            if(res){
                try {
                    String object = stringRedisTemplate.opsForValue().get(key);
                    if(object != null){
                        if(object.isEmpty()) return null;
                        return JSONUtil.parseObj(object).get("data",clazz);
                    }
                    R queryRes = function.apply(id);
                    if (queryRes == null) {
                        setLogicalExpire(key, "", expireTime, timeUnit);
                    }else{
                        setLogicalExpire(key, queryRes, expireTime, timeUnit);
                    }
                } finally {
                    unLock(key);
                }
            }else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }

    private boolean tryLock(String key){
        Boolean res = stringRedisTemplate.opsForValue().setIfAbsent("lock:"+key,"1",1,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(res);
    }

    private void unLock(String key){
        stringRedisTemplate.delete("lock:"+key);
    }


}
