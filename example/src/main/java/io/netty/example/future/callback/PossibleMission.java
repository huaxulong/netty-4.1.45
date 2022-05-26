package io.netty.example.future.callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-25 8:59 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class PossibleMission implements Callable<Object> {

    private int threadNo;

    public PossibleMission(int threadNo) {
        super();
        this.threadNo = threadNo;
    }

    public int getThreadNo() {
        return threadNo;
    }

    public void setThreadNo(int threadNo) {
        this.threadNo = threadNo;
    }

    @Override
    public Object call() throws Exception {
        Thread.sleep(new Random().nextInt(20000));
        String hello = String.format("Hello from Marty McFly #%02d in 2015", this.threadNo);

        return hello;
    }

}
