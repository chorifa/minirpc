package com.chorifa.minirpc.remoting.entity;

import java.io.Serializable;
import java.util.Arrays;

public class RemotingRequest implements Serializable {
    private static final long serialVersionUID = 201907041533L;

    private String targetAddr;

    private String requestId;

    private String methodName;

    private String interfaceName;

    private String version;

    private Class<?>[] parameterType;

    private Object[] args;

    private boolean isBlocking = false;

    // -------------------------- get and set --------------------------

    public boolean isBlocking() {
        return isBlocking;
    }

    public void setBlocking(boolean blocking) {
        isBlocking = blocking;
    }

    public String getTargetAddr() {
        return targetAddr;
    }

    public void setTargetAddr(String targetAddr) {
        this.targetAddr = targetAddr;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Class<?>[] getParameterType() {
        return parameterType;
    }

    public void setParameterType(Class<?>[] parameterType) {
        this.parameterType = parameterType;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    @Override
    public String toString() {
        return "RemotingRequest{" +
                "requestId='" + requestId + '\'' +
                ", methodName='" + methodName + '\'' +
                ", interfaceName='" + interfaceName + '\'' +
                ", version='" + version + '\'' +
                ", parameterType=" + Arrays.toString(parameterType) +
                ", args=" + Arrays.toString(args) +
                '}';
    }
}
