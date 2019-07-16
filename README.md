miniRPC: a concise rpc implement with multi-basic functions
-----------------------------------------------------------
) use Netty(tcp/or/http) in the remoting module
) apply zookeeper/or/redis as different registers
) implement various load-balance strategys: random, round, consistentHash, LFU, LRU, LeastUnreplied, etc.
) provide different invoke-method: sync, future, call back
) offer diverse serial approaches

note: 
** the remoting module refers xuxueli/xxl-rpc >>> https://github.com/xuxueli/xxl-rpc
** load-balance methods refer dubbo >>> https://github.com/apache/dubbo
