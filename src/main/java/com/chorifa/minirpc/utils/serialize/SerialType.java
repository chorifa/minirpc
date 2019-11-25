package com.chorifa.minirpc.utils.serialize;

import com.chorifa.minirpc.utils.RPCException;

public enum SerialType {
    FASTJSON(new FastJsonSerializer()),
    JACKSON(new JacksonSerializer()),
    HESSIAN(new HessianSerializer()),
    PROTOSTUFF(new ProtostuffSerializer());

    private Serializer serializer;

    SerialType(Serializer serializer){
        this.serializer = serializer;
    }

    public static Serializer getSerializerByMagic(int magic){
        int ordinal = (magic & 0x0000_00FF) -1;
        for(SerialType serialType : SerialType.values())
            if(serialType.ordinal() == ordinal)
                return serialType.serializer;
        throw new RPCException("Unknown Serial Type.");
    }

    public static SerialType getSerialTypeByMagic(int magic) {
        int ordinal = (magic & 0x0000_00FF) -1;
        for(SerialType serialType : SerialType.values())
            if(serialType.ordinal() == ordinal)
                return serialType;
        throw new RPCException("Unknown Serial Type.");
    }

    public int getMagic() {
        return 0xCAFFEE00 ^ (ordinal() +1);
    }

    public Serializer getSerializer(){
        return serializer;
    }

}
