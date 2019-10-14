package com.chorifa.minirpc.invoker.type;

import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.remoting.entity.RemotingCompletableFuture;
import com.chorifa.minirpc.utils.RPCException;

import java.util.concurrent.*;

public class RemotingFutureAdaptor<T> extends CompletableFuture<T> {

    private final RemotingCompletableFuture cf;
    // patch --->>> when futureResponse.get() has RPCException , future pool may still hold future response
    private final DefaultRPCInvokerFactory factory;

    @SuppressWarnings("unchecked")
    public RemotingFutureAdaptor(RemotingCompletableFuture cf, DefaultRPCInvokerFactory factory) {
        this.cf = cf;
        this.factory = factory;
        cf.whenComplete(((remotingResponse, throwable) -> {
            if(throwable != null){
                if(throwable instanceof CompletionException){
                    throwable = throwable.getCause();
                }
                this.completeExceptionally(throwable);
            }else{
                if(remotingResponse.getErrorMsg() != null){
                    this.completeExceptionally(new RPCException(remotingResponse.getErrorMsg()));
                }else {
                    this.complete((T) remotingResponse.getResult());
                }
            }
        }));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cf.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return cf.isCancelled();
    }

    @Override
    public boolean isDone() {
        return super.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        try {
            return super.get();
        } catch (ExecutionException | InterruptedException e) {
            factory.removeFutureResponse(cf.getRequest().getRequestId());
            throw e;
        } catch (Throwable e) {
            factory.removeFutureResponse(cf.getRequest().getRequestId());
            throw new RPCException(e);
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return super.get(timeout, unit);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            factory.removeFutureResponse(cf.getRequest().getRequestId());
            throw e;
        } catch (Throwable e) {
            factory.removeFutureResponse(cf.getRequest().getRequestId());
            throw new RPCException(e);
        }
    }

    // ---------------------------------------- out api ----------------------------------------
    /**
     * actually, Return type T here is useless. caz when invoke this method, we certainly do not know generic type
     * so RemotingFutureAdaptor<?> adaptor = RemotingFutureAdaptor.generateFuture(...) will be invoked
     * when getCompletableFuture() or getFuture(), we try to convert generic type.
     */
    public static <T> RemotingFutureAdaptor<T> generateFuture(RemotingCompletableFuture futureResponse, DefaultRPCInvokerFactory invokerFactory){
        return new RemotingFutureAdaptor<>(futureResponse, invokerFactory);
    }

    private static final ThreadLocal<RemotingFutureAdaptor<?>> currentFuture = new ThreadLocal<>();

    public static <T> void setFuture(RemotingFutureAdaptor<T> future){
        currentFuture.set(future);
    }

    public static <T> RemotingFutureAdaptor<T> getCompletableFuture(){
        @SuppressWarnings("unchecked")
        RemotingFutureAdaptor<T> futureAdaptor = (RemotingFutureAdaptor<T>)currentFuture.get();
        currentFuture.remove();
        return futureAdaptor;
    }

    public static <T> Future<T> getFuture(){
        return getCompletableFuture();
    }

}
