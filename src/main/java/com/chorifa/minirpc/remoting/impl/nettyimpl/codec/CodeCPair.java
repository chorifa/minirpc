package com.chorifa.minirpc.remoting.impl.nettyimpl.codec;

import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.serialize.SerialType;

public class CodeCPair {

    private final SerialType serialType;
    private Object object;

    // for encode (send)
    public CodeCPair(SerialType serialType, Object object) {
        this.serialType = serialType;
        this.object = object;
    }

    public int getMagic() {
        return serialType.getMagic();
    }

    byte[] encodedData() {
        return serialType.getSerializer().serialize(object);
    }

    public void setObject(Object o) {
        this.object = o;
    }

    @SuppressWarnings("unchecked")
    public <C> C get(Class<C> cClass) {
        if(cClass.isInstance(object)) return (C) object;
        throw new RPCException("CodeCPair: cannot transfer object to given class "+cClass.getCanonicalName());
    }

    // for decode (receive)
    static CodeCPair generatePair(int magic, byte[] data, Class<?> clazz) {
        SerialType serialType = SerialType.getSerialTypeByMagic(magic);
        Object o = serialType.getSerializer().deserialize(data, clazz);
        return new CodeCPair(serialType, o);
    }
}
