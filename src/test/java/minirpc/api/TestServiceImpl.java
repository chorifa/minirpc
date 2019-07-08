package minirpc.api;

import minirpc.api.param.NageDO;
import minirpc.api.param.UserDO;

import java.util.List;

public class TestServiceImpl<T> implements TestService<T> {

	@Override
	public T echo(T a) {
		return a;
	}

	@Override
	public String sayHi() {
		return "hello world!";
	}

	@Override
	public UserDO show(NageDO nageDO, List<String> list) {
		UserDO userDO = new UserDO();
		userDO.age = nageDO.age;
		userDO.name = nageDO.name;
		userDO.like = list;
		return userDO;
	}
}
