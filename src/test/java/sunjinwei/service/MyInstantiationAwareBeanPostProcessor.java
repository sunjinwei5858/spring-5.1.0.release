package sunjinwei.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class MyInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {
    /**
     * 此方法可以对bean进行提前创建 因为获取到beanClass
     *
     * @param beanClass the class of the bean to be instantiated
     * @param beanName  the name of the bean
     * @return
     * @throws BeansException
     */
    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {

        System.out.println("===嘻嘻嘻嘻嘻嘻嘻");

        if (beanName.equals("userService")) {
            try {
                System.out.println("---MyInstantiationAwareBeanPostProcessor--postProcessBeforeInstantiation--beanName is:" + beanName + "---beanClass is:" + beanClass);
                //Object o = beanClass.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }


        return null;
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        return false;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

        System.out.println("===哈哈哈哈哈哈");

        /*if (beanName.equals("userService")) {
            System.out.println("---MyInstantiationAwareBeanPostProcessor--postProcessBeforeInstantiation--beanName is:" + beanName + "---bean is:" + bean);


        }*/

        return null;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return null;
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
        return null;
    }
}
