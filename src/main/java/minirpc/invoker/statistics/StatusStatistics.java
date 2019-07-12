package minirpc.invoker.statistics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class StatusStatistics {

    private static final ConcurrentHashMap<String/*host address*/, StatusStatistics> PROVIDER_STATUS_MAP = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String/*host address*/, ConcurrentHashMap<String/*method name*/,StatusStatistics>> METHOD_STATUS_MAP = new ConcurrentHashMap<>();

    private final AtomicInteger unreplied = new AtomicInteger();
    private final AtomicLong success = new AtomicLong();
    private final AtomicInteger failed = new AtomicInteger();
    // unreplied+success+failed = total

    private StatusStatistics(){}

    public static StatusStatistics getStatus(String host){
        StatusStatistics status = PROVIDER_STATUS_MAP.get(host);
        if(status == null){
            status = new StatusStatistics();
            if(PROVIDER_STATUS_MAP.putIfAbsent(host,status) != null)
                status = PROVIDER_STATUS_MAP.get(host);
        }
        return status;
    }

    public static boolean removeStatus(String host){
        return PROVIDER_STATUS_MAP.remove(host) != null;
    }

    public static StatusStatistics getStatus(String host, String methodName){
        ConcurrentHashMap<String,StatusStatistics> methodMap = METHOD_STATUS_MAP.get(host);
        if(methodMap == null){
            methodMap = new ConcurrentHashMap<>();
            if(METHOD_STATUS_MAP.putIfAbsent(host,methodMap) != null)
                methodMap = METHOD_STATUS_MAP.get(host);
        }
        StatusStatistics status = methodMap.get(methodName);
        if(status == null){
            status = new StatusStatistics();
            if(methodMap.putIfAbsent(methodName,status) != null)
                status = methodMap.get(methodName);
        }
        return status;
    }

    public static boolean removeStatus(String host, String methodName){
        ConcurrentHashMap<String, StatusStatistics> methodMap = METHOD_STATUS_MAP.get(host);
        if(methodMap != null){
            return methodMap.remove(methodName) != null;
        }
        return false;
    }

    public static void startCount(String host, String methodName){
        startCount(host,methodName,Integer.MAX_VALUE-100);
    }

    public static void startCount(String host, String methodName, int threshold){
        StatusStatistics hostStatus = getStatus(host);
        StatusStatistics methodStatus = getStatus(host,methodName);
        if(methodStatus.unreplied.incrementAndGet() > threshold){
            methodStatus.unreplied.decrementAndGet();
        }else hostStatus.unreplied.incrementAndGet();
    }

    public static void endCount(String host, String methodName, boolean succeed){
        endCount(getStatus(host),succeed);
        endCount(getStatus(host,methodName),succeed);
    }

    // TODO what if overflow
    private static void endCount(StatusStatistics status, boolean succeed){
        status.unreplied.decrementAndGet();
        if(succeed){
            status.success.incrementAndGet();
        }else{
            status.failed.incrementAndGet();
        }
    }

    // --------------------------------
    public int getUnreplied(){
        return unreplied.get();
    }

}
