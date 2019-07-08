package minirpc.utils.serialize;

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import minirpc.utils.RPCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class HessianSerializer implements Serializer{

    @Override
    public <T> byte[] serialize(T obj) {
        if(obj == null)
            throw new RPCException("Hessian2 serialize obj is null...");

        Hessian2Output ho = null;
        try(ByteArrayOutputStream os = new ByteArrayOutputStream()){
            ho = new Hessian2Output(os);
            ho.writeObject(obj);        // obj (include its field) must implement serializable
            ho.flush();                 // Hessian2Output must flush >>> hessian2
            return os.toByteArray();
        }catch (Exception e){
            throw new RPCException("serialize with Hessian encounter one error...",e);
        }finally {
            try {
                if(ho != null)
                    ho.close();
            }catch (IOException e){
                throw new RPCException("Hessian2Output can not close...",e);
            }
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        if(data == null || data.length == 0)
            throw new RPCException("Hessian2 deserialize byte[] is null or empty...");
        if(clazz == null)
            throw new RPCException("Class type is null...");

        Hessian2Input hi = null;
        try(ByteArrayInputStream is = new ByteArrayInputStream(data)){
            hi = new Hessian2Input(is);
            @SuppressWarnings("unchecked")
            T obj = (T) hi.readObject(clazz);
            return obj;
        }catch (IOException e){
            throw new RPCException("deserialize with Hessian encounter one error...",e);
        }finally {
            try {
                if(hi != null)
                    hi.close();
            }catch (IOException e){
                throw new RPCException("Hessian2Output can not close...",e);
            }
        }
    }
}
