package com.chorifa.minirpc.registry.impl;

import com.chorifa.minirpc.registry.RegistryConfig;
import com.chorifa.minirpc.registry.RegistryService;
import com.chorifa.minirpc.utils.RPCException;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ZookeeperRegistry implements RegistryService {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperRegistry.class);

    private static final String BASE_HOME = "minirpc";
    private CuratorFramework client;
    private String workspace;

    private Map<String/*service name*/, String/*host address*/> registerMap = new HashMap<>();
    private final Map<String/* service name */, PathChildrenCache> childCacheMap = new ConcurrentHashMap<>();
    private final Map<String /*service name*/, Set<String> /*host addresss*/> serviceMap = new ConcurrentHashMap<>();

    private volatile ExecutorService executor = null;

    public ZookeeperRegistry(){}

    @Override
    public boolean isAvailable() {
        return client.isStarted();
    }

    @Override
    public void start(RegistryConfig config) {
        String zkHost = config.getRegisterAddress();
        this.workspace = BASE_HOME.concat("/").concat(config.getEnvPrefix());

        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000,5); // connect exceed time
        client = CuratorFrameworkFactory.builder().connectString(zkHost)
                .sessionTimeoutMs(2000).retryPolicy(retryPolicy)
                .namespace(workspace)
                .build();
        client.start();
        logger.info("ZKRegister: zookeeper client started...");
    }

    @Override
    public void stop() {
        // no need for unregister
        // unsubscribe will also rectify childCacheMap. can not use forEach
        List<String> rawSet = new ArrayList<>(childCacheMap.keySet());
        rawSet.forEach(this::unsubscribe);
        if(executor != null)
            executor.shutdown();
        if(client != null)
            client.close();
        logger.info("ZKRegister: zookeeper client stopped...");
    }

    @Override
    public void register(String key, String data) {
        // data =def= server address
        // do not need workspace any more
        String nodePath = "/".concat(key).concat("/").concat(data);

        if (checkNodeExists(nodePath) != null)
            throw new RPCException("ZKRegister: register node already exists...");
        try {
            client.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                    .forPath(nodePath, ("ZKRegister: server: " + data + " -->> register...").getBytes(StandardCharsets.UTF_8));
            registerMap.put(key,data);
        }catch (Exception e){
            logger.error("ZKRegister: zookeeper encounter one Exception when create a node...",e);
            throw new RPCException(e);
        }
        logger.info("ZKRegister: zookeeper register a service[ name= {}, address= {} ] done... ",key,data);
    }

    /**
     * not safe
     * @param key
     */
    @Override
    public void unregister(String key) {
        Stat state = null;
        String addr = registerMap.get(key);
        if(addr == null){
            logger.info("ZKRegister: server have not register such service...");
            return;
        }
        String nodePath = "/".concat(key).concat("/").concat(addr);

        if((state = checkNodeExists(nodePath)) == null) {
            logger.error("ZKRegister: node do not exist... but server has registered such service[name= {}].",key);
            throw new RPCException("ZKRegister: node do not exist... but server has registered such service.");
        }

        try {
            client.delete().guaranteed()
                    .deletingChildrenIfNeeded()
                    .withVersion(state.getVersion())
                    .forPath(nodePath);
            registerMap.remove(key);
            logger.info("ZKRegister: unsubscribe service[name= {}]",key);
        }catch (Exception e){
            logger.error("ZKRegister: zookeeper encounter one exception when delete one node[path= {}]...",nodePath,e);
        }
    }

    /**
     * pay attention: listener may have delay.
     * @param key
     */
    @Override
    public void subscribe(@Nonnull final String key) {
        String nodePath = "/".concat(key);

        if(checkNodeExists(nodePath) == null){
            logger.error("ZKRegister: nodePath={} not exists...",nodePath);
            throw new RPCException("ZKRegister: nodePath not exists...");
        }

        PathChildrenCache childCache = childCacheMap.get(key);
        if(childCache == null){
            childCache = new PathChildrenCache(this.client,nodePath,false);
            // create ConcurrentSet for each childCache
            serviceMap.putIfAbsent(key, Collections.newSetFromMap(new ConcurrentHashMap<>()));

            if(this.executor == null) {
                synchronized (this) {
                    if(this.executor == null)
                        this.executor = Executors.newFixedThreadPool(50,
                                (r) -> new Thread(r, "zk--subscribe--thread pool: " + r.hashCode()));
                }
            }

            childCache.getListenable().addListener((CuratorFramework client, PathChildrenCacheEvent event)->{
                if(event.getData() != null) {
                    String modPath = event.getData().getPath();
                    String[] tmp = modPath.split("/");
                    String modHost = tmp[tmp.length - 1];
                    Set<String> hostSet = ZookeeperRegistry.this.serviceMap.get(key); // ensure not null
                    if (hostSet == null) {
                        logger.error("ZKRegister: childCacheMap contains {}. But serviceMap do not. Which should not occur.", key);
                        Set<String> newSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
                        hostSet = serviceMap.putIfAbsent(key, newSet);
                        if (hostSet == null)
                            hostSet = newSet;
                    }
                    switch (event.getType()) {
                        case CHILD_ADDED:
                            logger.info("ZKRegister: add child node: { path = " + modPath +/*", data = "+new String(event.getData().getData())+*/" }");
                            if (!hostSet.add(modHost))
                                logger.debug("ZKRegister: child node add. But local map already contains. ");
                            break;
                        case CHILD_UPDATED:
                            logger.info("ZKRegister: update child node: { path = " + modPath +/*", new data = "+new String(event.getData().getData())+*/" }");
                            if (hostSet.add(modHost))
                                logger.debug("ZKRegister: child node update. But local map do not contain. ");
                            break;
                        case CHILD_REMOVED:
                            logger.info("ZKRegister: remove child node: { path = " + modPath +/*", raw data = "+new String(event.getData().getData())+*/" }");
                            if (!hostSet.remove(modHost))
                                logger.debug("ZKRegister: child node remove. But local map do not contain. ");
                            break;
                        default:
                            logger.info("ZKRegister: other event: " + event.getType());
                    }
                }
            }, this.executor);

            try {
                childCacheMap.put(key,childCache);
                childCache.start(PathChildrenCache.StartMode.NORMAL);
                logger.info("ZKRegister: start childCache... subscribe {} done",key);
            }catch (Exception e){
                logger.error("ZKRegister: encounter one exception when childCache start...");
                childCacheMap.remove(key);
                throw new RPCException(e);
            }

        }else logger.info("ZKRegister: already subscribe such service: {}",key);

        // BUILD_INITIAL_CACHE同步模式下
        // 先start再添加listener的话，原来已经有的数据加入到cache中，不会触发listener
        // 反之，会触发

        // Normal是异步的,都会触发add操作
        // Postxxx模式init完了之后会触发额外的init操作

        //childCache.getListenable().addListener(listener, executor);
    }

    @Override
    public void unsubscribe(String key) {
        PathChildrenCache childCache = childCacheMap.remove(key); // stop refresh
        if(childCache != null){
            try {
                serviceMap.remove(key); // delete cache, really need?
                childCache.getListenable().clear();
                childCache.close();
            }catch (Exception e){
                logger.error("ZKRegister: encounter one exception when childCache close...");
                throw new RPCException(e);
            }
        }else logger.info("ZKRegister: have not yet subscribe such service [name= {}]",key);
    }

    /**
     * caz listener has delay, there is need for manually refresh
     * @param key
     * @return
     */
    @Override
    public List<String> discovery(String key) {
        Set<String> hosts = serviceMap.get(key);
        if(hosts == null)
            throw new RPCException("ZKRegister: invoker have not yet subscribe such service >>> name = "+key);
        /*
        if(hosts.isEmpty())
            inquireRefresh(key);
            //waitForRefresh();

        hosts = serviceMap.get(key);
        if(hosts != null)
            return new ArrayList<>(hosts);
        else return null;
         */
        if(!hosts.isEmpty())
            return new ArrayList<>(hosts);
        else
            return inquire(key);
    }

    private void waitForRefresh() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            logger.error("ZKRegister: sleep encounter interrupt...",e);
        }
    }

    private List<String> inquire(String key){
        String nodePath = "/".concat(key);
        if(checkNodeExists(nodePath) == null){
            logger.error("ZKRegister: nodePath={} not exists...",nodePath);
            throw new RPCException("ZKRegister: nodePath not exists...");
        }
        try{
            return client.getChildren().forPath(nodePath);
        }catch (Exception e){
            logger.error("ZKRegister: inquire nodePath={} for refresh encounter one exception...",nodePath);
            return null;
        }
    }

    @Override
    public List<String> inquireRefresh(String key){
        String nodePath = "/".concat(key);
        if(checkNodeExists(nodePath) == null){
            logger.error("ZKRegister: nodePath={} not exists...",nodePath);
            throw new RPCException("ZKRegister: nodePath not exists...");
        }
        try {
            List<String> hosts = client.getChildren().forPath(nodePath);
            if(hosts != null && !hosts.isEmpty()){
                Set<String> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
                set.addAll(hosts);
                serviceMap.put(key,set);
                //final Set<String> oldHosts = serviceMap.get(key);
                //oldHosts.retainAll(hosts); // not atomic
                //oldHosts.addAll(hosts);
            }
            return hosts;
        }catch (Exception e){
            logger.error("ZKRegister: inquire nodePath={} for refresh encounter one exception...",nodePath);
        }
        return null;
    }

    private Stat checkNodeExists(String nodePath){
        try {
            return client.checkExists().forPath(nodePath);
        }catch (Exception e){
            logger.error("zookeeper encounter one Exception when check exits a node...",e);
            throw new RPCException(e);
        }
    }

}
