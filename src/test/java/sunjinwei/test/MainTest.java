package sunjinwei.test;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import sunjinwei.config.MainConfig;
import sunjinwei.service.StudentService;

public class MainTest {

    @Test
    public void test0001() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MainConfig.class);

        StudentService studentService = (StudentService) context.getBean("studentService");

        studentService.getName();


        System.out.println(studentService);

    }
}
