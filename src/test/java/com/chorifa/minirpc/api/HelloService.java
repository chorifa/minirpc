package com.chorifa.minirpc.api;

import com.chorifa.minirpc.api.param.NageDO;
import com.chorifa.minirpc.api.param.UserDO;

import java.util.List;

public abstract class HelloService<T> {

	public String sayHi(int t){
		System.out.println("Hi "+t+" times.");
		return "Hi "+t+" times.";
	}

	public abstract String sayHello(String name, Integer num);

	public abstract UserDO show(NageDO nageDO, List<String> list);

	public abstract T echo(T a);

}
