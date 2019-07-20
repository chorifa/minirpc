package minirpc.remoting.impl.nettyhttp2impl.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import minirpc.provider.DefaultRPCProviderFactory;
import minirpc.remoting.entity.RemotingRequest;
import minirpc.remoting.entity.RemotingResponse;
import minirpc.utils.RPCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

@ChannelHandler.Sharable
public class NettyHttp2ServerHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttp2ServerHandler.class);

    // any change to origin String/bytes will NOT affect copiedBuffer
    // changes in origin String/bytes can be visible in wrappedBuffer
    private static ByteBuf WARN_WORDS = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("WARNING: Get Method NOT Supported!", StandardCharsets.UTF_8));

    private final DefaultRPCProviderFactory factory;
    private final ExecutorService service;

    NettyHttp2ServerHandler(DefaultRPCProviderFactory factory, ExecutorService service) {
        this.factory = factory;
        this.service = service;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        logger.warn("NettyHttp2Server >> Http2ServerHandler: ",cause);
        ctx.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof Http2HeadersFrame){
            onHeaderRead(ctx, (Http2HeadersFrame) msg);
        }else if(msg instanceof Http2DataFrame){
            onDataRead(ctx, (Http2DataFrame) msg);
        }else{
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        logger.info("using http2 handler... read request and send response done.");
    }

    private void onHeaderRead(ChannelHandlerContext ctx, Http2HeadersFrame headers){
        // actually not support
        if(headers.isEndStream()){ // get request
            logger.info("Netty Http2 server receive a get request...");
            // return a warn
            ByteBuf content = ctx.alloc().buffer();
            content.writeBytes(WARN_WORDS.duplicate());
            ByteBufUtil.writeAscii(content, " - via HTTP/2");

            sendResponse(ctx,content);
        }
    }

    private void onDataRead(ChannelHandlerContext ctx, Http2DataFrame data) {
        if(data.isEndStream()){
            logger.info("Netty Http2 server receive a post request...");
            final byte[] bytes = ByteBufUtil.getBytes(data.content());
            if(bytes == null || bytes.length == 0)
                throw new RPCException("NettyHttp2Server decode data is null...");

            RemotingRequest remotingRequest = factory.getSerializer().deserialize(bytes,RemotingRequest.class);
            try{
                service.execute(()->{
                    RemotingResponse response = factory.invokeService(remotingRequest); // will not throw exception and always return response
                    byte[] rpsBytes = factory.getSerializer().serialize(response); // may have runtime exception
                    ByteBuf payload = Unpooled.wrappedBuffer(rpsBytes);

                    sendResponse(ctx,payload);
                });
            }catch (Throwable e){ // ExecutorService exception : rejection
                logger.error("Netty Http Server encounter one error when handling the request...",e);
                RemotingResponse response = new RemotingResponse();
                response.setRequestId(remotingRequest.getRequestId());
                response.setErrorMsg(e.getMessage());
                byte[] rpsBytes = factory.getSerializer().serialize(response); // may have runtime exception
                ByteBuf payload = Unpooled.wrappedBuffer(rpsBytes);

                sendResponse(ctx,payload);
            }

        }else data.release();
    }

    private void sendResponse(ChannelHandlerContext ctx, ByteBuf payload) {
        Http2Headers headers = new DefaultHttp2Headers().status(HttpResponseStatus.OK.codeAsText());
        ctx.write(new DefaultHttp2HeadersFrame(headers));
        ctx.write(new DefaultHttp2DataFrame(payload,true));
        ctx.flush();
    }

}
