package sunjinwei.threadlocal;

import org.junit.Test;
import sunjinwei.domain.Student;

public class Test002 {

	@Test
	public void test001() {
		Student student = new Student();
		student.setStudentName("aaaa");

		UserContext.setStudent(student);

		System.out.println(UserContext.getStudent().getStudentName());

		new Thread(() -> {
			Student stu = UserContext.getStudent();
			System.out.println(stu.getStudentName());

		},"bbb").start();
	}
}
