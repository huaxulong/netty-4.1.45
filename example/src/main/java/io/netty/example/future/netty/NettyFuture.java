package io.netty.example.future.netty;

import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-06-28 10:06 上午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class NettyFuture {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);

        EventLoop eventLoop = workerGroup.next();

        Future<Integer> future = eventLoop.submit(new Callable<Integer>() {

            @Override
            public Integer call() throws Exception {
                System.out.println("计算中");
                Thread.sleep(1000);
                return 100;
            }
        });

        // 同步
        /*System.out.println("同步等待结果");
        System.out.println(future.get());
        System.out.println("主线程执行");*/

        future.addListener(new GenericFutureListener<Future<? super Integer>>() {
            @Override
            public void operationComplete(Future<? super Integer> future) throws Exception {
                System.out.println("result:"+future.getNow());
            }
        });
        System.out.println("主线程执行完毕");
    }

    public void testFuture(){

    }

}
