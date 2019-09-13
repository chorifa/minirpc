package com.chorifa.minirpc.remoting;

import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.invoker.reference.RPCReferenceManager;

public abstract class Client {

    // -----------------------  init    -----------------------

    // contain necessary para
    protected volatile RPCReferenceManager rpcReferenceManager;

    public void init(RPCReferenceManager rpcReferenceManager){
        this.rpcReferenceManager = rpcReferenceManager;
    }

    public abstract void asyncSend(String address, RemotingRequest request) throws Exception;

}
