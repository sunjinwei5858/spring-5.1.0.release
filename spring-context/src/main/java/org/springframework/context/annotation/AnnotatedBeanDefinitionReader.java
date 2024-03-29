/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.context.annotation;

import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

/**
 * 这个类的作用：【没有继承和实现BeanDefinitionReader接口，是个单独的类】
 * 【应用1：springboot启动类也就是配置类 就是通过AnnotatedBeanDefinitionReader注册配置类的bean定义的】
 * 【应用2：spring本身如果使用注解配置 也是 通过这个类进行注册的】 自己写的 {@link MainConfig}
 * 1。方便编程式动态注册一个带注解的bean
 * 2。可以替代ClassPathBeanDefinitionScanner，具备相同的解析功能，ClassPathBeanDefinitionScanner是spring完成扫描的核心类。
 * 比如注册配置类，因为配置类无法自己扫描自己，所以AnnotatedBeanDefinitionReader的作用就是注册配置类。
 * 简而言之：
 * spring如果想要完成扫描，必须先提供配置类AppConfig.java,所以AppConfig要在一开始就手动注册给Spring【由AnnotatedBeanDefinitionReader完成】，
 * spring得到AppConfig.class之后把他解析成bean定义对象，然后再去获取配置类上面的注解@ComponentScan的包路径，再注册包下面的类的bean定义【由ClassPathBeanDefinitionScanner完成】
 *
 * <p>
 * Convenient adapter for programmatic registration of annotated bean classes.
 * This is an alternative to {@link ClassPathBeanDefinitionScanner}, applying
 * the same resolution of annotations but for explicitly registered classes only.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Sam Brannen
 * @author Phillip Webb
 * @see AnnotationConfigApplicationContext#register
 * @since 3.0
 */
public class AnnotatedBeanDefinitionReader {

    private final BeanDefinitionRegistry registry;

    /**
     * 默认name生成器是AnnotationBeanNameGenerator
     */
    private BeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();

    private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

    private ConditionEvaluator conditionEvaluator;


    /**
     * 初始化AnnotatedBeanDefinitionReader【解析配置类】的时候进行创建Environment
     * <p>
     * Create a new {@code AnnotatedBeanDefinitionReader} for the given registry.
     * If the registry is {@link EnvironmentCapable}, e.g. is an {@code ApplicationContext},
     * the {@link Environment} will be inherited, otherwise a new
     * {@link StandardEnvironment} will be created and used.
     *
     * @param registry the {@code BeanFactory} to load bean definitions into,
     *                 in the form of a {@code BeanDefinitionRegistry}
     * @see #AnnotatedBeanDefinitionReader(BeanDefinitionRegistry, Environment)
     * @see #setEnvironment(Environment)
     */
    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
        this(registry, getOrCreateEnvironment(registry));
    }

    /**
     * 分析1：
     * spring容器构造化的时候会创建AnnotatedBeanDefinitionReader，
     * AnnotatedBeanDefinitionReader进行构造化的时候会进行注册5个bean定义【2个BeanFactory后置处理器+2个Bean后置处理器+1个事件监听工厂】，
     * 之前自己都不知道这几个后置处理器的bean定义是何时注册的，
     * 因为在此处已经注册好ConfigurationClassPostProcessor这些后置处理器，所以后续会进行getBean操作，
     * 然后添加到单例缓存池，在refresh方法中，会有很多地方需要foreach后置处理器，进行调用BeanFactoryPostProcessor接口
     * <p>
     * 分析2：
     * 注册这五个后置处理器的操作是哪个容器来完成的：DefaultListableBeanFactory的beanDefinitionMap
     * <p>
     * Create a new {@code AnnotatedBeanDefinitionReader} for the given registry and using
     * the given {@link Environment}.
     *
     * @param registry    the {@code BeanFactory} to load bean definitions into,
     *                    in the form of a {@code BeanDefinitionRegistry}
     * @param environment the {@code Environment} to use when evaluating bean definition
     *                    profiles.
     * @since 3.1
     */
    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        Assert.notNull(environment, "Environment must not be null");
        this.registry = registry;
        this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
        /**
         * 注册五个bean定义【2个BeanFactory后置处理器+2个Bean后置处理器+1个事件监听工厂】：
         * 4个后置处理器：ConfigurationClassPostProcessor，AutowiredAnnotationBeanPostProcessor，CommonAnnotationBeanPostProcessor，EventListenerMethodProcessor，
         * 1个事件监听工厂的bean定义：DefaultEventListenerFactory。
         * 如果开启了jpa 那么也会注册PersistenceAnnotationBeanPostProcessor后置处理器
         *
         */
        AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
    }


    /**
     * Return the BeanDefinitionRegistry that this scanner operates on.
     */
    public final BeanDefinitionRegistry getRegistry() {
        return this.registry;
    }

    /**
     * Set the Environment to use when evaluating whether
     * {@link Conditional @Conditional}-annotated component classes should be registered.
     * <p>The default is a {@link StandardEnvironment}.
     *
     * @see #registerBean(Class, String, Class...)
     */
    public void setEnvironment(Environment environment) {
        this.conditionEvaluator = new ConditionEvaluator(this.registry, environment, null);
    }

    /**
     * Set the BeanNameGenerator to use for detected bean classes.
     * <p>The default is a {@link AnnotationBeanNameGenerator}.
     */
    public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator = (beanNameGenerator != null ? beanNameGenerator : new AnnotationBeanNameGenerator());
    }

    /**
     * Set the ScopeMetadataResolver to use for detected bean classes.
     * <p>The default is an {@link AnnotationScopeMetadataResolver}.
     */
    public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
        this.scopeMetadataResolver =
                (scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
    }


    /**
     * Register one or more annotated classes to be processed.
     * <p>Calls to {@code register} are idempotent; adding the same
     * annotated class more than once has no additional effect.
     *
     * @param annotatedClasses one or more annotated classes,
     *                         e.g. {@link Configuration @Configuration} classes
     */
    public void register(Class<?>... annotatedClasses) {
        for (Class<?> annotatedClass : annotatedClasses) {
            registerBean(annotatedClass);
        }
    }

    /**
     * doXXX这里才是真正将配置类注册到beanDefinitionMap的方法
     * <p>
     * Register a bean from the given bean class, deriving its metadata from
     * class-declared annotations.
     *
     * @param annotatedClass the class of the bean
     */
    public void registerBean(Class<?> annotatedClass) {
        doRegisterBean(annotatedClass, null, null, null);
    }

    /**
     * Register a bean from the given bean class, deriving its metadata from
     * class-declared annotations, using the given supplier for obtaining a new
     * instance (possibly declared as a lambda expression or method reference).
     *
     * @param annotatedClass   the class of the bean
     * @param instanceSupplier a callback for creating an instance of the bean
     *                         (may be {@code null})
     * @since 5.0
     */
    public <T> void registerBean(Class<T> annotatedClass, @Nullable Supplier<T> instanceSupplier) {
        doRegisterBean(annotatedClass, instanceSupplier, null, null);
    }

    /**
     * Register a bean from the given bean class, deriving its metadata from
     * class-declared annotations, using the given supplier for obtaining a new
     * instance (possibly declared as a lambda expression or method reference).
     *
     * @param annotatedClass   the class of the bean
     * @param name             an explicit name for the bean
     * @param instanceSupplier a callback for creating an instance of the bean
     *                         (may be {@code null})
     * @since 5.0
     */
    public <T> void registerBean(Class<T> annotatedClass, String name, @Nullable Supplier<T> instanceSupplier) {
        doRegisterBean(annotatedClass, instanceSupplier, name, null);
    }

    /**
     * Register a bean from the given bean class, deriving its metadata from
     * class-declared annotations.
     *
     * @param annotatedClass the class of the bean
     * @param qualifiers     specific qualifier annotations to consider,
     *                       in addition to qualifiers at the bean class level
     */
    @SuppressWarnings("unchecked")
    public void registerBean(Class<?> annotatedClass, Class<? extends Annotation>... qualifiers) {
        doRegisterBean(annotatedClass, null, null, qualifiers);
    }

    /**
     * Register a bean from the given bean class, deriving its metadata from
     * class-declared annotations.
     *
     * @param annotatedClass the class of the bean
     * @param name           an explicit name for the bean
     * @param qualifiers     specific qualifier annotations to consider,
     *                       in addition to qualifiers at the bean class level
     */
    @SuppressWarnings("unchecked")
    public void registerBean(Class<?> annotatedClass, String name, Class<? extends Annotation>... qualifiers) {
        doRegisterBean(annotatedClass, null, name, qualifiers);
    }

    /**
     * 使用AnnotatedBeanDefinitionReader将配置类注册到BeanDefinitionMap中
     * <p>
     * Register a bean from the given bean class, deriving its metadata from
     * class-declared annotations.
     *
     * @param annotatedClass        the class of the bean
     * @param instanceSupplier      a callback for creating an instance of the bean
     *                              (may be {@code null})
     * @param name                  an explicit name for the bean
     * @param qualifiers            specific qualifier annotations to consider, if any,
     *                              in addition to qualifiers at the bean class level
     * @param definitionCustomizers one or more callbacks for customizing the
     *                              factory's {@link BeanDefinition}, e.g. setting a lazy-init or primary flag
     * @since 5.0
     */
    <T> void doRegisterBean(Class<T> annotatedClass, @Nullable Supplier<T> instanceSupplier, @Nullable String name,
                            @Nullable Class<? extends Annotation>[] qualifiers, BeanDefinitionCustomizer... definitionCustomizers) {

        /**
         * 1。创建配置类的bean定义对象 使用AnnotatedGenericBeanDefinition
         */
        AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(annotatedClass);
        if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
            return;
        }
        /**
         * 设置bean定义的instanceSupplier，作用创建bean的回调
         */
        abd.setInstanceSupplier(instanceSupplier);
        /**
         * 设置bean定义的作用域scope
         */
        ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
        abd.setScope(scopeMetadata.getScopeName());

        String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));

        /**
         * 处理 是否要设置懒加载
         */
        AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);
        if (qualifiers != null) {
            for (Class<? extends Annotation> qualifier : qualifiers) {
                if (Primary.class == qualifier) {
                    abd.setPrimary(true);
                } else if (Lazy.class == qualifier) {
                    abd.setLazyInit(true);
                } else {
                    abd.addQualifier(new AutowireCandidateQualifier(qualifier));
                }
            }
        }
        for (BeanDefinitionCustomizer customizer : definitionCustomizers) {
            customizer.customize(abd);
        }

        /**
         * 使用BeanDefinitionHolder对象进行封装bean定义，
         */
        BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
        definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
        /**
         * 调用BeanDefinitionReaderUtils工具类，进行注册bean定义
         */
        BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
    }


    /**
     * Get the Environment from the given registry if possible, otherwise return a new
     * StandardEnvironment.
     */
    private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        if (registry instanceof EnvironmentCapable) {
            return ((EnvironmentCapable) registry).getEnvironment();
        }
        return new StandardEnvironment();
    }

}
