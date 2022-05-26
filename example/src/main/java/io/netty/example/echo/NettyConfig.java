package io.netty.example.echo;

import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * @description: <p></p>
 * @author: DongxuHua
 * @create: at 2022-05-12 10:46 上午
 * @version: 1.0.0
 * @history: modify history             <desc>
 */
public class NettyConfig {

    public static ChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

}
