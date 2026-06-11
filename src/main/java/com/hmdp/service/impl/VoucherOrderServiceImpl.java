package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private IVoucherOrderService iVoucherOrderService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final BlockingQueue<VoucherOrder> blockingQueue = new ArrayBlockingQueue<VoucherOrder>(1024*1024);

    private final ExecutorService executorService =  Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        VoucherOrder voucherOrder = blockingQueue.take();
                        iVoucherOrderService.createVoucher(voucherOrder);
                    } catch (Exception e) {
                        log.error("处理订单请求时发生异常:{}",e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        });
    }



    private static final DefaultRedisScript<Long> redisScript;
    static{
        redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("seckill.lua"));
        redisScript.setResultType(Long.class);
    }
    public Result seckillVoucher(Long voucherId){
        String userId = UserHolder.getUser().getId().toString();
        Long result = stringRedisTemplate.execute(
                redisScript,
                Collections.emptyList(),
                voucherId.toString(),
                userId
        );
        if(result.intValue()!=0){
            return Result.fail(result==1?"来晚啦！库存已空！":"请勿重复下单！");
        }
        long orderId = redisIdWorker.generateId("order");
        //阻塞队列&消息队列异步处理
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        blockingQueue.add(voucherOrder);
        return Result.ok(orderId);
    }

    //乐观锁

/*    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher =  seckillVoucherService.getById(voucherId);
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("活动未开始！");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("活动已结束！");
        }
        if(voucher.getStock()<=0){
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
        SimpleRedisLock lock = new SimpleRedisLock("order:"+userId.toString(),stringRedisTemplate);
        boolean getLock = lock.tryLock(2);
        if(!getLock){
            return Result.fail("不允许重复下单！");
        }
        try{
            return iVoucherOrderService.createVoucher(voucher);
        }finally {
            lock.unlock();
        }
//        synchronized (userId.toString().intern()) {
//            //this.createVoucher(voucher);  事务失效！
//
//            //另一种方案，通过java AspectJ包提供的AopContext获取当前类的代理对象
//            //IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            //proxy.createVoucher(voucher);
//            return iVoucherOrderService.createVoucher(voucher);
//        }
    }*/
    //一人一单
    @Transactional
    public Result createVoucher(SeckillVoucher voucher) {
        Long userId = UserHolder.getUser().getId();
        Integer count = voucher.getStock();
        Long voucherId = voucher.getVoucherId();
        int orderCount = lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId)
                .count();
        if(orderCount > 0){
            return Result.fail("您已经下过单了！");
        }
        boolean res = seckillVoucherService.lambdaUpdate()
                .set(SeckillVoucher::getStock,count-1)
                .eq(SeckillVoucher::getVoucherId,voucherId)
                .gt(SeckillVoucher::getStock,0)
                .update();
        if(!res){
            return Result.fail("库存不足！");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        long voucherOrderId = redisIdWorker.generateId("voucherOrder:");
        voucherOrder.setId(voucherOrderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(voucherOrder);
    }

    public void createVoucher(VoucherOrder voucherOrder) {
        save(voucherOrder);
    }
}
