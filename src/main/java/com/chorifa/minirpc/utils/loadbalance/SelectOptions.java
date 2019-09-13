package com.chorifa.minirpc.utils.loadbalance;

public class SelectOptions {

    private String methodName;

    private Object[] args;

    public SelectOptions(){}

    public SelectOptions(String methodName, Object[] args){
        this.methodName = methodName;
        this.args = args;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }
}
