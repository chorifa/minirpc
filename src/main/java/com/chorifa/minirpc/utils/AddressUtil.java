package com.chorifa.minirpc.utils;

public class AddressUtil {

    public static String generateAddress(String ip, int port){
        return ip.concat(":").concat(String.valueOf(port));
    }

    public static Object[] parseAddress(String address){
        String[] strs = address.split(":");
        return new Object[]{strs[0],Integer.valueOf(strs[1])};
    }

}
