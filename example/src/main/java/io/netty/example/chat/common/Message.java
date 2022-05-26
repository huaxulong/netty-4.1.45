package io.netty.example.chat.common;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-23 1:52 下午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */

public class Message {

    private MessageHeader header;
    private byte[] body;

    public Message() {
    }

    public Message(MessageHeader header, byte[] body) {
        this.header = header;
        this.body = body;
    }

    public MessageHeader getHeader() {
        return header;
    }

    public void setHeader(MessageHeader header) {
        this.header = header;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}
