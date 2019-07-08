package minirpc.remoting.impl.nettyimpl.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import minirpc.utils.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyEncoder extends MessageToByteEncoder<Object> {
    private static Logger logger = LoggerFactory.getLogger(NettyDecoder.class);

    private Class<?> clazz;
    private Serializer serializer;

    public NettyEncoder(Class<?> clazz, Serializer serializer){
        this.clazz = clazz;
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Object o, ByteBuf byteBuf) throws Exception {
        if(clazz.isInstance(o)){
            logger.debug("start encoder");
            byte[] data = serializer.serialize(o);
            logger.debug("encoder done --->>> data.length = {}",data.length);
            byteBuf.writeInt(data.length);
            byteBuf.writeBytes(data);
        }
    }

}
