package com.chorifa.minirpc.remoting.impl.nettyhttpimpl.server;

import io.netty.buffer.ByteBufUtil;
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

import java.util.concurrent.ExecutorService;

public class NettyHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttpServerHandler.class);

    private DefaultRPCProviderFactory providerFactory;
    private ExecutorService executorService;

    NettyHttpServerHandler(DefaultRPCProviderFactory providerFactory, ExecutorService executorService){
        this.providerFactory = providerFactory;
        this.executorService = executorService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest request) throws Exception {
        logger.debug("Netty Http server receive a request...");
        final byte[] data = ByteBufUtil.getBytes(request.content());
        final String uri = request.uri();
        final boolean isKeepAlive = HttpUtil.isKeepAlive(request);

        // decode
        if(data == null || data.length == 0)
            throw new RPCException("NettyHttpServer decode data is null...");

        RemotingRequest remotingRequest = providerFactory.getSerializer().deserialize(data,RemotingRequest.class);
        try {
            executorService.execute(()->{
                RemotingResponse response = providerFactory.invokeService(remotingRequest);
                FullHttpResponse httpResponse = generateResponse(response,isKeepAlive);
                channelHandlerContext.writeAndFlush(httpResponse);
            });
        }catch (Throwable e){
            logger.error("Netty Http Server encounter one error when handling the request...");
            RemotingResponse response = new RemotingResponse();
            response.setRequestId(remotingRequest.getRequestId());
            response.setErrorMsg(e.getMessage());
            FullHttpResponse httpResponse = generateResponse(response,isKeepAlive);
            channelHandlerContext.writeAndFlush(httpResponse);
        }
    }

    private FullHttpResponse generateResponse(RemotingResponse response, boolean isKeepAlive){
        byte[] data = providerFactory.getSerializer().serialize(response);

        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(data));
        httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH,httpResponse.content().readableBytes());
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE,"text/html;charset=UTF-8");
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
