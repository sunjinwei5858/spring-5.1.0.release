/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.lang.Nullable;

import java.beans.PropertyDescriptor;

/**
 * 这个接口重载的postProcessBeforeInstantiation()方法很强大，
 * 1。可以提前创建bean，因为该方法的入参为 beanClass
 * 2。可以属性注入postProcessProperties
 * <p>
 * Subinterface of {@link BeanPostProcessor} that adds a before-instantiation callback,
 * and a callback after instantiation but before explicit properties are set or
 * autowiring occurs.
 *
 * <p>Typically used to suppress default instantiation for specific target beans,
 * for example to create proxies with special TargetSources (pooling targets,
 * lazily initializing targets, etc), or to implement additional injection strategies
 * such as field injection.
 *
 * <p><b>NOTE:</b> This interface is a special purpose interface, mainly for
 * internal use within the framework. It is recommended to implement the plain
 * {@link BeanPostProcessor} interface as far as possible, or to derive from
 * {@link InstantiationAwareBeanPostProcessorAdapter} in order to be shielded
 * from extensions to this interface.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#setCustomTargetSourceCreators
 * @see org.springframework.aop.framework.autoproxy.target.LazyInitTargetSourceCreator
 * @since 1.2
 */
public interface InstantiationAwareBeanPostProcessor extends BeanPostProcessor {

    /**
     * doCreateBean 之前调用，传入的是beanClass
     * <p>
     * 这个方法可以提前创建bean，功能非常强大
     * 因为入参传入的是beanClass，所以很多实现类 比如aop 事务 都实现了该接口，可以进行扩展
     * <p>
     * Apply this BeanPostProcessor <i>before the target bean gets instantiated</i>.
     * The returned bean object may be a proxy to use instead of the target bean,
     * effectively suppressing default instantiation of the target bean.
     * <p>If a non-null object is returned by this method, the bean creation process
     * will be short-circuited. The only further processing applied is the
     * {@link #postProcessAfterInitialization} callback from the configured
     * {@link BeanPostProcessor BeanPostProcessors}.
     * <p>This callback will only be applied to bean definitions with a bean class.
     * In particular, it will not be applied to beans with a factory method.
     * <p>Post-processors may implement the extended
     * {@link SmartInstantiationAwareBeanPostProcessor} interface in order
     * to predict the type of the bean object that they are going to return here.
     * <p>The default implementation returns {@code null}.
     *
     * @param beanClass the class of the bean to be instantiated
     * @param beanName  the name of the bean
     * @return the bean object to expose instead of a default instance of the target bean,
     * or {@code null} to proceed with default instantiation
     * @throws org.springframework.beans.BeansException in case of errors
     * @see #postProcessAfterInstantiation
     * @see org.springframework.beans.factory.support.AbstractBeanDefinition#hasBeanClass
     */
    @Nullable
    default Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        return null;
    }

    /**
     * Perform operations after the bean has been instantiated, via a constructor or factory method,
     * but before Spring property population (from explicit properties or autowiring) occurs.
     * <p>This is the ideal callback for performing custom field injection on the given bean
     * instance, right before Spring's autowiring kicks in.
     * <p>The default implementation returns {@code true}.
     *
     * @param bean     the bean instance created, with properties not having been set yet
     * @param beanName the name of the bean
     * @return {@code true} if properties should be set on the bean;  true代表属性可以注入
     * {@code false} if property population should be skipped. false代表属性注入需要忽略
     * Normal implementations should return {@code true}. 正常的实现应该返回true 所以默认为true
     * Returning {@code false} will also prevent any subsequent InstantiationAwareBeanPostProcessor
     * instances being invoked on this bean instance.
     * @throws org.springframework.beans.BeansException in case of errors
     * @see #postProcessBeforeInstantiation
     */
    default boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        return true;
    }

    /**
     * bean的第二阶段populateBean：属性注入
     * 属性注入就是在这里进行完成!!! 可以进行属性注入有好多个后置处理器，比如
     * 使用jdk自带的注解 @resource : CommonAnnotationBeanPostProcessor
     * 使用spring的注解 @Autowire : AutowiredAnnotationBeanPostProcessor
     * jpa  @PersistenceUnit ：PersistenceAnnotationBeanPostProcessor
     * <p>
     * Post-process the given property values before the factory applies them
     * to the given bean, without any need for property descriptors.
     * <p>Implementations should return {@code null} (the default) if they provide a custom
     * {@link #postProcessPropertyValues} implementation, and {@code pvs} otherwise.
     * In a future version of this interface (with {@link #postProcessPropertyValues} removed),
     * the default implementation will return the given {@code pvs} as-is directly.
     *
     * @param pvs      the property values that the factory is about to apply (never {@code null})
     * @param bean     the bean instance created, but whose properties have not yet been set
     * @param beanName the name of the bean
     * @return the actual property values to apply to the given bean (can be the passed-in
     * PropertyValues instance), or {@code null} which proceeds with the existing properties
     * but specifically continues with a call to {@link #postProcessPropertyValues}
     * (requiring initialized {@code PropertyDescriptor}s for the current bean class)
     * @throws org.springframework.beans.BeansException in case of errors
     * @see #postProcessPropertyValues
     * @since 5.1
     */
    @Nullable
    default PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName)
            throws BeansException {

        return null;
    }

    /**
     * 该重载方法已经被弃用了，@Deprecated标识
     * <p>
     * <p>
     * Post-process the given property values before the factory applies them
     * to the given bean. Allows for checking whether all dependencies have been
     * satisfied, for example based on a "Required" annotation on bean property setters.
     * <p>Also allows for replacing the property values to apply, typically through
     * creating a new MutablePropertyValues instance based on the original PropertyValues,
     * adding or removing specific values.
     * <p>The default implementation returns the given {@code pvs} as-is.
     *
     * @param pvs      the property values that the factory is about to apply (never {@code null})
     * @param pds      the relevant property descriptors for the target bean (with ignored
     *                 dependency types - which the factory handles specifically - already filtered out)
     * @param bean     the bean instance created, but whose properties have not yet been set
     * @param beanName the name of the bean
     * @return the actual property values to apply to the given bean (can be the passed-in
     * PropertyValues instance), or {@code null} to skip property population
     * @throws org.springframework.beans.BeansException in case of errors
     * @see #postProcessProperties
     * @see org.springframework.beans.MutablePropertyValues
     * @deprecated as of 5.1, in favor of {@link #postProcessProperties(PropertyValues, Object, String)}
     */
    @Deprecated
    @Nullable
    default PropertyValues postProcessPropertyValues(
            PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {

        return pvs;
    }

}
