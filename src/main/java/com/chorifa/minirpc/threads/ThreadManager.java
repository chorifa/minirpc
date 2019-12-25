package com.chorifa.minirpc.threads;

import com.chorifa.mini0q.builder.RingQueueManager;
import com.chorifa.mini0q.core.consumer.EventHandler;
import com.chorifa.mini0q.core.event.EventFactory;
import com.chorifa.mini0q.core.event.EventTranslator1Arg;
import com.chorifa.mini0q.core.producer.ProducerType;
import com.chorifa.mini0q.core.wait.LiteBlockingWaitStrategy;
import com.chorifa.mini0q.core.wait.WaitStrategy;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public final class ThreadManager {

    private static final Logger logger = LoggerFactory.getLogger(ThreadManager.class);

    public static final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    public static final NioEventLoopGroup workGroup = new NioEventLoopGroup();
    private static final ConcurrentHashMap<EventLoop, RingQueueManager<RingQueueEvent>> ringQueueMap = new ConcurrentHashMap<>();

    // hook, close all thread. include eventLoops and ringQueue's consumers
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ThreadManager::shutdown));
    }

    /* ------------------------------ Event, Factory and Handler for RingQueue ----------------------------------- */
    private static final int DEFAULT_SIZE = 10000;
    private static final int DEFAULT_CONSUMER = 4;
    private static final DefaultEventFactory EVENT_FACTORY = new DefaultEventFactory();
    private static final WaitStrategy DEFAULT_WAIT_STRATEGY = new LiteBlockingWaitStrategy(10_000_000_000L);

    private static final EventHandler<RingQueueEvent> DEFAULT_HANDLER =
            (RingQueueEvent event, long sequence) -> event.execute();

    private static final EventTranslator1Arg<RingQueueEvent, Runnable> DEFAULT_TRANSLATOR =
            (RingQueueEvent event, long sequence, Runnable r) -> event.setTask(r);

    public static class RingQueueEvent {
        private volatile Runnable r; // really need volatile?

        public void execute() {
            if(r != null) r.run();
        }

        public void setTask(Runnable r) {
            this.r = r;
        }
    }

    private static class DefaultEventFactory implements EventFactory<RingQueueEvent> {
        @Override
        public RingQueueEvent newInstance() {
            return new RingQueueEvent();
        }
    }

    /* -------------------------------------------- outer method ------------------------------------------------- */
    /**
     * outside api for try to publish a task into ring queue, only when current thread is in eventLoop can do
     * hence, its thread-safe
     * Will not blocking a thread (retry 3 times)
     * @param eventLoop the eventLoop, which should do the task
     * @param r Task to publish
     * @return true if succeed or false if failed
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean tryPublishEvent(EventLoop eventLoop, Runnable r) {
        if(eventLoop.inEventLoop()) {
            RingQueueManager<RingQueueEvent> manager = ringQueueMap.get(eventLoop);
            if(manager == null) {
                manager = RingQueueManager.createBuilder(EVENT_FACTORY, DEFAULT_SIZE,
                        null, ProducerType.SINGLE, DEFAULT_WAIT_STRATEGY)
                        .handleEventInPoolWith(DEFAULT_HANDLER, DEFAULT_CONSUMER).getManager();
                ringQueueMap.put(eventLoop, manager);
                // TODO set consumers to daemon (should modify DefaultThreadFactory)
                manager.start(); // start the consumer
                logger.info("start new RingQueue...");
            }
            // retry 3 times
            for(int i = 0; i < 3; i++)
                if(manager.tryPublishEvent(DEFAULT_TRANSLATOR, r)) return true;
            return false;
        }else {
            try {
                eventLoop.execute(()->{
                    RingQueueManager<RingQueueEvent> manager = ringQueueMap.get(eventLoop);
                    if(manager == null) {
                        manager = RingQueueManager.createBuilder(EVENT_FACTORY, DEFAULT_SIZE,
                                null, ProducerType.SINGLE, DEFAULT_WAIT_STRATEGY)
                                .handleEventInPoolWith(DEFAULT_HANDLER, DEFAULT_CONSUMER).getManager();
                        ringQueueMap.put(eventLoop, manager);
                        // TODO set consumers to daemon (should modify DefaultThreadFactory)
                        manager.start(); // start the consumer
                        logger.info("start new RingQueue...");
                    }
                    // retry 3 times
                    for(int i = 0; i < 3; i++)
                        if(manager.tryPublishEvent(DEFAULT_TRANSLATOR, r)) break;
                });
                return true;
            }catch (Exception e) {
                return false;
            }
        }
    }

    /**
     * outside api for publish a task into ring queue, only when current thread is in eventLoop can do
     * hence, its thread-safe
     * May throw RuntimeException if reject
     * @param eventLoop the eventLoop, which should do the task
     * @param r Task to publish
     */
    public static void publishEvent(EventLoop eventLoop, Runnable r) {
        if(eventLoop.inEventLoop()) { // only when current thread is eventLoop can hold its RingQueue
            RingQueueManager<RingQueueEvent> manager = ringQueueMap.get(eventLoop);
            if(manager == null) {
                manager = RingQueueManager.createBuilder(EVENT_FACTORY, DEFAULT_SIZE,
                        null, ProducerType.SINGLE, DEFAULT_WAIT_STRATEGY)
                        .handleEventInPoolWith(DEFAULT_HANDLER, DEFAULT_CONSUMER).getManager();
                ringQueueMap.put(eventLoop, manager);
                // TODO set consumers to daemon (should modify DefaultThreadFactory)
                manager.start(); // start the consumer
                logger.info("start new RingQueue...");
            }
            manager.publishEvent(DEFAULT_TRANSLATOR, r); // may throw RuntimeException if reject
//            logger.info("publish new event");
        }else { // safe run
            eventLoop.execute(()->{
                RingQueueManager<RingQueueEvent> manager = ringQueueMap.get(eventLoop);
                if(manager == null) {
                    manager = RingQueueManager.createBuilder(EVENT_FACTORY, DEFAULT_SIZE,
                            null, ProducerType.SINGLE, DEFAULT_WAIT_STRATEGY)
                            .handleEventInPoolWith(DEFAULT_HANDLER, DEFAULT_CONSUMER).getManager();
                    ringQueueMap.put(eventLoop, manager);
                    // TODO set consumers to daemon (should modify DefaultThreadFactory)
                    manager.start(); // start the consumer
                }
                manager.publishEvent(DEFAULT_TRANSLATOR, r); // may throw RuntimeException if reject
            });
        }
    }

    public static void shutdown() {
        bossGroup.shutdownGracefully().syncUninterruptibly();
        workGroup.shutdownGracefully().syncUninterruptibly();
        for(RingQueueManager<RingQueueEvent> manager : ringQueueMap.values()) manager.stop();
        System.out.println("EventLoops and RingQueueConsumers shutdown");
    }

}
