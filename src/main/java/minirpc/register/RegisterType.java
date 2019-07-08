package minirpc.register;

import minirpc.register.impl.ZookeeperRegister;

public enum RegisterType {

    ZOOKEEPER(ZookeeperRegister.class);

    private Class<? extends RegisterService> registerClass;

    RegisterType(Class<? extends RegisterService> clazz){
        this.registerClass = clazz;
    }

    public Class<? extends RegisterService> getRegisterClass(){
        return registerClass;
    }

}
