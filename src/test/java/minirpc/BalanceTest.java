package minirpc;

import minirpc.utils.loadbalance.SelectOptions;
import minirpc.utils.loadbalance.impl.ConsistentHashMethod;
import minirpc.utils.loadbalance.impl.LeastFrequentUsedMethod;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class BalanceTest {

    @Test
    public void testSingle(){
        //LeastRecentUsedMethod method = new LeastRecentUsedMethod();
        //LeastFrequentUsedMethod method = new LeastFrequentUsedMethod();
        //LFUMethod method = new LFUMethod();
        ConsistentHashMethod method = new ConsistentHashMethod();
        HashMap<String,Integer> cnt = new HashMap<>(100);

        List<String> list = new ArrayList<>();
        for(int j = 0; j< 100; j++)
            list.add("node"+j);

        SelectOptions ops = new SelectOptions();
        ops.setMethodName("testMethod");
        ops.setArgs(new Object[]{});

        for(int j = 0; j < 1000; j++){
            String s = method.select("test",list,ops);
            Integer n = cnt.get(s);
            if(n == null) n = 0;
            cnt.put(s,n+1);
            list.remove("node" + ThreadLocalRandom.current().nextInt(100));
            list.add("node" + ThreadLocalRandom.current().nextInt(100));
        }

        System.out.println(cnt.size());
        cnt.forEach((k,v)-> System.out.println("key: "+ k+"  value: "+v));
    }

    @Test
    public void testMulti(){
        //LRUMethod method = new LRUMethod();
        //LFUMethod method = new LFUMethod();
        //LeastRecentUsedMethod method = new LeastRecentUsedMethod();
        //LeastFrequentUsedMethod method = new LeastFrequentUsedMethod();
        ConsistentHashMethod method = new ConsistentHashMethod();

        ConcurrentHashMap<String, AtomicInteger> cnt = new ConcurrentHashMap<>(1000);
        Thread[] ts = new Thread[100];

//        SelectOptions[] ops = new SelectOptions[50];
//        for(int i = 0; i < 50; i++){
//            ops[i] = new SelectOptions();
//            ops[i].setMethodName("testMethod");
//            ops[i].setArgs(new Object[]{59*i*(i+67),"arg"});
//        }

        for(int i = 0; i < 100; i++) {
            ts[i] = new Thread(() -> {

                SelectOptions ops = new SelectOptions();
                ops.setMethodName("testMethod");
                ops.setArgs(new Object[]{Thread.currentThread().getName()+ThreadLocalRandom.current().nextInt(100),1}); // different args

                List<String> list = new ArrayList<>(1000);
                for(int j = 0; j< 1000; j++)
                    list.add("node"+j);

                for (int j = 0; j < 200; j++) {
                    String s = method.select("test", list,ops);
                    AtomicInteger n = cnt.get(s);
                    if (n == null) {
                        n = new AtomicInteger(0);
                        if(cnt.putIfAbsent(s,n) != null)
                            n = cnt.get(s);
                    }
                    n.incrementAndGet();
                    list.remove("node" + ThreadLocalRandom.current().nextInt(1000));
                    list.add("node" + ThreadLocalRandom.current().nextInt(1000));
                }
            });
        }

        for(Thread t : ts)
            t.start();
        try {
            for(Thread t: ts)
                t.join();
        }catch (InterruptedException e){
            e.printStackTrace();
        }

        System.out.println(cnt.size());
        cnt.forEach((k,v)-> System.out.println("key: "+ k+"  value: "+v));
    }

    @Test
    public void testLRU(){
        LinkedHashMap<Integer,String> map = new LinkedHashMap<>();
        for(int i = 0; i < 10; i++)
            map.put(i,"node"+i);
        for(Iterator<Integer> iterator = map.keySet().iterator(); iterator.hasNext(); ){
            Integer i = iterator.next();
            if(i == 3)
                iterator.remove();
        }
        map.forEach((k,v)->System.out.println("key: "+k+" value: "+v));
    }

    public void testMap(){
        HashMap<Integer,String> map = new HashMap<>();
        for(int i = 0; i < 10; i++)
            map.put(i,"node"+i);
        map.forEach((k,v)->System.out.println("key: "+k+" value: "+v));
    }

    public void testList(){
        ArrayList<Integer> list = new ArrayList<>(16);
        for(int i = 0; i < 16; i++)
            list.add(16-i);
        list.sort(Integer::compareTo);
        list.forEach(System.out::println);
    }

    public void testRandom(){
        Thread[] ts = new Thread[10];
        for(int i = 0; i < 10; i++){
            ts[i] = new Thread(()->{
                System.out.println(ThreadLocalRandom.current().nextInt(1000));
                System.out.println(ThreadLocalRandom.current().nextInt(1000));
            });
        }

        for(Thread t : ts)
            t.start();
        try {
            for(Thread t: ts)
                t.join();
        }catch (InterruptedException e){
            e.printStackTrace();
        }

    }

}
