package com.chorifa.minirpc.remoting.impl.nettyimpl.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NettyDecoder extends ByteToMessageDecoder {
    private static Logger logger = LoggerFactory.getLogger(NettyDecoder.class);

    private Class<?> clazz;

    public NettyDecoder(Class<?> clazz){
        this.clazz = clazz;
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        if(byteBuf.readableBytes() < 8) return;
        byteBuf.markReaderIndex(); //
        int magic = byteBuf.readInt();
        // TODO what if magic num is wrong
        int dataLength = byteBuf.readInt();
        if(dataLength < 0){
            channelHandlerContext.close();
            return;
        }
        if(byteBuf.readableBytes() < dataLength){
            byteBuf.resetReaderIndex();
            return;
        }

        logger.debug("Netty Decoder start decoding ...");

        byte[] data = new byte[dataLength];
        byteBuf.readBytes(data);
        CodeCPair pair = CodeCPair.generatePair(magic, data, clazz);

        list.add(pair);

        logger.debug("Netty Decoder decode succeed .");
    }

}
