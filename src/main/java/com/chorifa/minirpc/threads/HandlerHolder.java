package com.chorifa.minirpc.threads;

import io.netty.channel.EventLoop;

public class HandlerHolder<T> {

    private final EventLoop eventLoop;

    private MessageHandler<T> handler;

    public HandlerHolder(EventLoop eventLoop) {
        this(eventLoop, null);
    }

    public HandlerHolder(EventLoop eventLoop, MessageHandler<T> handler) {
        if(eventLoop == null) throw new NullPointerException("HandlerHolder: EventLoop cannot be null");
        this.eventLoop = eventLoop;
        this.handler = handler;
    }

    public EventLoop getEventLoop() {
        return eventLoop;
    }

    public MessageHandler<T> getHandler() {
        return handler;
    }
}
