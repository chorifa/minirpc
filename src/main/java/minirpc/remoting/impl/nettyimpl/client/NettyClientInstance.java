package minirpc.remoting.impl.nettyimpl.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import minirpc.invoker.DefaultRPCInvokerFactory;
import minirpc.remoting.ClientInstance;
import minirpc.remoting.entity.RemotingRequest;
import minirpc.remoting.entity.RemotingResponse;
import minirpc.remoting.impl.nettyimpl.codec.NettyDecoder;
import minirpc.remoting.impl.nettyimpl.codec.NettyEncoder;
import minirpc.utils.AddressUtil;
import minirpc.utils.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class NettyClientInstance extends ClientInstance {

    private static Logger logger = LoggerFactory.getLogger(NettyClientInstance.class);

    private Channel channel;
    private EventLoopGroup group;

    @Override
    protected void init(String address, Serializer serializer) throws Exception {
        group = new NioEventLoopGroup();
        Object[] objs = AddressUtil.parseAddress(address);
        String ip = (String)objs[0];
        int port = (int)objs[1];
        objs = null;

        Bootstrap bs = new Bootstrap();
        bs.group(group).channel(NioSocketChannel.class)
                       .option(ChannelOption.TCP_NODELAY,true)
                       .option(ChannelOption.SO_KEEPALIVE,true)
                       .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,10000)
                       .handler(new ChannelInitializer<SocketChannel>() {
                           @Override
                           protected void initChannel(SocketChannel socketChannel) throws Exception {
                               socketChannel.pipeline()
                                       .addLast(new IdleStateHandler(0,0,10, TimeUnit.MINUTES))
                                       .addLast(new NettyDecoder(RemotingResponse.class, serializer))
                                       .addLast(new NettyEncoder(RemotingRequest.class,serializer))
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
        group.shutdownGracefully();

        logger.info("client --->>> server  close channel.");
    }

    @Override
    protected void send(RemotingRequest request) throws Exception{
        this.channel.writeAndFlush(request);

        logger.info("client --->>> server  send request: {}.",request.toString());
    }
}