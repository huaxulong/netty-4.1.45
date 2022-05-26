package io.netty.example.future;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-26 5:25 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class ThreadBO {

    private String threadName;

    private ThreadGroup threadGroup;

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    public void setThreadGroup(ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
    }

    @Override
    public String toString() {
        return "ThreadBO{" +
                "threadName='" + threadName + '\'' +
                ", threadGroup=" + threadGroup +
                '}';
    }
}
