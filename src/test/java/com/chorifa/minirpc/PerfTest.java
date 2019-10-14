package com.chorifa.minirpc;

import com.chorifa.minirpc.api.*;
import com.chorifa.minirpc.invoker.reference.RPCReferenceManager;
import com.chorifa.minirpc.invoker.reference.ReferenceManagerBuilder;
import com.chorifa.minirpc.invoker.type.RemotingFutureAdaptor;
import com.chorifa.minirpc.invoker.type.SendType;
import com.chorifa.minirpc.provider.DefaultRPCProviderFactory;
import com.chorifa.minirpc.remoting.RemotingType;
import com.chorifa.minirpc.utils.serialize.SerialType;
import org.apache.dubbo.config.*;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PerfTest {

    @Test
    public void requestPerf4miniRPCProducer(){
        DefaultRPCProviderFactory providerFactory = new DefaultRPCProviderFactory().init(RemotingType.NETTY, SerialType.HESSIAN)
                .addService(PerfService.class.getName(),null, new PerfServiceImpl());
        providerFactory.start();
        try{
            TimeUnit.MINUTES.sleep(1);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            providerFactory.stop();
        }
    }

    /**
     * one sync: seq 100000(10w) time 16611ms (16s)
     *                           time 16347ms
     */
    @Test
    public void requestPerf4miniRPCInvokerSync(){
        RPCReferenceManager manager = ReferenceManagerBuilder.init()
                .forService(PerfService.class).forAddress("localhost:8086").build();
        manager.getInvokerFactory().start();

        String s = "abcdefghijklmnopqrstuvwxyz1234567890";
        StringBuilder sb = new StringBuilder(s);
        for(int i = 0; i < 27; i++)
            sb.append(s);
        s = sb.toString();

        PerfService service = manager.get();

        int reqLoop = 100000;
        long start = System.currentTimeMillis();
        for(int i = 0; i < reqLoop; i++)
            service.tryPerfTest(s);
        long end = System.currentTimeMillis();
        System.out.println("minirpc: req - "+reqLoop+"  time - "+(end-start)+" ms");

        try {
            TimeUnit.SECONDS.sleep(5);
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            manager.getInvokerFactory().stop();
        }
    }

    /**
     * one sync: seq 100000(10w) time 5021ms (5s)
     *                           time 4460ms (4s)
     */
    @Test
    public void requestPerf4miniRPCInvokerAsync(){
        RPCReferenceManager manager = ReferenceManagerBuilder.init()
                .applySendType(SendType.FUTURE)
                .forService(PerfService.class).forAddress("localhost:8086").build();
        manager.getInvokerFactory().start();

        String s = "abcdefghijklmnopqrstuvwxyz1234567890";
        StringBuilder sb = new StringBuilder(s);
        for(int i = 0; i < 27; i++)
            sb.append(s);
        s = sb.toString();

        PerfService service = manager.get();
        int reqLoop = 100000;
        List<CompletableFuture<String>> list = new ArrayList<>(reqLoop);

        long start = System.currentTimeMillis();
        for(int i = 0; i < reqLoop; i++) {
            service.tryPerfTest(s);
            list.add(RemotingFutureAdaptor.getCompletableFuture());
        }
        CompletableFuture<Void> cf = CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
        cf.join();
        long end = System.currentTimeMillis();
        for(CompletableFuture<String> ctf : list) {
            try{
                String tmp = ctf.get();
                if(tmp == null || !tmp.equals(s))
                    System.out.println("error equal.");
            }catch (Exception e){
                System.out.println("error get.");
            }
        }
        System.out.println("minirpc: req - "+reqLoop+"  time - "+(end-start)+" ms");

        try {
            TimeUnit.SECONDS.sleep(5);
        }catch (InterruptedException e){
            e.printStackTrace();
        }finally {
            manager.getInvokerFactory().stop();
        }
    }

    @Test
    public void requestPerf4DubboProducer(){
        ServiceConfig<PerfServiceImpl> service = new ServiceConfig<>();
        service.setApplication(new ApplicationConfig("perf-test-producer"));
        service.setRegistry(new RegistryConfig("N/A"));

        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("dubbo");
        protocol.setPort(8086);
        protocol.setThreads(200);
        service.setProtocol(protocol);

        service.setInterface(PerfService.class);
        service.setRef(new PerfServiceImpl());
        service.export();
        System.out.println("producer start.");
        try {
            TimeUnit.MINUTES.sleep(1L);
        }catch (InterruptedException ignore){
        }
        System.out.println("done.");
    }

    /**
     * one sync: req 100000(10w) time 22356ms (22s)
     *                           time 21899ms
     */
    @Test
    public void requestPerf4DubboInvokerSync(){
        ReferenceConfig<PerfService> ref = new ReferenceConfig<>();
        ref.setApplication(new ApplicationConfig("perf-test-invoker"));
        ref.setInterface(PerfService.class);
        ref.setUrl("dubbo://localhost:8086/com.chorifa.minirpc.api.PerfService");

        PerfService service = ref.get();

        String s = "abcdefghijklmnopqrstuvwxyz1234567890";
        StringBuilder sb = new StringBuilder(s);
        for(int i = 0; i < 27; i++)
            sb.append(s);
        s = sb.toString();

        int reqLoop = 100000;
        long start = System.currentTimeMillis();
        for(int i = 0; i < reqLoop; i++)
            service.tryPerfTest(s);
        long end = System.currentTimeMillis();
        System.out.println("dubbo: req - "+reqLoop+"  time - "+(end-start)+" ms");
    }

    /**
     * one async: req 100000(10w) time 6308ms (6s)
     *                            time 5218ms (5s)
     */
    @Test
    public void requestPerf4DubboInvokerAsync() {
        ReferenceConfig<PerfService> ref = new ReferenceConfig<>();
        ref.setApplication(new ApplicationConfig("perf-test-invoker"));
        ref.setInterface(PerfService.class);
        ref.setUrl("dubbo://localhost:8086/com.chorifa.minirpc.api.PerfService");
        ref.setAsync(true);
        ref.setTimeout(1000*1000); // 1000s

        PerfService service = ref.get();

        String s = "abcdefghijklmnopqrstuvwxyz1234567890";
        StringBuilder sb = new StringBuilder(s);
        for(int i = 0; i < 27; i++)
            sb.append(s);
        s = sb.toString();

        int reqLoop = 100000;
        List<CompletableFuture<String>> list = new ArrayList<>(reqLoop);
        long start = System.currentTimeMillis();
        for(int i = 0; i < reqLoop; i++) {
            service.tryPerfTest(s);
            CompletableFuture<String> f =  RpcContext.getContext().getCompletableFuture();
            list.add(f);
        }
        CompletableFuture<Void> cf = CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
        cf.join();
        long end = System.currentTimeMillis();
        for(CompletableFuture<String> ctf : list) {
            try{
                String tmp = ctf.get();
                if(tmp == null || !tmp.equals(s))
                    System.out.println("error equal.");
            }catch (Exception e){
                System.out.println("error get.");
            }
        }
        System.out.println("dubbo: req - "+reqLoop+"  time - "+(end-start)+" ms");
    }

    @Test
    public void test(){
        CompletableFuture cf = new CompletableFuture();
        String s = "abcdefghijklmnopqrstuvwxyz1234567890";
        StringBuilder sb = new StringBuilder(s);
        for(int i = 0; i < 27; i++)
            sb.append(s);
        s = sb.toString();
        System.out.println(s.getBytes().length);
    }

    @Test
    public void testComF(){
        CompletableFuture<String> cf = new CompletableFuture<>();
        Thread t = new Thread(()->{
            try {
                String s = cf.get();
                System.out.println("get future result: "+s);
            }catch (Exception e){
                System.out.println("get future result failed.");
            }
        });
        t.start();
        try {
            TimeUnit.SECONDS.sleep(10);
        }catch (InterruptedException ignore){}
        System.out.println("i will put result in future");
        cf.complete("Hello World");
        System.out.println("already put result in future");
    }

}
