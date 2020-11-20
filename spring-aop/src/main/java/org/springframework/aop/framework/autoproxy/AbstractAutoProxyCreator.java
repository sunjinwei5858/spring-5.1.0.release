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

package org.springframework.aop.framework.autoproxy;

import org.aopalliance.aop.Advice;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyProcessorSupport;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 该英文注释已经说明了aop代理对象是在此处实现的，aop的逻辑分析也是在这个类开始，
 * 因为该类在bean的实例化前后实现了后置处理器before【第一阶段 实例化前】和after【第三阶段 初始化后】的方法
 * before方法：进行aop的解析和缓存，为什么要缓存 因为解析的工作量很大，提高性能
 * after方法：生成代理对象
 * <p>
 * <p>
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that wraps each eligible bean with an AOP proxy, delegating to specified interceptors
 * before invoking the bean itself.
 *
 * <p>This class distinguishes between "common" interceptors: shared for all proxies it
 * creates, and "specific" interceptors: unique per bean instance. There need not be any
 * common interceptors. If there are, they are set using the interceptorNames property.
 * As with {@link org.springframework.aop.framework.ProxyFactoryBean}, interceptors names
 * in the current factory are used rather than bean references to allow correct handling
 * of prototype advisors and interceptors: for example, to support stateful mixins.
 * Any advice type is supported for {@link #setInterceptorNames "interceptorNames"} entries.
 *
 * <p>Such auto-proxying is particularly useful if there's a large number of beans that
 * need to be wrapped with similar proxies, i.e. delegating to the same interceptors.
 * Instead of x repetitive proxy definitions for x target beans, you can register
 * one single such post processor with the bean factory to achieve the same effect.
 *
 * <p>Subclasses can apply any strategy to decide if a bean is to be proxied, e.g. by type,
 * by name, by definition details, etc. They can also return additional interceptors that
 * should just be applied to the specific bean instance. A simple concrete implementation is
 * {@link BeanNameAutoProxyCreator}, identifying the beans to be proxied via given names.
 *
 * <p>Any number of {@link TargetSourceCreator} implementations can be used to create
 * a custom target source: for example, to pool prototype objects. Auto-proxying will
 * occur even if there is no advice, as long as a TargetSourceCreator specifies a custom
 * {@link org.springframework.aop.TargetSource}. If there are no TargetSourceCreators set,
 * or if none matches, a {@link org.springframework.aop.target.SingletonTargetSource}
 * will be used by default to wrap the target bean instance.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Rob Harrop
 * @see #setInterceptorNames
 * @see #getAdvicesAndAdvisorsForBean
 * @see BeanNameAutoProxyCreator
 * @see DefaultAdvisorAutoProxyCreator
 * @since 13.10.2003
 */
@SuppressWarnings("serial")
public abstract class AbstractAutoProxyCreator extends ProxyProcessorSupport
        implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware {

    /**
     * Convenience constant for subclasses: Return value for "do not proxy".
     *
     * @see #getAdvicesAndAdvisorsForBean
     */
    @Nullable
    protected static final Object[] DO_NOT_PROXY = null;

    /**
     * Convenience constant for subclasses: Return value for
     * "proxy without additional interceptors, just the common ones".
     *
     * @see #getAdvicesAndAdvisorsForBean
     */
    protected static final Object[] PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS = new Object[0];


    /**
     * Logger available to subclasses.
     */
    protected final Log logger = LogFactory.getLog(getClass());

    /**
     * Default is global AdvisorAdapterRegistry.
     */
    private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();

    /**
     * Indicates whether or not the proxy should be frozen. Overridden from super
     * to prevent the configuration from becoming frozen too early.
     */
    private boolean freezeProxy = false;

    /**
     * Default is no common interceptors.
     */
    private String[] interceptorNames = new String[0];

    private boolean applyCommonInterceptorsFirst = true;

    @Nullable
    private TargetSourceCreator[] customTargetSourceCreators;

    @Nullable
    private BeanFactory beanFactory;

    private final Set<String> targetSourcedBeans = Collections.newSetFromMap(new ConcurrentHashMap<>(16));

    /**
     * earlyProxyReferences集合的作用：
     * 1。getEarlyBeanReference：进行add
     * 2。postProcessAfterInitialization：进行get操作
     *
     */
    private final Set<Object> earlyProxyReferences = Collections.newSetFromMap(new ConcurrentHashMap<>(16));

    private final Map<Object, Class<?>> proxyTypes = new ConcurrentHashMap<>(16);

    private final Map<Object, Boolean> advisedBeans = new ConcurrentHashMap<>(256);


    /**
     * Set whether or not the proxy should be frozen, preventing advice
     * from being added to it once it is created.
     * <p>Overridden from the super class to prevent the proxy configuration
     * from being frozen before the proxy is created.
     */
    @Override
    public void setFrozen(boolean frozen) {
        this.freezeProxy = frozen;
    }

    @Override
    public boolean isFrozen() {
        return this.freezeProxy;
    }

    /**
     * Specify the {@link AdvisorAdapterRegistry} to use.
     * <p>Default is the global {@link AdvisorAdapterRegistry}.
     *
     * @see org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry
     */
    public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
        this.advisorAdapterRegistry = advisorAdapterRegistry;
    }

    /**
     * Set custom {@code TargetSourceCreators} to be applied in this order.
     * If the list is empty, or they all return null, a {@link SingletonTargetSource}
     * will be created for each bean.
     * <p>Note that TargetSourceCreators will kick in even for target beans
     * where no advices or advisors have been found. If a {@code TargetSourceCreator}
     * returns a {@link TargetSource} for a specific bean, that bean will be proxied
     * in any case.
     * <p>{@code TargetSourceCreators} can only be invoked if this post processor is used
     * in a {@link BeanFactory} and its {@link BeanFactoryAware} callback is triggered.
     *
     * @param targetSourceCreators the list of {@code TargetSourceCreators}.
     *                             Ordering is significant: The {@code TargetSource} returned from the first matching
     *                             {@code TargetSourceCreator} (that is, the first that returns non-null) will be used.
     */
    public void setCustomTargetSourceCreators(TargetSourceCreator... targetSourceCreators) {
        this.customTargetSourceCreators = targetSourceCreators;
    }

    /**
     * Set the common interceptors. These must be bean names in the current factory.
     * They can be of any advice or advisor type Spring supports.
     * <p>If this property isn't set, there will be zero common interceptors.
     * This is perfectly valid, if "specific" interceptors such as matching
     * Advisors are all we want.
     */
    public void setInterceptorNames(String... interceptorNames) {
        this.interceptorNames = interceptorNames;
    }

    /**
     * Set whether the common interceptors should be applied before bean-specific ones.
     * Default is "true"; else, bean-specific interceptors will get applied first.
     */
    public void setApplyCommonInterceptorsFirst(boolean applyCommonInterceptorsFirst) {
        this.applyCommonInterceptorsFirst = applyCommonInterceptorsFirst;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * Return the owning {@link BeanFactory}.
     * May be {@code null}, as this post-processor doesn't need to belong to a bean factory.
     */
    @Nullable
    protected BeanFactory getBeanFactory() {
        return this.beanFactory;
    }


    @Override
    @Nullable
    public Class<?> predictBeanType(Class<?> beanClass, String beanName) {
        if (this.proxyTypes.isEmpty()) {
            return null;
        }
        Object cacheKey = getCacheKey(beanClass, beanName);
        return this.proxyTypes.get(cacheKey);
    }

    @Override
    @Nullable
    public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName) {
        return null;
    }

    /**
     * 如果开启了aop，并且有循环依赖，A有B，B有A。实例化A，发现有B，实例化B，发现有A，那么此时就会从三级缓存中获取到A的对象工厂，getEarlyBeanReference
     * 如果A需要代理，doGetBean方法中调用getSingleton(beanName),就会去三级缓存中get，调用lambda表达式getEarlyBeanReference，
     *
     * @param bean     the raw bean instance
     * @param beanName the name of the bean
     * @return
     */
    @Override
    public Object getEarlyBeanReference(Object bean, String beanName) {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);
        /**
         * 如果这里没有缓存上，那么进行缓存
         */
        if (!this.earlyProxyReferences.contains(cacheKey)) {
            this.earlyProxyReferences.add(cacheKey);
        }
        /**
         * 偷梁换柱！！！ 将target换成proxy
         */
        return wrapIfNecessary(bean, beanName, cacheKey);
    }

    /**
     * 第一阶段 实例化前：【解析切面以及事务解析 事务注解都是在这里完成的】
     * postProcessBeforeInstantiation --- Instantiation 实例化前进行的逻辑：
     * 入参是Class<?> beanClass
     * <p>
     * 在我们还没创建bean的流程中 还没调用构造器来实例化bean的时候进行调用（实例化前后）
     * 我们的aop 解析切面以及事务解析 事务注解都是在这里完成的
     *
     * @param beanClass the class of the bean to be instantiated
     * @param beanName  the name of the bean
     * @return
     */
    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
        /**
         * 构造缓存key
         */
        Object cacheKey = getCacheKey(beanClass, beanName);

        if (!StringUtils.hasLength(beanName) || !this.targetSourcedBeans.contains(beanName)) {
            /**
             * 如果被解析过，那么直接返回
             */
            if (this.advisedBeans.containsKey(cacheKey)) {
                return null;
            }
            // 是不是基础的bean
            boolean infrastructureClass = isInfrastructureClass(beanClass);

            /**
             * shouldSkip该方法会去扫描aop切面类的通知 并进行缓存！！！！
             * 调用的是AspectJAwareAdvisorAutoProxyCreator#shouldSkip(java.lang.Class, java.lang.String)
             */
            boolean shouldSkip = shouldSkip(beanClass, beanName);
            if (infrastructureClass || shouldSkip) {
                this.advisedBeans.put(cacheKey, Boolean.FALSE);
                return null;
            }
        }

        // Create proxy here if we have a custom TargetSource.
        // Suppresses unnecessary default instantiation of the target bean:
        // The TargetSource will handle target instances in a custom fashion.
        TargetSource targetSource = getCustomTargetSource(beanClass, beanName);
        if (targetSource != null) {
            if (StringUtils.hasLength(beanName)) {
                this.targetSourcedBeans.add(beanName);
            }
            /**
             * 如果有自定义的目标 那么在这里找到增强器
             */
            Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);

            Object proxy = createProxy(beanClass, beanName, specificInterceptors, targetSource);
            this.proxyTypes.put(cacheKey, proxy.getClass());
            /**
             * 将target变成了proxy
             */
            return proxy;
        }

        return null;
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) {
        return true;
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
        return pvs;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    /**
     * 第三阶段 初始化后
     * postProcessAfterInitialization--Initialization--初始化后【第三阶段】进行生成代理对象，
     * 如何生成aop代理对象的逻辑全部封装在wrapIfNecessary方法中
     *
     * <p>
     * Create a proxy with the configured interceptors if the bean is
     * identified as one to proxy by the subclass.
     *
     * @see #getAdvicesAndAdvisorsForBean
     */
    @Override
    public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
        if (bean != null) {
            Object cacheKey = getCacheKey(bean.getClass(), beanName);
            /**
             * 如果earlyProxyReferences不包含cacheKey
             */
            if (!this.earlyProxyReferences.contains(cacheKey)) {
                /**
                 * 找到合适的 就会生成代理。这里进行偷天换日
                 */
                return wrapIfNecessary(bean, beanName, cacheKey);
            }
        }
        return bean;
    }


    /**
     * Build a cache key for the given bean class and bean name.
     * <p>Note: As of 4.2.3, this implementation does not return a concatenated
     * class/name String anymore but rather the most efficient cache key possible:
     * a plain bean name, prepended with {@link BeanFactory#FACTORY_BEAN_PREFIX}
     * in case of a {@code FactoryBean}; or if no bean name specified, then the
     * given bean {@code Class} as-is.
     *
     * @param beanClass the bean class
     * @param beanName  the bean name
     * @return the cache key for the given class and name
     */
    protected Object getCacheKey(Class<?> beanClass, @Nullable String beanName) {
        if (StringUtils.hasLength(beanName)) {
            return (FactoryBean.class.isAssignableFrom(beanClass) ?
                    BeanFactory.FACTORY_BEAN_PREFIX + beanName : beanName);
        } else {
            return beanClass;
        }
    }

    /**
     * 此处进行生成代理对象 偷梁换柱，创建aop代理：
     * 1。获取增强器
     * 2。寻找匹配的增强器
     * 3。创建代理
     * <p>
     * Wrap the given bean if necessary, i.e. if it is eligible for being proxied.
     *
     * @param bean     the raw bean instance
     * @param beanName the name of the bean
     * @param cacheKey the cache key for metadata access
     * @return a proxy wrapping the bean, or the raw bean instance as-is
     */
    protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
        /**
         * 已经被处理过的
         */
        if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
            return bean;
        }

        /**
         * 不需要增强的【true代表需要代理 false代表不需要被代理】
         */
        if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
            return bean;
        }

        /**
         * 基础设施类不需要处理或者配置了指定bean不需要自动代理
         */
        if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
            this.advisedBeans.put(cacheKey, Boolean.FALSE);
            return bean;
        }

        // Create proxy if we have advice.
        /**
         * 如果存在增强方法则创建代理：getAdvicesAndAdvisorsForBean就是真正创建代理的方法，交给子类去实现的，这就是扩展，父类定义的足够高，子类可以尽情扩展
         *
         * 如果有通知的话 就创建代理对象。获取对应的advise,不但要找出增强器，还需要判断增强器是否满足要求。
         * 1。找出增强器
         * 2。寻找匹配的增强器【是否在切点配置的通配符中】
         * 3。创建代理createProxy
         */
        Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
        if (specificInterceptors != DO_NOT_PROXY) {
            this.advisedBeans.put(cacheKey, Boolean.TRUE);
            // 创建代理
            Object proxy = createProxy(bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
            this.proxyTypes.put(cacheKey, proxy.getClass());
            return proxy;
        }
        /**
         * 走到这里 说明该类不需要被代理 那么设置为false
         */
        this.advisedBeans.put(cacheKey, Boolean.FALSE);
        return bean;
    }

    /**
     * Return whether the given bean class represents an infrastructure class
     * that should never be proxied.
     * <p>The default implementation considers Advices, Advisors and
     * AopInfrastructureBeans as infrastructure classes.
     *
     * @param beanClass the class of the bean
     * @return whether the bean represents an infrastructure class
     * @see org.aopalliance.aop.Advice
     * @see org.springframework.aop.Advisor
     * @see org.springframework.aop.framework.AopInfrastructureBean
     * @see #shouldSkip
     */
    protected boolean isInfrastructureClass(Class<?> beanClass) {
        boolean retVal = Advice.class.isAssignableFrom(beanClass) ||
                Pointcut.class.isAssignableFrom(beanClass) ||
                Advisor.class.isAssignableFrom(beanClass) ||
                AopInfrastructureBean.class.isAssignableFrom(beanClass);
        if (retVal && logger.isTraceEnabled()) {
            logger.trace("Did not attempt to auto-proxy infrastructure class [" + beanClass.getName() + "]");
        }
        return retVal;
    }

    /**
     * Subclasses should override this method to return {@code true} if the
     * given bean should not be considered for auto-proxying by this post-processor.
     * <p>Sometimes we need to be able to avoid this happening, e.g. if it will lead to
     * a circular reference or if the existing target instance needs to be preserved.
     * This implementation returns {@code false} unless the bean name indicates an
     * "original instance" according to {@code AutowireCapableBeanFactory} conventions.
     *
     * @param beanClass the class of the bean
     * @param beanName  the name of the bean
     * @return whether to skip the given bean
     * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#ORIGINAL_INSTANCE_SUFFIX
     */
    protected boolean shouldSkip(Class<?> beanClass, String beanName) {
        return AutoProxyUtils.isOriginalInstance(beanName, beanClass);
    }

    /**
     * Create a target source for bean instances. Uses any TargetSourceCreators if set.
     * Returns {@code null} if no custom TargetSource should be used.
     * <p>This implementation uses the "customTargetSourceCreators" property.
     * Subclasses can override this method to use a different mechanism.
     *
     * @param beanClass the class of the bean to create a TargetSource for
     * @param beanName  the name of the bean
     * @return a TargetSource for this bean
     * @see #setCustomTargetSourceCreators
     */
    @Nullable
    protected TargetSource getCustomTargetSource(Class<?> beanClass, String beanName) {
        // We can't create fancy target sources for directly registered singletons.
        if (this.customTargetSourceCreators != null &&
                this.beanFactory != null && this.beanFactory.containsBean(beanName)) {
            for (TargetSourceCreator tsc : this.customTargetSourceCreators) {
                TargetSource ts = tsc.getTargetSource(beanClass, beanName);
                if (ts != null) {
                    // Found a matching TargetSource.
                    if (logger.isTraceEnabled()) {
                        logger.trace("TargetSourceCreator [" + tsc +
                                "] found custom TargetSource for bean with name '" + beanName + "'");
                    }
                    return ts;
                }
            }
        }

        // No custom TargetSource found.
        return null;
    }

    /**
     * Create an AOP proxy for the given bean.
     *
     * @param beanClass            the class of the bean
     * @param beanName             the name of the bean
     * @param specificInterceptors the set of interceptors that is
     *                             specific to this bean (may be empty, but not null)
     * @param targetSource         the TargetSource for the proxy,
     *                             already pre-configured to access the bean
     * @return the AOP proxy for the bean
     * @see #buildAdvisors
     */
    protected Object createProxy(Class<?> beanClass, @Nullable String beanName,
                                 @Nullable Object[] specificInterceptors, TargetSource targetSource) {

        if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
            AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
        }

        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.copyFrom(this);

        if (!proxyFactory.isProxyTargetClass()) {
            if (shouldProxyTargetClass(beanClass, beanName)) {
                proxyFactory.setProxyTargetClass(true);
            } else {
                evaluateProxyInterfaces(beanClass, proxyFactory);
            }
        }

        Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);

        proxyFactory.addAdvisors(advisors);
        proxyFactory.setTargetSource(targetSource);
        customizeProxyFactory(proxyFactory);

        proxyFactory.setFrozen(this.freezeProxy);
        if (advisorsPreFiltered()) {
            proxyFactory.setPreFiltered(true);
        }

        return proxyFactory.getProxy(getProxyClassLoader());
    }

    /**
     * Determine whether the given bean should be proxied with its target class rather than its interfaces.
     * <p>Checks the {@link AutoProxyUtils#PRESERVE_TARGET_CLASS_ATTRIBUTE "preserveTargetClass" attribute}
     * of the corresponding bean definition.
     *
     * @param beanClass the class of the bean
     * @param beanName  the name of the bean
     * @return whether the given bean should be proxied with its target class
     * @see AutoProxyUtils#shouldProxyTargetClass
     */
    protected boolean shouldProxyTargetClass(Class<?> beanClass, @Nullable String beanName) {
        return (this.beanFactory instanceof ConfigurableListableBeanFactory &&
                AutoProxyUtils.shouldProxyTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName));
    }

    /**
     * Return whether the Advisors returned by the subclass are pre-filtered
     * to match the bean's target class already, allowing the ClassFilter check
     * to be skipped when building advisors chains for AOP invocations.
     * <p>Default is {@code false}. Subclasses may override this if they
     * will always return pre-filtered Advisors.
     *
     * @return whether the Advisors are pre-filtered
     * @see #getAdvicesAndAdvisorsForBean
     * @see org.springframework.aop.framework.Advised#setPreFiltered
     */
    protected boolean advisorsPreFiltered() {
        return false;
    }

    /**
     * Determine the advisors for the given bean, including the specific interceptors
     * as well as the common interceptor, all adapted to the Advisor interface.
     *
     * @param beanName             the name of the bean
     * @param specificInterceptors the set of interceptors that is
     *                             specific to this bean (may be empty, but not null)
     * @return the list of Advisors for the given bean
     */
    protected Advisor[] buildAdvisors(@Nullable String beanName, @Nullable Object[] specificInterceptors) {
        // Handle prototypes correctly...
        Advisor[] commonInterceptors = resolveInterceptorNames();

        List<Object> allInterceptors = new ArrayList<>();
        if (specificInterceptors != null) {
            allInterceptors.addAll(Arrays.asList(specificInterceptors));
            if (commonInterceptors.length > 0) {
                if (this.applyCommonInterceptorsFirst) {
                    allInterceptors.addAll(0, Arrays.asList(commonInterceptors));
                } else {
                    allInterceptors.addAll(Arrays.asList(commonInterceptors));
                }
            }
        }
        if (logger.isTraceEnabled()) {
            int nrOfCommonInterceptors = commonInterceptors.length;
            int nrOfSpecificInterceptors = (specificInterceptors != null ? specificInterceptors.length : 0);
            logger.trace("Creating implicit proxy for bean '" + beanName + "' with " + nrOfCommonInterceptors +
                    " common interceptors and " + nrOfSpecificInterceptors + " specific interceptors");
        }

        Advisor[] advisors = new Advisor[allInterceptors.size()];
        for (int i = 0; i < allInterceptors.size(); i++) {

            advisors[i] = this.advisorAdapterRegistry.wrap(allInterceptors.get(i));
        }
        return advisors;
    }

    /**
     * Resolves the specified interceptor names to Advisor objects.
     *
     * @see #setInterceptorNames
     */
    private Advisor[] resolveInterceptorNames() {
        BeanFactory bf = this.beanFactory;
        ConfigurableBeanFactory cbf = (bf instanceof ConfigurableBeanFactory ? (ConfigurableBeanFactory) bf : null);
        List<Advisor> advisors = new ArrayList<>();
        for (String beanName : this.interceptorNames) {
            if (cbf == null || !cbf.isCurrentlyInCreation(beanName)) {
                Assert.state(bf != null, "BeanFactory required for resolving interceptor names");
                Object next = bf.getBean(beanName);
                advisors.add(this.advisorAdapterRegistry.wrap(next));
            }
        }
        return advisors.toArray(new Advisor[0]);
    }

    /**
     * Subclasses may choose to implement this: for example,
     * to change the interfaces exposed.
     * <p>The default implementation is empty.
     *
     * @param proxyFactory a ProxyFactory that is already configured with
     *                     TargetSource and interfaces and will be used to create the proxy
     *                     immediately after this method returns
     */
    protected void customizeProxyFactory(ProxyFactory proxyFactory) {
    }


    /**
     * Return whether the given bean is to be proxied, what additional
     * advices (e.g. AOP Alliance interceptors) and advisors to apply.
     *
     * @param beanClass          the class of the bean to advise
     * @param beanName           the name of the bean
     * @param customTargetSource the TargetSource returned by the
     *                           {@link #getCustomTargetSource} method: may be ignored.
     *                           Will be {@code null} if no custom target source is in use.
     * @return an array of additional interceptors for the particular bean;
     * or an empty array if no additional interceptors but just the common ones;
     * or {@code null} if no proxy at all, not even with the common interceptors.
     * See constants DO_NOT_PROXY and PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS.
     * @throws BeansException in case of errors
     * @see #DO_NOT_PROXY
     * @see #PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS
     */
    @Nullable
    protected abstract Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName,
                                                             @Nullable TargetSource customTargetSource) throws BeansException;

}
