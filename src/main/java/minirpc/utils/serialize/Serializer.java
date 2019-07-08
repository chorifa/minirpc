package minirpc.utils.serialize;

public interface Serializer {

    public <T> byte[] serialize(T obj);

    public <T> T deserialize(byte[] data, Class<T> clazz);
    
}
