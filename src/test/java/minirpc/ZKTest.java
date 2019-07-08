package minirpc;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ZKTest {

    static class CuratorConnect {
        private CuratorFramework client;

        private final HashMap<String/* node path */, PathChildrenCache> childCacheMap = new HashMap<>();

        private static final String zkHost = "localhost:2181";

        private final ExecutorService executor = Executors.newFixedThreadPool(10,
                (r)->new Thread(r,"zk--test--thread pool: "+r.hashCode()));

        CuratorConnect(){
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000,5);
            client = CuratorFrameworkFactory.builder().connectString(zkHost)
                    .sessionTimeoutMs(30000).retryPolicy(retryPolicy)
                    .namespace("workspace")
                    .build();
            client.start();
        }

        ExecutorService getExecutor(){
            return executor;
        }

        void closeConnect(){
            childCacheMap.values().forEach((v)->{
                try{v.close();}
                catch (IOException e){throw new UncheckedIOException(e);}
            });
            if(client != null)
                this.client.close();
        }

        boolean isConnectStarted(){
            return client.isStarted();
        }

        CuratorFramework getClient(){
            return client;
        }

        String createNode(String nodePath, byte[] data, boolean isPersistent) throws Exception {
            if(checkNodeExists(nodePath) != null)
                throw new RuntimeException("node already exists...");
            if(isPersistent){
                return client.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath(nodePath, data);
            }else {
                return client.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath(nodePath, data);
            }
        }

        Stat rectifyNode(String nodePath, byte[] data, Integer ver) throws Exception{
            if(checkNodeExists(nodePath) == null)
                throw new RuntimeException("node do not exist...");
            if(ver == null)
                return client.setData().forPath(nodePath,data);
            else
                return client.setData().withVersion(ver).forPath(nodePath,data);
        }

        void deleteNode(String nodePath, Integer ver) throws Exception{
            if(checkNodeExists(nodePath) == null)
                throw new RuntimeException("node do not exist...");
            if(ver == null) {
                client.delete().guaranteed()
                        .deletingChildrenIfNeeded()
                        .forPath(nodePath);
            }else{
                client.delete().guaranteed()
                        .deletingChildrenIfNeeded()
                        .withVersion(ver)
                        .forPath(nodePath);
            }
        }

        byte[] getNodeData(String nodePath, Stat stateStore) throws Exception{
            if(checkNodeExists(nodePath) == null)
                throw new RuntimeException("node do not exist...");
            if(stateStore == null){
                return client.getData().forPath(nodePath);
            }else{
                return client.getData().storingStatIn(stateStore).forPath(nodePath);
            }
        }

        List<String> getNodeChilds(String nodePath) throws Exception{
            if(checkNodeExists(nodePath) == null)
                throw new RuntimeException("node do not exist...");
            return client.getChildren().forPath(nodePath);
        }

        Stat checkNodeExists(String nodePath) throws Exception{
            return client.checkExists().forPath(nodePath);
        }

        void addNodeChildListener(String nodePath, ExecutorService executor, PathChildrenCacheListener listener) throws Exception{
            PathChildrenCache childCache = childCacheMap.get(nodePath);
            if(childCache == null){
                childCache = new PathChildrenCache(this.client,nodePath,false);
                childCacheMap.put(nodePath,childCache);
            }
            // BUILD_INITIAL_CACHE同步模式下
            // 先start再添加listener的话，原来已经有的数据加入到cache中，不会触发listener
            // 反之，会触发

            // Normal是异步的,都会触发add操作
            // Postxxx模式init完了之后会触发额外的init操作
            childCache.getListenable().addListener(listener, executor);

            childCache.start(PathChildrenCache.StartMode.NORMAL);

        }

        void removeAllChildListener(String nodePath){
            PathChildrenCache childCache = childCacheMap.get(nodePath);
            if(childCache == null)
                throw new RuntimeException("no cache add to such path");
            childCache.getListenable().clear();
        }

    }

    @Test
    public void testConn(){
        CuratorConnect curatorConnect = new CuratorConnect();
        System.out.println(curatorConnect.isConnectStarted()?"connected...":"closed...");
        String listenPath = "/test";

        try{
            curatorConnect.addNodeChildListener(listenPath, curatorConnect.getExecutor(),
                    (CuratorFramework client, PathChildrenCacheEvent event)->{
                        switch (event.getType()){
                            case CHILD_ADDED:
                                System.out.println("add child node: { path = "+event.getData().getPath()/*+", data = "+new String(event.getData().getData())+" }"*/);
                                break;
                            case CHILD_UPDATED:
                                System.out.println("update child node: { path = "+event.getData().getPath()/*+", new data = "+new String(event.getData().getData())+" }"*/);
                                break;
                            case CHILD_REMOVED:
                                System.out.println("remove child node: { path = "+event.getData().getPath()/*+", raw data = "+new String(event.getData().getData())+" }"*/);
                                break;
                            default:
                                System.out.println("other event: "+event.getType());
                        }
                    });

            TimeUnit.SECONDS.sleep(30);

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            curatorConnect.removeAllChildListener(listenPath);
            curatorConnect.getExecutor().shutdown();
            curatorConnect.closeConnect();
        }
        System.out.println(curatorConnect.isConnectStarted()?"connected...":"closed...");

    }
}
