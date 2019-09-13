package com.chorifa.minirpc.utils.struct;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.concurrent.locks.StampedLock;

public class ConcurrentTreeSet<E> {

    private TreeSet<E> innerSet;
    private final StampedLock readWriteLock = new StampedLock();

    public ConcurrentTreeSet(){
        this.innerSet = new TreeSet<>();
    }

    public ConcurrentTreeSet(Comparator<? super E> o){
        this.innerSet = new TreeSet<>(o);
    }

    public boolean addIfAbsent(E value){
        long stamp = readWriteLock.writeLock();
        try {
            return innerSet.add(value);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public boolean removeIfContain(E value){
        long stamp = readWriteLock.writeLock();
        try {
            return innerSet.remove(value);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public boolean contains(E value){
        long stamp = readWriteLock.readLock();
        try {
            return innerSet.contains(value);
        }finally {
            readWriteLock.unlockRead(stamp);
        }
    }

    @SuppressWarnings("unchecked")
    public TreeSet<E> cloneSet(){
        long stamp = readWriteLock.readLock();
        try{
            return (TreeSet<E>)innerSet.clone();
        }finally {
            readWriteLock.unlockRead(stamp);
        }
    }

    public void reset(Collection<E> refer){
        long stamp = readWriteLock.writeLock();
        try {
            innerSet = new TreeSet<>(refer);
        }finally {
            readWriteLock.unlockWrite(stamp);
        }
    }

    public int size(){
        long stamp = readWriteLock.tryOptimisticRead();
        int finalSize = innerSet.size();
        if(!readWriteLock.validate(stamp)) {
            stamp = readWriteLock.readLock();
            try {
                finalSize = innerSet.size();
            } finally {
                readWriteLock.unlockRead(stamp);
            }
        }
        return finalSize;
    }

    public boolean isEmpty(){
        return size()==0;
    }

}
