package com.chorifa.minirpc.provider;

import com.chorifa.minirpc.registry.RegistryConfig;
import com.chorifa.minirpc.registry.RegistryService;
import com.chorifa.minirpc.registry.RegistryType;
import com.chorifa.minirpc.remoting.RemotingType;
import com.chorifa.minirpc.remoting.Server;
import com.chorifa.minirpc.remoting.entity.RemotingRequest;
import com.chorifa.minirpc.remoting.entity.RemotingResponse;
import com.chorifa.minirpc.utils.AddressUtil;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.serialize.SerialType;
import com.chorifa.minirpc.utils.serialize.Serializer;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class DefaultRPCProviderFactory {
    private static Logger logger = LoggerFactory.getLogger(DefaultRPCProviderFactory.class);

    private String ip;
    private int port;

    private RemotingType remotingType;

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

    // --------------------------- server manager ---------------------------
    private Server server;
    private String address;
    private RegistryService register;
    private RegistryConfig config;

    public void start(){
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
                register=null;
            });
        }
        server.start(this);
    }

    public void stop(){
        server.stop();
    }

    // --------------------------- service invoke ---------------------------
    private Map<String /* interface name */ ,Object> serviceMap = new HashMap<>();

    public Map<String, Object> getServiceMap() {
        return serviceMap;
    }

    public DefaultRPCProviderFactory addService(String interfaceName, String version, Object serviceEntity){
        if(interfaceName == null || interfaceName.length() == 0 || serviceEntity == null)
            throw new RPCException("add invalid service...");

        serviceMap.put(generateKey(interfaceName, version),serviceEntity);
        return this;
    }

    private String generateKey(String interfaceName, String version){
        return version == null?interfaceName:interfaceName+":"+version.trim();
    }

    public RemotingResponse invokeService(RemotingRequest request) {

        RemotingResponse response = new RemotingResponse();
        response.setRequestId(request.getRequestId());

        String key = generateKey(request.getInterfaceName(),request.getVersion());
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

}
