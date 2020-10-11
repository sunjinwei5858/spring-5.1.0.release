package sunjinwei.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class StudentService {

    @Autowired
    private ApplicationContext applicationContext;


    @Autowired
    private UserService userService;

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StudentService() {
        System.out.println("==StudentService no args constructor==");
    }

    @PostConstruct
    public void postConstructMethod() {
        System.out.println("==StudentService @PostConstruct==");
    }


}
