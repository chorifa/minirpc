package com.chorifa.minirpc.remoting.impl.nettyimpl.server;

import com.chorifa.minirpc.provider.DefaultRPCProviderFactory;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.remoting.impl.nettyimpl.codec.CodeCPair;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

class NettyServerHandler extends SimpleChannelInboundHandler<CodeCPair> {

    private static Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);

    private DefaultRPCProviderFactory providerFactory;

    private ExecutorService executorService;

    NettyServerHandler(DefaultRPCProviderFactory factory, ExecutorService service){
        this.providerFactory = factory;
        this.executorService = service;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, final CodeCPair pair) {
        logger.debug("Netty server receive a request...");
        RemotingRequest request = pair.get(RemotingRequest.class);

        //provider.invoke >>> writeAndFlush(Response)
        try {
            executorService.execute(() -> {
                RemotingResponse response = providerFactory.invokeService(request);
                pair.setObject(response);
//                logger.info("start encoder");
//                byte[] rep = serializer.serialize(response);
//                logger.info("encoder done --->>> data.length = {}", rep.length);
//                ByteBuf buf = Unpooled.buffer(rep.length +4 +4);
//                buf.writeInt(magic);
//                buf.writeInt(rep.length);
//                buf.writeBytes(rep);
                channelHandlerContext.writeAndFlush(pair);
            });
        } catch (Throwable e) {
            logger.error("Netty server encounter one exception while handling one request...");
            RemotingResponse response = new RemotingResponse();
            response.setRequestId(request.getRequestId());
            response.setErrorMsg(e.getMessage());

            pair.setObject(response);
            channelHandlerContext.writeAndFlush(pair);
        }
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
        logger.error("one error occur when handler request in server...", cause);
        ctx.close();
    }
}
