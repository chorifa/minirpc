package minirpc.utils.loadbalance.impl;

import minirpc.utils.loadbalance.BalanceMethod;
import minirpc.utils.loadbalance.SelectOptions;
import minirpc.utils.struct.ConcurrentLRUCache;

import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class LeastRecentUsedMethod implements BalanceMethod {

    private volatile ConcurrentHashMap<String/*service key*/, ConcurrentLRUCache<String,String>> cacheMap = new ConcurrentHashMap<>();

    private static final int CACHE_CAPACITY = 1<<10;
    private static final long TIME_THRESHOLD = 60*60*60;

    /**
     * in such impl, only notModTime >= TIME_THRESHOLD will remove
     * such impl is completely synchronized. seriously loss performance under multi-thread
     * but can guarantee more accurate LRU results
     * guarantee that return T must contain in TreeSet<T>, which may not be effective
     * @param serviceKey
     * @param providers
     * @param ops
     * @return String
     */
    @Override
    public String select(String serviceKey, List<String> providers, SelectOptions ops) {
        if(providers == null) return null;
        if(providers.size() == 1) return providers.get(0);

        Collections.shuffle(providers);

        ConcurrentLRUCache<String,String> cache = cacheMap.get(serviceKey);
        if(cache == null){
            cache = new ConcurrentLRUCache<>(CACHE_CAPACITY, true, TIME_THRESHOLD);
            if(cacheMap.putIfAbsent(serviceKey,cache) != null) // already have
                cache = cacheMap.get(serviceKey);
        }

        // add entry and get in same lock -->> in some case do clean
        String t = cache.putAndGetEldestKeyWithCleanIn(providers);
        if(t == null)
            t = providers.get(0);
        return t;
    }

}
