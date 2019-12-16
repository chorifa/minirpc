package com.chorifa.minirpc.remoting.entity;

import com.chorifa.minirpc.invoker.type.InvokeCallBack;

import java.util.concurrent.CompletableFuture;

public class RemotingCompletableFuture extends CompletableFuture<RemotingResponse> implements RemotingInject<RemotingResponse> {

    private RemotingRequest request;

    private InvokeCallBack<?> callBack;

    private boolean isCallBackBlocking;

    @Override
    public boolean isCallBackBlocking() {
        return isCallBackBlocking && callBack != null;
    }

    public RemotingCompletableFuture(RemotingRequest request) {
        this.request = request;
    }

    @Override
    public InvokeCallBack<?> getCallBack() {
        return callBack;
    }

    @Override
    public void setCallBack(InvokeCallBack<?> callBack) {
        this.callBack = callBack;
        this.isCallBackBlocking = false;
    }

    @Override
    public void setCallBack(InvokeCallBack<?> callBack, boolean blocking) {
        this.callBack = callBack;
        this.isCallBackBlocking = blocking;
    }

    @Override
    public RemotingRequest getRequest() {
        return request;
    }

    @Override
    public boolean complete(RemotingResponse value) {
        return super.complete(value);
    }

}
