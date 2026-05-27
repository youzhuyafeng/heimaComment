package com.hmdp;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


public class ConcurrencyTest {

    private final int concurrencyThreadsAmount = 10;
    private final ExecutorService es = Executors.newFixedThreadPool(concurrencyThreadsAmount);


    @Test(timeout = 60000)
    public void test() {
        int requestTime = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch countDownLatch = new CountDownLatch(300);
        AtomicInteger atomicInteger = new AtomicInteger(0);
        for (int i = 0; i < 300; i++) {
            es.submit(() -> {
                try {
                    startLatch.await();
                    for(int j=0;j<requestTime;j++){
                        atomicInteger.getAndIncrement();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }finally {
                    countDownLatch.countDown();
                }
            });
        }
        long begin =  System.currentTimeMillis();
        try {
            startLatch.countDown();
            Boolean res =  countDownLatch.await(60, TimeUnit.SECONDS);
            long end = System.currentTimeMillis();
            System.out.println("计数器:"+atomicInteger.intValue());
            System.out.println((end-begin)/1000+"s"+(end-begin)%1000+"ms");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
