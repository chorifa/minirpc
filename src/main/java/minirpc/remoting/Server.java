package minirpc.remoting;

import minirpc.provider.DefaultRPCProviderFactory;
import minirpc.utils.InnerCallBack;
import minirpc.utils.RPCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Server {

    private static Logger logger = LoggerFactory.getLogger(Server.class);

    private InnerCallBack startCallBack;
    private InnerCallBack stopCallBack;

    public InnerCallBack getStartCallBack() {
        return startCallBack;
    }

    public void setStartCallBack(InnerCallBack startCallBack) {
        this.startCallBack = startCallBack;
    }

    public InnerCallBack getStopCallBack() {
        return stopCallBack;
    }

    public void setStopCallBack(InnerCallBack stopCallBack) {
        this.stopCallBack = stopCallBack;
    }

    public abstract void start(DefaultRPCProviderFactory providerFactory);

    protected void beforeStart(){
        if(startCallBack != null) {
            try {
                startCallBack.run();
            } catch (Exception e) {
                logger.error("Remoting: server run start call back failed...", e);
                throw new RPCException(e);
            }
        }
        logger.debug("run start call back...");
    }

    public abstract void stop();

    protected void afterStop(){
        if(stopCallBack != null){
            try {
                stopCallBack.run();
            }catch (Exception e){
                logger.error("Remoting: server run stop call back failed...", e);
                // throw new RPCException(e);
            }
        }
        logger.debug("run stop call back...");
    }

}
