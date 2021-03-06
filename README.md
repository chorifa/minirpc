# MiniRPC: a concise rpc implement with multi functions  

A concise RPC framework based on Netty Transport with Zookeeper/Redis as the registry.

- jdk 9+ (8 for RPC, 9 for mini0q)  

## Features  

- Exploit Netty(TCP/HTTP/HTTP2) in the remoting module  
- Use [mini0q](https://github.com/chorifa/mini0q)(a Disruptor-like Producer-Consumer model) to substitute JDK ExecutorService for blocking service or invoke-callback method  
- Add simple EventBus support and provide the basic PubSub function, based on which, method calls can be bound to a fixed EventLoop(which means thread-safe)  
- Apply zookeeper/or/redis as different registries  
- Implement various load-balance strategies: random, round, consistentHash, LFU, LRU, LeastUnreplied, etc.  
- Provide different invoke-method: sync, future, call-back  
- Offer diverse serial approaches, including Protostuff, Hessian, JSON, etc.  
- Support proxy for interface(default, jdk-impl) and class(javassist-impl)

## Performance Test  

Description: one producer(thread) along with one invoker(thread). Invoker continuously invokes simple echo service(1kB data size) 100000(10w) times. Both MiniRPC and Dubbo use Hessian2 serialization.

| **Time Consumption** | **MiniRPC** | **Dubbo** |
|:------------------------------:|:-----------:|:---------:|
| Sync \(Request after Response\) | 16611ms     | 21899ms   |
| Async \(Future\)               | 4460ms      | 5218ms    |

## Example

If there is a TestService or HelloService like:  

``` java
public interface TestService<T> {
    T echo(T a);
}

public abstract class HelloSercice<T> {
    public abstract T echo(T a);
}
```

And their implements like:

``` java
public class TestServiceImpl<T> implements TestService<T> {
    @Override
    public T echo(T a) {
        return a;
    }
}

public class HelloServiceImpl<T> extends HelloService<T> {
    @Override
    public T echo(T a) {
        return a;
    }
}
```

Provider can provide service like:

``` java
TestServiceImpl<String> DEFAULT_TEST_SERVICE = new TestServiceImpl<String>();

DefaultRPCProviderFactory providerFactory1 = new DefaultRPCProviderFactory().init(RemotingType.NETTY, 8081)
        // NON_BLOCKING means such method will not block EventLoop
        .addService(HelloService.class.getName(),null, new HelloServiceImpl<Integer>(), ServiceCtl.NON_BLOCKING)
        // BIND means such method will bind to fix EventLoop, Hence, it is thread-safe
        // Note that, if BIND is used, such method should not block
        .addService(TestService.class.getName(),null, DEFAULT_TEST_SERVICE, ServiceCtl.BIND);

DefaultRPCProviderFactory providerFactory2 = new DefaultRPCProviderFactory().init(RemotingType.NETTY_HTTP, 8082)
        // BLOCKING means such method will block EventLoop
        .addService(HelloService.class.getName(),null, new HelloServiceImpl<Integer>(), ServiceCtl.BLOCKING)
        .addService(TestService.class.getName(),null, DEFAULT_TEST_SERVICE);

DefaultRPCProviderFactory providerFactory3 = new DefaultRPCProviderFactory().init(RemotingType.NETTY_HTTP2, 8083)
        .addService(HelloService.class.getName(),null, new HelloServiceImpl<Integer>(), ServiceCtl.NON_BLOCKING)
        .addService(TestService.class.getName(),null, DEFAULT_TEST_SERVICE);

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
```

Invoker can call service like:

``` java
RPCReferenceManager manager1 = ReferenceManagerBuilder.init()
        .applySerializer(SerialType.HESSIAN)
        .applyRemotingType(RemotingType.NETTY)
        .applySendType(SendType.CALLBACK)
        .forService(TestService.class).forAddress("localhost:8081").build();
manager1.setCallBack(new InvokeCallBack<Object>() {
    @Override
    public void onSuccess(Object result) throws Exception {
        System.out.println("This is a call back: "+result);
    }

    @Override
    public void onException(Throwable t) throws Exception {
        System.err.println(t.getMessage());
    }
}, false); // false means such call back will not block EventLoopThread

RPCReferenceManager manager2 = ReferenceManagerBuilder.init()
        .applySerializer(SerialType.PROTOSTUFF)
        .applyRemotingType(RemotingType.NETTY_HTTP)
        .applySendType(SendType.FUTURE) // future type
        .forService(TestService.class).forAddress("localhost:8082").build();

RPCReferenceManager manager3 = ReferenceManagerBuilder.init()
        .applyRemotingType(RemotingType.NETTY_HTTP2)
        .applySendType(SendType.SYNC) // sync type
        .forService(TestService.class).forAddress("localhost:8083").build();

TestService<String> service1 = manager1.get();
TestService<String> service2 = manager2.get();
TestService<String> service3 = manager3.get();

service1.echo("call echo method in Callback way.");
service2.echo("call echo method in Future way.");
CompletableFuture<String> cf = RemotingFutureAdaptor.getCompletableFuture(); // get future
String s = service3.echo("call echo method in Sync way.");
System.out.println(s);
System.out.println(cf.get());

try {
    TimeUnit.SECONDS.sleep(20);
}catch (InterruptedException ignore) {
}finally {
    manager1.getInvokerFactory().stop();
}
```

## Note  

- The remoting module refers to [xuxueli/xxl-rpc](https://github.com/xuxueli/xxl-rpc)  
- Load-balance methods refer to [Dubbo](https://github.com/apache/dubbo)  
- The mini0q is also implemented by myself, specifically refers to [Disruptor](https://github.com/LMAX-Exchange/disruptor)