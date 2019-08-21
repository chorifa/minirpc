MiniRPC: a concise rpc implement with multi-basic functions
===
A concise RPC framework based on Netty Transport with Zookeeper/Redis as the registry.
- jdk 8+

Features
-----------------------------------------------------------
- Exploit Netty(TCP/HTTP/HTTP2) in the remoting module
- Apply zookeeper/or/redis as different registries   
- Implement various load-balance strategys: random, round, consistentHash, LFU, LRU, LeastUnreplied, etc.  
- Provide different invoke-method: sync, future, call-back  
- Offer diverse serial approaches, including Protostuff, Hessian, JSON, etc.   
- Support proxy for interface(default, jdk-impl) and class(javassist-impl)

#### Note:  
- the remoting module refers to [xuxueli/xxl-rpc](https://github.com/xuxueli/xxl-rpc)  
- load-balance methods refer to [dubbo](https://github.com/apache/dubbo)  
