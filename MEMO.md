# MEMO

## 待完成事项
- 增加其他动态代理的支持如CGLIB，asm
- 增加自定义注解，增加对springboot的支持

## 架构总结
miniRPC分为4个大部分，其中invoker包代表了服务调用方，provider包代表了服务提供方，register包包含了注册中心，remoting包抽象了NIO通信的行为，util包定义了一些方法/结构模块

### Invoker
- invoker包下属DefaultRPCInvokerFactory维护一个结果Map，key为某次请求的ID，value为Future功能的RemotingFutureResponse对象，存放有request以及response。每次调用远程功能时（发送请求前），都会预先在该map内创建id-FutureResponse对；接收到回信后，NIO线程会根据FutureResponse是否存在CallBack字段，执行CallBack方法或是将response注入对应的FutureResponse中，并将该id-FutureResponse对从map中删除(所以外部要及时拿到这个引用)。DefaultRPCInvokerFactory自有一个线程池，callBack的执行会交给线程池，因此NIO线程可以放心的执行注入。  
- RPCReferenceManager主要完成创建代理类的功能，基于JDK的代理类Proxy.newProxyInstance()，实现其invoke方法。首先判断必要参数的合法性，比如想要调用的方法以及参数等，接着通过注册中心拿到某个提供方地址，创建Request，接着根据选择配置的SNYC，FUTURE或是CallBack，创建不同的RemotingFutureResponse，以及异常处理。  
DefaultRPCInvokerFactory位于ReferenceManager和Remoting之间，包含所有调用都需要进行的结果存放方法。RPCReferenceManager则是某种调用的代理实体。 

### Provider
- provider包下属DefaultRPCProviderFactory定义了serviceMap和invokeService方法，启动前通过addService方法将支持的method存放到这个map中，Server接收到请求后会调用该对象的该方法运行服务，该方法实际通过反射实现。   
DefaultRPCProviderFactory并不是真正的服务提供者，其内部拥有Server字段，Server才是真正的提供者。   

### Remoting
#### Server
- class Server是一个抽象类，定义了一些公共的startCallBack和stopCallBack方法；留出了start和stop方法由不同类实现
- 实现类都持有一个Thread，由这个线程去真正的创建NettyServer。这么做是为了方便在外部(Server中)通过stop方法中断该thread，在中断处理中进行NettyEventLoop的关闭。
#### Client
- Client和ClientInstance都是抽象类，Client和ReferenceManger对应，ClientInstance和InvokerFactory对应。Client包含调用的必要信息，通过ClientInstance类的静态asyncSend方法进行发送。ClientInstance定义了一个ConcurrentHashMap(String,ClientInstance对)，作为连接池用来缓存所有连接。
- 静态asyncSend方法需要完成拿到ClientInstance通信实体，以及调用ClientInstance对象的send方法完成真实发送。先根据address取得对应的连接实体ClientInstance，如果没有就新建一个连接。作为连接池的map有一个对应的lockMap，同样是address作为key，lockMap的元素一旦创建不允许更改。新建连接时会先拿到address对应lock的监视器锁，然后二次判断没有连接时再创建。
- ClientInstance类的实现类需要重载send,close等方法
#### Protocol
借助Netty支持TCP(socket),HTTP1,以及HTTP2三种协议。  
___(阐述HTTP2的协商以及解析过程，Netty对HTTP和HTTP2的处理过程)___   
由于HTTP1默认是单个TCP在同一时刻只能处理一个请求，为了正确接收，request的消息体内会带有id标识。而HTTP2由于有复用机制，在HTTP2的附加头中定义StreamID就可以区分各个request，并且各个request可以并行处理。

### Register
_TODO: zookeeper/redis/load balance/CAP_   
