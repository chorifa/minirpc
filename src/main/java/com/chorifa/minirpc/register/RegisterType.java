package com.chorifa.minirpc.register;

import com.chorifa.minirpc.register.impl.RedisRegister;
import com.chorifa.minirpc.register.impl.ZookeeperRegister;

public enum RegisterType {
    REDIS(RedisRegister.class),
    ZOOKEEPER(ZookeeperRegister.class);

    private Class<? extends RegisterService> registerClass;

    RegisterType(Class<? extends RegisterService> clazz){
        this.registerClass = clazz;
    }

    public Class<? extends RegisterService> getRegisterClass(){
        return registerClass;
    }

}
