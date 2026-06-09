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
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private IVoucherOrderService iVoucherOrderService;

    //乐观锁

    public Result seckillVoucher(Long voucherId) {
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
        synchronized (userId.toString().intern()) {
            //this.createVoucher(voucher);  事务失效！

            //另一种方案，通过java AspectJ包提供的AopContext获取当前类的代理对象
            //IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            //proxy.createVoucher(voucher);
            return iVoucherOrderService.createVoucher(voucher);
        }
    }
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
}
