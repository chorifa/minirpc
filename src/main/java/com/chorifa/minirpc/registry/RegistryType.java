package com.chorifa.minirpc.registry;

import com.chorifa.minirpc.registry.impl.RedisRegistry;
import com.chorifa.minirpc.registry.impl.ZookeeperRegistry;

public enum RegistryType {
    REDIS(RedisRegistry.class),
    ZOOKEEPER(ZookeeperRegistry.class);

    private Class<? extends RegistryService> registerClass;

    RegistryType(Class<? extends RegistryService> clazz){
        this.registerClass = clazz;
    }

    public Class<? extends RegistryService> getRegisterClass(){
        return registerClass;
    }

}
