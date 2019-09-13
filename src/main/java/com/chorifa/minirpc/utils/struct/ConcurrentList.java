package com.chorifa.minirpc.utils.struct;

import java.util.Collection;

public interface ConcurrentList<E> {

    int size();

    E get(int index);

    void add(E e);

    void add(int index, E e);

    boolean remove(E e);

    boolean removeAll(Collection<E> c);

    boolean retainAll(Collection<E> c);

}
