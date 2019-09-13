package com.chorifa.minirpc.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class StreamID4Http2Util {

    private static final AtomicInteger count = new AtomicInteger(Integer.MIN_VALUE);

    private static final int ODD_NUM = ((Integer.MAX_VALUE -3)>>1)+1;

    private static final int OFFSET = (Integer.MIN_VALUE)%ODD_NUM; // actually, -2

    /**
     *  map [Integer.MIN_VALUE, Integer.MAX_VALUE] to odd integer between [3,Integer.MAX_VALUE]
     * @return odd integer between [3,Integer.MAX_VALUE]
     */
    public static int getCurrentID(){
        return ((count.getAndIncrement()%ODD_NUM - OFFSET)%ODD_NUM <<1) +3;
    }
}
