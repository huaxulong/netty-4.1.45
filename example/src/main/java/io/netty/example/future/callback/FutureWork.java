package io.netty.example.future.callback;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-25 9:04 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class FutureWork extends FutureTask<Object> {

    private FutureCallBack callback;

    public FutureWork(Callable<Object> callable) {
        super(callable);
    }

    public FutureWork(Runnable runnable, Object result) {
        super(runnable, result);
    }

    public void setCallback(FutureCallBack callback) {
        this.callback = callback;
    }

    @Override
    protected void done() {
        if (callback != null) {
            callback.call();
        }
    }
}
