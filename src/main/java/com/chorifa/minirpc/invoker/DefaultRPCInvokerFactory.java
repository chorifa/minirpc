package com.chorifa.minirpc.invoker;

import com.chorifa.minirpc.invoker.statistics.StatusStatistics;
import com.chorifa.minirpc.invoker.type.InvokeCallBack;
import com.chorifa.minirpc.registry.RegistryConfig;
import com.chorifa.minirpc.registry.RegistryService;
import com.chorifa.minirpc.registry.RegistryType;
import com.chorifa.minirpc.remoting.entity.RemotingInject;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.utils.InnerCallBack;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.loadbalance.BalanceMethod;
import com.chorifa.minirpc.utils.loadbalance.LoadBalance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultRPCInvokerFactory {
    private final static Logger logger = LoggerFactory.getLogger(DefaultRPCInvokerFactory.class);

    private RegistryService register;
    private RegistryConfig config;
    private BalanceMethod balanceMethod;

    private volatile boolean isRunning = false;
    private AtomicBoolean runLock = new AtomicBoolean(false);

    private final static DefaultRPCInvokerFactory DEFAULT_INSTANCE = new DefaultRPCInvokerFactory();
    public static DefaultRPCInvokerFactory getInstance(){
        return DEFAULT_INSTANCE;
    }

    // if we do not need registry. start directly.
    public DefaultRPCInvokerFactory(){ start(); }

    public DefaultRPCInvokerFactory(RegistryType registryType, RegistryConfig config){
        this(registryType,config,LoadBalance.LEAST_UNREPLIED);
    }

    public DefaultRPCInvokerFactory(RegistryType registryType, RegistryConfig config, LoadBalance loadBalance){

        if(registryType == null || registryType.getRegisterClass() == null)
            throw new RPCException("InvokerFactory: register type cannot be null...");

        if(config == null)
            throw new RPCException("InvokerFactory: register config cannot be null...");

        if(loadBalance == null || loadBalance.getBalanceMethod() == null)
            throw new RPCException("InvokerFactory: loadBalance cannot be null...");

        try {
            register = registryType.getRegisterClass().getDeclaredConstructor().newInstance();
        }catch (Exception e){
            logger.error("InvokerFactory: new register failed... try again",e);
            try {
                register = registryType.getRegisterClass().newInstance();
            }catch (Exception ex){
                logger.error("InvokerFactory: new register failed...",ex);
                throw new RPCException(ex);
            }
        }
        this.config = config;
        this.balanceMethod = loadBalance.getBalanceMethod();

        // TODO now --->>> void start()
    }

    public RegistryService getRegister() {
        return register;
    }

    public BalanceMethod getBalanceMethod() {
        return balanceMethod;
    }

    // --------------------------- start && stop ---------------------------
    // TODO watch out multi-thread
    private ArrayList<InnerCallBack> stopCallBackList = new ArrayList<>();

    public void addStopCallBack(InnerCallBack innerCallBack){
        stopCallBackList.add(innerCallBack);
    }

    // when to start
    public void start(){
        start(false);
    }

    public void start(boolean autoClose){
        if(!isRunning && runLock.compareAndSet(false,true)){
            if(register != null)
                register.start(config);
            if(autoClose)
                Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
            isRunning = true;
            logger.info("InvokerFactory start running...");
        }
    }

    // when to stop : Can use hook thread to auto-close
    public void stop(){
        if(isRunning && runLock.compareAndSet(true,false)) {
            if (register != null && register.isAvailable())
                register.stop();

            for (InnerCallBack callBack : stopCallBackList) // one in: close all connection
                callBack.run();

            if (executorService != null && !executorService.isShutdown())
                executorService.shutdown();
            logger.info("InvokerFactory shut down...");
        }
        else logger.info("InvokerFactory already shut down...");
    }

    // --------------------------- future map ---------------------------
    private ConcurrentHashMap<String /*ID*/, RemotingInject<RemotingResponse>> futureResponseMap = new ConcurrentHashMap<>();

    public void putFutureResponse(String id, RemotingInject<RemotingResponse> futureResponse){
        futureResponseMap.put(id,futureResponse);
    }

    public void removeFutureResponse(String id){
        futureResponseMap.remove(id);
    }

    @SuppressWarnings("unchecked")
    public void injectResponse(String id, RemotingResponse response){
        RemotingInject<RemotingResponse> futureResponse = futureResponseMap.remove(id);
        if(futureResponse == null)
            throw new RPCException("InvokerFactory: futureResponseMap do not have such futureResponse: id = "+id);
        InvokeCallBack callBack = futureResponse.getCallBack();

        // statistics
        if(response.getErrorMsg() == null) // success
            StatusStatistics.endCount(futureResponse.getRequest().getTargetAddr(),futureResponse.getRequest().getMethodName(),true);
        else // fail
            StatusStatistics.endCount(futureResponse.getRequest().getTargetAddr(),futureResponse.getRequest().getMethodName(),false);

        if(callBack != null){ // callBack
            executeTask(()-> {
                if(response.getErrorMsg() == null){
                    try {
                        callBack.onSuccess(response.getResult());
                    }catch (Exception e) {
                        logger.error("InvokerFactory: encounter exception when execute onSuccess CallBack", e);
                        throw new RPCException("InvokerFactory: encounter exception when execute onSuccess CallBack", e);
                    }
                }else{
                    try {
                        callBack.onException(new RPCException(response.getErrorMsg()));
                    }catch (Exception e){
                        logger.error("encounter exception when execute onException CallBack",e);
                        throw new RPCException("encounter exception when execute onException CallBack",e);
                    }
                }

            });
        }else if(!futureResponse.complete(response)) // future and sync
            throw new RPCException("set response in future failed...");

        //futureResponseMap.remove(id);
    }

    // --------------------------- execute pool for callBack ---------------------------
    // TODO how to auto close this service pool ??? --->>> use hook thread
    private volatile ExecutorService executorService = null;

    private void executeTask(Runnable runnable){
        if(executorService == null){
            synchronized (this){
                if(executorService == null)
                    executorService = Executors.newFixedThreadPool(50,
                            (r)->new Thread(r,"rpc-invoker Factory-callBackPool: "+r.hashCode()));
            }
        }
        executorService.execute(runnable);
    }

}
