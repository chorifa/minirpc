package com.chorifa.minirpc.remoting.impl.nettyimpl.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyEncoder extends MessageToByteEncoder<CodeCPair> {
    private static Logger logger = LoggerFactory.getLogger(NettyEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, CodeCPair pair, ByteBuf byteBuf) throws Exception {
        logger.debug("start encode");
        byteBuf.writeInt(pair.getMagic());
        byte[] data = pair.encodedData();
        byteBuf.writeInt(data.length);
        byteBuf.writeBytes(data);
        logger.debug("encode done");
    }

}
