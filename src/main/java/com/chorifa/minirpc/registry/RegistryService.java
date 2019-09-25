package com.chorifa.minirpc.registry;

import java.util.List;

public interface RegistryService {

    boolean isAvailable();

    void start(RegistryConfig config);

    void stop();

    void register(String key, String data);

    void unregister(String key);

    void subscribe(String key);

    void unsubscribe(String key);

    List<String> discovery(String key);

    List<String> inquireRefresh(String key);

}
