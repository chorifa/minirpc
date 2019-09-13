package com.chorifa.minirpc.remoting.entity;

import java.io.Serializable;

public class RemotingResponse implements Serializable {
    private static final long serialVersionUID = 201907041533L;

    private String requestId;

    private String errorMsg;

    private Object result;

    // -------------------------- get and set --------------------------

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "RemotingResponse{" +
                "requestId='" + requestId + '\'' +
                ", errorMsg='" + errorMsg + '\'' +
                ", result=" + result +
                '}';
    }
}
