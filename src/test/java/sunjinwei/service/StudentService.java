package sunjinwei.service;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class StudentService {

   /* @Autowired
    private ApplicationContext applicationContext;
*/

   /* @Autowired
    private UserService userService;*/

    private String name;

    /**
     * 如果getName被代理了，如果 @EnableAspectJAutoProxy(exposeProxy = true)
     * exposeProxy为true，那么
     * @return
     */
    public String getName() {

        System.out.println(6/0);

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
