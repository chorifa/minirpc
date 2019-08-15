# MEMO

## 待完成事项
- 测试javassist和jdk代理的速度(创建速度以及运行速度)
- 增加自定义注解，增加对springboot的支持
- 实现依赖注入DI的功能

## 架构总结
miniRPC分为4个大部分，其中invoker包代表了服务调用方，provider包代表了服务提供方，register包包含了注册中心，remoting包抽象了NIO通信的行为，util包定义了一些方法/结构模块

### Invoker
- invoker包下属DefaultRPCInvokerFactory维护一个结果Map，key为某次请求的ID，value为Future功能的RemotingFutureResponse对象，存放有request以及response。每次调用远程功能时（发送请求前），都会预先在该map内创建id-FutureResponse对；接收到回信后，NIO线程会根据FutureResponse是否存在CallBack字段，执行CallBack方法或是将response注入对应的FutureResponse中，并将该id-FutureResponse对从map中删除(所以外部要及时拿到这个引用)。DefaultRPCInvokerFactory自有一个线程池，callBack的执行会交给线程池，因此NIO线程可以放心的执行注入。  
- RPCReferenceManager主要完成创建代理类的功能，基于JDK的代理类Proxy.newProxyInstance()，实现其invoke方法。首先判断必要参数的合法性，比如想要调用的方法以及参数等，接着通过注册中心拿到某个提供方地址，创建Request，接着根据选择配置的SNYC，FUTURE或是CallBack，创建不同的RemotingFutureResponse，以及异常处理。  
DefaultRPCInvokerFactory位于ReferenceManager和Remoting之间，包含所有调用都需要进行的结果存放方法。RPCReferenceManager则是某种调用的代理实体。  
- RPCReferenceManger针对interface会使用JDK的Proxy创建代理类;针对(abstract)class会使用Javassist创建新的Class，具体而言会给新的Class增加Handler字段，所有abstract方法实际是调用这个Handler的invoke方法，另外将每个abstract方法的CtMethod保存为数组作为新的Class的methods字段，其中的CtMethod作为Handler的参数。由于没办法获取CtMethod对应的Method，只能创建新的InvokeHandler类型，同时里边parameterType和returnType也是CtClass类型而非Class类型，因此使用Class.forName()让jvm查找对应的类拿到Class，这里边可能有bug(比如CtClass不是用Class.forName的ClassLoader加载的，会找不到)。此外，JDK的proxy支持int,double等基本类型;JavassistGenerator不支持基本类型，暂时没有尝试去避开。 
- 关于泛型，在当前的代码中，服务端的泛型实例如何定义没有关系，返回的都是Object类型，由invoker内部强转。

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
[服务注册为什么需要AP而不是CP?](http://jm.taobao.org/2018/06/13/%E5%81%9A%E6%9C%8D%E5%8A%A1%E5%8F%91%E7%8E%B0%EF%BC%9F/?from=singlemessage)对服务发现而言，强一致性不是那么重要，最严重的结果就是流量不均匀以及可能失败重试；相对的，可用性反而更加重要，不应该因为服务中心的局部错误造成整体的不可用。   
#### Zookeeper
- ZookeeperRegister使用Curator作为客户端。内部维护一个serviceMap和registerMap。当register一个服务时，将Service的name作为子路径，address作为value，创建一个临时节点。创建某个服务的代理类时会提前订阅一个服务，创建PathChildrenCache，监控某个路径，通过设置listener在回调函数中进行增改删。在真实调用发送请求前会从serviceMap中拿到所有的address，由于使用监听回调，所以存在一定的延时，如果没有拿到address，则会主动的查询一次。serviceMap起到一个缓存的作用，即使register暴毙，也可以提供有限的服务。
- Zookeeper是分布式协调服务，其设计的目的在于保证数据在其管辖的所有服务间保持同步和一致。ZK的CP性:这是设计初的目的。如果集群中出现了网络分割故障(如交换机故障导致其下的子网不能互相访问)，若分区中的节点达不到选举leader的法定人数，ZK会将其剔除，外界就不能访问这些节点，无论其是否正常工作，此时不再提供写操作，到达这些节点的写操作被丢弃了。
- 脑裂:若一个集群分成两部分，互相不知道对方是否存活，各自推举除了一个leader，当分区合并时，就形成了脑裂。常见的解决措施有
    - 法定人数:大于半数的人同意时才能进行重新选举
    - 冗余通信:选择多种通信模式，防止某种通信失效
    - 共享资源:能看到(可读)共享资源的节点就在集群中，能获得锁(可写)共享资源的就是leader
- ZK选择第①种。一方面体现在选举leader时，法定人数为集群可用的最大容错数;一方面用在数据同步，只要有法定人数的节点保存了数据(写数据)就可以返回客户端写入成功，其余节点异步写入。这也是建议设置为奇数的原因，因为2n和2n+1的法定人数都是n+1，也就是奇数能容忍更多的错误。   
- 谷歌的etcd实现目的是分布式key-value数据库，使用Raft协议，因此也是CP





