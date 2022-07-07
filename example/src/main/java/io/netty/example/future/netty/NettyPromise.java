package io.netty.example.future.netty;

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultPromise;

import java.util.concurrent.ExecutionException;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-06-28 11:07 上午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class NettyPromise {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        EventLoop eventLoop = eventLoopGroup.next();

        DefaultPromise<Object> promise = new DefaultPromise<>(eventLoop);

        new Thread(() -> {
            System.out.println("开始计算");
            try {
                Thread.sleep(1000);
            } catch (Exception e){
                e.printStackTrace();
            }
            promise.setSuccess(100);
        }).start();

        System.out.println("waitting...");
        System.out.println("result: " + promise.get());
    }

}
