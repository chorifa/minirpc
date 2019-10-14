package com.chorifa.minirpc.remoting.entity;

import com.chorifa.minirpc.invoker.type.InvokeCallBack;

import java.util.concurrent.CompletableFuture;

public class RemotingCompletableFuture extends CompletableFuture<RemotingResponse> implements RemotingInject<RemotingResponse> {

    private RemotingRequest request;

    private InvokeCallBack<?> callBack;

    public RemotingCompletableFuture(RemotingRequest request) {
        this.request = request;
    }

    public InvokeCallBack<?> getCallBack() {
        return callBack;
    }

    public void setCallBack(InvokeCallBack callBack) {
        this.callBack = callBack;
    }

    public RemotingRequest getRequest() {
        return request;
    }

    @Override
    public boolean complete(RemotingResponse value) {
        return super.complete(value);
    }

}
