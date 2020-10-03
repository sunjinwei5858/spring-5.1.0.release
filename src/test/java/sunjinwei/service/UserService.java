package sunjinwei.service;

import org.springframework.stereotype.Component;

@Component
public class UserService {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "UserService{" +
                "name='" + name + '\'' +
                '}';
    }

    public void test() {
        System.out.println("test方法执行。。。。。");
    }

}
