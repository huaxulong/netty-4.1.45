package io.netty.example.future;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-25 6:16 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class FutureTaskDemo {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        int corePoolSize = 5;
        int maximumPoolSize = 10;
        long keepAliveTime = 60;
        TimeUnit unit = TimeUnit.SECONDS;
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(1000);
        ThreadFactory threadFactory = new DefaultThreadFactory("Future Task Test");
        RejectedExecutionHandler handler = new ThreadPoolExecutor.AbortPolicy();
        ExecutorService executorService = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);

        List<Future> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            DoSomething something = new DoSomething("12770001-12770005", "10991217");
            Future<ThreadBO> future = executorService.submit(something);
            list.add(future);
        }

        System.out.println("futureTask 开始获取");
        if (list != null && list.size() >0){
            for (Future future : list) {
                Object obj = future.get();
                if (obj instanceof ThreadBO){
                    ThreadBO threadBO = (ThreadBO)obj;
                    System.out.println(threadBO);
                }
            }
        }

        System.out.println("获取完毕， 准备关闭线程池");
        executorService.shutdown();
    }




}
