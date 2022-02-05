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

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * 这个类相当于是BeanFactoryPostProcessor和BeanPostProcessor后置处理器的工具类：
 * 1。BeanFactoryPostProcessor:
 * 有invoke方法，
 * AnnotatedBeanDefinitionReader初始化已经将基本的后置处理器注册到bean定义，所以这里直接调用getBean方法进行初始化，然后存储到单例缓存池
 * 接着回调各个后置处理器重写接口的方法
 * <p>
 * 2。BeanPostProcessor:
 * 有register方法，
 * 仅仅就是调用getBean方法，注册到单例缓存池 DefaultSingletonBeanRegistry
 * <p>
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

    private PostProcessorRegistrationDelegate() {
    }


    /**
     * 此处想表达的是：
     * 这里有两个过程：一是注册BeanFactoryPostProcessor后置处理器到BeanFactory容器中 二是回调后置处理器的方法
     * <p>
     * 1。BeanDefinitionRegistryPostProcessor比BeanFactoryPostProcessor先实例化和回调方法，调用时机更早，可以在此处进行扩展
     * 2。BeanDefinitionRegistryPostProcessor还可以进行扩展，控制调用时机，【PriorityOrdered最早，其次Ordered】
     *
     * @param beanFactory
     * @param beanFactoryPostProcessors
     */
    public static void invokeBeanFactoryPostProcessors(
            ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

        /**
         * Invoke BeanDefinitionRegistryPostProcessors first, if any.!!!这句话翻译过来就是先处理BeanDefinitionRegistryPostProcessors
         */
        Set<String> processedBeans = new HashSet<>();

        if (beanFactory instanceof BeanDefinitionRegistry) {
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;

            /**
             * regularPostProcessors --> 常规的BeanFactory后置处理器【BeanFactoryPostProcessor】
             */
            List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();

            /**
             * registryProcessors --> 更特殊的BeanFactory后置处理器【BeanDefinitionRegistryPostProcessor】 注册bean定义时机更早
             */
            List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

            for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
                /**
                 * 此处可以说明实现了BeanDefinitionRegistryPostProcessor接口的实现类的后置处理器，会先调用postProcessBeanDefinitionRegistry，调用时机更早!!!!
                 */
                if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
                    BeanDefinitionRegistryPostProcessor registryProcessor = (BeanDefinitionRegistryPostProcessor) postProcessor;
                    /**
                     * 如果有接口实现了BeanDefinitionRegistryPostProcessor 那么调用postProcessBeanDefinitionRegistry方法进行扩展处理
                     */
                    registryProcessor.postProcessBeanDefinitionRegistry(registry);
                    registryProcessors.add(registryProcessor);
                } else {
                    regularPostProcessors.add(postProcessor);
                }
            }

            // Do not initialize FactoryBeans here: We need to leave all regular beans
            // uninitialized to let the bean factory post-processors apply to them!
            // Separate between BeanDefinitionRegistryPostProcessors that implement
            // PriorityOrdered, Ordered, and the rest.
            /**
             * currentRegistryProcessors该集合的声明是为了需要getBean注册到单例缓存池和回调方法的，
             * 因为后置处理器的实例化和回调 是有顺序的 根据后置处理器是否实现了PriorityOrdered，Ordered等接口
             */
            List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

            /**
             * 1 First, 【BeanDefinitionRegistryPostProcessor】
             * invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
             * 这些后置处理器的处理时机:PriorityOrdered-->Ordered
             */
            String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            for (String ppName : postProcessorNames) {
                /**
                 * 1.1【PriorityOrdered】getBean 注册到单例缓存池 比如ConfigurationClassPostProcessor后置处理器
                 */
                if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                    /**
                     * getBean()：注册BeanDefinitionRegistryPostProcessor到BeanFactory容器中 也就是BeanFactoryPostProcessor
                     */
                    BeanDefinitionRegistryPostProcessor beanDefinitionRegistryPostProcessor = beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class);
                    currentRegistryProcessors.add(beanDefinitionRegistryPostProcessor);
                    processedBeans.add(ppName);
                }
            }
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            registryProcessors.addAll(currentRegistryProcessors);

            /**
             * 1.1【PriorityOrdered】回调后置处理器的方法
             * 比如：
             * ConfigurationClassPostProcessor后置处理器进行处理将配置类AppConfig的@ComponentScan包下面的类进行注册bean定义!!!!和AppConfig类中的@EnableXXX
             * 包括@ComponentScan包下面的类，如果有@EnableXXX注解 那么也会进行解析成bean定义
             * 注意：
             * 此处可以说明实现了BeanDefinitionRegistryPostProcessor接口的实现类的后置处理器，会先调用postProcessBeanDefinitionRegistry，调用时机更早!!!!
             */
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
            currentRegistryProcessors.clear(); // 处理完了 就进行清空，方便下一种类型的后置处理器使用

            /**
             * 1.2【Ordered】getBean 注册到单例缓存池
             * Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
             */
            postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            for (String ppName : postProcessorNames) {
                if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
                    BeanDefinitionRegistryPostProcessor bean = beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class);
                    currentRegistryProcessors.add(bean);
                    processedBeans.add(ppName);
                }
            }
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            registryProcessors.addAll(currentRegistryProcessors);
            /**
             * 1.2【Ordered】回调后置处理器的方法
             */
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
            currentRegistryProcessors.clear();

            // Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
            /**
             * 1.3【不需要控制顺序的BeanDefinitionRegistryPostProcessor】
             * 目前知道的一个扩展：mybatis-spring的MapperScannerConfigurer实现了BeanDefinitionRegistryPostProcessor后置处理器接口
             * 该后置处理器没有实现Order或者PrioryOrder接口，就在此处进行invoke方法，将mapper的bean定义注册到spring容器
             */
            boolean reiterate = true;
            while (reiterate) {
                reiterate = false;
                postProcessorNames = beanFactory.getBeanNamesForType(
                        BeanDefinitionRegistryPostProcessor.class, true, false
                );
                for (String ppName : postProcessorNames) {
                    if (!processedBeans.contains(ppName)) {
                        BeanDefinitionRegistryPostProcessor beanDefinitionRegistryPostProcessor = beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class);
                        currentRegistryProcessors.add(beanDefinitionRegistryPostProcessor);
                        processedBeans.add(ppName);
                        reiterate = true;
                    }
                }
                sortPostProcessors(currentRegistryProcessors, beanFactory);
                registryProcessors.addAll(currentRegistryProcessors);
                invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
                currentRegistryProcessors.clear();
            }

            // Now, invoke the postProcessBeanFactory callback of all processors handled so far.
            /**
             * 这里也可以看出是先实例化registryProcessors的后置处理器
             */
            invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
            /**
             * 然后再实例化regularPostProcessors的后置处理器
             */
            invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
        } else {
            // Invoke factory processors registered with the context instance.
            invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
        }

        // Do not initialize FactoryBeans here: We need to leave all regular beans
        // uninitialized to let the bean factory post-processors apply to them!
        String[] postProcessorNames = beanFactory.getBeanNamesForType(
                BeanFactoryPostProcessor.class, true, false);

        // Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
        // Ordered, and the rest.
        List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
        List<String> orderedPostProcessorNames = new ArrayList<>();
        List<String> nonOrderedPostProcessorNames = new ArrayList<>();
        for (String ppName : postProcessorNames) {
            if (processedBeans.contains(ppName)) {
                // skip - already processed in first phase above
            } else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
        sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

        // Next, invoke the BeanFactoryPostProcessors that implement Ordered.
        List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
        for (String postProcessorName : orderedPostProcessorNames) {
            orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
        }
        sortPostProcessors(orderedPostProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

        // Finally, invoke all other BeanFactoryPostProcessors.
        List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
        for (String postProcessorName : nonOrderedPostProcessorNames) {

            BeanFactoryPostProcessor beanFactoryPostProcessor = beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class);

            nonOrderedPostProcessors.add(beanFactoryPostProcessor);
        }

        invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

        // Clear cached merged bean definitions since the post-processors might have
        // modified the original metadata, e.g. replacing placeholders in values...
        beanFactory.clearMetadataCache();
    }

    /**
     * 实例化后置处理器BeanPostProcessors，并且将后置处理器保存到到容器的一级缓存中：调用了getBean()方法
     * 【不需要回调后置处理器的before和after方法，但是invokeBeanFactoryPostProcessors需要回调】
     * 根据优先级的顺序：PriorityOrdered,Ordered优先处理，最后轮到普通的BeanPost接口
     *
     * @param beanFactory
     * @param applicationContext
     */
    public static void registerBeanPostProcessors(
            ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

        String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

        // Register BeanPostProcessorChecker that logs an info message when
        // a bean is created during BeanPostProcessor instantiation, i.e. when
        // a bean is not eligible for getting processed by all BeanPostProcessors.
        int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
        beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

        // Separate between BeanPostProcessors that implement PriorityOrdered,
        // Ordered, and the rest.
        List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
        List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
        List<String> orderedPostProcessorNames = new ArrayList<>();
        List<String> nonOrderedPostProcessorNames = new ArrayList<>();
        for (String ppName : postProcessorNames) {
            if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                /**
                 * 1。对于实现了PriorityOrdered接口的后置处理器，调用getBean方法进行实例化+初始化 添加到单例缓存池中
                 */
                BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
                priorityOrderedPostProcessors.add(pp);
                if (pp instanceof MergedBeanDefinitionPostProcessor) {
                    internalPostProcessors.add(pp);
                }
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // First, register the BeanPostProcessors that implement PriorityOrdered.
        sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

        // Next, register the BeanPostProcessors that implement Ordered.
        List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
        for (String ppName : orderedPostProcessorNames) {
            /**
             * 2。对于实现了Ordered接口的后置处理器，调用getBean方法进行实例化+初始化 添加到单例缓存池中
             */
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            orderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        sortPostProcessors(orderedPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, orderedPostProcessors);

        // Now, register all regular BeanPostProcessors.
        List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
        for (String ppName : nonOrderedPostProcessorNames) {
            /**
             * 3。不需要优先初始化的后置处理器，调用getBean方法进行实例化+初始化 添加到单例缓存池中
             */
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            nonOrderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

        // Finally, re-register all internal BeanPostProcessors.
        sortPostProcessors(internalPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, internalPostProcessors);

        // Re-register post-processor for detecting inner beans as ApplicationListeners,
        // moving it to the end of the processor chain (for picking up proxies etc).
        beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
    }

    private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
        Comparator<Object> comparatorToUse = null;
        if (beanFactory instanceof DefaultListableBeanFactory) {
            comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
        }
        if (comparatorToUse == null) {
            comparatorToUse = OrderComparator.INSTANCE;
        }
        postProcessors.sort(comparatorToUse);
    }

    /**
     * 调用BeanDefinitionRegistryPostProcessors后置处理器的postProcessBeanDefinitionRegistry方法
     * <p>
     * Invoke the given BeanDefinitionRegistryPostProcessor beans.
     */
    private static void invokeBeanDefinitionRegistryPostProcessors(
            Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

        for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
            /**
             * !!!!
             */
            postProcessor.postProcessBeanDefinitionRegistry(registry);
        }
    }

    /**
     * 调用BeanFactoryPostProcessor后置处理器的postProcessBeanFactory()方法
     * <p>
     * Invoke the given BeanFactoryPostProcessor beans.
     */
    private static void invokeBeanFactoryPostProcessors(
            Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

        for (BeanFactoryPostProcessor postProcessor : postProcessors) {
            postProcessor.postProcessBeanFactory(beanFactory);
        }
    }

    /**
     * 将BeanPostProcessors后置处理器添加到AbstractBeanFactory的beanPostProcessors这个list集合
     * <p>
     * Register the given BeanPostProcessor beans.
     */
    private static void registerBeanPostProcessors(
            ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

        for (BeanPostProcessor postProcessor : postProcessors) {
            beanFactory.addBeanPostProcessor(postProcessor);
        }
    }


    /**
     * BeanPostProcessor that logs an info message when a bean is created during
     * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
     * getting processed by all BeanPostProcessors.
     */
    private static final class BeanPostProcessorChecker implements BeanPostProcessor {

        private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

        private final ConfigurableListableBeanFactory beanFactory;

        private final int beanPostProcessorTargetCount;

        public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
            this.beanFactory = beanFactory;
            this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
                    this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
                if (logger.isInfoEnabled()) {
                    logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
                            "] is not eligible for getting processed by all BeanPostProcessors " +
                            "(for example: not eligible for auto-proxying)");
                }
            }
            return bean;
        }

        private boolean isInfrastructureBean(@Nullable String beanName) {
            if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
                BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
                return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
            }
            return false;
        }
    }

}
