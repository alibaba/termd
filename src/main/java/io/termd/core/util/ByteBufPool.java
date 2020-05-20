package io.termd.core.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的ByteBuf池，比netty的PooledByteBufAllocator更轻量，内存碎片少
 * @author gongdewei 2020/5/19
 */
public class ByteBufPool {

    private int byteBufCapacity;// = 128;

    //pool size: 128 * 1024 = 128K
    private int poolSize;// = 1024;
    private AtomicInteger allocSize = new AtomicInteger();
    private BlockingQueue<ByteBuf> byteBufPool;

    public ByteBufPool() {
        this(1024, 128);
    }

    public ByteBufPool(int poolSize, int byteBufCapacity) {
        this.byteBufCapacity = byteBufCapacity;
        this.poolSize = poolSize;
        byteBufPool = new ArrayBlockingQueue<ByteBuf>(poolSize);
    }

    public ByteBuf get() {
        ByteBuf byteBuf = null;
        if (allocSize.get() >= poolSize) {
            //wait if pool is full alloc
            try {
                byteBuf = byteBufPool.poll(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                //ignore
            }
        } else {
            byteBuf = byteBufPool.poll();
        }
        if (byteBuf == null) {
            byteBuf = Unpooled.buffer(byteBufCapacity);
            allocSize.incrementAndGet();
        }
        return byteBuf.retain();
    }

    public void put(ByteBuf byteBuf) {
        // give back
        byteBuf.clear();
        if(!(byteBuf.capacity() == byteBufCapacity && byteBufPool.offer(byteBuf))){
            //buf pool is full
            byteBuf.release();
        }
    }
}
