package io.netty.example.chat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.example.chat.common.Message;
import io.netty.example.chat.common.MessageHeader;
import io.netty.example.chat.common.MessageType;
import io.netty.example.chat.common.util.ProtoStuffUtil;

import javax.swing.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-23 9:52 上午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
@ChannelHandler.Sharable
public class ChatClientServerHandler extends ChannelInboundHandlerAdapter {


    private final ByteBuf firstMessage;

    /**
     * Creates a client-side handler.
     */
    public ChatClientServerHandler() {
        firstMessage = Unpooled.buffer(ChatClientServer.SIZE);
//        for (int i = 0; i < firstMessage.capacity(); i ++) {
//            firstMessage.writeByte((byte) i);
//        }
        firstMessage.writeBytes("user1".getBytes());
    }

    private boolean isLogin = false;


    private Charset charset = StandardCharsets.UTF_8;


    private String userName = "";

    public void send(String content) {
        if (!isLogin) {
            JOptionPane.showMessageDialog(null, "尚未登录");
            return;
        }
        Message message;
        try {
            if (content.startsWith("@")) {
                String[] slices = content.split(":");
                String receiver = slices[0].substring(1);
                MessageHeader messageHeader = MessageHeader.builder()
                        .type(MessageType.NORMAL)
                        .sender(userName)
                        .receiver(receiver)
                        .timestamp(System.currentTimeMillis())
                        .build();
                message = new Message(messageHeader, slices[1].getBytes(charset));
            } else {
                // 广播模式
                message = new Message(
                        MessageHeader.builder()
                                .type(MessageType.BROADCAST)
                                .sender(userName)
                                .timestamp(System.currentTimeMillis())
                                .build(), content.getBytes(charset));

                System.out.println("message:" + message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelRegistered");
        super.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelUnregistered");
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelActive start");
        /*Scanner in = new Scanner(System.in);
        System.out.print("请输入需要登录的 userName : ");
        String username = in.next();
        System.out.print("请输入密码 : ");
        String password = in.next();

        Message message = new Message(
                MessageHeader.builder()
                        .type(MessageType.LOGIN)
                        .sender(username)
                        .timestamp(System.currentTimeMillis())
                        .build(), password.getBytes(charset));

        ctx.channel().writeAndFlush(ByteBuffer.wrap(ProtoStuffUtil.serialize(message)));*/
        String msg = "你好呀，，，，mm";
        byte[] bytes = msg.getBytes("utf-8");
        ByteBuf buffer = Unpooled.buffer(bytes.length);
        buffer.writeBytes(bytes);
        ctx.channel().writeAndFlush(buffer);//将信息写入管道发送给服务端

        //super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelInactive");
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("channelRead");
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelReadComplete");
        super.channelReadComplete(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        System.out.println("userEventTriggered");
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channelWritabilityChanged");
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("exceptionCaught");
        super.exceptionCaught(ctx, cause);
    }
}
