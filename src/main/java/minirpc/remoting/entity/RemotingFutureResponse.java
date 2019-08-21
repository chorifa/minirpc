package minirpc.remoting.entity;

import minirpc.invoker.type.InvokeCallBack;
import minirpc.utils.RPCException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RemotingFutureResponse implements Future<RemotingResponse> {

    private RemotingRequest request;
    private RemotingResponse response;

    private InvokeCallBack<?> callBack;

    public RemotingFutureResponse(RemotingRequest request) {
        this.request = request;
    }

    public InvokeCallBack<?> getCallBack() {
        return callBack;
    }

    public void setCallBack(InvokeCallBack callBack) {
        this.callBack = callBack;
    }

    public RemotingRequest getRequest() {
        return request;
    }

    // lock and flag
    private volatile boolean done = false;
    private final Object lock = new Object();

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // TODO
        return false;
    }

    @Override
    public boolean isCancelled() {
        // TODO
        return false;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    // ------------------------------ put and get ------------------------------
    public boolean set(RemotingResponse response){
        if(done) return false;
        synchronized (lock){
            this.response = response;
            done = true;
            lock.notifyAll();
            return true;
        }
    }

    @Override
    public RemotingResponse get() throws InterruptedException, ExecutionException {
        if(!done){
            synchronized (lock){
                if (!done){ // if is better rather than while, here. caz only set once.
                    lock.wait();
                }
            }
        }
        return response;
    }

    @Override
    public RemotingResponse get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long timeoutMillis = unit==TimeUnit.MILLISECONDS?timeout:TimeUnit.MILLISECONDS.convert(timeout, unit);
        if(!done){
            synchronized (lock){
                if (!done){
                    lock.wait(timeoutMillis);
                }
            }
        }
        if(!done) throw new RPCException("response not access in "+timeoutMillis +"ms...");
        return response;
    }
}
