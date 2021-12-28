package sunjinwei.test;

import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.PropertiesBeanDefinitionReader;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
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
	 * 1配置
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

	/**
	 * 2解析
	 */
	@Test
	public void testBeanDefinitionParse() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		// 基于java注解的AnnotatedBeanDefinitionReader实现
		AnnotatedBeanDefinitionReader beanDefinitionReader = new AnnotatedBeanDefinitionReader(beanFactory);

		int pre = beanFactory.getBeanDefinitionCount();
		// 注册当前类(非@component class)
		beanDefinitionReader.registerBean(BeanDefinitionTest.class);
		int post = beanFactory.getBeanDefinitionCount();
		System.out.println("===已加载的===" + (post - pre));

		// 普通的class作为component注册到spring ioc容器后，通常bean名称为 beanDefinitionTest
		// bean名称生成来自于BeanNameGenerator 注解实现AnnotatedBeanNameGenerator
		BeanDefinitionTest beanDefinitionTest = beanFactory.getBean("beanDefinitionTest", BeanDefinitionTest.class);
		System.out.println(beanDefinitionTest);
	}

}