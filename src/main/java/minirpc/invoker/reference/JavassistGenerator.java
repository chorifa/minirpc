package minirpc.invoker.reference;

import javassist.*;
import minirpc.utils.RPCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

class JavassistGenerator {
    private static final Logger logger = LoggerFactory.getLogger(JavassistGenerator.class);

    private static final Map<ClassLoader, ClassPool> POOL_MAP = Collections.synchronizedMap(new WeakHashMap<>()); //ClassLoader - ClassPool
    private static final Map<ClassLoader, Map<String, Class<?>>> PROXY_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    static Object getProxy(Class<?> origin, InvocationHandlerForJavassist handler){
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Map<String, Class<?>> map = PROXY_CACHE.get(loader);
        // make sure value in PROXY_CACHE can only create once
        if(map == null){
            map = new HashMap<>();
            Map<String, Class<?>> oldMap = PROXY_CACHE.putIfAbsent(loader,map); // already have value in multi-thread
            if(oldMap != null) //
                map = oldMap;
        }

        Class<?> clazz;
        synchronized (map){ // not thread-safe
            clazz = map.get(origin.getName()); //
            if(clazz == null){
                clazz = generateProxy(origin);
                if(clazz != null)
                    map.put(origin.getName(),clazz);
            }
        }

        if(clazz == null)
            throw new RPCException("JavassistGenerator: cannot create proxy for "+origin.getName());
        // assignment handler with relection
        try {
            Object proxy = clazz.getDeclaredConstructor().newInstance();
            Field f = clazz.getDeclaredField("handler");
            f.setAccessible(true);
            f.set(proxy, handler);  // can not be null
            return proxy;
        }catch (Exception e){
            logger.error("JavassistGenerator: exception when modify handler field",e);
            throw new RPCException(e);
        }
    }

    private static Class<?> generateProxy(Class<?> origin){
        ClassPool pool = getPool(Thread.currentThread().getContextClassLoader());
        try {
            CtClass ct = pool.getAndRename(origin.getName(), origin.getName() + "Proxy");
            // Add InvocationHandler Field
            ct.addField(CtField.make("private minirpc.invoker.reference.InvocationHandlerForJavassist handler;", ct));
            // Add methods field
            ct.addField(CtField.make("private static javassist.CtMethod[] methods;", ct));
            // delete abstract flag
            ct.setModifiers(ct.getModifiers() & ~Modifier.ABSTRACT);
            ct.setSuperclass(pool.get(origin.getName()));
            // modify abstract method
            List<CtMethod> list = new ArrayList<>();
            for(CtMethod method : ct.getMethods()){
                if(Modifier.isAbstract(method.getModifiers())){
                    int index = list.size();
                    method.setBody("{return ($r) this.handler.invoke(this, methods["+index+"],$args);}");
                    list.add(method);
                }
            }
            // assignment with reflection
            Class<?> clazz = ct.toClass();

            /*// get all methods
            Method[] methods = clazz.getMethods();
            List<Method> methodList = new ArrayList<>();
            for(CtMethod m : list){

            }*/
            Field f = clazz.getDeclaredField("methods");
            f.setAccessible(true); // forbidden security check
            f.set(null, list.toArray(new CtMethod[0])); // null -> methods is static field

            return clazz;
        }catch (NotFoundException e){
            logger.error("JavassistGenerator: not found origin class {} or InvocationHandler",origin.getName(),e);
        }catch (CannotCompileException e){
            logger.error("JavassistGenerator: can not compile.", e);
        }catch (NoSuchFieldException e){
            logger.error("JavassistGenerator: no such field", e);
        }catch (IllegalAccessException e){
            logger.error("JavassistGenerator: illegal access exception", e);
        }
        return null;
    }

    private static ClassPool getPool(ClassLoader loader){
        if(loader == null)
            return ClassPool.getDefault();

        ClassPool pool = POOL_MAP.get(loader);
        if(pool == null){
            pool = new ClassPool(true);
            pool.appendClassPath(new LoaderClassPath(loader));
            ClassPool oldPool = POOL_MAP.putIfAbsent(loader, pool);
            if(oldPool != null)
                pool = oldPool;
        }
        return pool;
    }

    static Class<?> findClassViaCtClass(CtClass ctClass){
        try {
            return Class.forName(ctClass.getName());
        }catch (ClassNotFoundException e){
            logger.error("JavassistGenerator: class not found, when find class via CtClass.",e);
            throw new RPCException(e);
        }
    }

    static Class<?>[] findClassViaCtClass(CtClass[] ctClasses){
        Class<?>[] classes = new Class[ctClasses.length];
        for(int i = 0; i < ctClasses.length; i++)
            classes[i] = findClassViaCtClass(ctClasses[i]);
        return classes;
    }

}
