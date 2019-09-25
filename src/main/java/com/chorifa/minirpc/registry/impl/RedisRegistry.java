package com.chorifa.minirpc.registry.impl;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import com.chorifa.minirpc.registry.RegistryConfig;
import com.chorifa.minirpc.registry.RegistryService;
import com.chorifa.minirpc.utils.RPCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class RedisRegistry implements RegistryService {

    private static final Logger logger = LoggerFactory.getLogger(RedisRegistry.class);

    private static final String BASE_HOME = "minirpc";
    private String prefix;

    private final Map<String /*service name*/, Set<String> /*host addresss*/> serviceMap = new ConcurrentHashMap<>();

    private ExecutorService executor;
    private ScheduledExecutorService schedule;

    private InvokerClient invokerClient;
    private ProviderClient providerClient;

    @Override
    public boolean isAvailable() {
        return (invokerClient != null && invokerClient.isActive()) || (providerClient != null && providerClient.isActive());
    }

    /**
     * not thread-safe
     * @param config
     */
    @Override
    public void start(RegistryConfig config) {
        if(config == null) throw new RPCException("Register config cannot be null.");

        String redisURI = config.getRegisterAddress();
        prefix = BASE_HOME+"/"+config.getEnvPrefix();
        if(config.isInvoker()) {

            executor = Executors.newFixedThreadPool(50,
                    (r) -> new Thread(r, "redis--subscribe--thread pool: " + r.hashCode()));

            invokerClient = new InvokerClient(redisURI, new RedisPubSubListener<String, String>() {
                @Override
                public void message(String s, String s2) { // s is serviceKey, s2 is register+unregister:address
                    logger.info("get msg. execute in pool.");

                    executor.execute(()->{
                        Set<String> hostSet = RedisRegistry.this.serviceMap.get(s); // ensure not null
                        if (hostSet == null) {
                            logger.error("RedisRegister: PubSub contains {}. But serviceMap do not. Which should not occur.", s);
                            Set<String> newSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
                            hostSet = serviceMap.putIfAbsent(s, newSet);
                            if (hostSet == null)
                                hostSet = newSet;
                        }

                        String[] tmps = s2.split(":");
                        if (tmps.length != 2) throw new RPCException("RedisRegister: invoker get msg format error.");
                        String event = tmps[0];
                        String host = tmps[1];
                        switch (event) {
                            case "register":
                                logger.info("RedisRegister: add new address: { address = " + host +/*", data = "+new String(event.getData().getData())+*/" }");
                                if (!hostSet.add(host))
                                    logger.debug("RedisRegister: new address add. But local map already contains. ");
                                break;
                            case "unregister":
                                logger.info("RedisRegister: remove host address: { address = " + host +/*", raw data = "+new String(event.getData().getData())+*/" }");
                                if (!hostSet.remove(host))
                                    logger.debug("RedisRegister: host address remove. But local map do not contain. ");
                                break;
                            case "refresh":
                                logger.info("RedisRegister: receive clean instruction");
                                inquireRefresh(s);
                            default:
                                logger.warn("RedisRegister: not support msg event.");
                                break;
                        }
                    });

                }

                @Override
                public void message(String s, String k1, String s2) {
                    // not use
                }

                @Override
                public void subscribed(String s, long l) {
                    // not use
                }

                @Override
                public void psubscribed(String s, long l) {
                    // not use
                }

                @Override
                public void unsubscribed(String s, long l) {
                    // not use
                }

                @Override
                public void punsubscribed(String s, long l) {
                    // not use
                }
            });
        }
        else {

            schedule = Executors.newScheduledThreadPool(50,
                    (r) -> new Thread(r, "redis--publish--schedule pool: " + r.hashCode()));

            providerClient = new ProviderClient(redisURI,config.getExpiredTime());
        }
        logger.info("RedisRegister: init done. start...");
    }

    @Override
    public void stop() {
        // unregister + unsubscribe
        if(invokerClient != null){
            // not unsubscribe
            invokerClient.stop();
        }
        if(providerClient != null){
            List<String> rawSet = new ArrayList<>(providerClient.registerMap.keySet());
            rawSet.forEach(this::unregister);
            providerClient.stop();
        }
        if(executor != null && !executor.isShutdown())
            executor.shutdown();
        if(schedule != null && !schedule.isShutdown())
            schedule.shutdown();
        logger.info("RedisRegister: redis client stopped...");
    }

    @Override
    public void register(String key, String data) {
        if(providerClient == null)
            throw new RPCException("RedisRegister: client try to register. but provider client is null...");

        // data =def= server address
        String zsetKey = prefix.concat("/").concat(key);
        providerClient.commands.zadd(zsetKey,System.currentTimeMillis(),data); // add to redis
        providerClient.registerMap.put(key,data); // provider cache. for unregister
        providerClient.commands.publish(key,"register:".concat(data)); // notify invoker
        ScheduledFuture<?> future = schedule.scheduleAtFixedRate( // schedule task
                ()-> providerClient.commands.zadd(zsetKey,new ZAddArgs().xx(),System.currentTimeMillis(),data),
                providerClient.refreshPeriod,providerClient.refreshPeriod, TimeUnit.MILLISECONDS);
        providerClient.scheduleMap.put(key,future); // save for cancel >>> unregister
        logger.info("RedisRegister: register >> service={}, address={} << done.",key,data);
    }

    @Override
    public void unregister(String key) {
        if(providerClient == null)
            throw new RPCException("RedisRegister: client try to unregister. but provider client is null...");

        String addr = providerClient.registerMap.get(key);
        if(addr == null){
            logger.warn("RedisRegister: server have not register such service...");
            return;
        }
        String zsetKey = prefix.concat("/").concat(key);
        providerClient.commands.zrem(zsetKey,addr); // delete ele in redis
        providerClient.registerMap.remove(key);
        providerClient.commands.publish(key,"unregister:".concat(addr)); // notify invoker
        ScheduledFuture<?> future = providerClient.scheduleMap.get(key); // get future for cancel
        if(future == null)
            logger.warn("RedisRegister: unregister service= {}. but no schedule future found, which should not occur",key);
        else future.cancel(false); // end future
        providerClient.scheduleMap.remove(key);
    }

    // all subscribe use one command(connection), and repeat subscribe has no side-effect
    @Override
    public void subscribe(String key) {
        if(invokerClient == null)
            throw new RPCException("RedisRegister: client try to subscribe. but invoker client is null...");
        serviceMap.putIfAbsent(key, Collections.newSetFromMap(new ConcurrentHashMap<>()));
        invokerClient.pscommands.subscribe(key);
    }

    @Override
    public void unsubscribe(String key) {
        if(invokerClient == null)
            throw new RPCException("RedisRegister: client try to unsubscribe. but invoker client is null...");
        invokerClient.pscommands.unsubscribe(key);
        // clear the cache
        serviceMap.remove(key);
    }

    @Override
    public List<String> discovery(String key) {
        Set<String> hosts = serviceMap.get(key);
        if(hosts == null)
            throw new RPCException("ZKRegister: invoker have not yet subscribe such service >>> name = "+key);

        if(hosts.isEmpty()){
            return inquireRefresh(key); // refresh and inquire
        }
        else return new ArrayList<>(hosts);
    }

    @Override
    public List<String> inquireRefresh(String key){
        String zsetKey = prefix.concat("/").concat(key);
        List<String> hosts = invokerClient.commands.zrange(zsetKey,0,-1);
        if(hosts != null && !hosts.isEmpty()){
            Set<String> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
            set.addAll(hosts);
            serviceMap.put(key,set);
        }
        return hosts;
    }

    /**
     * for invoker: caz invoker and provider will use different register instances
     * invoker client should need two connections: one for PubSub, the other for zrange
     */
    private static class InvokerClient{
        RedisClient client;
        StatefulRedisPubSubConnection<String,String> psConnection;
        StatefulRedisConnection<String,String> connection;
        RedisPubSubCommands<String,String> pscommands;
        RedisCommands<String,String> commands;

        InvokerClient(String redisURI, RedisPubSubListener<String,String> listener){
            this.client = RedisClient.create(redisURI);
            this.psConnection = client.connectPubSub();
            this.pscommands = psConnection.sync();
            this.connection = client.connect();
            this.commands = connection.sync();
            psConnection.addListener(listener);
        }

        void stop(){
            if(psConnection != null)
                psConnection.close();
            if(connection != null)
                connection.close();
            if(client != null)
                client.shutdown();
        }

        boolean isActive(){
            return psConnection != null && connection != null && psConnection.isOpen() && connection.isOpen();
        }

    }

    private static class ProviderClient{
        RedisClient client;
        StatefulRedisConnection<String,String> connection;
        RedisCommands<String,String> commands;
        long refreshPeriod;
        private Map<String/*service name*/, String/*host address*/> registerMap = new ConcurrentHashMap<>();
        private Map<String/*service name*/, ScheduledFuture<?>/*used for cancel schedule task*/> scheduleMap = new ConcurrentHashMap<>();

        ProviderClient(String redisURI, long refreshPeriod){
            this.client = RedisClient.create(redisURI);
            this.connection = client.connect();
            this.commands = connection.sync();
            this.refreshPeriod = refreshPeriod;
        }

        void stop(){
            if(connection != null)
                connection.close();
            if(client != null)
                client.shutdown();
        }

        boolean isActive(){
            return connection != null && connection.isOpen();
        }

    }

    /**
     * clean redis key in schedule time
     * need choose suitable para (for cleanPeriod , validityPeriod and scanEachPeriod)
     */
    public static class Monitor{

        private ScheduledExecutorService schedule;

        private static final String INS = "refresh:placeholder";
        private long cleanPeriod;
        private long validityPeriod;

        private ScanCursor cursor = new ScanCursor("0",false);
        private ScanArgs args;

        private RedisClient client;
        private StatefulRedisConnection<String,String> connection;
        private RedisCommands<String,String> commands;

        /**
         *
         * @param env
         * @param cleanPeriod unit s
         * @param validityPeriod unit ms
         * @param scanEachPeriod
         * @param uri
         */
        public Monitor(String env, long cleanPeriod, long validityPeriod, int scanEachPeriod, String uri){
            String workspace = "minirpc".concat("/").concat(env);
            this.cleanPeriod = cleanPeriod;
            this.validityPeriod = validityPeriod;

            this.client = RedisClient.create(uri);
            this.connection = client.connect();
            this.commands = connection.sync();
            this.schedule = Executors.newSingleThreadScheduledExecutor();

            args = new ScanArgs().match(workspace.concat("/*")).limit(scanEachPeriod);
        }

        public void start(){
            schedule.scheduleAtFixedRate(()->{
                scanKey().forEach((String zsetKey)->{
                    if(zRemRangeByScore(zsetKey)) {
                        String[] tmps = zsetKey.split("/");
                        if(tmps.length == 3) {
                            String channel = tmps[2];
                            commands.publish(channel, INS);
                        }
                    }
                });
            },cleanPeriod,cleanPeriod,TimeUnit.SECONDS);
        }

        public void stop(){
            if(connection != null)
                connection.close();
            if(client != null)
                client.shutdown();
            if(schedule != null)
                schedule.shutdown();
        }

        private boolean zRemRangeByScore(String zsetKey){
            Range<Long> range = Range.from(Range.Boundary.unbounded(), Range.Boundary.excluding(System.currentTimeMillis()-validityPeriod));
            Long num = commands.zremrangebyscore(zsetKey,range);
            return num != null && num > 0;
        }

        private boolean zRemRangeByScore(String zsetKey, long min, long max){
            Range<Long> range = Range.from(Range.Boundary.including(min), Range.Boundary.excluding(max-validityPeriod));
            Long num = commands.zremrangebyscore(zsetKey,range);
            return num != null && num > 0;
        }

        private Set<String> scanKey(){
            KeyScanCursor<String> cur = commands.scan(cursor,args);
            cursor.setCursor(cur.getCursor()); // cursor's finish always be false;
            return new HashSet<>(cur.getKeys());
        }

    }
}
