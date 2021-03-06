package com.chorifa.minirpc.invoker.reference;

import com.chorifa.minirpc.invoker.statistics.StatusStatistics;
import com.chorifa.minirpc.remoting.Client;
import com.chorifa.minirpc.remoting.RemotingType;
import com.chorifa.minirpc.remoting.entity.RemotingCompletableFuture;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.invoker.type.FutureType;
import com.chorifa.minirpc.invoker.type.InvokeCallBack;
import com.chorifa.minirpc.invoker.type.SendType;
import com.chorifa.minirpc.registry.RegistryService;
import com.chorifa.minirpc.remoting.entity.RemotingFutureResponse;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.StreamID4Http2Util;
import com.chorifa.minirpc.utils.loadbalance.SelectOptions;
import com.chorifa.minirpc.utils.serialize.SerialType;
import com.chorifa.minirpc.utils.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Deprecated
public class RPCReferenceManagerOld {
    private final static Logger logger = LoggerFactory.getLogger(RPCReferenceManagerOld.class);

    private Serializer serializer;

    private Class<?> serviceClass;
    private String version;

    private String address;

    private SendType sendType;

    private RemotingType remotingType;

    private DefaultRPCInvokerFactory invokerFactory;

    private InvokeCallBack<?> callBack;

    public RPCReferenceManagerOld(Class<?> serviceClass){
        this(serviceClass,null,"localhost:8086",RemotingType.NETTY,SendType.SYNC,SerialType.HESSIAN,null);
    }

    public RPCReferenceManagerOld(Class<?> serviceClass, SendType sendType){
        this(serviceClass,null,"localhost:8086",RemotingType.NETTY,sendType,SerialType.HESSIAN,null);
    }

    public RPCReferenceManagerOld(Class<?> serviceClass, SendType sendType, SerialType serialType){
        this(serviceClass,null,"localhost:8086",RemotingType.NETTY,sendType,serialType,null);
    }

    public RPCReferenceManagerOld(Class<?> serviceClass, RemotingType remotingType , SendType sendType, SerialType serialType){
        this(serviceClass,null,"localhost:8086",remotingType,sendType,serialType,null);
    }

    public RPCReferenceManagerOld(Class<?> serviceClass, String version, String address, RemotingType remotingType, SendType sendType, SerialType serialType, DefaultRPCInvokerFactory invokerFactory) {

        if(serialType == null ||serialType.getSerializer() == null)
            throw new RPCException("serializer can not be null...");

        if(serviceClass == null)
            throw new RPCException("interface can not be null...");

        if(sendType == null)
            throw new RPCException("send type can not be null...");

        if(remotingType == null)
            throw new RPCException("remoting type can not be null...");

        this.serializer = serialType.getSerializer();
        this.serviceClass = serviceClass;
        this.version = version;
        this.address = address;
        this.remotingType = remotingType;
        this.sendType = sendType;
        if(invokerFactory != null)
            this.invokerFactory = invokerFactory;
        else this.invokerFactory = DefaultRPCInvokerFactory.getInstance();

        // register >>> subscribe the key
        if(this.invokerFactory.getRegister() != null) {
            String serviceKey = generateKey(this.serviceClass.getName(), this.version);
            this.invokerFactory.getRegister().subscribe(serviceKey);
            this.invokerFactory.getRegister().inquireRefresh(serviceKey); // first time refresh
        }
        // TODO when referenceManger is useless, it should auto unsubscribe the interface
        initClient();
    }

    public void setCallBack(InvokeCallBack<?> callBack) {
        this.callBack = callBack;
    }

    public Serializer getSerializer() {
        return serializer;
    }

    public DefaultRPCInvokerFactory getInvokerFactory() {
        return invokerFactory;
    }
// ----------------------------- proxy -----------------------------

    private Client client;
    // TODO watch out reference 'this' run out.
    private void initClient(){
        try {
            client = remotingType.getClient().getDeclaredConstructor().newInstance();
            //client.init(this);
        } catch (Exception e) {
            logger.error("create remoting client failed... try again",e);
            try{
                client = remotingType.getClient().newInstance();
                //client.init(this);
            }catch (Exception ex){
                logger.error("create remoting client failed...",ex);
                throw new RPCException("create remoting client failed.",ex);
            }
        }
    }

    public Object get(){
        return Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{serviceClass},
                (proxy, method, args) -> {
                    // create parameters
                    String methodName = method.getName();
                    String interfaceName = serviceClass.getName();
                    Class<?>[] parameterType = method.getParameterTypes();
                    Class<?> returnType = method.getReturnType();

                    // TODO not find >>> retry
                    String finalAddress = this.address;
                    if(finalAddress == null){
                        RegistryService register = this.invokerFactory.getRegister();
                        if(register != null && register.isAvailable()){
                            String serviceKey = generateKey(this.serviceClass.getName(),this.version);
                            List<String> hosts = register.discovery(serviceKey);
                            if(hosts != null && !hosts.isEmpty()) // do balance
                                finalAddress = this.invokerFactory.getBalanceMethod().select(serviceKey,hosts,new SelectOptions(methodName,args));
                            else logger.warn("Invoker: cannot find address from register...");
                        }
                        if(finalAddress == null)
                            throw new RPCException("Invoker: cannot resolve the host address...");
                        logger.info("Invoker: discovery the host address: {}",finalAddress);
                    }

                    // create request
                    RemotingRequest request = new RemotingRequest();
                    request.setTargetAddr(finalAddress);
                    request.setArgs(args);
                    request.setInterfaceName(interfaceName);
                    request.setMethodName(methodName);
                    request.setVersion(version);
                    request.setParameterType(parameterType);
                    if(remotingType == RemotingType.NETTY_HTTP2) // use stream id (odd int, 3 - Integer.MAX)
                        request.setRequestId(String.valueOf(StreamID4Http2Util.getCurrentID()));
                    else
                        request.setRequestId(UUID.randomUUID().toString());

                    logger.debug("try to send...");
                    // send
                    switch (sendType) {
                        case SYNC: {
                            RemotingFutureResponse futureResponse = new RemotingFutureResponse(request);
                            invokerFactory.putFutureResponse(request.getRequestId(), futureResponse);
                            RemotingResponse response = null;
                            try {

                                // statistics
                                StatusStatistics.startCount(finalAddress,methodName);

                                client.asyncSend(finalAddress, request);
                                // wait until get
                                response = futureResponse.get(10, TimeUnit.SECONDS);

                            } catch (Exception e) {
                                logger.info("Invoker: encounter one exception via SYNC on: {}", finalAddress,e);
                                // statistics
                                StatusStatistics.endCount(finalAddress,methodName,false);

                                throw (e instanceof RPCException) ? e : new RPCException(e);
                            } finally {
                                invokerFactory.removeFutureResponse(request.getRequestId());
                            }
                            if (response.getErrorMsg() != null)
                                throw new RPCException(response.getErrorMsg());
                            return response.getResult();
                        }
                        case FUTURE: {
                            RemotingFutureResponse futureResponse = new RemotingFutureResponse(request);
                            invokerFactory.putFutureResponse(request.getRequestId(), futureResponse);
                            FutureType<?> futureType = FutureType.generateFuture(returnType,futureResponse,invokerFactory);
                            try {
                                FutureType.setFuture(futureType);
                                //statistics
                                StatusStatistics.startCount(finalAddress,methodName);

                                client.asyncSend(finalAddress, request);
                                return null;
                            } catch (Exception e) {
                                invokerFactory.removeFutureResponse(request.getRequestId());
                                // statistics
                                StatusStatistics.endCount(finalAddress,methodName,false);

                                logger.error("Invoker: encounter one exception via FUTURE on: {}", finalAddress);
                                throw (e instanceof RPCException) ? e : new RPCException(e);
                            }
                        }
                        case CALLBACK: {
                            if(callBack == null)
                                throw new RPCException("Invoker: call back function should not be null...");
                            RemotingFutureResponse futureResponse = new RemotingFutureResponse(request);
                            futureResponse.setCallBack(callBack);
                            invokerFactory.putFutureResponse(request.getRequestId(), futureResponse);
                            try{
                                //statistics
                                StatusStatistics.startCount(finalAddress,methodName);

                                client.asyncSend(finalAddress,request);
                                return null;
                            }catch (Exception e){
                                invokerFactory.removeFutureResponse(request.getRequestId());
                                // statistics
                                StatusStatistics.endCount(finalAddress,methodName,false);

                                logger.error("Invoker: encounter one exception via CallBack on: {}", finalAddress);
                                throw (e instanceof RPCException) ? e : new RPCException(e);
                            }
                        }
                        case ONEWAY:

                            return null;
                        default:
                            throw new RPCException("Invoker: no such Send Type...");
                    }
                });
    }

    private String generateKey(String interfaceName, String version){
        return version == null?interfaceName:interfaceName+":"+version.trim();
    }

}
