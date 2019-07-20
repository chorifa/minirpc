package minirpc.remoting.impl.nettyhttp2impl.client;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil;
import minirpc.invoker.DefaultRPCInvokerFactory;
import minirpc.remoting.entity.RemotingResponse;
import minirpc.utils.RPCException;
import minirpc.utils.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyHttp2ClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttp2ClientHandler.class);

    private final DefaultRPCInvokerFactory invokerFactory;
    private final Serializer serializer;

    NettyHttp2ClientHandler(DefaultRPCInvokerFactory factory, Serializer serializer){
        this.invokerFactory = factory;
        this.serializer = serializer;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if(streamId == null){
            logger.error("HttpResponseHandler unexpected message received: " + msg);
            return;
        }
        byte[] data = ByteBufUtil.getBytes(msg.content());
        if(data == null || data.length == 0)
            throw new RPCException("NettyHttp2Client: decode data is null...");

        RemotingResponse response = serializer.deserialize(data, RemotingResponse.class);
        invokerFactory.injectResponse(String.valueOf(streamId),response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("an error occur when handler response in client : ", cause);
        ctx.close();
    }

}
