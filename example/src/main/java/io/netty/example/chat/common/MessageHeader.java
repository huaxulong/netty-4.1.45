package io.netty.example.chat.common;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-23 1:52 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class MessageHeader {

    private String sender;
    private String receiver;
    private MessageType type;
    private Long timestamp;

    public static MessageHeader builder(){
        return new MessageHeader();
    }

    public MessageHeader type(MessageType type){
        this.type = type;
        return this;
    }

    public MessageHeader sender(String sender){
        this.sender = sender;
        return this;
    }

    public MessageHeader receiver(String receiver){
        this.receiver = receiver;
        return this;
    }

    public MessageHeader timestamp(Long timestamp){
        this.timestamp = timestamp;
        return this;
    }

    public MessageHeader build(){
        return this;
    }


}
