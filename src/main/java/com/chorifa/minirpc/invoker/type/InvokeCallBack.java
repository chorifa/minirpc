package com.chorifa.minirpc.invoker.type;

public interface InvokeCallBack<T> {

	void onSuccess (T result) throws Exception;

	void onException(Throwable t) throws Exception;

}
