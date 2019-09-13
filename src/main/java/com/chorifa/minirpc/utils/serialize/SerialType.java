package com.chorifa.minirpc.utils.serialize;

public enum SerialType {
    FASTJSON(new FastJsonSerializer()),
    JACKSON(new JacksonSerializer()),
    HESSIAN(new HessianSerializer()),
    PROTOSTUFF(new ProtostuffSerializer());

    private Serializer serializer;

    SerialType(Serializer serializer){
        this.serializer = serializer;
    }

    public Serializer getSerializer(){
        return serializer;
    }

}
