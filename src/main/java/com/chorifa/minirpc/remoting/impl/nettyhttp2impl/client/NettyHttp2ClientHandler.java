package com.chorifa.minirpc.remoting.impl.nettyhttp2impl.client;

import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.serialize.SerialType;
import com.chorifa.minirpc.utils.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyHttp2ClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttp2ClientHandler.class);

    private final DefaultRPCInvokerFactory invokerFactory;

    NettyHttp2ClientHandler(DefaultRPCInvokerFactory factory){
        this.invokerFactory = factory;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if(streamId == null){
            logger.error("HttpResponseHandler unexpected message received: " + msg);
            return;
        }
        ByteBuf byteBuf = msg.content();
        if(byteBuf == null || byteBuf.readableBytes() <= 4)
            throw new RPCException("NettyHttp2Client: decode data is null...");

        int magic = byteBuf.readInt();
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        final Serializer serializer = SerialType.getSerializerByMagic(magic);

        RemotingResponse response = serializer.deserialize(data, RemotingResponse.class);
        invokerFactory.injectResponse(ctx.channel().eventLoop(), String.valueOf(streamId), response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("an error occur when handler response in client : ", cause);
        ctx.close();
    }

}
