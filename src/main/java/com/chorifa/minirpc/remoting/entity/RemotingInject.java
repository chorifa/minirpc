package com.chorifa.minirpc.remoting.entity;

import com.chorifa.minirpc.invoker.type.InvokeCallBack;

public interface RemotingInject<T> {

    boolean isCallBackBlocking();

    InvokeCallBack<?> getCallBack();

    void setCallBack(InvokeCallBack<?> callBack);

    void setCallBack(InvokeCallBack<?> callBack, boolean blocking);

    RemotingRequest getRequest();

    boolean complete(T value);

}
