package com.hmdp.service.impl;

import cn.hutool.json.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheUtils cacheUtils;


    public Result queryById(Long id){
        Shop shop = cacheUtils.queryWithBreakDown("cache:shop:",id,Shop.class,this::getById,30,TimeUnit.MINUTES);
        //Shop shop = cacheUtils.queryWithPassThrough("cache:shop:",id,Shop.class,this::getById,30,TimeUnit.MINUTES);
        if(shop == null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }
}
