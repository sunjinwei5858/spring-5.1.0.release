package sunjinwei.test;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import sunjinwei.config.MainConfig;

public class MainTest {

    @Test
    public void test0001() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MainConfig.class);

        Object object = context.getBean("myFactoryBean");

        System.out.println(object);


    }
}
