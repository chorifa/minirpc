package minirpc.register;

import javax.annotation.Nonnull;

public class RegisterConfig {

    private String registerAddress = "localhost:2181";

    private String envPrefix = "test";

    // for redis (provider)
    private long expiredTime = 10*1000; // 10s

    private boolean isInvoker = false;

    public String getRegisterAddress() {
        return registerAddress;
    }

    public RegisterConfig setRegisterAddress(@Nonnull String registerAddress) {
        this.registerAddress = registerAddress;
        return this;
    }

    public String getEnvPrefix() {
        return envPrefix;
    }

    public RegisterConfig setEnvPrefix(@Nonnull String envPrefix) {
        this.envPrefix = envPrefix;
        return this;
    }

    public long getExpiredTime() {
        return expiredTime;
    }

    public void setExpiredTime(long expiredTime) {
        this.expiredTime = expiredTime;
    }

    public boolean isInvoker() {
        return isInvoker;
    }

    public void setInvoker(boolean invoker) {
        isInvoker = invoker;
    }
}
