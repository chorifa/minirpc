package com.chorifa.minirpc.registry;

import javax.annotation.Nonnull;

public class RegistryConfig {

    private String registerAddress = "localhost:2181";

    private String envPrefix = "test";

    // for redis (provider)
    private long expiredTime = 10*1000; // 10s

    private boolean isInvoker = false;

    public String getRegisterAddress() {
        return registerAddress;
    }

    public RegistryConfig setRegisterAddress(@Nonnull String registerAddress) {
        this.registerAddress = registerAddress;
        return this;
    }

    public String getEnvPrefix() {
        return envPrefix;
    }

    public RegistryConfig setEnvPrefix(@Nonnull String envPrefix) {
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
