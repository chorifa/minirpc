package minirpc.invoker;

import minirpc.invoker.type.InvokeCallBack;
import minirpc.register.RegisterConfig;
import minirpc.register.RegisterService;
import minirpc.register.RegisterType;
import minirpc.remoting.entity.RemotingFutureResponse;
import minirpc.remoting.entity.RemotingResponse;
import minirpc.utils.InnerCallBack;
import minirpc.utils.RPCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultRPCInvokerFactory {
    private final static Logger logger = LoggerFactory.getLogger(DefaultRPCInvokerFactory.class);

    private RegisterService register;
    private RegisterConfig config;

    private final static DefaultRPCInvokerFactory DEFAULT_INSTANCE = new DefaultRPCInvokerFactory();
    public static DefaultRPCInvokerFactory getInstance(){
        return DEFAULT_INSTANCE;
    }

    public DefaultRPCInvokerFactory(){}

    public DefaultRPCInvokerFactory(@Nonnull RegisterType registerType, @Nonnull RegisterConfig config){
        try {
            register = registerType.getRegisterClass().getDeclaredConstructor().newInstance();
        }catch (Exception e){
            logger.error("InvokerFactory: new register failed... try again",e);
            try {
                register = registerType.getRegisterClass().newInstance();
            }catch (Exception ex){
                logger.error("InvokerFactory: new register failed...",ex);
                throw new RPCException(ex);
            }
        }
        this.config = config;

        // TODO now --->>> void start()
    }

    public RegisterService getRegister() {
        return register;
    }

    // --------------------------- start && stop ---------------------------
    // TODO watch out multi-thread
    private ArrayList<InnerCallBack> stopCallBackList = new ArrayList<>();

    public void addStopCallBack(InnerCallBack innerCallBack){
        stopCallBackList.add(innerCallBack);
    }

    // TODO when to start
    public void start(){
        if(register != null)
            register.start(config);
    }

    // TODO when to stop
    public void stop(){
        if(register != null && register.isAvailable())
            register.stop();

        for(InnerCallBack callBack : stopCallBackList)
            callBack.run();

        if(executorService != null && !executorService.isShutdown())
            executorService.shutdown();
        logger.info("InvokerFactory shut down...");
    }

    // --------------------------- future map ---------------------------
    private ConcurrentHashMap<String /*ID*/, RemotingFutureResponse> futureResponseMap = new ConcurrentHashMap<>();

    public void putFutureResponse(String id, RemotingFutureResponse futureResponse){
        futureResponseMap.put(id,futureResponse);
    }

    public void removeFutureResponse(String id){
        futureResponseMap.remove(id);
    }

    @SuppressWarnings("unchecked")
    public void injectResponse(String id, RemotingResponse response){
        RemotingFutureResponse futureResponse = futureResponseMap.get(id);
        if(futureResponse == null)
            throw new RPCException("InvokerFactory: futureResponseMap do not have such futureResponse: id = "+id);
        InvokeCallBack callBack = futureResponse.getCallBack();

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
            /*
            if(response.getErrorMsg() == null)
                executeTask(()->{
                    try {
                        callBack.onSuccess(response.getResult());
                    }catch (Exception e){
                        logger.error("encounter exception when execute onSuccess CallBack",e);
                        throw new RPCException("encounter exception when execute onSuccess CallBack",e);
                    }
                });
            else executeTask(()->{
                try {
                    callBack.onException(new RPCException(response.getErrorMsg()));
                }catch (Exception e){
                    logger.error("encounter exception when execute onException CallBack",e);
                    throw new RPCException("encounter exception when execute onException CallBack",e);
                }
            });*/
        }else if(!futureResponse.set(response)) // future and sync
            throw new RPCException("set response in future failed...");

        futureResponseMap.remove(id);
    }

    // --------------------------- execute pool for callBack ---------------------------
    // TODO how to auto close this service pool ???
    private ExecutorService executorService = null;

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
