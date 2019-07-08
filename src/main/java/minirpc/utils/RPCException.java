package minirpc.utils;

public class RPCException extends RuntimeException {

    public RPCException() {
        super();
    }

    public RPCException(String message) {
        super(message);
    }

    public RPCException(String message, Throwable cause) {
        super(message, cause);
    }

    public RPCException(Throwable cause) {
        super(cause);
    }
}
