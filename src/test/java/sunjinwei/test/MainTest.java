package sunjinwei.test;

import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.PropertiesBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import sunjinwei.config.MainConfig;
import sunjinwei.domain.Student;

public class MainTest {

	@Test
	public void test0001() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MainConfig.class);

		Object object = context.getBean("myFactoryBean");

		System.out.println(object);

	}


}
