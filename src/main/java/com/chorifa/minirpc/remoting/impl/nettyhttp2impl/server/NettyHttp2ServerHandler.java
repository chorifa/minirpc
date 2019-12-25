package com.chorifa.minirpc.remoting.impl.nettyhttp2impl.server;

import com.chorifa.minirpc.provider.DefaultRPCProviderFactory;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.threads.EventBus;
import com.chorifa.minirpc.threads.ThreadManager;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.serialize.SerialType;
import com.chorifa.minirpc.utils.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

@ChannelHandler.Sharable
public class NettyHttp2ServerHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(NettyHttp2ServerHandler.class);

    // any change to origin String/bytes will NOT affect copiedBuffer
    // changes in origin String/bytes can be visible in wrappedBuffer
    private static ByteBuf WARN_WORDS = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("WARNING: Get Method NOT Supported!", StandardCharsets.UTF_8));

    private final DefaultRPCProviderFactory providerFactory;

    NettyHttp2ServerHandler(DefaultRPCProviderFactory factory) {
        this.providerFactory = factory;
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
        logger.debug("using http2 handler... read request and send response done.");
    }

    private void onHeaderRead(ChannelHandlerContext ctx, Http2HeadersFrame headers){
        // actually not support
        if(headers.isEndStream()){ // get request
            logger.debug("Netty Http2 server receive a get request...");
            // return a warn
            ByteBuf content = ctx.alloc().buffer();
            content.writeBytes(WARN_WORDS.duplicate());
            ByteBufUtil.writeAscii(content, " - via HTTP/2");

            sendResponse(ctx,content);
        }
    }

    private void onDataRead(ChannelHandlerContext ctx, Http2DataFrame data) {
        if(data.isEndStream()){
            logger.debug("Netty Http2 server receive a post request...");
            ByteBuf byteBuf = data.content();
            if(byteBuf == null || byteBuf.readableBytes() <= 4)
                throw new RPCException("NettyHttp2Server decode data is null...");

            final int magic = byteBuf.readInt();
            final Serializer serializer = SerialType.getSerializerByMagic(magic);

            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);

            RemotingRequest remotingRequest = serializer.deserialize(bytes, RemotingRequest.class);
            try{
                Runnable task = ()->{
                    RemotingResponse response = providerFactory.invokeService(remotingRequest); // will not throw exception and always return response
//                    logger.info("Service: {} run on thread name: {}",remotingRequest.getInterfaceName(),Thread.currentThread().getName());
                    byte[] rpsBytes = serializer.serialize(response); // may have runtime exception
                    ByteBuf payload = Unpooled.buffer(rpsBytes.length +4);
                    payload.writeInt(magic);
                    payload.writeBytes(rpsBytes);

                    sendResponse(ctx, payload);
                };
                String key = providerFactory.generateKey(remotingRequest.getInterfaceName(), remotingRequest.getVersion());
                if(!providerFactory.getEventBus().publish(key, task)) { // fail
                    if(providerFactory.isBlocking(key) || remotingRequest.isBlocking()) {
                        if(!ThreadManager.tryPublishEvent(ctx.channel().eventLoop(), task))
                            throw new RPCException("Service Provider busy (RingQueue is full, cannot publish new task)");
                    }else task.run();
                }
//                if(providerFactory.isBlocking(remotingRequest.getInterfaceName(), remotingRequest.getVersion())
//                        || remotingRequest.isBlocking()) {
//                    if(!ThreadManager.tryPublishEvent(ctx.channel().eventLoop(), ()->{
//                        RemotingResponse response = providerFactory.invokeService(remotingRequest); // will not throw exception and always return response
//                        byte[] rpsBytes = serializer.serialize(response); // may have runtime exception
//                        ByteBuf payload = Unpooled.buffer(rpsBytes.length +4);
//                        payload.writeInt(magic);
//                        payload.writeBytes(rpsBytes);
//
//                        sendResponse(ctx, payload);
//                    })) throw new RPCException("Service Provider busy (cannot publish new task)");
//                }else {
//                    RemotingResponse response = providerFactory.invokeService(remotingRequest); // will not throw exception and always return response
//                    byte[] rpsBytes = serializer.serialize(response); // may have runtime exception
//                    ByteBuf payload = Unpooled.buffer(rpsBytes.length +4);
//                    payload.writeInt(magic);
//                    payload.writeBytes(rpsBytes);
//
//                    sendResponse(ctx, payload);
//                }
            }catch (Throwable e){ // ExecutorService exception : rejection
                logger.error("Netty Http Server encounter one error when handling the request...",e);
                RemotingResponse response = new RemotingResponse();
                response.setRequestId(remotingRequest.getRequestId());
                response.setErrorMsg(e.getMessage());

                byte[] rpsBytes = serializer.serialize(response); // may have runtime exception
                ByteBuf payload = Unpooled.buffer(rpsBytes.length +4);
                payload.writeInt(magic);
                payload.writeBytes(rpsBytes);

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
