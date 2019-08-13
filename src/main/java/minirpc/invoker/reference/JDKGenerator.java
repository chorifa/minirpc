package minirpc.invoker.reference;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class JDKGenerator {

    static Object generateProxy(Class<?> clazz, InvocationHandler handler){
        return Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{clazz},
                handler);
    }

}
