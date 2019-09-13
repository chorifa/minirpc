package com.chorifa.minirpc.invoker.reference;

import com.chorifa.minirpc.invoker.DefaultRPCInvokerFactory;
import com.chorifa.minirpc.invoker.type.SendType;
import com.chorifa.minirpc.remoting.RemotingType;
import com.chorifa.minirpc.utils.RPCException;
import com.chorifa.minirpc.utils.serialize.SerialType;
import com.chorifa.minirpc.utils.serialize.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReferenceManagerBuilder {
	private static final Logger logger = LoggerFactory.getLogger(ReferenceManagerBuilder.class);

	private Serializer serializer;

	private Class<?> serviceClass;
	private String version;

	private String address;

	private SendType sendType;

	private RemotingType remotingType;

	private DefaultRPCInvokerFactory invokerFactory;

	// raw type, T cannot infer in static
	public static ReferenceManagerBuilder init(){
		return new ReferenceManagerBuilder();
	}

	// according to return type to infer generic
	public RPCReferenceManager build (){
		if(serviceClass == null)
			throw new RPCException("origin service can not be null...");
		if(serializer == null){
			serializer = SerialType.HESSIAN.getSerializer();
			logger.warn("serializer not specify. using HESSIAN as default.");
		}
		if(sendType == null){
			sendType = SendType.SYNC;
			logger.warn("sendType not specify. using SYNC as default.");
		}
		if(invokerFactory == null){
			invokerFactory = DefaultRPCInvokerFactory.getInstance();
			logger.warn("invokerFactory not specify. using default instance.");
		}
		if(remotingType == null){
			remotingType = RemotingType.NETTY;
			logger.warn("remoting type not specify. using TCP(Socket) as default.");
		}
		return new RPCReferenceManager(this);
	}

	// set
	public ReferenceManagerBuilder applySerializer(SerialType serialType){
		if(serialType != null)
			this.serializer = serialType.getSerializer();
		return this;
	}

	public ReferenceManagerBuilder forService(Class<?> serviceClass){
		if(serviceClass != null)
			this.serviceClass = serviceClass;
		return this;
	}

	public ReferenceManagerBuilder applyVersion(String version){
		this.version = version;
		return this;
	}

	public ReferenceManagerBuilder forAddress(String address){
		this.address = address;
		return this;
	}

	public ReferenceManagerBuilder applySendType (SendType type){
		this.sendType = type;
		return this;
	}

	public ReferenceManagerBuilder applyRemotingType (RemotingType type){
		this.remotingType = type;
		return this;
	}

	public ReferenceManagerBuilder applyInvokeFactory (DefaultRPCInvokerFactory factory){
		this.invokerFactory = factory;
		return this;
	}

	Serializer getSerializer() {
		return serializer;
	}

	Class<?> getServiceClass() {
		return serviceClass;
	}

	String getVersion() {
		return version;
	}

	String getAddress() {
		return address;
	}

	SendType getSendType() {
		return sendType;
	}

	RemotingType getRemotingType() {
		return remotingType;
	}

	DefaultRPCInvokerFactory getInvokerFactory() {
		return invokerFactory;
	}
}
