package com.chorifa.minirpc;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import com.chorifa.minirpc.registry.impl.RedisRegistry;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class RedisTest {

    @Test
    public void testLettuce(){
        RedisClient redisClient = RedisClient.create("redis://localhost:6379");
        StatefulRedisConnection<String,String> connection = redisClient.connect();
        RedisCommands<String,String> syncCommands = connection.sync();

        syncCommands.set("testKey","Hello, Redis!");

        String s = syncCommands.get("testKey");
        System.out.println(s);

        connection.close();
        redisClient.shutdown();

    }

    @Test
    public void addKey(){
        RedisClient redisClient = RedisClient.create("redis://localhost:6379");
        StatefulRedisConnection<String,String> connection = redisClient.connect();
        RedisCommands<String,String> syncCommands = connection.sync();

        try {
            for(int i = 0; i < 1000; i++){
                for(int j = 0; j < 50; j++)
                    syncCommands.zadd("minirpc/test/"+i, System.currentTimeMillis()+ ThreadLocalRandom.current().nextInt(100000),String.valueOf(i*100+j));
            }
        }finally {
            connection.close();
            redisClient.shutdown();
        }
    }

    @Test
    public void testClean(){
        RedisRegistry.Monitor monitor = new RedisRegistry.Monitor(
                "test",10,20*1000,500,"redis://localhost:6379");
        try {
            monitor.start();
            TimeUnit.SECONDS.sleep(200);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            monitor.stop();
        }
    }

    @Test
    public void testCursor(){
        RedisClient redisClient = RedisClient.create("redis://localhost:6379");
        StatefulRedisConnection<String,String> connection = redisClient.connect();
        RedisCommands<String,String> syncCommands = connection.sync();

        ScanCursor cursor = new ScanCursor("0",false);
        ScanArgs args = new ScanArgs().match("test/*").limit(10);
        KeyScanCursor<String> nextCursor = syncCommands.scan(cursor,args);
        System.out.println(nextCursor.getCursor());
        nextCursor.getKeys().forEach(System.out::println);

        nextCursor = syncCommands.scan(nextCursor,args);
        System.out.println(nextCursor.getCursor());
        nextCursor.getKeys().forEach(System.out::println);

        connection.close();
        redisClient.shutdown();
    }

    @Test
    public void testZset(){
        RedisClient redisClient = RedisClient.create("redis://localhost:6379");
        StatefulRedisConnection<String,String> connection = redisClient.connect();
        RedisCommands<String,String> syncCommands = connection.sync();

        long expirePeriod = 30*1000; // 30s

        try {
            syncCommands.zadd("serviceKey",new ZAddArgs().xx(),System.currentTimeMillis(),"localhost:1010");
            syncCommands.zadd("serviceKey",System.currentTimeMillis()+expirePeriod,"localhost:9527");
            syncCommands.zadd("serviceKey",System.currentTimeMillis()+expirePeriod,"localhost:8086");
            List<String> list = syncCommands.zrange("serviceKey",0,-1);
            list.forEach(System.out::println);
            syncCommands.zrem("serviceKey","localhost:9527");
            list = syncCommands.zrange("serviceKey",0,-1);
            list.forEach(System.out::println);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            connection.close();
            redisClient.shutdown();
        }

    }

    @Test
    public void testMutliSub(){
        RedisClient client = RedisClient.create("redis://localhost:6379");
        StatefulRedisPubSubConnection<String,String> psConnection = client.connectPubSub();
        RedisPubSubCommands<String,String> commands = psConnection.sync();
        try {
            psConnection.addListener(new RedisPubSubListener<String, String>() {
                @Override
                public void message(String s, String s2) { // s is channel; s2 is message
                    System.out.println("channel: "+s+" get message: "+s2);
                }

                @Override
                public void message(String s, String k1, String s2) { // s is pattern; k1 is channel
                    System.out.println("pattern: "+s+" in channel: "+s+" get message: "+s2);
                }

                @Override
                public void subscribed(String s, long l) {

                }

                @Override
                public void psubscribed(String s, long l) {

                }

                @Override
                public void unsubscribed(String s, long l) {

                }

                @Override
                public void punsubscribed(String s, long l) {

                }
            });
            commands.subscribe("serviceKey");
            commands.subscribe("testKey");
            TimeUnit.SECONDS.sleep(60);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            psConnection.close();
            client.shutdown();
        }
    }

    @Test
    public void testPubSub(){
        Thread t1 = new Thread(()->{
            RedisPS[] subs = new RedisPS[3];
            for(int i = 0; i < subs.length; i++){
                subs[i] = new RedisPS();
                subs[i].init(true).subscribe("serviceKey").subscribe("serviceKey");
            }
            for (RedisPS redisPS : subs) {
                redisPS.subscribe("serviceKey");
            }
            try{
                TimeUnit.SECONDS.sleep(10);
                for (RedisPS redisPS : subs) {
                    redisPS.subscribe("serviceKey");
                }
                TimeUnit.SECONDS.sleep(30);
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                for(RedisPS sub : subs)
                    sub.shutdown();
            }
        });

        Thread t2 = new Thread(()->{
           RedisPS[] pubs = new RedisPS[3];
           for(int i = 0; i < 3; i++){
               pubs[i] = new RedisPS();
               pubs[i].init(false);
           }
           for(int i= 0; i < 3; i++){
               pubs[i].publish("serviceKey","localhost:808"+i);
               pubs[i].publish("testKey","localhost:808"+i);
           }
           for(RedisPS pub : pubs)
               pub.shutdown();
        });

        try {
            t1.start();
            Thread.sleep(1000);
            t2.start();
            t1.join();
            t2.join();
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    static class RedisPS{
        private RedisClient client = RedisClient.create("redis://localhost:6379");
        private StatefulRedisConnection<String,String> connection;
        private StatefulRedisPubSubConnection<String,String> psConnection;
        private RedisPubSubCommands<String,String> pscommands;

        RedisPS init(boolean isConsumer){
            if(isConsumer) {
                psConnection = client.connectPubSub();
                psConnection.addListener(new RedisPubSubListener<String, String>() {
                    @Override
                    public void message(String s, String s2) { // s is channel; s2 is message
                        System.out.println("channel: "+s+" get message: "+s2);
                    }

                    @Override
                    public void message(String s, String k1, String s2) { // s is pattern; k1 is channel
                        System.out.println("pattern: "+s+" in channel: "+s+" get message: "+s2);
                    }

                    @Override
                    public void subscribed(String s, long l) {

                    }

                    @Override
                    public void psubscribed(String s, long l) {

                    }

                    @Override
                    public void unsubscribed(String s, long l) {

                    }

                    @Override
                    public void punsubscribed(String s, long l) {

                    }
                });
                pscommands = psConnection.sync();
            }
            else
                connection = client.connect();
            return this;
        }

        RedisPS subscribe(String channel){
            pscommands.subscribe(channel);
            return this;
        }

        void publish(String channel, String msg){
            RedisCommands<String,String> commands = connection.sync();
            commands.publish(channel,msg);
        }

        void shutdown(){
            if(connection != null)
                connection.close();
            if(psConnection != null)
                psConnection.close();
            client.shutdown();
        }

    }

}
