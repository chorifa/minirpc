package minirpc.utils.struct;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K,V> extends LinkedHashMap<K,V> {

    private int capacity;

    public LRUCache(){
        super(16,0.75f,true);
        this.capacity = 1<<10;
    }

    public LRUCache(int capacity){
        super(16,0.75f,true);
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return super.size()>capacity;
    }

}
