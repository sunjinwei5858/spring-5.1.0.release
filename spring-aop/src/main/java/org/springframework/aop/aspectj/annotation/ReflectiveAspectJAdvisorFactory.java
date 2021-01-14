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

import org.aopalliance.aop.Advice;
import org.aspectj.lang.annotation.*;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.aspectj.*;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConvertingComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.comparator.InstanceComparator;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Factory that can create Spring AOP Advisors given AspectJ classes from
 * classes honoring the AspectJ 5 annotation syntax, using reflection to
 * invoke the corresponding advice methods.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Phillip Webb
 * @since 2.0
 */
@SuppressWarnings("serial")
public class ReflectiveAspectJAdvisorFactory extends AbstractAspectJAdvisorFactory implements Serializable {

    private static final Comparator<Method> METHOD_COMPARATOR;

    static {
        Comparator<Method> adviceKindComparator = new ConvertingComparator<>(
                new InstanceComparator<>(
                        Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class),
                (Converter<Method, Annotation>) method -> {
                    AspectJAnnotation<?> annotation =
                            AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(method);
                    return (annotation != null ? annotation.getAnnotation() : null);
                });
        Comparator<Method> methodNameComparator = new ConvertingComparator<>(Method::getName);
        METHOD_COMPARATOR = adviceKindComparator.thenComparing(methodNameComparator);
    }


    @Nullable
    private final BeanFactory beanFactory;


    /**
     * Create a new {@code ReflectiveAspectJAdvisorFactory}.
     */
    public ReflectiveAspectJAdvisorFactory() {
        this(null);
    }

    /**
     * Create a new {@code ReflectiveAspectJAdvisorFactory}, propagating the given
     * {@link BeanFactory} to the created {@link AspectJExpressionPointcut} instances,
     * for bean pointcut handling as well as consistent {@link ClassLoader} resolution.
     *
     * @param beanFactory the BeanFactory to propagate (may be {@code null}}
     * @see AspectJExpressionPointcut#setBeanFactory
     * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#getBeanClassLoader()
     * @since 4.3.6
     */
    public ReflectiveAspectJAdvisorFactory(@Nullable BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }


    /**
     * 解析切面 并且获取增强器 !!!
     *
     * @param aspectInstanceFactory the aspect instance factory
     *                              (not the aspect instance itself in order to avoid eager instantiation)
     * @return
     */
    @Override
    public List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory) {
        // 获取标记为aspectj的类
        Class<?> aspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
        // 获取标记为aspectj的name
        String aspectName = aspectInstanceFactory.getAspectMetadata().getAspectName();
        // 验证
        validate(aspectClass);
        // We need to wrap the MetadataAwareAspectInstanceFactory with a decorator
        // so that it will only instantiate once.
        MetadataAwareAspectInstanceFactory lazySingletonAspectInstanceFactory =
                new LazySingletonAspectInstanceFactoryDecorator(aspectInstanceFactory);
        List<Advisor> advisors = new ArrayList<>();
        /**
         * 获取切面类所有的方法 包括object的方法
         */
        List<Method> methodList = getAdvisorMethods(aspectClass);
        /**
         * 获取增强器 包括：对切点信息的获取
         */
        for (Method method : methodList) {
            /**
             * 切点信息的获取 getPointcut 真正获取Advisor的方法!!!!
             */
            Advisor advisor = getAdvisor(method, lazySingletonAspectInstanceFactory, advisors.size(), aspectName);

            if (advisor != null) {
                advisors.add(advisor);
            }
        }
        // If it's a per target aspect, emit the dummy instantiating aspect.
        /**
         * 如果寻找的增强器不为空而且又配置了增强延迟初始化 那么需要在首位加入同步器实例化增强器
         */
        if (!advisors.isEmpty() && lazySingletonAspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
            Advisor instantiationAdvisor = new SyntheticInstantiationAdvisor(lazySingletonAspectInstanceFactory);
            advisors.add(0, instantiationAdvisor);
        }
        // Find introduction fields.
        for (Field field : aspectClass.getDeclaredFields()) {
            Advisor advisor = getDeclareParentsAdvisor(field);
            if (advisor != null) {
                advisors.add(advisor);
            }
        }
        return advisors;
    }

    private List<Method> getAdvisorMethods(Class<?> aspectClass) {
        final List<Method> methods = new ArrayList<>();
        ReflectionUtils.doWithMethods(aspectClass, method -> {
            // Exclude pointcuts
            // 意思就是声明为@PointCut 不需要处理
            if (AnnotationUtils.getAnnotation(method, Pointcut.class) == null) {
                methods.add(method);
            }
        });
        methods.sort(METHOD_COMPARATOR);
        return methods;
    }

    /**
     * Build a {@link org.springframework.aop.aspectj.DeclareParentsAdvisor}
     * for the given introduction field.
     * <p>Resulting Advisors will need to be evaluated for targets.
     *
     * @param introductionField the field to introspect
     * @return the Advisor instance, or {@code null} if not an Advisor
     */
    @Nullable
    private Advisor getDeclareParentsAdvisor(Field introductionField) {
        DeclareParents declareParents = introductionField.getAnnotation(DeclareParents.class);
        if (declareParents == null) {
            // Not an introduction field
            return null;
        }

        if (DeclareParents.class == declareParents.defaultImpl()) {
            throw new IllegalStateException("'defaultImpl' attribute must be set on DeclareParents");
        }

        return new DeclareParentsAdvisor(
                introductionField.getType(), declareParents.value(), declareParents.defaultImpl());
    }


    /**
     * 生成增强器，所有的增强器都是InstantiationModelAwarePointcutAdvisorImpl实现类进行封装
     *
     * @param candidateAdviceMethod    the candidate advice method
     * @param aspectInstanceFactory    the aspect instance factory
     * @param declarationOrderInAspect
     * @param aspectName               the name of the aspect
     * @return
     */
    @Override
    @Nullable
    public Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory,
                              int declarationOrderInAspect, String aspectName) {

        AspectMetadata aspectMetadata = aspectInstanceFactory.getAspectMetadata();
        Class<?> aspectClass = aspectMetadata.getAspectClass();
        validate(aspectClass);
        // 1对切点信息的获取  比如 @Before("test()") 注解表达式信息的获取
        AspectJExpressionPointcut expressionPointcut = getPointcut(candidateAdviceMethod, aspectClass);
        if (expressionPointcut == null) {
            return null;
        }
        // 2根据切点信息生成通知 由实现类InstantiationModelAwarePointcutAdvisorImpl封装
        // 还会初始化增强器
        return new InstantiationModelAwarePointcutAdvisorImpl(expressionPointcut, candidateAdviceMethod,
                this, aspectInstanceFactory, declarationOrderInAspect, aspectName);
    }

    /**
     * 切点信息的获取
     *
     * @param candidateAdviceMethod
     * @param candidateAspectClass
     * @return
     */
    @Nullable
    private AspectJExpressionPointcut getPointcut(Method candidateAdviceMethod, Class<?> candidateAspectClass) {

        /**
         * 获取方法上的注解 查询是否是 @Pointcut, @Around, @Before, @After, @AfterReturning, @AfterThrowing
         */
        AspectJAnnotation<?> aspectJAnnotation = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);

        if (aspectJAnnotation == null) {
            return null;
        }
        // 封装注解信息
        AspectJExpressionPointcut ajexp = new AspectJExpressionPointcut(candidateAspectClass, new String[0], new Class<?>[0]);
        // 提取得到的注解中的表达式
        ajexp.setExpression(aspectJAnnotation.getPointcutExpression());
        if (this.beanFactory != null) {
            ajexp.setBeanFactory(this.beanFactory);
        }
        return ajexp;
    }


    /**
     * 使用InstantiationModelAwarePointcutAdvisorImpl进行封装的时候 会去初始化对应的增强器Advice
     * 这些增强的位置不同，before after afterReturning 所以增强器advice也是不同的
     *
     * @param candidateAdviceMethod the candidate advice method
     * @param expressionPointcut    the AspectJ expression pointcut
     * @param aspectInstanceFactory the aspect instance factory
     * @param declarationOrder      the declaration order within the aspect
     * @param aspectName            the name of the aspect
     * @return
     */
    @Override
    @Nullable
    public Advice getAdvice(Method candidateAdviceMethod, AspectJExpressionPointcut expressionPointcut,
                            MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName) {

        Class<?> candidateAspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
        validate(candidateAspectClass);

        AspectJAnnotation<?> aspectJAnnotation =
                AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
        if (aspectJAnnotation == null) {
            return null;
        }

        // If we get here, we know we have an AspectJ method.
        // Check that it's an AspectJ-annotated class
        if (!isAspect(candidateAspectClass)) {
            throw new AopConfigException("Advice must be declared inside an aspect type: " +
                    "Offending method '" + candidateAdviceMethod + "' in class [" +
                    candidateAspectClass.getName() + "]");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Found AspectJ method: " + candidateAdviceMethod);
        }

        AbstractAspectJAdvice springAdvice;

        /**
         * 根据不同的注解封装不同的增强器
         */
        switch (aspectJAnnotation.getAnnotationType()) {
            case AtPointcut:
                if (logger.isDebugEnabled()) {
                    logger.debug("Processing pointcut '" + candidateAdviceMethod.getName() + "'");
                }
                return null;
            // 使用AspectJAroundAdvice
            case AtAround:
                springAdvice = new AspectJAroundAdvice(
                        candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                break;
            // 使用AspectJMethodBeforeAdvice
            case AtBefore:
                springAdvice = new AspectJMethodBeforeAdvice(
                        candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                break;
            // 使用AspectJAfterAdvice
            case AtAfter:
                springAdvice = new AspectJAfterAdvice(
                        candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                break;
            // 使用AspectJAfterReturningAdvice
            case AtAfterReturning:
                springAdvice = new AspectJAfterReturningAdvice(
                        candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                AfterReturning afterReturningAnnotation = (AfterReturning) aspectJAnnotation.getAnnotation();
                if (StringUtils.hasText(afterReturningAnnotation.returning())) {
                    springAdvice.setReturningName(afterReturningAnnotation.returning());
                }
                break;
            // 使用AspectJAfterThrowingAdvice
            case AtAfterThrowing:
                springAdvice = new AspectJAfterThrowingAdvice(
                        candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                AfterThrowing afterThrowingAnnotation = (AfterThrowing) aspectJAnnotation.getAnnotation();
                if (StringUtils.hasText(afterThrowingAnnotation.throwing())) {
                    springAdvice.setThrowingName(afterThrowingAnnotation.throwing());
                }
                break;
            default:
                throw new UnsupportedOperationException(
                        "Unsupported advice type on method: " + candidateAdviceMethod);
        }

        // Now to configure the advice...
        springAdvice.setAspectName(aspectName);
        springAdvice.setDeclarationOrder(declarationOrder);
        String[] argNames = this.parameterNameDiscoverer.getParameterNames(candidateAdviceMethod);
        if (argNames != null) {
            springAdvice.setArgumentNamesFromStringArray(argNames);
        }
        springAdvice.calculateArgumentBindings();

        return springAdvice;
    }


    /**
     * Synthetic advisor that instantiates the aspect.
     * Triggered by per-clause pointcut on non-singleton aspect.
     * The advice has no effect.
     */
    @SuppressWarnings("serial")
    protected static class SyntheticInstantiationAdvisor extends DefaultPointcutAdvisor {

        public SyntheticInstantiationAdvisor(final MetadataAwareAspectInstanceFactory aif) {
            super(aif.getAspectMetadata().getPerClausePointcut(), (MethodBeforeAdvice)
                    (method, args, target) -> aif.getAspectInstance());
        }
    }

}
