package com.chorifa.minirpc.remoting.impl.nettyhttp2impl.server;

import com.chorifa.minirpc.provider.DefaultRPCProviderFactory;
import com.chorifa.minirpc.utils.RPCException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;

import java.util.concurrent.ExecutorService;

public class Http2OrHttpHandler extends ApplicationProtocolNegotiationHandler {

    private static final int MAX_CONTENT_LENGTH = 1024 * 100;

    private final DefaultRPCProviderFactory factory;
    private final ExecutorService service;

    Http2OrHttpHandler(DefaultRPCProviderFactory factory, ExecutorService service){
        super(ApplicationProtocolNames.HTTP_1_1);
        this.factory = factory;
        this.service = service;
    }

//    Http2OrHttpHandler(){
//        super(ApplicationProtocolNames.HTTP_1_1);
//        this.factory = null;
//    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
        switch (protocol){
            case ApplicationProtocolNames.HTTP_2:
                ctx.pipeline().addLast(Http2FrameCodecBuilder.forServer().build())
                        .addLast(new Http2MultiplexHandler(new NettyHttp2ServerHandler(factory,service)));
                break;
            case ApplicationProtocolNames.HTTP_1_1:
                ctx.pipeline().addLast(new HttpServerCodec(),
                        new HttpObjectAggregator(MAX_CONTENT_LENGTH),
                        new NettyHttp1ServerHandler(factory,service,"ALPN Negotiation"));
                break;
            default:
                throw new RPCException("NettyHttp2Server >> ALPN-Negotiation: unsupported protocol: " + protocol);
        }
    }
}
