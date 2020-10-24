/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.aop.aspectj;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.AfterAdvice;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * 后置通知 拦截器 实现了MethodInterceptor接口
 * <p>
 * Spring AOP advice wrapping an AspectJ after advice method.
 *
 * @author Rod Johnson
 * @since 2.0
 */
@SuppressWarnings("serial")
public class AspectJAfterAdvice extends AbstractAspectJAdvice
        implements MethodInterceptor, AfterAdvice, Serializable {

    public AspectJAfterAdvice(
            Method aspectJBeforeAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aif) {

        super(aspectJBeforeAdviceMethod, pointcut, aif);
    }


    /**
     * 后置通知方法执行，这里面用了try-finally
     *
     * @param mi
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(MethodInvocation mi) throws Throwable {
        try {
            /**
             *
             */
            return mi.proceed();
        } finally {
            /**
             * 后置方法一定会执行在这句代码得到验证，因为 mi.proceed();方法抛出异常，finally方法肯定会被执行
             */
            invokeAdviceMethod(getJoinPointMatch(), null, null);
        }
    }

    @Override
    public boolean isBeforeAdvice() {
        return false;
    }

    @Override
    public boolean isAfterAdvice() {
        return true;
    }

}
