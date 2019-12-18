package com.chorifa.minirpc.threads;

public interface MessageHandler<T> {

    void handle(T message);

}
