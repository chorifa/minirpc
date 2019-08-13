package minirpc.invoker.reference;

import minirpc.invoker.DefaultRPCInvokerFactory;
import minirpc.invoker.statistics.StatusStatistics;
import minirpc.invoker.type.FutureType;
import minirpc.invoker.type.InvokeCallBack;
import minirpc.invoker.type.SendType;
import minirpc.register.RegisterService;
import minirpc.remoting.Client;
import minirpc.remoting.RemotingType;
import minirpc.remoting.entity.RemotingFutureResponse;
import minirpc.remoting.entity.RemotingRequest;
import minirpc.remoting.entity.RemotingResponse;
import minirpc.utils.RPCException;
import minirpc.utils.StreamID4Http2Util;
import minirpc.utils.loadbalance.SelectOptions;
import minirpc.utils.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RPCReferenceManager<T> {
    private final static Logger logger = LoggerFactory.getLogger(RPCReferenceManager.class);

    private Serializer serializer;

    private Class<?> serviceClass;
    private String version;

    private String address;

    private SendType sendType;

    private RemotingType remotingType;

    private DefaultRPCInvokerFactory invokerFactory;

    private InvokeCallBack<?> callBack;

    RPCReferenceManager(ReferenceManagerBuilder builder){
        this.serializer = builder.getSerializer();
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
    public T get(){
        if(serviceClass.isInterface()) {
            InvocationHandler handler = (proxy, method, args) -> {
                // create parameters
                String methodName = method.getName();
                String interfaceName = serviceClass.getName();
                Class<?>[] parameterType = method.getParameterTypes();
                Class<?> returnType = method.getReturnType();

                // TODO not find >>> retry
                String finalAddress = this.address;
                if(finalAddress == null){
                    RegisterService register = this.invokerFactory.getRegister();
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
            };
            return (T) JDKGenerator.generateProxy(serviceClass, handler);
        }else{
            InvocationHandlerForJavassist handler = (proxy, method, args) -> {
                // create parameters
                String methodName = method.getName();
                String interfaceName = serviceClass.getName();
                Class<?>[] parameterType = JavassistGenerator.findClassViaCtClass(method.getParameterTypes());
                Class<?> returnType = JavassistGenerator.findClassViaCtClass(method.getReturnType());

                // TODO not find >>> retry
                String finalAddress = this.address;
                if(finalAddress == null){
                    RegisterService register = this.invokerFactory.getRegister();
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
            };
            return (T) JavassistGenerator.getProxy(serviceClass, handler);
        }
    }

    private String generateKey(String interfaceName, String version){
        return version == null?interfaceName:interfaceName+":"+version.trim();
    }

}
