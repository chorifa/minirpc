package com.chorifa.minirpc.remoting.impl.nettyhttpimpl.client;

import com.chorifa.minirpc.utils.serialize.SerialType;
import io.netty.buffer.ByteBuf;
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

    NettyHttpClientHandler(DefaultRPCInvokerFactory invokerFactory){
        this.invokerFactory = invokerFactory;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpResponse fullHttpResponse) throws Exception {
        ByteBuf byteBuf = fullHttpResponse.content();
        if(byteBuf == null || byteBuf.readableBytes() <= 4)
            throw new RPCException("NettyHttp decode data is null...");

        int magic = byteBuf.readInt();
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        final Serializer serializer = SerialType.getSerializerByMagic(magic);

        RemotingResponse response = serializer.deserialize(data, RemotingResponse.class);
        invokerFactory.injectResponse(channelHandlerContext.channel().eventLoop(), response.getRequestId(), response);
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
