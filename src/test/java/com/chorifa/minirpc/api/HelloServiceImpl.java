package com.chorifa.minirpc.api;

import com.chorifa.minirpc.api.param.NageDO;
import com.chorifa.minirpc.api.param.UserDO;

import java.util.List;

public class HelloServiceImpl<T> extends HelloService<T> {
	@Override
	public String sayHello(String name, Integer num) {
		String s = "name = "+name + " num = " +num;
		System.out.println(s);
		return s;
	}

	@Override
	public UserDO show(NageDO nageDO, List<String> list) {
		UserDO userDO = new UserDO();
		userDO.age = nageDO.age;
		userDO.name = nageDO.name;
		userDO.like = list;
		return userDO;
	}

	@Override
	public T echo(T a) {
		return a;
	}
}
