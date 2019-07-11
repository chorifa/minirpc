package minirpc.utils.loadbalance;

import java.util.List;

public interface BalanceMethod {

    String select(String serviceKey, List<String> providers, SelectOptions ops);

}
