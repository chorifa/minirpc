package com.chorifa.minirpc.remoting.impl.nettyhttpimpl.client;

import com.chorifa.minirpc.remoting.ClientInstance;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.serialize.Serializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class NettyHttpClientInstance extends ClientInstance {
    private static Logger logger = LoggerFactory.getLogger(NettyHttpClientInstance.class);

    private Channel channel;
    // private EventLoopGroup group;
    private Serializer serializer;
    private String address;
    private String host;

    @Override
    protected void init(String address, final Serializer serializer) throws Exception {

        if (!address.toLowerCase().startsWith("http")) {
            address = "http://" + address;	// IP:PORT, need parse to url
        }

        this.address = address;
        URL url = new URL(address);
        this.host = url.getHost();
        int port = url.getPort();
        if(port <= 0) port = 80;

        Bootstrap bs = new Bootstrap();
        bs.group(ClientInstance.group).channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE,true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(new IdleStateHandler(0,0,10, TimeUnit.MINUTES))
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024*1024))
                                .addLast(new NettyHttpClientHandler(invokerFactory,serializer));
                    }
                });

        this.serializer = serializer;
        this.channel = bs.connect(host,port).sync().channel();

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
        // can only close channel. cannot shut down group
        // group.shutdownGracefully();

        logger.info("client --->>> server  close channel.");
    }

    @Override
    protected void send(RemotingRequest request) throws Exception {
        if(request == null)
            throw new RPCException("NettyHttpClint send null request...");

        byte[] data = serializer.serialize(request);

        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.POST,new URI(address).getRawPath(), Unpooled.wrappedBuffer(data));
        httpRequest.headers().set(HttpHeaderNames.HOST, host);
        httpRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        httpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpRequest.content().readableBytes());

        this.channel.writeAndFlush(httpRequest);
        logger.debug("client --->>> server  send request: {}.",request.toString());
    }
}
