package com.chorifa.minirpc.remoting.impl.nettyhttpimpl.server;

import com.chorifa.minirpc.threads.ThreadManager;
import com.chorifa.minirpc.utils.serialize.SerialType;
import com.chorifa.minirpc.utils.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import com.chorifa.minirpc.provider.DefaultRPCProviderFactory;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.utils.RPCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttpServerHandler.class);

    private DefaultRPCProviderFactory providerFactory;

    NettyHttpServerHandler(DefaultRPCProviderFactory providerFactory){
        this.providerFactory = providerFactory;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest request) throws Exception {
        logger.debug("Netty Http server receive a request...");
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
            Runnable task = ()->{
//                logger.info("Service: {} run on thread name: {}",remotingRequest.getInterfaceName(),Thread.currentThread().getName());
                RemotingResponse response = providerFactory.invokeService(remotingRequest);
                byte[] rep = serializer.serialize(response);
                FullHttpResponse httpResponse = generateResponse(rep, isKeepAlive, magic);
                channelHandlerContext.writeAndFlush(httpResponse);
            };
            String key = providerFactory.generateKey(remotingRequest.getInterfaceName(), remotingRequest.getVersion());
            if(!providerFactory.getEventBus().publish(key, task)) {
                if(providerFactory.isBlocking(key) || remotingRequest.isBlocking()) {
                    if(!ThreadManager.tryPublishEvent(channelHandlerContext.channel().eventLoop(), task))
                        throw new RPCException("Service Provider busy (RingQueue is full, cannot publish new task)");
                }else task.run();
            }
//            if(providerFactory.isBlocking(remotingRequest.getInterfaceName(), remotingRequest.getVersion())
//                    || remotingRequest.isBlocking()) {
//                if(!ThreadManager.tryPublishEvent(channelHandlerContext.channel().eventLoop(), ()->{
//                    RemotingResponse response = providerFactory.invokeService(remotingRequest);
//                    byte[] rep = serializer.serialize(response);
//                    FullHttpResponse httpResponse = generateResponse(rep, isKeepAlive, magic);
//                    channelHandlerContext.writeAndFlush(httpResponse);
//                })) throw new RPCException("Service Provider busy (cannot publish new task)");
//            }else {
//                RemotingResponse response = providerFactory.invokeService(remotingRequest);
//                byte[] rep = serializer.serialize(response);
//                FullHttpResponse httpResponse = generateResponse(rep, isKeepAlive, magic);
//                channelHandlerContext.writeAndFlush(httpResponse);
//            }
        }catch (Throwable e){
            logger.error("Netty Http Server encounter one error when handling the request...", e);
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

        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, byteBuf);
        httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");
        if(isKeepAlive)
            httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

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
