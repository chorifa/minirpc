package minirpc.remoting.impl.nettyimpl.client;

import minirpc.remoting.Client;
import minirpc.remoting.ClientInstance;
import minirpc.remoting.entity.RemotingRequest;

public class NettyClient extends Client {

    private static final Class<? extends ClientInstance> CLASS_NAME = NettyClientInstance.class;

    @Override
    public void asyncSend(String address, RemotingRequest request) throws Exception {
        ClientInstance.asyncSend(address,request,CLASS_NAME,rpcReferenceManager);
    }

}
