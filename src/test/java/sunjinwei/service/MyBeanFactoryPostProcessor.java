package sunjinwei.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Arrays;

//@Component
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {





    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String[] beanDefinitionNames = beanFactory.getBeanDefinitionNames();
        System.out.println("=====MyBeanFactoryPostProcessor--postProcessBeanFactory--beanDefinitionNames is :" + Arrays.toString(beanDefinitionNames));
    }
}
