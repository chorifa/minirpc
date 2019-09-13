package com.chorifa.minirpc.invoker.type;

import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.remoting.entity.RemotingFutureResponse;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.utils.RPCException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureType<T> implements Future<T> {

    private RemotingFutureResponse futureResponse;
    private DefaultRPCInvokerFactory invokerFactory; // patch --->>> when futureResponse.get() has RPCException , future pool may still hold future response

    private FutureType(RemotingFutureResponse futureResponse, DefaultRPCInvokerFactory invokerFactory) {
        this.futureResponse = futureResponse;
        this.invokerFactory = invokerFactory;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return futureResponse.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return futureResponse.isCancelled();
    }

    @Override
    public boolean isDone() {
        return futureResponse.isDone();
    }

    @Override

    public T get() throws InterruptedException, ExecutionException {
        RemotingResponse response;
        try {
            response = futureResponse.get();
        }finally {
            invokerFactory.removeFutureResponse(futureResponse.getRequest().getRequestId());
        }
        if (response.getErrorMsg() != null) {
            throw new RPCException(response.getErrorMsg());
        }
        @SuppressWarnings("unchecked")
        T result = (T)response.getResult();
        return result;
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        RemotingResponse response;
        try {
            response= futureResponse.get(timeout, unit);
        }finally {
            invokerFactory.removeFutureResponse(futureResponse.getRequest().getRequestId());
        }
        if (response.getErrorMsg() != null) {
            throw new RPCException(response.getErrorMsg());
        }
        @SuppressWarnings("unchecked")
        T result = (T)response.getResult();
        return result;
    }

    // ---------------------------------------- out api ----------------------------------------
    public static <T> FutureType<T> generateFuture(Class<T> clazz,RemotingFutureResponse futureResponse, DefaultRPCInvokerFactory invokerFactory){
        return new FutureType<T>(futureResponse,invokerFactory);
    }

    private static final ThreadLocal<FutureType<?>> currentFuture = new ThreadLocal<>();

    public static <T> void setFuture(FutureType<T> future){
        currentFuture.set(future);
    }


    public static <T> FutureType<T> getFuture(){
        @SuppressWarnings("unchecked")
        FutureType<T> futureType = (FutureType<T>)currentFuture.get();
        currentFuture.remove();
        return futureType;
    }

}
