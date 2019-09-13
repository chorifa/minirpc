package com.chorifa.minirpc.api.param;

import java.io.Serializable;

public class NageDO implements Serializable {
	private static final long serialVersionUID = 201907041533L;
	public String name;
	public int age;

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "NageDO{" +
				"name='" + name + '\'' +
				", age=" + age +
				'}';
	}
}
