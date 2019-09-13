package com.chorifa.minirpc.utils.loadbalance;

import com.chorifa.minirpc.utils.loadbalance.impl.*;

public enum LoadBalance {

    CONSISTENT_HASH(new ConsistentHashMethod()),
    LFU(new LeastFrequentUsedMethod()),
    LRU(new LeastRecentUsedMethod()),
    LEAST_UNREPLIED(new LeastUnrepliedMethod()),
    RANDOM(new RandomMethod()),
    ROUND(new RoundMethod());

    private BalanceMethod balanceMethod;

    LoadBalance(BalanceMethod balanceMethod){
        this.balanceMethod = balanceMethod;
    }

    public BalanceMethod getBalanceMethod(){
        return balanceMethod;
    }

}
