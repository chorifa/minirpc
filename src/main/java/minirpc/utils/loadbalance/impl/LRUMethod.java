package minirpc.utils.loadbalance.impl;

import minirpc.utils.loadbalance.BalanceMethod;
import minirpc.utils.loadbalance.SelectOptions;
import minirpc.utils.struct.ConcurrentLRUCache;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Deprecated
public class LRUMethod implements BalanceMethod {

    private volatile ConcurrentHashMap<String/*service key*/,ConcurrentLRUCache<String,String>> cacheMap = new ConcurrentHashMap<>();

    private static final int CACHE_CAPACITY = 1<<10;

    @Override
    public String select(String serviceKey, List<String> providers, SelectOptions ops) {
        if(providers == null) return null;
        if(providers.size() == 1) return providers.get(0);

        Collections.shuffle(providers);

        ConcurrentLRUCache<String,String> cache = cacheMap.get(serviceKey);
        if(cache == null){
            cache = new ConcurrentLRUCache<>(CACHE_CAPACITY);
            if(cacheMap.putIfAbsent(serviceKey,cache) != null) // already have
                cache = cacheMap.get(serviceKey);
        }
        cache.putKeyAll(providers);
        cache.retainKeyAll(providers);
        String provider = cache.getLRKeyWithRefresh();
        if(provider == null)
            provider = providers.get(0);
        return provider;
    }

}
