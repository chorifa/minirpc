package com.chorifa.minirpc;

import static org.junit.Assert.assertTrue;

import com.chorifa.minirpc.api.HelloService;
import com.chorifa.minirpc.api.HelloServiceImpl;
import com.chorifa.minirpc.api.TestService;
import com.chorifa.minirpc.api.TestServiceImpl;
import com.chorifa.minirpc.api.param.NageDO;
import com.chorifa.minirpc.api.param.TestDO;
import com.chorifa.minirpc.api.param.UserDO;
import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.invoker.reference.RPCReferenceManager;
import com.chorifa.minirpc.invoker.reference.RPCReferenceManagerOld;
import com.chorifa.minirpc.invoker.type.FutureType;
import com.chorifa.minirpc.invoker.type.InvokeCallBack;
import com.chorifa.minirpc.invoker.type.RemotingFutureAdaptor;
import com.chorifa.minirpc.invoker.type.SendType;
import com.chorifa.minirpc.provider.DefaultRPCProviderFactory;
import com.chorifa.minirpc.registry.RegistryConfig;
import com.chorifa.minirpc.registry.RegistryType;
import com.chorifa.minirpc.remoting.RemotingType;
import com.chorifa.minirpc.utils.serialize.SerialType;
import com.chorifa.minirpc.invoker.reference.ReferenceManagerBuilder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    /**
     * Rigorous Test :-)
     */

    @Test
    public void testMultiProvider(){
        DefaultRPCProviderFactory providerFactory1 = new DefaultRPCProviderFactory().init(RemotingType.NETTY, SerialType.HESSIAN, 8081)
                .addService(HelloService.class.getName(),null, new HelloServiceImpl<Integer>())
                .addService(TestService.class.getName(),null, new TestServiceImpl<String>());
        DefaultRPCProviderFactory providerFactory2 = new DefaultRPCProviderFactory().init(RemotingType.NETTY, SerialType.HESSIAN, 8082)
                .addService(HelloService.class.getName(),null, new HelloServiceImpl<Integer>())
                .addService(TestService.class.getName(),null, new TestServiceImpl<String>());
        DefaultRPCProviderFactory providerFactory3 = new DefaultRPCProviderFactory().init(RemotingType.NETTY, SerialType.HESSIAN, 8083)
                .addService(HelloService.class.getName(),null, new HelloServiceImpl<Integer>())
                .addService(TestService.class.getName(),null, new TestServiceImpl<String>());
        providerFactory1.start();
        providerFactory2.start();
        providerFactory3.start();
        try{
            TimeUnit.MINUTES.sleep(1);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            providerFactory1.stop();
            providerFactory2.stop();
            providerFactory3.stop();
        }
    }

    @Test
    public void testMultiInvoker() throws ExecutionException, InterruptedException {
        RPCReferenceManager manager1 = ReferenceManagerBuilder.init()
                .applySendType(SendType.FUTURE)
                .forService(TestService.class).forAddress("localhost:8081").build();
        RPCReferenceManager manager2 = ReferenceManagerBuilder.init()
                .applySendType(SendType.FUTURE)
                .forService(TestService.class).forAddress("localhost:8082").build();
        RPCReferenceManager manager3 = ReferenceManagerBuilder.init()
                .applySendType(SendType.FUTURE)
                .forService(TestService.class).forAddress("localhost:8083").build();

        TestService<UserDO> service1 = manager1.get();
        TestService<UserDO> service2 = manager2.get();
        TestService<UserDO> service3 = manager3.get();

        NageDO nageDO = new NageDO();
        nageDO.age=10; nageDO.name="jiecheng Chong";
        List<String> likes = new ArrayList<>();
        likes.add("apple");
        likes.add("egg");

        service1.show(nageDO, likes);
        CompletableFuture<UserDO> cf1 = RemotingFutureAdaptor.getCompletableFuture();

        service2.show(nageDO, likes);
        CompletableFuture<UserDO> cf2 = RemotingFutureAdaptor.getCompletableFuture();

        service3.show(nageDO, likes);
        CompletableFuture<UserDO> cf3 = RemotingFutureAdaptor.getCompletableFuture();

        UserDO userDO1 = cf1.get();
        UserDO userDO2 = cf2.get();
        UserDO userDO3 = cf3.get();
        System.out.println(userDO1);
        System.out.println(userDO2);
        System.out.println(userDO3);
        try {
            TimeUnit.SECONDS.sleep(5);
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            manager1.getInvokerFactory().stop();
        }
    }

    @Test
    public void testProvider(){
        RegistryConfig config = new RegistryConfig();
        config.setRegisterAddress("redis://localhost:6379");
        DefaultRPCProviderFactory providerFactory = new DefaultRPCProviderFactory()
                .init(RegistryType.REDIS, config ,8086)
                .addService(HelloService.class.getName(),null, new HelloServiceImpl<Integer>())
                .addService(TestService.class.getName(),null, new TestServiceImpl());
        providerFactory.start();
        try{
            TimeUnit.MINUTES.sleep(1);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            providerFactory.stop();
        }

    }

    @Test
    public void testDefaultProvider(){
        DefaultRPCProviderFactory providerFactory = new DefaultRPCProviderFactory().init(RemotingType.NETTY, SerialType.HESSIAN)
                .addService(HelloService.class.getName(),null, new HelloServiceImpl<Integer>())
                .addService(TestService.class.getName(),null, new TestServiceImpl<String>());
        providerFactory.start();
        try{
            TimeUnit.MINUTES.sleep(1);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            providerFactory.stop();
        }

    }

    @Test
    public void testInvokerNewCallBack() throws ExecutionException, InterruptedException {
        RPCReferenceManager manager = ReferenceManagerBuilder.init()
                .applySendType(SendType.CALLBACK)
                .forService(TestService.class).forAddress("localhost:8086").build();
        manager.setCallBack(new InvokeCallBack<Object>() {
            @Override
            public void onSuccess(Object result) throws Exception {
                System.out.println(result);
            }

            @Override
            public void onException(Throwable t) throws Exception {
                t.printStackTrace();
            }
        });
        TestService<UserDO> service = manager.get();
        NageDO nageDO = new NageDO();
        nageDO.age=10; nageDO.name="jiecheng Chong";
        List<String> likes = new ArrayList<>();
        likes.add("apple");
        likes.add("egg");
        service.show(nageDO, likes);
        UserDO userDO = new UserDO();
        userDO.like = likes;
        userDO.age = 19;
        userDO.name = "jieCheng Chong";
        service.echo(userDO);
        try {
            TimeUnit.SECONDS.sleep(5);
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            manager.getInvokerFactory().stop();
        }
    }

    @Test
    public void testInvokerCompletableFuture() throws ExecutionException, InterruptedException {
        RPCReferenceManager manager = ReferenceManagerBuilder.init()
                .applySendType(SendType.FUTURE)
                .forService(TestService.class).forAddress("localhost:8086").build();
        TestService<UserDO> service = manager.get();
        NageDO nageDO = new NageDO();
        nageDO.age=10; nageDO.name="jiecheng Chong";
        List<String> likes = new ArrayList<>();
        likes.add("apple");
        likes.add("egg");
        service.show(nageDO, likes);
        CompletableFuture<UserDO> cf = RemotingFutureAdaptor.getCompletableFuture();
        UserDO userDO = cf.get();
        System.out.println(userDO);
        userDO = service.echo(userDO);
        cf = RemotingFutureAdaptor.getCompletableFuture();
        System.out.println(cf.get());
        try {
            TimeUnit.SECONDS.sleep(5);
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            manager.getInvokerFactory().stop();
        }
    }

    @Test
    public void testInvokerWithRegistry(){
        RegistryConfig config = new RegistryConfig();
        config.setRegisterAddress("redis://localhost:6379");
        config.setInvoker(true);
        DefaultRPCInvokerFactory factory = new DefaultRPCInvokerFactory(RegistryType.REDIS,config);
        factory.start();
        RPCReferenceManager manager = ReferenceManagerBuilder.init()
                .forService(HelloService.class).applyInvokeFactory(factory).build();

        HelloService<UserDO> service = manager.get();
        NageDO nageDO = new NageDO();
        nageDO.age=10; nageDO.name="jiecheng Chong";
        List<String> likes = new ArrayList<>();
        likes.add("apple");
        likes.add("egg");
        service.sayHi(123);
        String s = service.sayHello("jiecheng Chong", 25);
        System.out.println(s);
        UserDO userDO = service.show(nageDO, likes);
        System.out.println(userDO);
        userDO = service.echo(userDO);
        System.out.println(userDO);
        try {
            TimeUnit.SECONDS.sleep(5);
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            factory.stop();
        }
    }

    @Test
    public void testInvokerBuilder(){
        RPCReferenceManager manager = ReferenceManagerBuilder.init()
                .forService(HelloService.class).forAddress("localhost:8086").build();
        manager.getInvokerFactory().start();
        HelloService<UserDO> service = manager.get();
        NageDO nageDO = new NageDO();
        nageDO.age=10; nageDO.name="jiecheng Chong";
        List<String> likes = new ArrayList<>();
        likes.add("apple");
        likes.add("egg");
        service.sayHi(123);
        String s = service.sayHello("jiecheng Chong", 25);
        System.out.println(s);
        UserDO userDO = service.show(nageDO, likes);
        System.out.println(userDO);
        userDO = service.echo(userDO);
        System.out.println(userDO);
        try {
            TimeUnit.SECONDS.sleep(5);
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            manager.getInvokerFactory().stop();
        }
    }

    @Test
    public void TestInvokerInterface(){
        RPCReferenceManager manager = ReferenceManagerBuilder.init()
                .forService(TestService.class).forAddress("localhost:8086").build();
        TestService<UserDO> service = manager.get();
        NageDO nageDO = new NageDO();
        nageDO.age=10; nageDO.name="jiecheng Chong";
        List<String> likes = new ArrayList<>();
        likes.add("apple");
        likes.add("egg");
        UserDO userDO = service.show(nageDO, likes);
        System.out.println(userDO);
        userDO = service.echo(userDO);
        System.out.println(userDO);
        try {
            TimeUnit.SECONDS.sleep(5);
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            manager.getInvokerFactory().stop();
        }
    }

    @Test
    public void testInvoker(){
        RegistryConfig config = new RegistryConfig();
        config.setInvoker(true);
        config.setRegisterAddress("redis://localhost:6379");
        DefaultRPCInvokerFactory invokerFactory = new DefaultRPCInvokerFactory(RegistryType.REDIS, config);
        try{
            invokerFactory.start(); // start the register
            RPCReferenceManagerOld manager = new RPCReferenceManagerOld(TestService.class, null,
                    null,RemotingType.NETTY, SendType.CALLBACK,SerialType.HESSIAN,invokerFactory); // subscribe
            manager.setCallBack(new InvokeCallBack<UserDO>() {
                @Override
                public void onSuccess(UserDO s) throws Exception {
                    System.out.println("execute success. get result :>>> "+s);
                }

                @Override
                public void onException(Throwable t) throws Exception {
                    System.out.println("execute failed. get error msg :>>>");
                    t.printStackTrace();
                }
            });
            NageDO nageDO = new NageDO();
            nageDO.age=10; nageDO.name="jiecheng Chong";
            List<String> likes = new ArrayList<>();
            likes.add("apple");
            likes.add("egg");
            TestService<UserDO> service = (TestService<UserDO>) manager.get();
            service.show(nageDO,likes);
            UserDO userDO = new UserDO();
            userDO.age = 22; userDO.name = "jiecheng Chong"; userDO.like = likes;
            service.echo(userDO);
            TimeUnit.SECONDS.sleep(5); // wait for call back
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            invokerFactory.stop();
        }
    }

    //@Test
    public void testSerialize(){
        TestDO testDO = new TestDO();
        List<String> list = new ArrayList<>();
        list.add("apple"); list.add("orange");
        String s = "test";
        testDO.args = new Object[]{list,s};
        testDO.likes = list;
        byte[] data = SerialType.FASTJSON.getSerializer().serialize(testDO);
        TestDO res = SerialType.FASTJSON.getSerializer().deserialize(data,TestDO.class);
        List<String> l = res.likes;
        l.forEach(System.out::print);
        l = (List<String>)res.args[0];
        l.forEach(System.out::print);
        System.out.println((String) (res.args[1]));
    }

    @Test
    public void testInvokerSerializeOrRemoting(){
        try{
            RPCReferenceManagerOld manager = new RPCReferenceManagerOld(TestService.class, RemotingType.NETTY_HTTP2, SendType.CALLBACK, SerialType.HESSIAN);
            manager.setCallBack(new InvokeCallBack<UserDO>() {
                @Override
                public void onSuccess(UserDO s) throws Exception {
                    System.out.println("execute success. get result :>>> "+s);
                }

                @Override
                public void onException(Throwable t) throws Exception {
                    System.out.println("execute failed. get error msg :>>>");
                    t.printStackTrace();
                }
            });
            NageDO nageDO = new NageDO();
            nageDO.age=10; nageDO.name="jiecheng Chong";
            List<String> likes = new ArrayList<>();
            likes.add("apple");
            likes.add("egg");
            TestService<UserDO> service = (TestService<UserDO>) manager.get();
            service.show(nageDO,likes);
            UserDO userDO = new UserDO();
            userDO.age = 22; userDO.name = "jiecheng Chong"; userDO.like = likes;
            service.echo(userDO);
            TimeUnit.SECONDS.sleep(5); // wait for call back
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            DefaultRPCInvokerFactory.getInstance().stop();
        }
    }

    @Test
    public void testInvokerCallBack(){
        try {
            RPCReferenceManagerOld manager = new RPCReferenceManagerOld(TestService.class, SendType.CALLBACK, SerialType.JACKSON);
            manager.setCallBack(new InvokeCallBack<String>() {
                @Override
                public void onSuccess(String s) throws Exception {
                    System.out.println("execute success. get result :>>> "+s);
                }

                @Override
                public void onException(Throwable t) throws Exception {
                    System.out.println("execute failed. get error msg :>>>");
                    t.printStackTrace();
                }
            });
            TestService<String> service = (TestService<String>) manager.get();
            service.echo("jiecheng Chong");
            TimeUnit.SECONDS.sleep(5); // wait for call back
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            DefaultRPCInvokerFactory.getInstance().stop();
        }
    }


    public void testInvokerGeneric(){
        try {
            RPCReferenceManagerOld manager = new RPCReferenceManagerOld(TestService.class);
            TestService<String> service = (TestService<String>) manager.get();
            String s = service.echo("jiecheng Chong");
            System.out.println(s);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            DefaultRPCInvokerFactory.getInstance().stop();
        }
    }


    public void testInvokerMulti(){
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                RPCReferenceManagerOld manager = new RPCReferenceManagerOld(TestService.class, SendType.FUTURE);
                try {
                    TestService service = (TestService) manager.get();
        /*
        NageDO nageDO = new NageDO();
        nageDO.age = 10; nageDO.name = "jiecheng Chong";
        List<String> likes = new ArrayList<>();
        likes.add("apple");
        likes.add("egg");
        service.show(nageDO,likes);
         */
                    for (int j = 0; j < 10; j++) {
                        service.sayHi();
                        Future<String> future = FutureType.getFuture();
                        System.out.println("times: " + j + " --->>> " + future.get(5, TimeUnit.MINUTES));
                        Thread.yield();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        try{
            for(int i = 0; i < 10; i++)
                threads[i].start();
            for(int i = 0; i < 10; i++)
                threads[i].join();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            DefaultRPCInvokerFactory.getInstance().stop();
            System.out.println("done");
        }

    }

    @Test
    public void shouldAnswerWithTrue()
    {
        assertTrue( true );
    }
}
