package com.chorifa.minirpc.remoting.impl.nettyimpl.client;

import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.remoting.impl.nettyimpl.codec.CodeCPair;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NettyClientHandler extends SimpleChannelInboundHandler<CodeCPair> {

    private static Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);

    private DefaultRPCInvokerFactory invokerFactory;

    NettyClientHandler(DefaultRPCInvokerFactory invokerFactory){
        this.invokerFactory = invokerFactory;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, CodeCPair pair) throws Exception {
        logger.debug("Netty Client receive response.");
        RemotingResponse remotingResponse = pair.get(RemotingResponse.class);
        // make response seen to invoker
        // InvokerManager set response to Future
        invokerFactory.injectResponse(remotingResponse.getRequestId(), remotingResponse);
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
