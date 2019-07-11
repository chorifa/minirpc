package minirpc.utils.struct;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.locks.StampedLock;

public class ConcurrentLinkedList<E> {

    private LinkedList<E> innerList;
    private final StampedLock readWriteLock = new StampedLock();

    /**
     * unsafe. watch out
     * @return
     */
    public LinkedList<E> getInnerList(){
        return innerList;
    }

    public ConcurrentLinkedList(){
        this.innerList = new LinkedList<>();
    }

    public boolean remove(E e){
        long stamp = readWriteLock.writeLock();
        try {
            return innerList.remove(e);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public boolean removeAll(Collection<E> c){
        long stamp = readWriteLock.writeLock();
        try {
            return innerList.removeAll(c);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public boolean retainAll(Collection<E> c){
        long stamp = readWriteLock.writeLock();
        try {
            return innerList.retainAll(c);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public void add(int index, E e){
        long stamp = readWriteLock.writeLock();
        try {
            innerList.add(index, e);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    /**
     * add at tail
     * @param e
     * @return
     */
    public boolean offer(E e){
        long stamp = readWriteLock.writeLock();
        try {
            return innerList.offer(e);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public boolean offerFirst(E e){
        long stamp = readWriteLock.writeLock();
        try {
            return innerList.offerFirst(e);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public E poll(){
        long stamp = readWriteLock.writeLock();
        try {
            return innerList.poll();
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public E peek(){
        long stamp = readWriteLock.tryOptimisticRead();
        E head = innerList.peek();
        if(!readWriteLock.validate(stamp)) {
            stamp = readWriteLock.readLock();
            try {
                head = innerList.peek();
            } finally {
                readWriteLock.unlockRead(stamp);
            }
        }
        return head;
    }

    public int size(){
        long stamp = readWriteLock.tryOptimisticRead();
        int finalSize = innerList.size();
        if(!readWriteLock.validate(stamp)) {
            stamp = readWriteLock.readLock();
            try {
                finalSize = innerList.size();
            } finally {
                readWriteLock.unlockRead(stamp);
            }
        }
        return finalSize;
    }

    // ------------------------------- for LRU -------------------------------
    // head is LR element

    public boolean rectify(E e){
        long stamp = readWriteLock.writeLock();
        try{
            if(innerList.remove(e))
                return innerList.offer(e);
            else return false;
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

}
