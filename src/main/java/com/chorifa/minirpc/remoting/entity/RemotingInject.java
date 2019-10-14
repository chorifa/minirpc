package com.chorifa.minirpc.remoting.entity;

import com.chorifa.minirpc.invoker.type.InvokeCallBack;

public interface RemotingInject<T> {

    InvokeCallBack<?> getCallBack();

    void setCallBack(InvokeCallBack<?> callBack);

    RemotingRequest getRequest();

    boolean complete(T value);

}
