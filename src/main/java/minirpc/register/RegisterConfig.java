package minirpc.register;

import javax.annotation.Nonnull;

public class RegisterConfig {

    private String registerAddress = "localhost:2181";

    private String envPrefix = "test";

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
}
