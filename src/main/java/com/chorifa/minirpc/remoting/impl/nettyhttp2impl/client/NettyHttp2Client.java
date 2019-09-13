package com.chorifa.minirpc.remoting.impl.nettyhttp2impl.client;

import com.chorifa.minirpc.remoting.Client;
import com.chorifa.minirpc.remoting.ClientInstance;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;

public class NettyHttp2Client extends Client {

    private static final Class<? extends ClientInstance> CLASS_NAME = NettyHttp2ClientInstance.class;

    @Override
    public void asyncSend(String address, RemotingRequest request) throws Exception {
        ClientInstance.asyncSend(address,request,CLASS_NAME,rpcReferenceManager);
    }
}
