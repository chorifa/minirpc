package minirpc.register;

import java.util.List;

public interface RegisterService {

    boolean isAvailable();

    void start(RegisterConfig config);

    void stop();

    void register(String key, String data);

    void unregister(String key);

    void subscribe(String key);

    void unsubscribe(String key);

    List<String> discovery(String key);

    List<String> inquireRefresh(String key);

}
