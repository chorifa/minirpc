package com.chorifa.minirpc.utils.loadbalance.impl;

import com.chorifa.minirpc.utils.loadbalance.BalanceMethod;
import com.chorifa.minirpc.utils.loadbalance.SelectOptions;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundMethod implements BalanceMethod {

    private ConcurrentHashMap<String /*service key*/, AtomicInteger> cache = new ConcurrentHashMap<>();
    private static final int BOUND = 1<<30;

    private int count(String key){
        AtomicInteger times = cache.get(key);

        if(times == null){
            int initValue = ThreadLocalRandom.current().nextInt(BOUND);
            if(cache.putIfAbsent(key,new AtomicInteger(initValue)) != null){ // namely, multi rectify
                times = cache.get(key);
            }else return initValue;
        }

        int curValue = 0;
        while ((curValue = times.get()) >= BOUND){ // ensure times.get < Bound
            int initValue = ThreadLocalRandom.current().nextInt(BOUND);
            if(times.compareAndSet(curValue,initValue))
                return initValue;
        }

        return times.incrementAndGet(); // no guarantee will < Bound

    }

    @Override
    public String select(String serviceKey, List<String> providers, SelectOptions ops) {
        Collections.sort(providers);
        return providers.get(count(serviceKey));
    }

}
