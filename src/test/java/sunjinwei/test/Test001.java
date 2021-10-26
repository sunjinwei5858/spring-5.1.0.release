package sunjinwei.test;

import org.junit.Test;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import sunjinwei.domain.Student;

public class Test001 {

	@Test
	public void test001() {
		SpelExpressionParser spelExpressionParser = new SpelExpressionParser();
		SpelExpression spelExpression = spelExpressionParser.parseRaw("#root.studentName");

		Student student = new Student();
		student.setStudentName("aaa");

		System.out.println(spelExpression.getValue(student));


	}
}
