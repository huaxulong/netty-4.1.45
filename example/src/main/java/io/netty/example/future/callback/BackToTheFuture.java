package io.netty.example.future.callback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-25 9:01 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class BackToTheFuture {

    public static void main(String[] args) throws InterruptedException {
        List<FutureTask> taskList = new ArrayList<FutureTask>();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        long startMillis = System.currentTimeMillis();
        for (int i = 0; i < 20; ++i) {
            Callable callable = new PossibleMission(i);
            FutureWork futureTask = new FutureWork(callable);
            FutureCallBack callback = new FutureCallBack(futureTask) {
                @Override
                public void call() {
                    System.out.println((String) this.getReturnedObject());
                }
            };
            futureTask.setCallback(callback);
            taskList.add(futureTask);
            executor.execute(futureTask);
        }
        boolean done = false;
        while (!done) {
            for (FutureTask task : taskList) {
                if (!(done = task.isDone())) {
                    continue;
                }
            }
            Thread.sleep(1000);
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.MINUTES);
        long endMillis = System.currentTimeMillis();
        System.out.printf("Elapsed time: %d ms\n", (endMillis - startMillis));
    }

}
