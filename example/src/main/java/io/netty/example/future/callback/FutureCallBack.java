package io.netty.example.future.callback;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-25 9:02 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public abstract class FutureCallBack {

    private FutureTask futureTask;

    public FutureCallBack(FutureTask futureTask) {
        this.futureTask = futureTask;
    }

    public FutureTask getFutureTask() {
        return futureTask;
    }

    public void setFutureTask(FutureTask futureTask) {
        this.futureTask = futureTask;
    }

    public Object getReturnedObject() {
        try {
            return this.futureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return null;
    }

    public abstract void call();

}
