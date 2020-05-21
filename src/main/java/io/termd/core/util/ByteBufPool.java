package io.termd.core.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的ByteBuf池，比netty的PooledByteBufAllocator更轻量，内存碎片少
 * <pre>
 *     //get buf
 *     final ByteBuf byteBuf = byteBufPool.get(50, TimeUnit.MILLISECONDS);
 *     boolean done = false;
 *     try {
 *       //write bytes
 *       byteBuf.writeBytes(buffer, start, size);
 *       if (context != null) {
 *         context.writeAndFlush(new TextWebSocketFrame(byteBuf)).addListener(new ChannelFutureListener() {
 *           public void operationComplete(ChannelFuture future) throws Exception {
 *           	//give back
 *             byteBufPool.put(byteBuf);
 *           }
 *         });
 *         done = true;
 *       }
 *     } finally {
 *       if (!done) {
 *         //discard
 *         byteBufPool.discard(byteBuf);
 *       }
 *     }
 *
 *     //onClose
 *     protected void onClose() {
 *       //...
 *       byteBufPool.release();
 *     }
 * </pre>
 * @author gongdewei 2020/5/19
 */
public class ByteBufPool {

    private int byteBufCapacity;// = 128;

    //pool size: 128 * 1024 = 128K
    private int poolSize;// = 1024;
    private AtomicInteger allocSize = new AtomicInteger();
    private BlockingQueue<ByteBuf> byteBufPool;
    private AtomicBoolean closed = new AtomicBoolean(false);

    public ByteBufPool() {
        this(1024, 128);
    }

    public ByteBufPool(int poolSize, int byteBufCapacity) {
        this.byteBufCapacity = byteBufCapacity;
        this.poolSize = poolSize;
        byteBufPool = new ArrayBlockingQueue<ByteBuf>(poolSize);
    }

    /**
     * Apply a fixed capacity ByteBuf
     * @return
     */
    public ByteBuf get(long timeout, TimeUnit unit) {
        if (closed.get()) {
            throw new IllegalStateException("ByteBufPool is closed");
        }
        ByteBuf byteBuf = byteBufPool.poll();
        if (byteBuf == null && allocSize.get() >= poolSize) {
            //wait if pool is full alloc
            try {
                byteBuf = byteBufPool.poll(timeout, unit);
            } catch (InterruptedException e) {
                //ignore
            }
        }
        if (byteBuf == null) {
            //heap buffer
            byteBuf = Unpooled.buffer(byteBufCapacity);
            allocSize.incrementAndGet();
        }
        return byteBuf.retain();
    }

    /**
     * Give back a ByteBuf
     * @param byteBuf
     */
    public void put(ByteBuf byteBuf) {
        // give back
        byteBuf.clear();
        if (byteBuf.capacity() != byteBufCapacity || !byteBufPool.offer(byteBuf)) {
            //buf pool is full or capacity not match
            byteBuf.release();
            allocSize.decrementAndGet();
        }
    }

    /**
     * Discard a ByteBuf, call it when there is a problem/exception and you don’t know how to recycle it safely.
     * @param byteBuf
     */
    public void discard(ByteBuf byteBuf) {
        //just release refCnt
        byteBuf.release();
        allocSize.decrementAndGet();
    }

    /**
     * Release all cached ByteBuf, call it when shutdown/stop service
     */
    public void release() {
        closed.set(true);
        while (!byteBufPool.isEmpty()) {
            ByteBuf byteBuf = byteBufPool.poll();
            byteBuf.release();
        }
    }
}
