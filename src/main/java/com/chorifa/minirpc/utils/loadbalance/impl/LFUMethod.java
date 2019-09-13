package com.chorifa.minirpc.utils.loadbalance.impl;

import com.chorifa.minirpc.utils.loadbalance.BalanceMethod;
import com.chorifa.minirpc.utils.loadbalance.SelectOptions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Deprecated
public class LFUMethod implements BalanceMethod {

    private volatile ConcurrentHashMap<String /*service key*/, ConcurrentHashMap<String, AtomicInteger>> cacheMap = new ConcurrentHashMap<>();
    private static final int BOUND = 1<<30;

    @Override
    public String select(String serviceKey, final List<String> providers, SelectOptions ops) {
        if(providers == null) return null;
        if(providers.size() == 1) return providers.get(0);

        ConcurrentHashMap<String,AtomicInteger> cache = cacheMap.get(serviceKey);
        if(cache == null){
            cache = new ConcurrentHashMap<>();
            if(cacheMap.putIfAbsent(serviceKey,cache) != null)
                cache = cacheMap.get(serviceKey);
        }

        for(String provider : providers){
            if(!cache.containsKey(provider))
                cache.putIfAbsent(provider,new AtomicInteger(0));

            AtomicInteger cnt = cache.get(provider);
            if(cnt != null && cnt.get() >= BOUND){ // all should be reset
                synchronized (this){
                    cnt = cache.get(provider);
                    if(cnt != null && cnt.get() >= BOUND){
                        Set<String> keySet = cache.keySet();
                        for(String key: keySet){ // note here. such code is not allowed in HashMap, for it may concurrent modify entry
                            cache.put(key,new AtomicInteger(0)); // but here, key is not modified. it's ok, but not suggested
                        }
                    }
                }
            }// reset finish
        }

        // delete
        Set<String> keySet = new HashSet<>(cache.keySet());
        for(String key: keySet){
            if(!providers.contains(key))
                cache.remove(key);
        }

        // not exact. but no need synchronized
        AtomicInteger realValue = null;
        String lfKey = null;
        ArrayList<Pair> cnts = new ArrayList<>(cache.size());
        cache.forEach((k, v) -> cnts.add(new Pair(k, v.get())));
        cnts.sort(Comparator.comparingInt(o -> o.value)); // from small to large
        for(Pair p : cnts){
            if((realValue = cache.get(p.key)) != null){
                realValue.incrementAndGet();
                lfKey = p.key;
                break;
            }
        }
        if(lfKey == null)
            lfKey = providers.get(0);

        return lfKey;
    }

    private Object bubble(ArrayList<Pair> list){
        for(int i = list.size()-1; i > 0; i--){
            if(list.get(i).value < list.get(i-1).value){
                Pair p = list.get(i);
                list.set(i,list.get(i-1));
                list.set(i-1,p);
            }
        }
        return list.get(0).key;
    }

    private Object findMin(ArrayList<Pair> list){
        Pair min = list.get(0);
        for(Pair p : list)
            if(p.value < min.value)
                min = p;
        return min.key;
    }

    static class Pair{
        String key;
        int value;
        Pair(String o, int v){
            key = o;
            value = v;
        }
    }

}
