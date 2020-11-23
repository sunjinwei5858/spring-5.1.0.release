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

package org.springframework.web.servlet.support;

import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/**
 * 这个类用来创建父容器和子容器，但是父子容器还没进行关联，那么在哪里进行关联呢？
 * web容器类型
 * web.xml: XmlWebApplicationContext
 * spi: AnnotationConfigWebApplicationContext
 *
 * <p>
 * <p>
 * {@link org.springframework.web.WebApplicationInitializer WebApplicationInitializer}
 * to register a {@code DispatcherServlet} and use Java-based Spring configuration.
 *
 * <p>Implementations are required to implement:
 * <ul>
 * <li>{@link #getRootConfigClasses()} -- for "root" application context (non-web
 * infrastructure) configuration.
 * <li>{@link #getServletConfigClasses()} -- for {@code DispatcherServlet}
 * application context (Spring MVC infrastructure) configuration.
 * </ul>
 *
 * <p>If an application context hierarchy is not required, applications may
 * return all configuration via {@link #getRootConfigClasses()} and return
 * {@code null} from {@link #getServletConfigClasses()}.
 *
 * @author Arjen Poutsma
 * @author Chris Beams
 * @since 3.2
 */
public abstract class AbstractAnnotationConfigDispatcherServletInitializer extends AbstractDispatcherServletInitializer {

    /**
     * 创建父容器AnnotationConfigWebApplicationContext
     * <p>
     * {@inheritDoc}
     * <p>This implementation creates an {@link AnnotationConfigWebApplicationContext},
     * providing it the annotated classes returned by {@link #getRootConfigClasses()}.
     * Returns {@code null} if {@link #getRootConfigClasses()} returns {@code null}.
     */
    @Override
    @Nullable
    protected WebApplicationContext createRootApplicationContext() {
        // 获取关于父容器的配置
        Class<?>[] configClasses = getRootConfigClasses();
        if (!ObjectUtils.isEmpty(configClasses)) {
            // 父容器 类型：AnnotationConfigWebApplicationContext
            AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
            context.register(configClasses);
            return context;
        } else {
            return null;
        }
    }

    /**
     * 创建子容器AnnotationConfigWebApplicationContext，父子的容器都是同一个类型
     * {@inheritDoc}
     * <p>This implementation creates an {@link AnnotationConfigWebApplicationContext},
     * providing it the annotated classes returned by {@link #getServletConfigClasses()}.
     */
    @Override
    protected WebApplicationContext createServletApplicationContext() {
        // 子容器
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        // 获取关于子容器配置
        Class<?>[] configClasses = getServletConfigClasses();
        if (!ObjectUtils.isEmpty(configClasses)) {
            context.register(configClasses);
        }
        return context;
    }

    /**
     * Specify {@code @Configuration} and/or {@code @Component} classes for the
     * {@linkplain #createRootApplicationContext() root application context}.
     *
     * @return the configuration for the root application context, or {@code null}
     * if creation and registration of a root context is not desired
     */
    @Nullable
    protected abstract Class<?>[] getRootConfigClasses();

    /**
     * Specify {@code @Configuration} and/or {@code @Component} classes for the
     * {@linkplain #createServletApplicationContext() Servlet application context}.
     *
     * @return the configuration for the Servlet application context, or
     * {@code null} if all configuration is specified through root config classes.
     */
    @Nullable
    protected abstract Class<?>[] getServletConfigClasses();

}
