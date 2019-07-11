package minirpc.utils.loadbalance.impl;

import minirpc.utils.RPCException;
import minirpc.utils.loadbalance.BalanceMethod;
import minirpc.utils.loadbalance.SelectOptions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class ConsistentHashMethod implements BalanceMethod {

    private final ConcurrentHashMap<String/*service and method*/, ConsistentHashSelector> selectors = new ConcurrentHashMap<>();

    @Override
    public String select(String serviceKey, List<String> providers, SelectOptions ops) {
        if(ops == null)
            throw new RPCException("LoadBalance: SelectOptions should not be null...");
        String key = serviceKey+"."+ops.getMethodName();
        int identityHashCode = System.identityHashCode(providers);
        ConsistentHashSelector selector = selectors.get(key);
        if(selector == null || selector.identityHashCode != identityHashCode){
            selector = new ConsistentHashSelector(providers,identityHashCode);
            selectors.put(key,selector);
        }
        return selector.doSelect(ops.getArgs());
    }

    private static final class ConsistentHashSelector{

        private static final int VIRTUAL_NODE_NUM = 160;
        private static final int[] ARGUMENT_INDEX = {0};

        private final TreeMap<Long/*hashcode*/, String/*host address*/> virtualNodes;

        private final int identityHashCode;

        ConsistentHashSelector(List<String> providers, int identityHashCode){
            virtualNodes = new TreeMap<>();
            this.identityHashCode = identityHashCode;
            // cost most of time here
            for(String s : providers){
                for(int i = 0; i < VIRTUAL_NODE_NUM>>2; i++){
                    byte[] digest = md5(s+i);
                    for(int h = 0; h < 4; h++){
                        long m = hash(digest,h);
                        virtualNodes.put(m,s);
                    }
                }
            }
        }

        String doSelect(Object[] args){
            String key = toKey(args);
            byte[] digest = md5(key);
            return selectForKey(hash(digest,0));
        }

        private String selectForKey(long hash){
            Map.Entry<Long,String> entry = virtualNodes.ceilingEntry(hash);
            if(entry == null){
                entry = virtualNodes.firstEntry();
            }
            return entry.getValue();
        }

        private String toKey(Object[] args){
            StringBuilder sb = new StringBuilder();
            for(int i : ARGUMENT_INDEX)
                if(i>=0 && i<args.length)
                    sb.append(args[i]);
            return sb.toString();
        }

        private long hash(byte[] digest, int number){
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        private byte[] md5(String value){
            MessageDigest md5;
            try{
                md5 = MessageDigest.getInstance("MD5");
            }catch (NoSuchAlgorithmException e){
                throw new RPCException(e);
            }
            md5.reset();
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            md5.update(bytes);
            return md5.digest();
        }
    }

}
