package minirpc.utils.serialize;

import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import minirpc.utils.RPCException;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProtostuffSerializer implements Serializer {

    private static Objenesis objenesis = new ObjenesisStd(true);

    private static Map<Class<?>, Schema<?>> cachedSchema = new ConcurrentHashMap<>();

    private static ThreadLocal<LinkedBuffer> bufferManeger = new ThreadLocal<>();

    private static <T> Schema<T> getSchema(Class<T> clazz){
        @SuppressWarnings("unchecked")
        Schema<T> schema = (Schema<T>) cachedSchema.get(clazz);
        if(schema == null){
            schema = RuntimeSchema.getSchema(clazz);
            if(schema != null)
                cachedSchema.putIfAbsent(clazz,schema);
        }
        return schema;
    }

    /**
     * 序列化
     * @param obj
     * @param <T>
     * @return
     */
    public <T> byte[] serialize(T obj){
        if(obj == null)
            throw new RPCException("Protostuff serialize obj is null...");

        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>)obj.getClass();

        LinkedBuffer buffer = bufferManeger.get();
        if(buffer == null){
            buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
            bufferManeger.set(buffer);
        }

        try{
            Schema<T> schema = getSchema(clazz);
            return ProtostuffIOUtil.toByteArray(obj,schema,buffer);
        }catch (Exception e){
            throw new IllegalStateException(e.getMessage(),e);
        }finally {
            buffer.clear();
        }
    }

    public <T> T deserialize(byte[] data, Class<T> clazz){
        if(data == null || data.length == 0)
            throw new RPCException("Protostuff deserialize byte[] is null or empty...");
        if(clazz == null)
            throw new RPCException("Class type is null...");

        Schema<T> schema = getSchema(clazz);
        // T obj = schema.newMessage();
        T obj = generateInstance(clazz);
        ProtostuffIOUtil.mergeFrom(data,obj,schema);
        return obj;
    }

    private <T> T generateInstance(Class<T> clazz){
        ObjectInstantiator<T> instantiator = objenesis.getInstantiatorOf(clazz);
        return (T)instantiator.newInstance();
    }

}
