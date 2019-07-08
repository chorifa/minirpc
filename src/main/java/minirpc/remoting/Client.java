package minirpc.remoting;

import minirpc.invoker.reference.RPCReferenceManager;
import minirpc.remoting.entity.RemotingRequest;

public abstract class Client {

    // -----------------------  init    -----------------------

    // contain necessary para
    protected volatile RPCReferenceManager rpcReferenceManager;

    public void init(RPCReferenceManager rpcReferenceManager){
        this.rpcReferenceManager = rpcReferenceManager;
    }

    public abstract void asyncSend(String address, RemotingRequest request) throws Exception;

}
