package sunjinwei.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class UserService {

	@Autowired
	private StudentService studentService;

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void test() {
        System.out.println("test方法执行。。。。。");
    }

    public UserService() {
        System.out.println("==UserService no args constructor==");
    }

    @PostConstruct
    public void postConstructMethod() {
        System.out.println("==UserService @PostConstruct==");
    }


}
