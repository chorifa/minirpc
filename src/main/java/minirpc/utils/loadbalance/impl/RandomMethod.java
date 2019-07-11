package minirpc.utils.loadbalance.impl;

import minirpc.utils.loadbalance.BalanceMethod;
import minirpc.utils.loadbalance.SelectOptions;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomMethod implements BalanceMethod {

    @Override
    public String select(String serviceKey, List<String> providers, SelectOptions ops) {
        if(providers == null) return null;
        if(providers.size() == 1) return providers.get(0);
        Collections.sort(providers);
        return providers.get(ThreadLocalRandom.current().nextInt(providers.size()));
    }

}
