package com.chorifa.minirpc.remoting.impl.nettyimpl.client;

import com.chorifa.minirpc.remoting.Client;
import com.chorifa.minirpc.remoting.ClientInstance;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;

public class NettyClient extends Client {

    private static final Class<? extends ClientInstance> CLASS_NAME = NettyClientInstance.class;

    @Override
    public void asyncSend(String address, RemotingRequest request) throws Exception {
        ClientInstance.asyncSend(address, request, CLASS_NAME, rpcReferenceManager);
    }

}
