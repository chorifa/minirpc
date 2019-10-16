package com.chorifa.minirpc.remoting;

import com.chorifa.minirpc.invoker.reference.RPCReferenceManager;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.serialize.Serializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public abstract class ClientInstance {

    private static final Logger logger = LoggerFactory.getLogger(ClientInstance.class);

    // all in one EventLoopGroup
    protected static final EventLoopGroup group = new NioEventLoopGroup();
    static {
        // add hook to shut down EventLoopGroup
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            group.shutdownGracefully().syncUninterruptibly();
            logger.info("EventLoopGroup shut down by Hook.");
        }));
    }

    protected DefaultRPCInvokerFactory invokerFactory;
    // -------------------    abstract method    -------------------

    protected abstract boolean isValid();

    // TODO only shot down channel. when to shot down EventLoopGroup?
    protected abstract void close();

    protected abstract void init(String address, Serializer serializer) throws Exception;

    protected abstract void send(RemotingRequest request) throws Exception;

    // -------------------    outsider method    -------------------

    public static void asyncSend(String address, RemotingRequest request, Class<? extends ClientInstance> className, RPCReferenceManager referenceManager) throws Exception {
        ClientInstance clientInstance = getInstance(address,className,referenceManager);
        // send invoke request
        clientInstance.send(request);
    }

    // -------------------    pool manager    -------------------
    private static volatile ConcurrentHashMap<String /* address */ , ClientInstance> connectionPool;
    private static volatile ConcurrentHashMap<String /* address */ , Object> lockMap = new ConcurrentHashMap<>();

    private static ClientInstance getInstance(String address, Class<? extends ClientInstance> className, RPCReferenceManager referenceManager) throws Exception{
        if(connectionPool == null){
            synchronized (ClientInstance.class){
                if(connectionPool == null){
                    connectionPool = new ConcurrentHashMap<>();
                    // add callback: when invokerFactory is closed, all clintInstance in connectionPool should be closed...
                    referenceManager.getInvokerFactory().addStopCallBack(()->{
                        connectionPool.forEachValue(256, ClientInstance::close);
                        /* TODO
                        for(String s : new ArrayList<>(connectionPool.keySet())){
                            ClientInstance instance = connectionPool.get(s);
                            if(instance.invokerFactory == referenceManager.getInvokerFactory()){
                                instance.close();
                                connectionPool.remove(s,instance);
                            }
                        }*/
                        connectionPool.clear();
                        // can shut down here?
                        // group.shutdownGracefully();
                    });
                }
            }
        }

        // avoid get and put conflict. use concurrentMap
        ClientInstance clientInstance = connectionPool.get(address);
        if(clientInstance != null && clientInstance.isValid()) {
            if (clientInstance.invokerFactory != referenceManager.getInvokerFactory()) {
                logger.error("multi registers used. not support >>> only one invokerFactory");
                throw new RPCException("not support multi registers(invokerFactory)...");
            }
            return clientInstance;
        }

        // lock and generate a new connection
        Object lock = lockMap.get(address);
        if(lock == null){
            lock = new Object();
            Object oldVal = lockMap.putIfAbsent(address,lock);
            if(oldVal != null) lock = oldVal;
        }

        synchronized (lock){

            // only one thread can change connectPool
            clientInstance = connectionPool.get(address);
            if(clientInstance != null && clientInstance.isValid())
                return clientInstance;

            // not valid
            if(clientInstance != null){
                clientInstance.close();
                connectionPool.remove(address);
            }

            ClientInstance newInstance;
            try {
                 newInstance = className.getDeclaredConstructor().newInstance();
            }catch (Exception e){
                logger.error("remoting: new instance failed... try again",e);
                try{
                    newInstance = className.newInstance();
                }catch (Exception ex){
                    logger.error("remoting: new instance failed...",ex);
                    throw ex;
                }
            }
            newInstance.invokerFactory = referenceManager.getInvokerFactory();
            try{
                newInstance.init(address, referenceManager.getSerializer());
                connectionPool.put(address,newInstance);
            }catch (Exception e){
                if(newInstance != null)
                    newInstance.close();
                logger.error("ClientInstance: init new connection failed.");
                throw e;
            }
            return newInstance;
        }
    }
}
