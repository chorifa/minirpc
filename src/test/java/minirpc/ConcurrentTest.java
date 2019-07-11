package minirpc;

import minirpc.utils.struct.ConcurrentLRUCache;
import minirpc.utils.struct.ConcurrentTreeSet;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurrentTest {

    @Test
    public void testLRU(){
        ConcurrentLRUCache<String,Integer> cache = new ConcurrentLRUCache<>(4);
        cache.put("1",1);
        cache.put("2",2);
        cache.put("3",3);
        cache.put("4",4);
        System.out.println(cache.size());
        cache.put("5",5);
        System.out.println(cache.size());
    }

    @Test
    public void testConcurrentSet(){
        final ConcurrentTreeSet<String> set = new ConcurrentTreeSet<>();
        final CountDownLatch latch = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(300);
        for(int i = 0; i < 100; i++){
            String value = "node"+i%10;
            executor.execute(()->{
                try {
                    latch.await();
                    System.out.println("add value: " + value + " " + set.addIfAbsent(value));
                }catch (InterruptedException e){
                    System.out.println("interrupt");
                }
            });
        }
        for(int i = 0; i < 100; i++){
            String value = "node"+i%10;
            executor.execute(()->{
                try {
                    latch.await();
                    System.out.println("remove value: "+value + " " + set.removeIfContain(value));
                }catch (InterruptedException e){
                    System.out.println("interrupt");
                }
            });
        }
        for(int i = 0; i < 100; i++){
            executor.execute(()->{
                try {
                    latch.await();
                    System.out.println("get value: "+set.size());
                }catch (InterruptedException e){
                    System.out.println("interrupt");
                }
            });
        }
        latch.countDown();
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

}
