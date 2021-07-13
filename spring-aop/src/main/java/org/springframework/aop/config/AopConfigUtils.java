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

package org.springframework.aop.config;

import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * aop前置工作的工具类 比如注册aop的后置处理器，如果@EnableAspectJAutoProxy 配置了属性
 * <p>
 * <p>
 * Utility class for handling registration of AOP auto-proxy creators.
 *
 * <p>Only a single auto-proxy creator should be registered yet multiple concrete
 * implementations are available. This class provides a simple escalation protocol,
 * allowing a caller to request a particular auto-proxy creator and know that creator,
 * <i>or a more capable variant thereof</i>, will be registered as a post-processor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @see AopNamespaceUtils
 * @since 2.5
 */
public abstract class AopConfigUtils {

    /**
     * The bean name of the internally managed auto-proxy creator.
     */
    public static final String AUTO_PROXY_CREATOR_BEAN_NAME =
            "org.springframework.aop.config.internalAutoProxyCreator";

    /**
     * Stores the auto proxy creator classes in escalation order.
     */
    private static final List<Class<?>> APC_PRIORITY_LIST = new ArrayList<>(3);

    static {
        // Set up the escalation list...
        APC_PRIORITY_LIST.add(InfrastructureAdvisorAutoProxyCreator.class);
        APC_PRIORITY_LIST.add(AspectJAwareAdvisorAutoProxyCreator.class);
        APC_PRIORITY_LIST.add(AnnotationAwareAspectJAutoProxyCreator.class);
    }


    @Nullable
    public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
        return registerAutoProxyCreatorIfNecessary(registry, null);
    }

    /**
     * 注册的是：InfrastructureAdvisorAutoProxyCreator--spring的事务
     *
     * @param registry
     * @param source
     * @return
     */
    @Nullable
    public static BeanDefinition registerAutoProxyCreatorIfNecessary(
            BeanDefinitionRegistry registry, @Nullable Object source) {

        return registerOrEscalateApcAsRequired(InfrastructureAdvisorAutoProxyCreator.class, registry, source);
    }

    @Nullable
    public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
        return registerAspectJAutoProxyCreatorIfNecessary(registry, null);
    }

    /**
     * 注册的是：AspectJAwareAdvisorAutoProxyCreator--spring的aop
     *
     * @param registry
     * @param source
     * @return
     */
    @Nullable
    public static BeanDefinition registerAspectJAutoProxyCreatorIfNecessary(
            BeanDefinitionRegistry registry, @Nullable Object source) {

        return registerOrEscalateApcAsRequired(AspectJAwareAdvisorAutoProxyCreator.class, registry, source);
    }

    /**
     * 使用@EnableAspectJAutoProxy 开启aop 然后注册AnnotationAwareAspectJAutoProxyCreator aop的beanPost后置处理器
     *
     * @param registry
     * @return
     */
    @Nullable
    public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {

        return registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry, null);
    }

    /**
     * 使用xml开启aop，AopNamespaceUtils直接调用该方法进行注册AnnotationAwareAspectJAutoProxyCreator aop的beanpost后置处理器
     *
     * @param registry
     * @param source
     * @return
     */
    @Nullable
    public static BeanDefinition registerAspectJAnnotationAutoProxyCreatorIfNecessary(
            BeanDefinitionRegistry registry, @Nullable Object source) {

        /**
         * 注册AnnotationAwareAspectJAutoProxyCreator后置处理器
         */
        return registerOrEscalateApcAsRequired(AnnotationAwareAspectJAutoProxyCreator.class, registry, source);
    }

    /**
     * 对bean定义设置两个属性 proxyTargetClass
     *
     * @param registry
     */
    public static void forceAutoProxyCreatorToUseClassProxying(BeanDefinitionRegistry registry) {
        if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
            BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
            definition.getPropertyValues().add("proxyTargetClass", Boolean.TRUE);
        }
    }

    /**
     * 对bean定义设置属性 exposeProxy
     *
     * @param registry
     */
    public static void forceAutoProxyCreatorToExposeProxy(BeanDefinitionRegistry registry) {
        if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
            BeanDefinition definition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
            definition.getPropertyValues().add("exposeProxy", Boolean.TRUE);
        }
    }

    /**
     * aop：注册或者升级（escalate）AnnotationAwareAspectJAutoProxyCreator
     * tx: 注册或者升级（escalate）InfrastructureAdvisorAutoProxyCreator
     *
     * 可以看到，最终是注册到了Bean容器中，作为BeanDefinition存在。
     * 我们可以认为aop的自动配置过程就是为了创建AnnotationAwareAspectJAutoProxyCreator这个类的BeanDefinition。
     *
     * @param cls
     * @param registry
     * @param source
     * @return
     */
    @Nullable
    private static BeanDefinition registerOrEscalateApcAsRequired(
            Class<?> cls, BeanDefinitionRegistry registry, @Nullable Object source) {

        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

        /**
         * 如果已经存在了自动代理创建器，而且存在的自动代理创建器与现在的不一致，那么需要根据优先级来判断到底需要使用哪个
         */
        if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
            BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
            if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
                int currentPriority = findPriorityForClass(apcDefinition.getBeanClassName());
                int requiredPriority = findPriorityForClass(cls);
                /**
                 * 判断优先级
                 */
                if (currentPriority < requiredPriority) {
                    apcDefinition.setBeanClassName(cls.getName());
                }
            }
            return null;
        }
        // 构建BeanDefinition
        RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
        beanDefinition.setSource(source);
        beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
        beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        // 注册aop的AnnotationAwareAspectJAutoProxyCreator这个beanPost后置处理器
        // 也就是注册到bean容器
        registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME, beanDefinition);
        return beanDefinition;
    }

    private static int findPriorityForClass(Class<?> clazz) {
        return APC_PRIORITY_LIST.indexOf(clazz);
    }

    private static int findPriorityForClass(@Nullable String className) {
        for (int i = 0; i < APC_PRIORITY_LIST.size(); i++) {
            Class<?> clazz = APC_PRIORITY_LIST.get(i);
            if (clazz.getName().equals(className)) {
                return i;
            }
        }
        throw new IllegalArgumentException(
                "Class name [" + className + "] is not a known auto-proxy creator class");
    }

}
