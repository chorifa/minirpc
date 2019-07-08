package minirpc.remoting.impl.nettyimpl.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import minirpc.utils.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NettyDecoder extends ByteToMessageDecoder {
    private static Logger logger = LoggerFactory.getLogger(NettyDecoder.class);

    private Class<?> clazz;
    private Serializer serializer;

    public NettyDecoder(Class<?> clazz, Serializer serializer){
        this.clazz = clazz;
        this.serializer = serializer;
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        if(byteBuf.readableBytes() < 4) return;
        byteBuf.markReaderIndex(); //
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
        Object obj = serializer.deserialize(data,clazz);

        if(obj != null) list.add(obj);

        logger.debug("Netty Decoder decode succeed .");
    }

}
