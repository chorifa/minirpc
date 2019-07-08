package minirpc.utils.serialize;

import com.alibaba.fastjson.JSON;
import minirpc.utils.RPCException;

public class FastJsonSerializer implements Serializer {

	@Override
	public <T> byte[] serialize(T obj) {
		if(obj == null)
			throw new RPCException("FastJson serialize obj is null...");

		return JSON.toJSONBytes(obj);
	}

	@Override
	public <T> T deserialize(byte[] data, Class<T> clazz) {
		if(data == null || data.length == 0)
			throw new RPCException("FastJson deserialize byte[] is null or empty...");
		if(clazz == null)
			throw new RPCException("Class type is null...");
		return JSON.parseObject(data,clazz);
	}

}
