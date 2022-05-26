package io.netty.example.chat;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.example.chat.common.Message;
import io.netty.example.chat.common.MessageHeader;
import io.netty.example.chat.common.MessageType;
import io.netty.example.chat.common.util.ProtoStuffUtil;
import io.netty.util.CharsetUtil;

import java.nio.ByteBuffer;
import java.util.Scanner;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-23 9:49 上午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class ChatClientServer {

    static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));

    static final int PORT = Integer.parseInt(System.getProperty("port", "9001"));

    static final String HOST = System.getProperty("host", "127.0.0.1");

    private static boolean isLogin = false;

    public static void main(String[] args) throws Exception {

        ChatClientServerHandler chatClientServerHandler = new ChatClientServerHandler();
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            //p.addLast(new LoggingHandler(LogLevel.INFO));
                            p.addLast(chatClientServerHandler);
                        }
                    });

            ChannelFuture future = b.connect(HOST, PORT);
            future.sync();
            future.channel().closeFuture().sync();

            /*try {
                future.sync();
                System.out.println("连接服务器成功！");
                Scanner scanner = new Scanner(System.in);
                while (true){
                    if (!isLogin){
                        System.out.print("你还未登录，请输入用户名 : ");
                        String username = "user1";
                        System.out.print("你还未登录，请输入密码 : ");
                        String password = "pwd1";
                        Message message = new Message(
                                MessageHeader.builder()
                                        .type(MessageType.LOGIN)
                                        .sender(username)
                                        .timestamp(System.currentTimeMillis())
                                        .build(), password.getBytes(CharsetUtil.UTF_8));
                        Channel channel = future.channel();//获得连接通道
                        channel.writeAndFlush(ByteBuffer.wrap(ProtoStuffUtil.serialize(message)));
                        isLogin = true;
                    }else {
                        System.out.println("描述，单聊使用user@语言描述");
                        System.out.print("请输入对话内容 : ");
                        String msg = scanner.next();
                        Message message = new Message(
                                MessageHeader.builder()
                                        .type(MessageType.BROADCAST)
                                        .sender(null)
                                        .timestamp(System.currentTimeMillis())
                                        .build(), msg.getBytes(CharsetUtil.UTF_8));
                        Channel channel = future.channel();//获得连接通道
                        channel.writeAndFlush(ByteBuffer.wrap(ProtoStuffUtil.serialize(message)));
                    }
                }


            }catch (Exception e){
                e.printStackTrace();
            }*/


        } finally {
            // Shut down the event loop to terminate all threads.
            group.shutdownGracefully();
        }
    }

}
