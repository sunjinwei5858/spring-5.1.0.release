package sunjinwei.service;

import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.stereotype.Component;
import sunjinwei.domain.Student;

@Component
public class MyFactoryBean implements SmartFactoryBean<Student> {
    @Override
    public Student getObject() throws Exception {
        return new Student();
    }

    @Override
    public Class<?> getObjectType() {
        return Student.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @Override
    public boolean isPrototype() {
        return false;
    }

    @Override
    public boolean isEagerInit() {
        return false;
    }
}
