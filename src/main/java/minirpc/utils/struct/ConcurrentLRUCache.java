package minirpc.utils.struct;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.locks.StampedLock;

/**
 *  not that. iterator(also for...each) will not change order in LRU cache
 * @param <K>
 * @param <V>
 */
public class ConcurrentLRUCache<K,V> {

    private LRUCache<K,V> innerCache;
    private HashMap<K,Long> timeCache;
    private Long timeThreshold;
    private final StampedLock readWriteLock = new StampedLock();

    public ConcurrentLRUCache(){
        innerCache = new LRUCache<>();
    }

    public ConcurrentLRUCache(boolean autoClean, long timeThreshold){
        if(autoClean) {
            timeCache = new HashMap<>();
            this.timeThreshold = timeThreshold;
        }
        innerCache = new LRUCache<>();
    }

    public ConcurrentLRUCache(int capacity){
        innerCache = new LRUCache<>(capacity);
    }

    public ConcurrentLRUCache(int capacity, boolean autoClean, long timeThreshold){
        if(autoClean) {
            timeCache = new HashMap<>();
            this.timeThreshold = timeThreshold;
        }
        innerCache = new LRUCache<>(capacity);
    }

    public void put(K key, V value){
        long stamp = readWriteLock.writeLock();
        try{
            innerCache.put(key,value);
            if(timeCache != null)
                timeCache.put(key,System.currentTimeMillis());
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public K getLRKeyWithRefresh(){
        long stamp = readWriteLock.writeLock();
        try{
            K key = innerCache.keySet().iterator().next();
            innerCache.get(key); // this will modify linked list
            if(timeCache != null)
                timeCache.put(key,System.currentTimeMillis());
            return key;
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public V getLRValue(){
        long stamp = readWriteLock.writeLock();
        try{
            K key = innerCache.keySet().iterator().next();
            if(timeCache != null)
                timeCache.put(key,System.currentTimeMillis());
            return innerCache.get(key);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public int size(){
        long stamp = readWriteLock.tryOptimisticRead();
        int finalSize = innerCache.size();
        if(!readWriteLock.validate(stamp)) {
            stamp = readWriteLock.readLock();
            try {
                finalSize = innerCache.size();
            } finally {
                readWriteLock.unlockRead(stamp);
            }
        }
        return finalSize;
    }

    public void putKeyAll(final Collection<K> keys){
        long stamp = readWriteLock.writeLock();
        try{
            if(timeCache != null) {
                for (K key : keys) {
                    if (!innerCache.containsKey(key)) {
                        innerCache.put(key, null);
                        timeCache.put(key, System.currentTimeMillis());
                    }
                }
            }else{
                for (K key : keys) {
                    if (!innerCache.containsKey(key)) {
                        innerCache.put(key, null);
                    }
                }
            }
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public void retainKeyAll(final Collection<K> keys){
        long stamp = readWriteLock.writeLock();
        try{
            if(timeCache != null) {
                for (Iterator<K> iterator = innerCache.keySet().iterator(); iterator.hasNext(); ) {
                    K key = iterator.next();
                    if (!keys.contains(key)) {
                        iterator.remove(); // must use iterator
                        timeCache.remove(key);
                    }
                }
            }else{
                innerCache.keySet().removeIf(k -> !keys.contains(k));
            }
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public K putAndGetEldestKeyWithCleanIn(final Collection<K> keys){
        long stamp = readWriteLock.writeLock();
        try{
            if(timeCache == null) {
                for (K key : keys) {
                    if (!innerCache.containsKey(key))
                        innerCache.put(key, null);
                }
                for(Iterator<K> iterator = innerCache.keySet().iterator(); iterator.hasNext(); ){
                    K key = iterator.next();
                    if(keys.contains(key)){
                        innerCache.get(key); // refresh
                        return key;
                    }
                    else{ // delete anyway
                        iterator.remove();
                    }
                }
            }else{
                for (K key : keys) {
                    if (!innerCache.containsKey(key)) {
                        innerCache.put(key, null);
                        timeCache.put(key, System.currentTimeMillis());
                    }
                }

                for(Iterator<K> iterator = innerCache.keySet().iterator(); iterator.hasNext(); ){
                    K key = iterator.next();
                    if(keys.contains(key)){
                        innerCache.get(key); // refresh
                        timeCache.put(key,System.currentTimeMillis());
                        return key;
                    }
                    else{
                        if(timeCache.get(key) == null) {
                            iterator.remove();
                        }else if(System.currentTimeMillis() - timeCache.get(key) >= timeThreshold){
                            iterator.remove();
                            timeCache.remove(key);
                        }
                    }
                }
            }
            return null;
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

}
