package com.chorifa.minirpc.provider;

import com.chorifa.minirpc.registry.RegistryConfig;
import com.chorifa.minirpc.registry.RegistryService;
import com.chorifa.minirpc.registry.RegistryType;
import com.chorifa.minirpc.remoting.RemotingType;
import com.chorifa.minirpc.remoting.Server;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.threads.EventBus;
import com.chorifa.minirpc.utils.AddressUtil;
import com.chorifa.minirpc.utils.RPCException;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultRPCProviderFactory {
    private static Logger logger = LoggerFactory.getLogger(DefaultRPCProviderFactory.class);

    private String ip;
    private int port;

    private RemotingType remotingType;

    private final EventBus eventBus;

    public DefaultRPCProviderFactory() {
        this(null);
    }

    public DefaultRPCProviderFactory(EventBus bus) {
        if(bus == null)
            this.eventBus = EventBus.DEFAULT_EVENT_BUS;
        else this.eventBus = bus;
    }

    public DefaultRPCProviderFactory init(RemotingType remotingType, int port){
        return init("localhost",port,remotingType,null,null);
    }

    public DefaultRPCProviderFactory init(RemotingType remotingType){
        return init("localhost",8086,remotingType,null,null);
    }

    public DefaultRPCProviderFactory init(RegistryType registryType, RegistryConfig config, int port){
        return init("localhost", port, RemotingType.NETTY, registryType,config);
    }

    public DefaultRPCProviderFactory init(){
        return init("localhost",8086, RemotingType.NETTY, null,null);
    }

    public DefaultRPCProviderFactory init(String ip, int port, RemotingType remotingType, RegistryType registryType, RegistryConfig config){

        // TODO check whether port is used && default ip

        if(ip == null)
            throw new RPCException("server: ip should not be null...");
        if(port <= 0)
            throw new RPCException("server: port invalid...");
        if(remotingType == null)
            throw new RPCException("server: remoting type should not be null...");
        if(registryType != null && config == null)
            throw new RPCException("server: register config not specify...");

        this.ip = ip;
        this.port = port;
        this.remotingType = remotingType;
        if(registryType != null && registryType.getRegisterClass() != null) {
            try {
                this.register = registryType.getRegisterClass().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                logger.error("create register failed... try again", e);
                try {
                    this.register = registryType.getRegisterClass().newInstance();
                } catch (IllegalAccessException | InstantiationException ex) {
                    logger.error("create register failed...", ex);
                    throw new RPCException(ex);
                }
            }
        }
        this.config = config;
        this.address = AddressUtil.generateAddress(this.ip,this.port);

        return this;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    // --------------------------- server manager ---------------------------
    private Server server;
    private String address;
    private RegistryService register;
    private RegistryConfig config;

    public void start() {
        try {
            server = this.remotingType.getServer().getDeclaredConstructor().newInstance();
        }catch (Exception e){
            logger.error("Provider: new server start error... try again",e);
            try {
                server = this.remotingType.getServer().newInstance();
            }catch (Exception ex) {
                logger.error("Provider: new server start error...",ex);
                throw new RPCException("start server error...");
            }
        }
        // >>> register
        if(register != null){
            server.setStartCallBack(()->{
                register.start(config);
                serviceMap.keySet().forEach((k)-> register.register(k,this.address));
            });
            server.setStopCallBack(()->{
                register.stop();
                register = null;
            });
        }
        try {
            server.start(this);
        }catch (Exception ex) {
            if(ex instanceof RPCException) throw (RPCException)ex;
            else throw new RPCException("Provider: occur exception when start server", ex);
        }

    }

    public void stop(){
        server.stop();
    }

    // --------------------------- service invoke ---------------------------
    private final Map<String /* interface name */ ,Object> serviceMap = new HashMap<>();
    private final Set<String /* interface name */> blockingServices = new HashSet<>();

    public Map<String, Object> getServiceMap() {
        return serviceMap;
    }

    public DefaultRPCProviderFactory addService(String interfaceName, String version, Object serviceEntity) {
        return addService(interfaceName, version, serviceEntity, ServiceCtl.NON_BLOCKING);
    }

    public DefaultRPCProviderFactory addService(String interfaceName, String version, Object serviceEntity, ServiceCtl ctl) {
        if(interfaceName == null || interfaceName.length() == 0 || serviceEntity == null)
            throw new RPCException("add invalid service...");
        String key = generateKey(interfaceName, version);
        serviceMap.put(key, serviceEntity);
        switch (ctl) {
            // NOTE: Even if key is bound to a fix EventLoop, when invoking the service,
            // providerFactory.invokeService(request) is used,
            // Hence, it should be promised that, Each service corresponded to key in all providerFactory
            // should be the same, i.e. use Singleton when addService().
            // TODO: or use static ConcurrentHashMap to maintain <key, service>, if ServiceCtl.BIND used
            case BIND: eventBus.subscribe(key, true); break;
            case BLOCKING: blockingServices.add(key); break;
        }
        return this;
    }

    public String generateKey(String interfaceName, String version){
        return version == null?interfaceName:interfaceName+":"+version.trim();
    }

    public RemotingResponse invokeService(RemotingRequest request) {

        RemotingResponse response = new RemotingResponse();
        response.setRequestId(request.getRequestId());

        String key = generateKey(request.getInterfaceName(), request.getVersion());
        Object serviceEntity = serviceMap.get(key);
        if(serviceEntity == null) {
            response.setErrorMsg("no such service...");
            return response;
        }

        try {
            // invokeExactMethod --->>> setAccessible(false)
            Object result = MethodUtils.invokeExactMethod(serviceEntity, request.getMethodName(), request.getArgs(), request.getParameterType());
            response.setResult(result);
        }catch (Throwable e){
            logger.error("service invoke method encounter error --->>>",e);
            response.setErrorMsg(e.getMessage());
        }

        return response;
    }

    public boolean isBlocking(String key) {
        return blockingServices.contains(key);
    }

    public boolean isBlocking(String interfaceName, String version) {
        return blockingServices.contains(generateKey(interfaceName, version));
    }

}
