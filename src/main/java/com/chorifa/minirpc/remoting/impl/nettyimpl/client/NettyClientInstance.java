package com.chorifa.minirpc.remoting.impl.nettyimpl.client;

import com.chorifa.minirpc.remoting.ClientInstance;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.remoting.impl.nettyimpl.codec.CodeCPair;
import com.chorifa.minirpc.remoting.impl.nettyimpl.codec.NettyDecoder;
import com.chorifa.minirpc.remoting.impl.nettyimpl.codec.NettyEncoder;
import com.chorifa.minirpc.threads.ThreadManager;
import com.chorifa.minirpc.utils.AddressUtil;
import com.chorifa.minirpc.utils.serialize.SerialType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class NettyClientInstance extends ClientInstance {

    private static Logger logger = LoggerFactory.getLogger(NettyClientInstance.class);

    private Channel channel;
    //private EventLoopGroup group;

    @Override
    protected void init(String address) throws Exception {
        Object[] objs = AddressUtil.parseAddress(address);
        String ip = (String)objs[0];
        int port = (int)objs[1];
        objs = null;

        Bootstrap bs = new Bootstrap();
        bs.group(ThreadManager.workGroup).channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY,true)
                .option(ChannelOption.SO_KEEPALIVE,true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,10000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline()
                                .addLast(new IdleStateHandler(0,0,10, TimeUnit.MINUTES))
                                .addLast(new NettyDecoder(RemotingResponse.class))
                                .addLast(new NettyEncoder())
                                .addLast(new NettyClientHandler(invokerFactory));
                    }
                });
        this.channel = bs.connect(ip,port).sync().channel();

        if(!isValid()){
            close();
            return;
        }

        logger.info("client --->>> server:{}  connect done.", address);
    }


    @Override
    protected boolean isValid() {
        return this.channel != null && this.channel.isActive();
    }

    @Override
    protected void close() {
        if(this.channel != null) this.channel.close();
        // cannot shot down group
        //group.shutdownGracefully();
        logger.info("client --->>> server  close.");
    }

    @Override
    protected void send(RemotingRequest request, SerialType serialType) throws Exception{
        channel.writeAndFlush(new CodeCPair(serialType, request));
        logger.debug("client --->>> server  send request: {}.", request.toString());
    }
}
