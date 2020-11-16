/*
 * Copyright 2002-2013 the original author or authors.
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

import org.springframework.core.type.AnnotationMetadata;

/**
 * 参考博客链接：
 * https://blog.csdn.net/elim168/article/details/88131614
 * https://blog.csdn.net/Smallc0de/article/details/108619562?utm_medium=distribute.pc_relevant.none-task-blog-BlogCommendFromMachineLearnPai2-2.channel_param&depth_1-utm_source=distribute.pc_relevant.none-task-blog-BlogCommendFromMachineLearnPai2-2.channel_param
 *
 * ImportSelector真正的作用：
 * 如果有些功能我们并不需要Spring在一开始就加载进去，而是需要Spring帮助我们把这些功能动态加载进去，
 * 这时候这个ImportSelector的作用就来了。
 * 我们完全可以把实现这个接口的类做成一个开关，用来开启或者关闭某一个或者某些功能类。
 *
 *
 * Interface to be implemented by types that determine which @{@link Configuration}
 * class(es) should be imported based on a given selection criteria, usually one or more
 * annotation attributes.
 *
 * 如果ImportSelector的实现类还实现了Aware接口 那么会先比selectImports更先调用awareMethods方法，自己在源码中得到验证
 * 就在parse方法，ConfigurationClassPostProcessor后置处理器调用processConfigBeanDefinitions(BeanDefinitionRegistry)方法中：
 * 使用ConfigurationClassParser解析parse()进行了
 * ConfigurationClassParser#parse()
 *  ConfigurationClassParser#processImports()
 *      ParserStrategyUtils.invokeAwareMethods
 *
 * <p>An {@link ImportSelector} may implement any of the following
 * {@link org.springframework.beans.factory.Aware Aware} interfaces, and their respective
 * methods will be called prior to {@link #selectImports}:
 * <ul>
 * <li>{@link org.springframework.context.EnvironmentAware EnvironmentAware}</li>
 * <li>{@link org.springframework.beans.factory.BeanFactoryAware BeanFactoryAware}</li>
 * <li>{@link org.springframework.beans.factory.BeanClassLoaderAware BeanClassLoaderAware}</li>
 * <li>{@link org.springframework.context.ResourceLoaderAware ResourceLoaderAware}</li>
 * </ul>
 *
 * <p>ImportSelectors are usually processed in the same way as regular {@code @Import}
 * annotations, however, it is also possible to defer selection of imports until all
 * {@code @Configuration} classes have been processed (see {@link DeferredImportSelector}
 * for details).
 *
 * @author Chris Beams
 * @see DeferredImportSelector
 * @see Import
 * @see ImportBeanDefinitionRegistrar
 * @see Configuration
 * @since 3.1
 */
public interface ImportSelector {

    /**
     * 参数importingClassMetadata是注解的元数据，
     * 为什么要有ImportSelector接口？因为可以进行动态选择并返回需要导入的类的名称。
     * 这些类基于AnnotationMetadata，并且导入到@Configuration注解的类中的
     *
     * Select and return the names of which class(es) should be imported based on
     * the {@link AnnotationMetadata} of the importing @{@link Configuration} class.
     */
    String[] selectImports(AnnotationMetadata importingClassMetadata);

}
