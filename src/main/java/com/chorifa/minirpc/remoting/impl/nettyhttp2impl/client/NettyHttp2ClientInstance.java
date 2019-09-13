package com.chorifa.minirpc.remoting.impl.nettyhttp2impl.client;

import com.chorifa.minirpc.remoting.ClientInstance;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.utils.AddressUtil;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.RuntimeUtil;
import com.chorifa.minirpc.utils.serialize.Serializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class NettyHttp2ClientInstance extends ClientInstance {

    private static Logger logger = LoggerFactory.getLogger(NettyHttp2ClientInstance.class);

    private static final boolean useSSL = OpenSsl.isAlpnSupported() | RuntimeUtil.JDK_VERSION >= 9;

    private Channel channel;
    private EventLoopGroup group;
    private Serializer serializer;
    private String address;
    private String host;

    @Override
    protected void init(String address, Serializer serializer) throws Exception {
        this.address = address;
        Object[] objs = AddressUtil.parseAddress(address);
        this.host = (String)objs[0];
        int port = (int)objs[1];

        final SslContext sslCtx;
        if(useSSL){
            SslProvider provider = OpenSsl.isAlpnSupported()? SslProvider.OPENSSL : SslProvider.JDK;
            sslCtx = SslContextBuilder.forClient()
                    .sslProvider(provider)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2,
                            ApplicationProtocolNames.HTTP_1_1))
                    .build();
        }else sslCtx = null;

        group = new NioEventLoopGroup();
        NettyHttp2ClientInitializer initializer = new NettyHttp2ClientInitializer(sslCtx,Integer.MAX_VALUE,invokerFactory,serializer);

        Bootstrap bs = new Bootstrap();
        bs.group(group);
        bs.channel(NioSocketChannel.class);
        bs.option(ChannelOption.SO_KEEPALIVE,true);
        bs.remoteAddress(host,port);
        bs.handler(initializer);

        this.channel = bs.connect().sync().channel();

        if(!isValid()){
            close();
            return;
        }

        this.serializer = serializer;
        logger.info("client --->>> server:{}  connect done.", address);

        try {
            Http2SettingsHandler http2SettingsHandler = initializer.settingsHandler();
            http2SettingsHandler.awaitSettings(5, TimeUnit.SECONDS);
        }catch (Exception e){
            close(); // upgrade failed
            throw e;
        }

        logger.info("client --->>> server:{}  upgrade to http2 done.", address);

    }

    @Override
    protected boolean isValid() {
        return this.channel != null && this.channel.isActive();
    }

    @Override
    protected void close() {
        if(this.channel != null)
            this.channel.close(); // note that: "close" will actively close the channel;
            // while, channel.closeFuture().sync() will start a sub-thread listener wait for channel is shutdown.
        group.shutdownGracefully();

        logger.info("client --->>> server  close channel.");
    }

    @Override
    protected void send(RemotingRequest request) throws Exception {
        if(request == null)
            throw new RPCException("NettyHttp2Clint send null request...");
        // actually, stream id has nothing to do with request id. here is for code compatibility
        int streamId = Integer.parseInt(request.getRequestId());
        byte[] data = serializer.serialize(request);
        HttpScheme scheme = useSSL ? HttpScheme.HTTPS : HttpScheme.HTTP;
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/post",
                Unpooled.wrappedBuffer(data));
        req.headers().add(HttpHeaderNames.HOST, address);
        req.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.name());
        req.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        req.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);
        req.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);

        channel.writeAndFlush(req);
        logger.info("client --->>> server  send request: {}.",request.toString());
    }
}
