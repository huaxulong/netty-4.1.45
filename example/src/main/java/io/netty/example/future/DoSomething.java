package io.netty.example.future;

import java.util.PrimitiveIterator;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-25 6:27 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class DoSomething implements Callable<ThreadBO>{

    private String cardNo;

    private String masterCode;

    public DoSomething(String cardNo, String masterCode){
        this.cardNo = cardNo;
        this.masterCode = masterCode;
    }


    @Override
    public ThreadBO call() throws Exception {


        System.out.println("Im do something now!");
        TimeUnit.SECONDS.sleep(10);
        ThreadBO threadBO = new ThreadBO();
        threadBO.setThreadName(Thread.currentThread().getName());
        threadBO.setThreadGroup(Thread.currentThread().getThreadGroup());
        return threadBO;
    }

}
