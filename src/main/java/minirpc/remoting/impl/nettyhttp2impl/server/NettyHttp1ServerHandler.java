package minirpc.remoting.impl.nettyhttp2impl.server;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateEvent;
import minirpc.provider.DefaultRPCProviderFactory;
import minirpc.remoting.entity.RemotingRequest;
import minirpc.remoting.entity.RemotingResponse;
import minirpc.utils.RPCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class NettyHttp1ServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttp1ServerHandler.class);

    private final DefaultRPCProviderFactory providerFactory;
    private final ExecutorService executorService;
    private final String establishApproach;

    NettyHttp1ServerHandler(DefaultRPCProviderFactory providerFactory, ExecutorService executorService, String establishApproach){
        this.providerFactory = providerFactory;
        this.executorService = executorService;
        this.establishApproach = establishApproach;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, FullHttpRequest request) throws Exception {
        if(HttpUtil.is100ContinueExpected(request)){
            channelHandlerContext.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        }

        logger.info("Netty Http2 server >>(in http mode established via {})<< receive a request...", establishApproach);
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
