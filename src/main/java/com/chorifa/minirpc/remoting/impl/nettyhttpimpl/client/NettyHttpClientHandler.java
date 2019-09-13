package com.chorifa.minirpc.remoting.impl.nettyhttpimpl.client;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.timeout.IdleStateEvent;
import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyHttpClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttpClientHandler.class);

    private DefaultRPCInvokerFactory invokerFactory;
    private Serializer serializer;

    NettyHttpClientHandler(DefaultRPCInvokerFactory invokerFactory, Serializer serializer){
        this.invokerFactory = invokerFactory;
        this.serializer = serializer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpResponse fullHttpResponse) throws Exception {
        byte[] data = ByteBufUtil.getBytes(fullHttpResponse.content());
        if(data == null || data.length == 0)
            throw new RPCException("NettyHttp decode data is null...");

        RemotingResponse response = serializer.deserialize(data, RemotingResponse.class);

        invokerFactory.injectResponse(response.getRequestId(),response);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof IdleStateEvent){
            ctx.channel().close();
            logger.info("a connect closed >>>---<<< IdleStateEvent");
        }
        else super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("an error occur when handler response in client : ", cause);
        ctx.close();
    }
}
