package minirpc.api.param;

import java.io.Serializable;
import java.util.List;

public class UserDO implements Serializable {
	private static final long serialVersionUID = 201907041533L;
	public String name;
	public int age;
	public List<String> like;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public List<String> getLike() {
		return like;
	}

	public void setLike(List<String> like) {
		this.like = like;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if(like != null)
			like.forEach(sb::append);
		return "UserDO{" +
				"name='" + name + '\'' +
				", age=" + age +
				", like=" + sb.toString() +
				'}';
	}
}
