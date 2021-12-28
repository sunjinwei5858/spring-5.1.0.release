package sunjinwei.test;

import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.PropertiesBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import sunjinwei.domain.Student;

/**
 * @program: sunjinwei.test
 * @author: sun jinwei
 * @create: 2021-12-28 20:12
 * @description:
 **/
public class BeanDefinitionTest {

	/**
	 * 使用properties的方式配置bean定义
	 * 1.在META-INF配置student.properties文件
	 * 2.参考PropertiesBeanDefinitionReader的注释的案例写法格式 在配置文件编写student相关的配置
	 */
	@Test
	public void testBeanDefinition() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		// 实例化基于 properties 资源 BeanDefinitionReader
		PropertiesBeanDefinitionReader definitionReader = new PropertiesBeanDefinitionReader(beanFactory);
		String location = "META-INF/student.properties";
		// 加载properties 资源
		Resource resource = new ClassPathResource(location);
		EncodedResource encodedResource = new EncodedResource(resource, "utf-8");
		int definitionNum = definitionReader.loadBeanDefinitions(encodedResource);
		System.out.println("==definitionNum====" + definitionNum);
		Student student = beanFactory.getBean("student", Student.class);
		System.out.println("student is:  " + student.getStudentName());
	}
}