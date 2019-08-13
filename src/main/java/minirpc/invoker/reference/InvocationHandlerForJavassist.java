package minirpc.invoker.reference;

import javassist.CtMethod;

public interface InvocationHandlerForJavassist {
    Object invoke(Object proxy, CtMethod ctMethod, Object[] args) throws Exception;
}
