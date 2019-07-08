package minirpc.api;

import minirpc.api.param.NageDO;
import minirpc.api.param.UserDO;

import java.util.List;

public interface TestService<T> {

	T echo(T a);

	String sayHi();

	UserDO show(NageDO nageDO, List<String> list);

}
