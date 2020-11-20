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

package org.springframework.aop.aspectj.annotation;

import org.aspectj.lang.reflect.PerClauseKind;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @see AnnotationAwareAspectJAutoProxyCreator
 * @since 2.0.2
 */
public class BeanFactoryAspectJAdvisorsBuilder {

    private final ListableBeanFactory beanFactory;

    private final AspectJAdvisorFactory advisorFactory;

    @Nullable
    private volatile List<String> aspectBeanNames;

    /**
     * 缓存切面类四个通知，key为切面类的名称，value为切面类配置的通知集合
     */
    private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

    private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


    /**
     * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
     *
     * @param beanFactory the ListableBeanFactory to scan
     */
    public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
        this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
    }

    /**
     * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
     *
     * @param beanFactory    the ListableBeanFactory to scan
     * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
     */
    public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
        Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
        Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
        this.beanFactory = beanFactory;
        this.advisorFactory = advisorFactory;
    }


    /**
     * 解析aop切面的方法: BeanFactoryAspectJAdvisorsBuilder#buildAspectJAdvisors()
     * 解析事务的方法: BeanFactoryAdvisorRetrievalHelper#findAdvisorBeans()
     *
     *  1。获取所有的beanName
     *  2。遍历所有的beanName 找出声明了@AspectJ注解的类
     *  3。对标记为@AspectJ注解的类进行增强器的获取【这里是最重要的】
     *  4。将提取结果加入缓存
     * Look for AspectJ-annotated aspect beans in the current bean factory,
     * and return to a list of Spring AOP Advisors representing them.
     * <p>Creates a Spring Advisor for each AspectJ advice method.
     *
     * @return the list of {@link org.springframework.aop.Advisor} beans
     * @see #isEligibleBean
     */
    public List<Advisor> buildAspectJAdvisors() {

        List<String> aspectNames = this.aspectBeanNames;

        /**
         * 如果缓存中没有，那么就去解析切面
         */
        if (aspectNames == null) {
            /**
             * 做了dcl检查 双重判断，联想到单例模式 也使用双重判断的方法 即dcl
             */
            synchronized (this) {
                aspectNames = this.aspectBeanNames;
                if (aspectNames == null) {
                    /**
                     * 用于保存所有解析出来的Advisor集合对象
                     */
                    List<Advisor> advisors = new ArrayList<>();
                    /**
                     * 用于保存所有切面名称的集合
                     */
                    aspectNames = new ArrayList<>();
                    /**
                     * 1.
                     * aop功能在这里传入的是Object.class!!!!，代表去容器中获取所有组件的名称，然后再经过一一遍历。
                     * 这个过程是十分耗性能的，所以说spring在这里加入了保存切面信息的缓存。
                     *
                     * 但是事务功能不一样，事务传的是Advisor.class，选择范围小，不消耗性能。
                     * 所以事务功能spring没有加入缓存来保存事务相关的Advisor
                     */
                    String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                            this.beanFactory, Object.class, true, false);
                    /**
                     * 遍历我们从容器中获取到的beanNames
                     */
                    for (String beanName : beanNames) {
                        if (!isEligibleBean(beanName)) {
                            continue;
                        }
                        // We must be careful not to instantiate beans eagerly as in this case they
                        // would be cached by the Spring container but would not have been weaved.
                        /**
                         * 通过beanName从容器中获取到Class对象 beanType: sunjinwei.service.MyAspect
                         */
                        Class<?> beanType = this.beanFactory.getType(beanName);
                        if (beanType == null) {
                            continue;
                        }
                        /**
                         * 2.
                         * !!!根据class对象判断是不是切面 是否加了@Aspect注解
                         */
                        if (this.advisorFactory.isAspect(beanType)) {
                            /**
                             * 如果是 加入到缓存中
                             * 高级面试题：为什么要将切面加入缓存，因为获取beanNames，传入的是Object 会将容器的object都获取出来，然后挨个遍历
                             * 这是十分耗性能的
                             */
                            aspectNames.add(beanName);
                            /**
                             * 将切面类的class和beanName封装成AspectMetadata对象
                             */
                            AspectMetadata amd = new AspectMetadata(beanType, beanName);
                            if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
                                /**
                                 * 构建切面注解的实例工厂
                                 */
                                MetadataAwareAspectInstanceFactory factory = new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
                                /**
                                 * 3.对标记为@AspectJ注解的类进行增强器的获取 这个步骤是最重要的 获取增强器 解析标记@AspectJ注解中的增强方法
                                 */
                                List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
                                /**
                                 * 4.加入到缓存中
                                 */
                                if (this.beanFactory.isSingleton(beanName)) {
                                    this.advisorsCache.put(beanName, classAdvisors);
                                } else {
                                    this.aspectFactoryCache.put(beanName, factory);
                                }
                                advisors.addAll(classAdvisors);
                            } else {
                                // Per target or per this.
                                if (this.beanFactory.isSingleton(beanName)) {
                                    throw new IllegalArgumentException("Bean with name '" + beanName +
                                            "' is a singleton, but aspect instantiation model is not singleton");
                                }
                                MetadataAwareAspectInstanceFactory factory =
                                        new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
                                this.aspectFactoryCache.put(beanName, factory);
                                advisors.addAll(this.advisorFactory.getAdvisors(factory));
                            }
                        }
                    }
                    this.aspectBeanNames = aspectNames;
                    return advisors;
                }
            }
        }

        if (aspectNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<Advisor> advisors = new ArrayList<>();
        for (String aspectName : aspectNames) {
            List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
            if (cachedAdvisors != null) {
                advisors.addAll(cachedAdvisors);
            } else {
                MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
                advisors.addAll(this.advisorFactory.getAdvisors(factory));
            }
        }
        return advisors;
    }

    /**
     * Return whether the aspect bean with the given name is eligible.
     *
     * @param beanName the name of the aspect bean
     * @return whether the bean is eligible
     */
    protected boolean isEligibleBean(String beanName) {
        return true;
    }

}
