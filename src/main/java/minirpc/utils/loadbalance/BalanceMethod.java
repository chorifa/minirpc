package minirpc.utils.loadbalance;

import java.util.List;

public interface BalanceMethod {

    String select(String serviceKey,final List<String> providers, final SelectOptions ops);

}
