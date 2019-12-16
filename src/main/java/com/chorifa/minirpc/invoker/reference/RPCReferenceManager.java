package com.chorifa.minirpc.invoker.reference;

import com.chorifa.minirpc.invoker.type.RemotingFutureAdaptor;
import com.chorifa.minirpc.remoting.Client;
import com.chorifa.minirpc.remoting.RemotingType;
import com.chorifa.minirpc.remoting.entity.RemotingCompletableFuture;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.invoker.statistics.StatusStatistics;
import com.chorifa.minirpc.invoker.type.InvokeCallBack;
import com.chorifa.minirpc.invoker.type.SendType;
import com.chorifa.minirpc.registry.RegistryService;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.StreamID4Http2Util;
import com.chorifa.minirpc.utils.loadbalance.SelectOptions;
import com.chorifa.minirpc.utils.serialize.SerialType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RPCReferenceManager {
    private final static Logger logger = LoggerFactory.getLogger(RPCReferenceManager.class);

    private SerialType serialType;

    private Class<?> serviceClass;
    private String version;

    private String address;

    private SendType sendType;

    private RemotingType remotingType;

    private DefaultRPCInvokerFactory invokerFactory;

    private InvokeCallBack<?> callBack;
    private boolean isCallBackBlocking = false;
    private boolean remoteUseBlocking = false;

    RPCReferenceManager(ReferenceManagerBuilder builder){
        this.serialType = builder.getSerialType();
        this.version = builder.getVersion();
        this.serviceClass = builder.getServiceClass();
        this.address = builder.getAddress();
        this.sendType = builder.getSendType();
        this.remotingType = builder.getRemotingType();
        this.invokerFactory = builder.getInvokerFactory();

        // register >>> subscribe the key
        if(this.invokerFactory.getRegister() != null) {
            String serviceKey = generateKey(this.serviceClass.getName(), this.version);
            this.invokerFactory.getRegister().subscribe(serviceKey);
            this.invokerFactory.getRegister().inquireRefresh(serviceKey); // first time refresh
        }
        // TODO when referenceManger is useless, it should auto unsubscribe the interface
        initClient();
    }

    public void useBlocking(boolean blocking) {
        this.remoteUseBlocking = blocking;
    }
    public void setCallBack(InvokeCallBack<?> callBack) {
        this.callBack = callBack;
    }
    public void setCallBack(InvokeCallBack<?> callBack, boolean blocking) {
        this.callBack = callBack;
        this.isCallBackBlocking = blocking;
    }

    public SerialType getSerialType() {
        return serialType;
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
            client.init(this);
        } catch (Exception e) {
            logger.error("create remoting client failed... try again",e);
            try{
                client = remotingType.getClient().newInstance();
                client.init(this);
            }catch (Exception ex){
                logger.error("create remoting client failed...",ex);
                throw new RPCException("create remoting client failed.",ex);
            }
        }
    }

    /**
     * get Proxy.
     * JDKGenerator only support interface
     * JavassistGenerator not support base type, such as int, double ...
     * @return T proxy
     */
    @SuppressWarnings("unchecked")
    public <T> T get(){
        if(serviceClass.isInterface()) {
            InvocationHandler handler = (proxy, method, args) -> {
                // create parameters
                String methodName = method.getName();
                String interfaceName = serviceClass.getName();
                Class<?>[] parameterType = method.getParameterTypes();
                //Class<?> returnType = method.getReturnType();

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
                    logger.info("Invoker: discovery the host address: {}", finalAddress);
                }

                // create request
                RemotingRequest request = new RemotingRequest();
                request.setTargetAddr(finalAddress);
                request.setArgs(args);
                request.setInterfaceName(interfaceName);
                request.setMethodName(methodName);
                request.setVersion(version);
                request.setParameterType(parameterType);
                request.setBlocking(remoteUseBlocking);
                if(remotingType == RemotingType.NETTY_HTTP2) // use stream id (odd int, 3 - Integer.MAX)
                    request.setRequestId(String.valueOf(StreamID4Http2Util.getCurrentID()));
                else
                    request.setRequestId(UUID.randomUUID().toString());

                logger.debug("try to send...");
                // send
                switch (sendType) {
                    case SYNC: {
                        RemotingCompletableFuture futureResponse = new RemotingCompletableFuture(request);
                        invokerFactory.putFutureResponse(request.getRequestId(), futureResponse);
                        RemotingResponse response = null;
                        try {

                            // statistics
                            StatusStatistics.startCount(finalAddress, methodName);

                            client.asyncSend(finalAddress, request);
                            // wait until get
                            response = futureResponse.get(10, TimeUnit.SECONDS);

                        } catch (Exception e) {
                            logger.info("Invoker: encounter one exception via SYNC on: {}", finalAddress,e);
                            // statistics
                            StatusStatistics.endCount(finalAddress, methodName,false);

                            throw (e instanceof RPCException) ? e : new RPCException(e);
                        } finally {
                            invokerFactory.removeFutureResponse(request.getRequestId());
                        }
                        if (response.getErrorMsg() != null)
                            throw new RPCException(response.getErrorMsg());
                        return response.getResult();
                    }
                    case FUTURE: {
                        RemotingCompletableFuture futureResponse = new RemotingCompletableFuture(request);
                        invokerFactory.putFutureResponse(request.getRequestId(), futureResponse);
                        RemotingFutureAdaptor<?> futureAdaptor = RemotingFutureAdaptor.generateFuture(futureResponse, invokerFactory);
                        try {
                            RemotingFutureAdaptor.setFuture(futureAdaptor);
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
                        RemotingCompletableFuture futureResponse = new RemotingCompletableFuture(request);
                        futureResponse.setCallBack(callBack, isCallBackBlocking);
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
            };
            return (T) JDKGenerator.generateProxy(serviceClass, handler);
        }else{
            InvocationHandlerForJavassist handler = (proxy, method, args) -> {
                // create parameters
                String methodName = method.getName();
                String interfaceName = serviceClass.getName();
                Class<?>[] parameterType = JavassistGenerator.findClassViaCtClass(method.getParameterTypes());
//                Class<?> returnType = JavassistGenerator.findClassViaCtClass(method.getReturnType());

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
                request.setBlocking(remoteUseBlocking);
                if(remotingType == RemotingType.NETTY_HTTP2) // use stream id (odd int, 3 - Integer.MAX)
                    request.setRequestId(String.valueOf(StreamID4Http2Util.getCurrentID()));
                else
                    request.setRequestId(UUID.randomUUID().toString());

                logger.debug("try to send...");
                // send
                switch (sendType) {
                    case SYNC: {
                        RemotingCompletableFuture futureResponse = new RemotingCompletableFuture(request);
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
                        RemotingCompletableFuture futureResponse = new RemotingCompletableFuture(request);
                        invokerFactory.putFutureResponse(request.getRequestId(), futureResponse);
                        RemotingFutureAdaptor<?> futureAdaptor = RemotingFutureAdaptor.generateFuture(futureResponse,invokerFactory);
                        try {
                            RemotingFutureAdaptor.setFuture(futureAdaptor);
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
                        RemotingCompletableFuture futureResponse = new RemotingCompletableFuture(request);
                        futureResponse.setCallBack(callBack, isCallBackBlocking);
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
            };
            return (T) JavassistGenerator.getProxy(serviceClass, handler);
        }
    }

    private String generateKey(String interfaceName, String version){
        return version == null?interfaceName:interfaceName+":"+version.trim();
    }

}
