package minirpc.utils.struct;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.locks.StampedLock;

public class ConcurrentArrayList<E> implements ConcurrentList<E>{

    private ArrayList<E> innerList;
    private final StampedLock readWriteLock = new StampedLock();

    /**
     * unsafe. watch out
     * @return
     */
    public ArrayList<E> getInnerList(){
        return innerList;
    }

    public ConcurrentArrayList(){
        this.innerList = new ArrayList<>();
    }

    public ConcurrentArrayList(int capacity){
        this.innerList = new ArrayList<>(capacity);
    }

    @Override
    public boolean remove(E e){
        long stamp = readWriteLock.writeLock();
        try {
            return innerList.remove(e);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean removeAll(Collection<E> c){
        long stamp = readWriteLock.writeLock();
        try {
            return innerList.removeAll(c);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean retainAll(Collection<E> c){
        long stamp = readWriteLock.writeLock();
        try {
            return innerList.retainAll(c);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    @Override
    public void add(E e){
        long stamp = readWriteLock.writeLock();
        try {
            innerList.add(e);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    @Override
    public void add(int index, E e){
        long stamp = readWriteLock.writeLock();
        try {
            innerList.add(index,e);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    @Override
    public E get(int index){
        long stamp = readWriteLock.readLock();
        try {
            return innerList.get(index);
        }finally {
            readWriteLock.unlockRead(stamp);
        }
    }

    @Override
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

}
