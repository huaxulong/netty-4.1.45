/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.echo;

import com.sun.xml.internal.ws.config.metro.util.ParserUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import sun.net.www.ParseUtil;

/**
 * Handler implementation for the echo server.
 * <p>
 * 服务端会响应客户端传入的消息，所以他需要实现 ChannelInBoundHandler 接口，用来定义响应入栈事件的方法。
 * <p>
 * 我们不需要实现所有方法的逻辑， 所以我们只需要继承 ChannelInboundHandlerAdapter 类即可。 他提供了 ChannelInboundHandle的默认实现
 * Sharable 表示一个 ChannelHandler 可以被多个Channel 安全的共享。
 */
@Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

    /**
     * 对于客户端发来的每个消息都会调用
     *
     * @param ctx
     * @param msg
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf in = (ByteBuf) msg;
        // 将消息记录到控制台
        System.out.println("Server receied " + in.toString(CharsetUtil.UTF_8));
        // 将接收到消息 写给发送者， 而不冲刷出站消息
        String str = "My name is dongxuHua";
        ctx.write(str);
//        ctx.write(strToByteBuf(in.toString(CharsetUtil.UTF_8) + " to "));
    }

    private static ByteBuf strToByteBuf(String con){
        if (!StringUtil.isNullOrEmpty(con)){
            ByteBuf byteBuf = Unpooled.buffer(EchoClient.SIZE);
            byteBuf.writeBytes(con.getBytes());
            return byteBuf;
        }
        return null;
    }

    /**
     * 通知 ChannelInboundHandler 最后一次对 channelRead 的调用是当前批量读取中的最后一条消息
     *
     * @param ctx
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    /**
     * 在读取操作期间， 有异常抛出会调用
     *
     * @param ctx
     * @param cause
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }
}
