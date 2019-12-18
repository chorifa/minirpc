package com.chorifa.minirpc.threads;

import io.netty.channel.EventLoop;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventBus {

    public static final EventBus DEFAULT_EVENT_BUS = new EventBus();

    private final Map<String, List<HandlerHolder>> handlerMap = new ConcurrentHashMap<>();

    public <T> void subscribe(String topic) {
        subscribe(topic, null, ThreadManager.workGroup.next());
    }

    public <T> void subscribe(String topic, MessageHandler<T> handler) {
        subscribe(topic, handler, ThreadManager.workGroup.next());
    }

    public <T> void subscribe(String topic, boolean addIfAbsent) {
        subscribe(topic, null, ThreadManager.workGroup.next(), addIfAbsent);
    }

    // subscribe do not run in event loop
    public <T> void subscribe(String topic, MessageHandler<T> handler, EventLoop eventLoop) {
        HandlerHolder<T> handlerHolder = new HandlerHolder<>(eventLoop, handler);
        List<HandlerHolder> copyOnWriteList = handlerMap.get(topic);
        if(copyOnWriteList == null) {
            List<HandlerHolder> list = new CopyOnWriteArrayList<>();
            copyOnWriteList = handlerMap.putIfAbsent(topic, list);
            if(copyOnWriteList == null) copyOnWriteList = list;
        }
        copyOnWriteList.add(handlerHolder);
    }

    public <T> void subscribe(String topic, MessageHandler<T> handler, EventLoop eventLoop, boolean addIfAbsent) {
        if(addIfAbsent) {
            HandlerHolder<T> handlerHolder = new HandlerHolder<>(eventLoop, handler);
            List<HandlerHolder> copyOnWriteList = handlerMap.get(topic);
            if(copyOnWriteList == null) {
                List<HandlerHolder> list = new CopyOnWriteArrayList<>();
                list.add(handlerHolder);
                handlerMap.putIfAbsent(topic, list);
            }
        }else subscribe(topic, handler, eventLoop);
    }

    public boolean publish(String topic, Object message) {
        List<HandlerHolder> list = handlerMap.get(topic);
        if(list == null || list.isEmpty()) return false;
        for(HandlerHolder holder : list)
            deliverToHandler(message, holder);
        return true;
    }

    public boolean publish(String topic, Runnable task) {
        List<HandlerHolder> list = handlerMap.get(topic);
        if(list == null || list.isEmpty()) return false;
        for(HandlerHolder holder : list)
            deliverTaskToEventLoop(task, holder.getEventLoop());
        return true;
    }

    private void deliverToHandler(Object message, HandlerHolder<Object> holder) {
        EventLoop eventLoop = holder.getEventLoop();
        if(holder.getHandler() == null) {
            if(message instanceof Runnable) {
                Runnable task = (Runnable) message;
                if(eventLoop.inEventLoop()) task.run();
                else eventLoop.execute(task);
            }
        }else {
            if(eventLoop.inEventLoop()) {
                holder.getHandler().handle(message);
            }
            else {
                eventLoop.execute(()->holder.getHandler().handle(message));
            }
        }
    }

    private void deliverTaskToEventLoop(Runnable task, EventLoop eventLoop) {
        if(eventLoop.inEventLoop()) task.run();
        else eventLoop.execute(task);
    }

}
