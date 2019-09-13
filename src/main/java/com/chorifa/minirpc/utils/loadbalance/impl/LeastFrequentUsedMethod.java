package com.chorifa.minirpc.utils.loadbalance.impl;

import com.chorifa.minirpc.utils.loadbalance.BalanceMethod;
import com.chorifa.minirpc.utils.loadbalance.SelectOptions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LeastFrequentUsedMethod implements BalanceMethod {

	private volatile ConcurrentHashMap<String /*service key*/, HashMap<String, Integer>> cacheMap = new ConcurrentHashMap<>();
	private static final int BOUND = 1<<30;

	@Override
	public String select(String serviceKey, List<String> providers, SelectOptions ops) {
		if(providers == null) return null;
		if(providers.size() == 1) return providers.get(0);

		HashMap<String, Integer> cache = cacheMap.get(serviceKey);
		if(cache == null){
			cache = new HashMap<>(providers.size()>>1);
			if(cacheMap.putIfAbsent(serviceKey, cache) != null){
				cache = cacheMap.get(serviceKey);
			}
		}

		synchronized (cache){
			for(String host : providers){
				Integer cnt = cache.get(host);
				if(cnt == null)
					cache.put(host, ThreadLocalRandom.current().nextInt(2)); // init
				else if(cnt >= BOUND || cnt < 0){
					Set<String> keySet = cache.keySet();
					for(String key: keySet){ // note here. such code is not allowed in HashMap, for it may concurrent modify entry
						cache.put(key, ThreadLocalRandom.current().nextInt(2)); // but here, key is not modified. it's ok, but not suggested
					}
				}
			}

			String lfKey = findMin(providers, cache);

			if(lfKey == null)
				lfKey = providers.get(0);

			//refresh
			cache.put(lfKey,cache.get(lfKey)+1);

			// remove if need
			if(cache.size() >= providers.size()*1.5)
				cache.keySet().removeIf(key -> !providers.contains(key));

			return lfKey;
		}

	}

	private String findMin(final List<String> list, final Map<String,Integer> map){
		int minValue = Integer.MAX_VALUE; String minKey = null;
		for(String s : list){
			Integer v = map.get(s);
			if(v != null && v < minValue){
				minKey = s;
				minValue = v;
			}
		}
		return minKey;
	}

}
