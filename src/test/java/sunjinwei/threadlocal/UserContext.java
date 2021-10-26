package sunjinwei.threadlocal;

import sunjinwei.domain.Student;

public class UserContext {

	private static InheritableThreadLocal<Student> inheritableThreadLocal = new InheritableThreadLocal<>();

	private UserContext() {

	}

	public static Student getStudent() {
		return inheritableThreadLocal.get();
	}

	public static void setStudent(Student student){
		inheritableThreadLocal.set(student);
	}

	public static void clean(){
		inheritableThreadLocal.remove();
	}

}
