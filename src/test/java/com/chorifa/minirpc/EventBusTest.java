package com.chorifa.minirpc;

import com.chorifa.minirpc.threads.EventBus;
import com.chorifa.minirpc.threads.MessageHandler;
import com.chorifa.minirpc.threads.ThreadManager;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class EventBusTest {

    @Test
    public void testPubSub() {
        EventBus bus = EventBus.DEFAULT_EVENT_BUS;
        bus.subscribe("sports.news");
        bus.publish("sports.news", () -> System.out.println("received a task"));

        try {
            TimeUnit.SECONDS.sleep(20);
        } catch (InterruptedException ignore) {
        } finally {
            ThreadManager.shutdown();
        }

    }

    @Test
    public void testPubSub2() {
        EventBus bus = EventBus.DEFAULT_EVENT_BUS;
        bus.subscribe("sports.news", (String message) -> System.out.println("receive a msg: "+message));
        bus.subscribe("sports.news", (String message) -> System.out.println("receive a msg: "+message));
        bus.publish("sports.news", "new sport news!");
        ThreadManager.workGroup.next().execute(()->bus.publish("sports.news", "another new sport news!"));
        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException ignore) {
        } finally {
            ThreadManager.shutdown();
        }

    }

    @Test
    public void testConcurrent() {
        EventBus bus = EventBus.DEFAULT_EVENT_BUS;
        ExecutorService executors = Executors.newFixedThreadPool(10);
        bus.subscribe("sports.news", new ConcurrentMessageHandler());
        for(int i = 0; i < 200; i++)
            executors.execute(() -> bus.publish("sports.news", "a piece of news"));
        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException ignore) {
        }finally {
            executors.shutdown();
            ThreadManager.shutdown();
        }
    }

    static class ConcurrentMessageHandler implements MessageHandler<String> {

        private Thread thread;

        @Override
        public void handle(String message) {
            if(thread == null) thread = Thread.currentThread();
            else if(thread != Thread.currentThread()) System.out.println("concurrent modify !!!");
        }
    }

}
