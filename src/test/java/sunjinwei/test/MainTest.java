package sunjinwei.test;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import sunjinwei.config.MainConfig;
import sunjinwei.service.UserService;

public class MainTest {

    @Test
    public void test0001() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MainConfig.class);
        UserService userService = (UserService) context.getBean("userService");

        userService.test();

        System.out.println(userService);

    }

    @Test
    public void test002() {
        boolean flag = true;
        if (flag) {
            System.out.println("fjhsdfha");
        }


    }
}
