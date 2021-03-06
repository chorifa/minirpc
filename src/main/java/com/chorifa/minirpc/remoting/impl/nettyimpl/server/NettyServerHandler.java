package com.chorifa.minirpc.remoting.impl.nettyimpl.server;

import com.chorifa.minirpc.provider.DefaultRPCProviderFactory;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.remoting.impl.nettyimpl.codec.CodeCPair;
import com.chorifa.minirpc.threads.ThreadManager;
import com.chorifa.minirpc.utils.RPCException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NettyServerHandler extends SimpleChannelInboundHandler<CodeCPair> {

    private static Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);

    private DefaultRPCProviderFactory providerFactory;

    NettyServerHandler(DefaultRPCProviderFactory factory){
        this.providerFactory = factory;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, final CodeCPair pair) {
        logger.debug("Netty server receive a request...");
        RemotingRequest request = pair.get(RemotingRequest.class);

        //provider.invoke >>> writeAndFlush(Response)
        try {
            Runnable task = () -> {
//                logger.info("Service: {} run on thread name: {}",request.getInterfaceName(),Thread.currentThread().getName());
                RemotingResponse response = providerFactory.invokeService(request);
                pair.setObject(response);
                channelHandlerContext.writeAndFlush(pair);
            }; // task
            String key = providerFactory.generateKey(request.getInterfaceName(), request.getVersion());
            if(!providerFactory.getEventBus().publish(key, task)) { // service not bind to fix event-loop
                if(providerFactory.isBlocking(key) || request.isBlocking()) {
                    if(!ThreadManager.tryPublishEvent(channelHandlerContext.channel().eventLoop(), task))
                        throw new RPCException("Service Provider busy (RingQueue is full, cannot publish new task)");
                }else task.run();
            }
//            if(providerFactory.isBlocking(request.getInterfaceName(), request.getVersion()) || request.isBlocking()) {
//                if(!ThreadManager.tryPublishEvent(channelHandlerContext.channel().eventLoop(), () -> {
//                    RemotingResponse response = providerFactory.invokeService(request);
//                    pair.setObject(response);
//                    channelHandlerContext.writeAndFlush(pair);
//                })) throw new RPCException("Service Provider busy (cannot publish new task)");
//            }else {
//                RemotingResponse response = providerFactory.invokeService(request);
//                pair.setObject(response);
////                logger.info("start encoder");
////                byte[] rep = serializer.serialize(response);
////                logger.info("encoder done --->>> data.length = {}", rep.length);
////                ByteBuf buf = Unpooled.buffer(rep.length +4 +4);
////                buf.writeInt(magic);
////                buf.writeInt(rep.length);
////                buf.writeBytes(rep);
//                channelHandlerContext.writeAndFlush(pair);
//            }
        } catch (Throwable e) { // TO catch RuntimeException for publish may reject
            logger.error("Netty server encounter one exception while handling one request...", e);
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
