package com.chorifa.minirpc.remoting.impl.nettyhttpimpl.client;

import com.chorifa.minirpc.remoting.Client;
import com.chorifa.minirpc.remoting.ClientInstance;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;

public class NettyHttpClient extends Client {

    private static final Class<? extends ClientInstance> CLASS_NAME = NettyHttpClientInstance.class;

    @Override
    public void asyncSend(String address, RemotingRequest request) throws Exception {
        ClientInstance.asyncSend(address,request,CLASS_NAME,rpcReferenceManager);
    }

}
