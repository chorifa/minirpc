package minirpc.remoting.impl.nettyhttpimpl.client;

import minirpc.remoting.Client;
import minirpc.remoting.ClientInstance;
import minirpc.remoting.entity.RemotingRequest;

public class NettyHttpClient extends Client {

    private static final Class<? extends ClientInstance> CLASS_NAME = NettyHttpClientInstance.class;

    @Override
    public void asyncSend(String address, RemotingRequest request) throws Exception {
        ClientInstance.asyncSend(address,request,CLASS_NAME,rpcReferenceManager);
    }

}
