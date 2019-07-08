package minirpc.remoting.impl.nettyimpl.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import minirpc.provider.DefaultRPCProviderFactory;
import minirpc.remoting.entity.RemotingRequest;
import minirpc.remoting.entity.RemotingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

class NettyServerHandler extends SimpleChannelInboundHandler<RemotingRequest> {

    private static Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);

    private DefaultRPCProviderFactory providerFactory;

    private ExecutorService executorService;

    NettyServerHandler(DefaultRPCProviderFactory factory, ExecutorService service){
        this.providerFactory = factory;
        this.executorService = service;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RemotingRequest request){
        logger.info("Netty server receive a request...");
        //provider.invoke >>> writeAndFlush(Response)
        try {
            executorService.execute(() -> {
                RemotingResponse response = providerFactory.invokeService(request);
                channelHandlerContext.writeAndFlush(response);
            });
        } catch (Throwable e) {
            logger.error("Netty server encounter one exception while handling one request...");
            RemotingResponse response = new RemotingResponse();
            response.setRequestId(request.getRequestId());
            response.setErrorMsg(e.getMessage());
            channelHandlerContext.writeAndFlush(response);
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
        logger.error("one error occur when handler request in server...",cause);
        ctx.close();
    }
}
