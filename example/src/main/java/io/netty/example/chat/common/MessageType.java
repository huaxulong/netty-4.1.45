package io.netty.example.chat.common;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-23 1:53 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public enum MessageType {

    LOGIN(1,"登录"),
    LOGOUT(2,"注销"),
    NORMAL(3,"单聊"),
    BROADCAST(4,"群发");

    private int code;
    private String  desc;

    MessageType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
