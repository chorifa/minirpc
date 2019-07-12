package minirpc.utils.loadbalance.impl;

import minirpc.invoker.statistics.StatusStatistics;
import minirpc.utils.loadbalance.BalanceMethod;
import minirpc.utils.loadbalance.SelectOptions;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class LeastUnrepliedMethod implements BalanceMethod {

    @Override
    public String select(String serviceKey, List<String> providers, SelectOptions ops) {

        int length = providers.size();
        int leastUnreplied = -1;
        int leastCount = 0;
        int[] leastIndexes = new int[length];
        // int[] weights
        // int totalWeight
        // int firstWeight
        // boolean sameWeight

        for(int i = 0; i < length; i++){
            String host = providers.get(i);
            int unreplied = StatusStatistics.getStatus(host,ops.getMethodName()).getUnreplied();
            if(leastUnreplied == -1 || unreplied < leastUnreplied){
                leastUnreplied = unreplied;
                leastCount = 1;
                leastIndexes[0] = i;
            }else if(unreplied == leastUnreplied){
                leastIndexes[leastCount++] = i;
            }
        }

        if(leastCount == 1){
            return providers.get(leastIndexes[0]);
        }

        return providers.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
    }

}
