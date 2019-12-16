package com.chorifa.minirpc.remoting.impl.nettyhttp2impl.server;

import com.chorifa.minirpc.provider.DefaultRPCProviderFactory;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.threads.ThreadManager;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.serialize.SerialType;
import com.chorifa.minirpc.utils.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyHttp1ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttp1ServerHandler.class);

    private final DefaultRPCProviderFactory providerFactory;
    private final String establishApproach;

    NettyHttp1ServerHandler(DefaultRPCProviderFactory providerFactory, String establishApproach){
        this.providerFactory = providerFactory;
        this.establishApproach = establishApproach;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest request) throws Exception {
        if(HttpUtil.is100ContinueExpected(request)){
            channelHandlerContext.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        }

        logger.debug("Netty Http2 server >>(in http mode established via {})<< receive a request...", establishApproach);
        ByteBuf byteBuf = request.content();
        final String uri = request.uri();
        final boolean isKeepAlive = HttpUtil.isKeepAlive(request);

        // decode
        if(byteBuf == null || byteBuf.readableBytes() <= 4)
            throw new RPCException("NettyHttpServer decode data is null...");

        final int magic = byteBuf.readInt();
        final Serializer serializer = SerialType.getSerializerByMagic(magic);

        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);

        RemotingRequest remotingRequest = serializer.deserialize(data, RemotingRequest.class);
        try {
            ThreadManager.publishEvent(channelHandlerContext.channel().eventLoop(), ()->{
                RemotingResponse response = providerFactory.invokeService(remotingRequest);
                byte[] rep = serializer.serialize(response);
                FullHttpResponse httpResponse = generateResponse(rep, isKeepAlive, magic);
                channelHandlerContext.writeAndFlush(httpResponse);
            });
        }catch (Throwable e){
            logger.error("Netty Http Server encounter one error when handling the request...");
            RemotingResponse response = new RemotingResponse();
            response.setRequestId(remotingRequest.getRequestId());
            response.setErrorMsg(e.getMessage());
            byte[] rep = serializer.serialize(response);
            FullHttpResponse httpResponse = generateResponse(rep, isKeepAlive, magic);
            channelHandlerContext.writeAndFlush(httpResponse);
        }
    }

    private FullHttpResponse generateResponse(byte[] data, boolean isKeepAlive, int magic){
        ByteBuf byteBuf = Unpooled.buffer(data.length +4);
        byteBuf.writeInt(magic);
        byteBuf.writeBytes(data);

        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                byteBuf);
        httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH,httpResponse.content().readableBytes());
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE,"text/html; charset=UTF-8");
        if(isKeepAlive)
            httpResponse.headers().set(HttpHeaderNames.CONNECTION,HttpHeaderValues.KEEP_ALIVE);

        return httpResponse;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof IdleStateEvent){
            logger.info("a connect closed >>>---<<< IdleStateEvent");
            ctx.channel().close();
            // ctx.channel.close function in all pipeline handler
            // ctx.close function in current and next pipeline handler
        }
        else super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("one error occur when handler request in server...",cause);
        ctx.close();
    }
}
