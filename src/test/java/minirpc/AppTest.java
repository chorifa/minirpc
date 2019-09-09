package minirpc;

import static org.junit.Assert.assertTrue;

import minirpc.api.HelloService;
import minirpc.api.HelloServiceImpl;
import minirpc.api.TestService;
import minirpc.api.TestServiceImpl;
import minirpc.api.param.NageDO;
import minirpc.api.param.TestDO;
import minirpc.api.param.UserDO;
import minirpc.invoker.DefaultRPCInvokerFactory;
import minirpc.invoker.reference.RPCReferenceManager;
import minirpc.invoker.reference.RPCReferenceManagerOld;
import minirpc.invoker.reference.ReferenceManagerBuilder;
import minirpc.invoker.type.FutureType;
import minirpc.invoker.type.InvokeCallBack;
import minirpc.invoker.type.SendType;
import minirpc.provider.DefaultRPCProviderFactory;
import minirpc.register.RegisterConfig;
import minirpc.register.RegisterType;
import minirpc.remoting.RemotingType;
import minirpc.utils.serialize.SerialType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
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
    public void testProvider(){
        RegisterConfig config = new RegisterConfig();
        config.setRegisterAddress("redis://localhost:6379");
        DefaultRPCProviderFactory providerFactory = new DefaultRPCProviderFactory()
                .init(RegisterType.REDIS, config ,8086)
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
        DefaultRPCProviderFactory providerFactory = new DefaultRPCProviderFactory().init(RemotingType.NETTY,SerialType.HESSIAN)
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
        RegisterConfig config = new RegisterConfig();
        config.setInvoker(true);
        config.setRegisterAddress("redis://localhost:6379");
        DefaultRPCInvokerFactory invokerFactory = new DefaultRPCInvokerFactory(RegisterType.REDIS, config);
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
