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

package org.springframework.context.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.core.NestedIOException;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.UnknownHostException;
import java.util.*;

/**
 * ConfigurationClassPostProcessor后置处理器调用该解析器进行解析配置类：递归调用，有六种形式，五个注解形式+一个接口默认方法
 *
 * 1。@ComponentScan：调用了ClassPathBeanDefinitionScanner进行包扫描注册bean定义，递归调用，一个类上可能不止一个@Component注解
 * 2。@Import：主要是@EnableXXX @MapperScan
 *  导入ImportBeanDefinitionRegistrar的实现类 这个接口就是用来整合第三方做扩展的，
 *  导入ImportSelector的实现类 动态加载
 *  重写registerBeanDefinitions方法，在方法里面可以让一个类不需要加任何spring的注解注册到spring容器。
 *  因为ConfigurationClassPostProcessor后置处理器的loadBeanDefinitions会去加载ImportBeanDefinitionRegistrar实现类的registerBeanDefinitions方法
 * 3。 @component
 * 4. @Bean
 * 5. @PropertySources
 * 6。接口的默认方法
 *
 * <p>
 * 翻译下面的英文注释：
 * 一般情况下一个@Configuration注解的类只会产生一个ConfigurationClass对象，
 * 但是因为@Configuration注解的类可能会使用注解@Import引入其他配置类，也可能内部嵌套定义配置类，
 * 所以总的来看，ConfigurationClassParser分析一个@Configuration注解的类，可能产生任意多个ConfigurationClass对象。
 * <p>
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @see ConfigurationClassBeanDefinitionReader
 * @since 3.0
 */
class ConfigurationClassParser {

    private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

    private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR =
            (o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


    private final Log logger = LogFactory.getLog(getClass());

    private final MetadataReaderFactory metadataReaderFactory;

    private final ProblemReporter problemReporter;

    private final Environment environment;

    private final ResourceLoader resourceLoader;

    private final BeanDefinitionRegistry registry;

    private final ComponentScanAnnotationParser componentScanParser;

    private final ConditionEvaluator conditionEvaluator;

    private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

    private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

    private final List<String> propertySourceNames = new ArrayList<>();

    private final ImportStack importStack = new ImportStack();

    /**
     * 注意：deferr 延迟的 推迟的
     * 只有是实现了deferredImportSelectors的接口才会添加到这个集合，比如springboot，但是事务的不会
     * spring5.1.2版本 已经将deferredImportSelectors集合放在了内部类DeferredImportSelectorHandler，这是一个改动，
     * 目的是为了springboot的@Import(AutoConfigurationImportSelector.class)
     */
    @Nullable
    private List<DeferredImportSelectorHolder> deferredImportSelectors;


    /**
     * Create a new {@link ConfigurationClassParser} instance that will be used
     * to populate the set of configuration classes.
     */
    public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
                                    ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
                                    BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

        this.metadataReaderFactory = metadataReaderFactory;
        this.problemReporter = problemReporter;
        this.environment = environment;
        this.resourceLoader = resourceLoader;
        this.registry = registry;
        this.componentScanParser = new ComponentScanAnnotationParser(
                environment, resourceLoader, componentScanBeanNameGenerator, registry);
        this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
    }


    /**
     * 这里的parse方法进行两个处理：
     * 1。parse 解析注解 包扫描
     * 2。processDeferredImportSelectors 处理实现了DeferredImportSelectors接口的方法
     *
     * parse方法进行了很多方式的重载，都将需要注册bean定义的对象使用ConfigurationClass对象封装
     * parse解析,注意spring5.1.2和当前的spring5.1.0版本已经做了变化!!!!!
     * 主要是@EnableAutoConfiguration的自动装配处理时机不同了
     *
     * @param configCandidates
     */
    public void parse(Set<BeanDefinitionHolder> configCandidates) {
        // 初始化deferredImportSelectors集合
        this.deferredImportSelectors = new LinkedList<>();

        for (BeanDefinitionHolder holder : configCandidates) {
            BeanDefinition bd = holder.getBeanDefinition();
            try {
                if (bd instanceof AnnotatedBeanDefinition) {
                    //重载
                    parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
                } else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
                    //重载
                    parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
                } else {
                    //重载
                    parse(bd.getBeanClassName(), holder.getBeanName());
                }
            } catch (BeanDefinitionStoreException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new BeanDefinitionStoreException(
                        "Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
            }
        }

        /**
         * 注意：
         * 1。如果有DeferredImportSelectors类型的 那么会进行处理；springboot是有的，springboot大量使用了DeferredImportSelectors
         * 2。spring5.1.2版本已经废除了该方法，当前版本是5.1.0
         * 而是替换成了this.deferredImportSelectorHandler.process();目的就是为了导入spring.factories的EnableAutoConfiguration
         */
        processDeferredImportSelectors();
    }

    /**
     * 重载
     *
     * @param className
     * @param beanName
     * @throws IOException
     */
    protected final void parse(@Nullable String className, String beanName) throws IOException {
        Assert.notNull(className, "No bean class name for configuration class bean definition");
        MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
        processConfigurationClass(new ConfigurationClass(reader, beanName));
    }

    /**
     * 重载
     *
     * @param clazz
     * @param beanName
     * @throws IOException
     */
    protected final void parse(Class<?> clazz, String beanName) throws IOException {
        processConfigurationClass(new ConfigurationClass(clazz, beanName));
    }

    /**
     * 重载
     *
     * @param metadata
     * @param beanName
     * @throws IOException
     */
    protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
        processConfigurationClass(new ConfigurationClass(metadata, beanName));
    }

    /**
     * Validate each {@link ConfigurationClass} object.
     *
     * @see ConfigurationClass#validate
     */
    public void validate() {
        for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
            configClass.validate(this.problemReporter);
        }
    }

    public Set<ConfigurationClass> getConfigurationClasses() {
        return this.configurationClasses.keySet();
    }


    /**
     * processConfigurationClass公共方法，会进行递归调用
     *
     * @param configClass
     * @throws IOException
     */
    protected void processConfigurationClass(ConfigurationClass configClass) throws IOException {
        if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
            return;
        }

        ConfigurationClass existingClass = this.configurationClasses.get(configClass);
        if (existingClass != null) {
            if (configClass.isImported()) {
                if (existingClass.isImported()) {
                    existingClass.mergeImportedBy(configClass);
                }
                // Otherwise ignore new imported config class; existing non-imported class overrides it.
                return;
            } else {
                // Explicit bean definition found, probably replacing an import.
                // Let's remove the old one and go with the new one.
                this.configurationClasses.remove(configClass);
                this.knownSuperclasses.values().removeIf(configClass::equals);
            }
        }

        // Recursively process the configuration class and its superclass hierarchy.
        SourceClass sourceClass = asSourceClass(configClass);
        do {
            /**
             * doProcessConfigurationClass真正做事情的方法
             */
            sourceClass = doProcessConfigurationClass(configClass, sourceClass);
        }
        while (sourceClass != null);

        this.configurationClasses.put(configClass, configClass);
    }

    /**
     * doProcessConfigurationClass真正做parse的方法：
     *
     * <p>
     * Apply processing and build a complete {@link ConfigurationClass} by reading the
     * annotations, members and methods from the source class. This method can be called
     * multiple times as relevant sources are discovered.
     *
     * @param configClass the configuration class being build
     * @param sourceClass a source class
     * @return the superclass, or {@code null} if none found or previously processed
     */
    @Nullable
    protected final SourceClass doProcessConfigurationClass(ConfigurationClass configClass, SourceClass sourceClass)
            throws IOException {

        /**
         * 1。处理@Component注解 递归调用
         */
        if (configClass.getMetadata().isAnnotated(Component.class.getName())) {
            // Recursively process any member (nested) classes first
            processMemberClasses(configClass, sourceClass);
        }

        /**
         * Process any @PropertySource annotations
         * 2。处理@PropertySources注解 递归调用
         */
        for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
                sourceClass.getMetadata(), PropertySources.class,
                org.springframework.context.annotation.PropertySource.class)) {
            if (this.environment instanceof ConfigurableEnvironment) {
                processPropertySource(propertySource);
            } else {
                logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
                        "]. Reason: Environment must implement ConfigurableEnvironment");
            }
        }

        /**
         * Process any @ComponentScan annotations
         * 3。处理@ComponentScans注解 递归调用
         */
        Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
                sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
        if (!componentScans.isEmpty() &&
                !this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
            for (AnnotationAttributes componentScan : componentScans) {
                // The config class is annotated with @ComponentScan -> perform the scan immediately
                /** The config class is annotated with @ComponentScan -> perform the scan immediately
                 * 这里进行解析和扫描 @ComponentScan 包下面的bean定义注册，userService的bean定义注册
                 */
                Set<BeanDefinitionHolder> scannedBeanDefinitions = this.componentScanParser.parse(
                        componentScan, sourceClass.getMetadata().getClassName()
                );
                // Check the set of scanned definitions for any further config classes and parse recursively if needed
                for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
                    BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition();
                    if (bdCand == null) {
                        bdCand = holder.getBeanDefinition();
                    }
                    if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
                        /**
                         * 这里会进行递归调用parse方法 UserService这个类除了加了@Component注解 有没有加别的注解 如果有的话 这里进行递归解析
                         */
                        parse(bdCand.getBeanClassName(), holder.getBeanName());
                    }
                }
            }
        }

        /**
         * Process any @Import annotations
         * 4。处理@Import注解
         * 有好几种情况：ImportSelector ImportBeanDefinitionRegistar
         *  4.1 ImportBeanDefinitionRegistar实现类缓存到ConfigurationClass的importBeanDefinitionRegistrars容器中
         *      便于ConfigurationClassPostProcessor后置处理器调用，统一进行注册包扫描和配置类即所有类的bean定义
         *  4.2 ImportSelector 调用selectImports方法
         */
        Set<SourceClass> imports = getImports(sourceClass);
        processImports(configClass, sourceClass, imports, true);

        // Process any @ImportResource annotations
        AnnotationAttributes importResource =
                AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
        if (importResource != null) {
            String[] resources = importResource.getStringArray("locations");
            Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
            for (String resource : resources) {
                String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
                configClass.addImportedResource(resolvedResource, readerClass);
            }
        }

        /**
         * 5。Process individual @Bean methods
         * 处理@Bean方式
         */
        Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
        for (MethodMetadata methodMetadata : beanMethods) {
            configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
        }

        /**
         * 6。Process default methods on interfaces
         * 处理接口的默认方法 因为jdk8支持接口可以默认方法
         */
        processInterfaces(configClass, sourceClass);

        // Process superclass, if any
        if (sourceClass.getMetadata().hasSuperClass()) {
            String superclass = sourceClass.getMetadata().getSuperClassName();
            if (superclass != null && !superclass.startsWith("java") &&
                    !this.knownSuperclasses.containsKey(superclass)) {
                this.knownSuperclasses.put(superclass, configClass);
                // Superclass found, return its annotation metadata and recurse
                return sourceClass.getSuperClass();
            }
        }

        // No superclass -> processing is complete
        return null;
    }

    /**
     * Register member (nested) classes that happen to be configuration classes themselves.
     */
    private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
        Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
        if (!memberClasses.isEmpty()) {
            List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
            for (SourceClass memberClass : memberClasses) {
                if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
                        !memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
                    candidates.add(memberClass);
                }
            }
            OrderComparator.sort(candidates);
            for (SourceClass candidate : candidates) {
                if (this.importStack.contains(configClass)) {
                    this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
                } else {
                    this.importStack.push(configClass);
                    try {
                        processConfigurationClass(candidate.asConfigClass(configClass));
                    } finally {
                        this.importStack.pop();
                    }
                }
            }
        }
    }

    /**
     * Register default methods on interfaces implemented by the configuration class.
     */
    private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
        for (SourceClass ifc : sourceClass.getInterfaces()) {
            Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
            for (MethodMetadata methodMetadata : beanMethods) {
                if (!methodMetadata.isAbstract()) {
                    // A default method or other concrete method on a Java 8+ interface...
                    configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
                }
            }
            processInterfaces(configClass, ifc);
        }
    }

    /**
     * Retrieve the metadata for all <code>@Bean</code> methods.
     */
    private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
        AnnotationMetadata original = sourceClass.getMetadata();
        Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
        if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
            // Try reading the class file via ASM for deterministic declaration order...
            // Unfortunately, the JVM's standard reflection returns methods in arbitrary
            // order, even between different runs of the same application on the same JVM.
            try {
                AnnotationMetadata asm =
                        this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
                Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
                if (asmMethods.size() >= beanMethods.size()) {
                    Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
                    for (MethodMetadata asmMethod : asmMethods) {
                        for (MethodMetadata beanMethod : beanMethods) {
                            if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
                                selectedMethods.add(beanMethod);
                                break;
                            }
                        }
                    }
                    if (selectedMethods.size() == beanMethods.size()) {
                        // All reflection-detected methods found in ASM method set -> proceed
                        beanMethods = selectedMethods;
                    }
                }
            } catch (IOException ex) {
                logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
                // No worries, let's continue with the reflection metadata we started with...
            }
        }
        return beanMethods;
    }


    /**
     * Process the given <code>@PropertySource</code> annotation metadata.
     *
     * @param propertySource metadata for the <code>@PropertySource</code> annotation found
     * @throws IOException if loading a property source failed
     */
    private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
        String name = propertySource.getString("name");
        if (!StringUtils.hasLength(name)) {
            name = null;
        }
        String encoding = propertySource.getString("encoding");
        if (!StringUtils.hasLength(encoding)) {
            encoding = null;
        }
        String[] locations = propertySource.getStringArray("value");
        Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
        boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

        Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
        PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
                DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

        for (String location : locations) {
            try {
                String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
                Resource resource = this.resourceLoader.getResource(resolvedLocation);
                addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
            } catch (IllegalArgumentException | FileNotFoundException | UnknownHostException ex) {
                // Placeholders not resolvable or resource not found when trying to open it
                if (ignoreResourceNotFound) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
                    }
                } else {
                    throw ex;
                }
            }
        }
    }

    private void addPropertySource(PropertySource<?> propertySource) {
        String name = propertySource.getName();
        MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

        if (this.propertySourceNames.contains(name)) {
            // We've already added a version, we need to extend it
            PropertySource<?> existing = propertySources.get(name);
            if (existing != null) {
                PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
                        ((ResourcePropertySource) propertySource).withResourceName() : propertySource);
                if (existing instanceof CompositePropertySource) {
                    ((CompositePropertySource) existing).addFirstPropertySource(newSource);
                } else {
                    if (existing instanceof ResourcePropertySource) {
                        existing = ((ResourcePropertySource) existing).withResourceName();
                    }
                    CompositePropertySource composite = new CompositePropertySource(name);
                    composite.addPropertySource(newSource);
                    composite.addPropertySource(existing);
                    propertySources.replace(name, composite);
                }
                return;
            }
        }

        if (this.propertySourceNames.isEmpty()) {
            propertySources.addLast(propertySource);
        } else {
            String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
            propertySources.addBefore(firstProcessed, propertySource);
        }
        this.propertySourceNames.add(name);
    }


    /**
     * 返回所有@Import相关的注解信息
     *
     * Returns {@code @Import} class, considering all meta-annotations.
     */
    private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
        Set<SourceClass> imports = new LinkedHashSet<>();
        Set<SourceClass> visited = new LinkedHashSet<>();
        /**
         * 这里进行add
         */
        collectImports(sourceClass, imports, visited);
        return imports;
    }

    /**
     * 这里进行将@Import注解导入进来的类获取
     * <p>
     * Recursively collect all declared {@code @Import} values. Unlike most
     * meta-annotations it is valid to have several {@code @Import}s declared with
     * different values; the usual process of returning values from the first
     * meta-annotation on a class is not sufficient.
     * <p>For example, it is common for a {@code @Configuration} class to declare direct
     * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
     * annotation.
     *
     * @param sourceClass the class to search
     * @param imports     the imports collected so far
     * @param visited     used to track visited classes to prevent infinite recursion
     * @throws IOException if there is any problem reading metadata from the named class
     */
    private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
            throws IOException {

        if (visited.add(sourceClass)) {
            for (SourceClass annotation : sourceClass.getAnnotations()) {
                String annName = annotation.getMetadata().getClassName();
                if (!annName.startsWith("java") && !annName.equals(Import.class.getName())) {
                    collectImports(annotation, imports, visited);
                }
            }
            /**
             * 这里将@Import的value 添加
             */
            imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
        }
    }

    /**
     * 处理DeferredImportSelectors类型的:springboot有大量应用
     */
    private void processDeferredImportSelectors() {
        List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
        this.deferredImportSelectors = null;
        if (deferredImports == null) {
            return;
        }

        deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
        Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();
        Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();
        for (DeferredImportSelectorHolder deferredImport : deferredImports) {
            Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();
            DeferredImportSelectorGrouping grouping = groupings.computeIfAbsent(
                    (group != null ? group : deferredImport),
                    key -> new DeferredImportSelectorGrouping(createGroup(group)));
            grouping.add(deferredImport);
            configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(),
                    deferredImport.getConfigurationClass());
        }
        for (DeferredImportSelectorGrouping grouping : groupings.values()) {
            /**
             * grouping.getImports()方法：调用selectImport方法
             * springboot导入自动配置类就在此处
             */
            Iterable<Group.Entry> imports = grouping.getImports();
            imports.forEach(entry -> {
                ConfigurationClass configurationClass = configurationClasses.get(entry.getMetadata());
                try {
                    processImports(configurationClass, asSourceClass(configurationClass),
                            asSourceClasses(entry.getImportClassName()), false);
                } catch (BeanDefinitionStoreException ex) {
                    throw ex;
                } catch (Throwable ex) {
                    throw new BeanDefinitionStoreException(
                            "Failed to process import candidates for configuration class [" +
                                    configurationClass.getMetadata().getClassName() + "]", ex);
                }
            });
        }
    }

    private Group createGroup(@Nullable Class<? extends Group> type) {
        Class<? extends Group> effectiveType = (type != null ? type : DefaultDeferredImportSelectorGroup.class);
        Group group = BeanUtils.instantiateClass(effectiveType);
        ParserStrategyUtils.invokeAwareMethods(group,
                ConfigurationClassParser.this.environment,
                ConfigurationClassParser.this.resourceLoader,
                ConfigurationClassParser.this.registry);
        return group;
    }

    /**
     * 1。ImportSelector接口
     * 2。DeferredImportSelector接口
     * 3。ImportBeanDefinitionRegistrar接口
     * 4。当成@Configuration配置类来处理
     *
     * 注意：1，2，3都会先判断是否实现了aware接口 如果有 调用aware接口方法
     *
     * @param configClass
     * @param currentSourceClass
     * @param importCandidates
     * @param checkForCircularImports
     */
    private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
                                Collection<SourceClass> importCandidates, boolean checkForCircularImports) {

        if (importCandidates.isEmpty()) {
            return;
        }

        if (checkForCircularImports && isChainedImportOnStack(configClass)) {
            this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
        } else {
            this.importStack.push(configClass);
            try {
                for (SourceClass candidate : importCandidates) {
                    /**
                     * 1。ImportSelector接口：
                     * 一处应用：
                     * @EnableTransactionManagement 开始事务注解，@Import(TransactionManagementConfigurationSelector.class)
                     * 其中TransactionManagementConfigurationSelector实现了ImportSelector接口
                     */
                    if (candidate.isAssignable(ImportSelector.class)) {
                        // Candidate class is an ImportSelector -> delegate to it to determine imports
                        Class<?> candidateClass = candidate.loadClass();

                        ImportSelector selector = BeanUtils.instantiateClass(candidateClass, ImportSelector.class);
                        /**
                         * 注意：ImportSelector的实现类如果实现了Aware接口 那么会先调用aware方法
                         */
                        ParserStrategyUtils.invokeAwareMethods(selector, this.environment, this.resourceLoader, this.registry);
                        if (this.deferredImportSelectors != null && selector instanceof DeferredImportSelector) {
                            /**
                             * 1-1 如果是DeferredImportSelector，添加到这个LinkedList集合。属于延迟的
                             * springboot使用这个接口，走这里
                             */
                            this.deferredImportSelectors.add(new DeferredImportSelectorHolder(configClass, (DeferredImportSelector) selector));
                        } else {
                            /**
                             * 1-2 如果是普通的ImportSelector走这里，调用selectImports方法，
                             * 事务的TransactionManagementConfigurationSelector就是走这里，调用selectImports方法，然后递归调用
                             */
                            String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata()); // 调用selectImports方法
                            Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames);
                            // 递归处理processImports
                            processImports(configClass, currentSourceClass, importSourceClasses, false);
                        }
                    }
                    /**
                     * 2。ImportBeanDefinitionRegistrar：
                     * 两处应用：
                     * 1。aop的@EnableAspectJAutoProxy 就导入了@Import(AspectJAutoProxyRegistrar.class)，其中AspectJAutoProxyRegistrar实现了ImportBeanDefinitionRegistrar
                     * 2。mybatis-spring 的@MapperScan注解也是@Import(MapperScannerRegistrar.class)，其中MapperScannerRegistrar实现了ImportBeanDefinitionRegistrar
                     * 3。事务的ImportSelector的selectImport方法导入的AutoProxyRegistrar也会走这里
                     */
                    else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
                        // Candidate class is an ImportBeanDefinitionRegistrar -> delegate to it to register additional bean definitions
                        Class<?> candidateClass = candidate.loadClass();
                        // 实例化ImportBeanDefinitionRegistrar这个实现类
                        ImportBeanDefinitionRegistrar registrar = BeanUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class);
                        /**
                         * 注意：ImportBeanDefinitionRegistrar的实现类如果实现了Aware接口 那么会先调用aware方法
                         */
                        ParserStrategyUtils.invokeAwareMethods(registrar, this.environment, this.resourceLoader, this.registry);
                        // 将ImportBeanDefinitionRegistrar先添加到容器 后续使用reader 再调用里面实现的方法
                        configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
                    } else {
                        // Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
                        // process it as an @Configuration class
                        /**
                         * 3。process it as an @Configuration class
                         * 一处应用：
                         * 事务的ProxyTransactionManagementConfiguration就走这里
                         */
                        this.importStack.registerImport(currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
                        // 递归调用
                        processConfigurationClass(candidate.asConfigClass(configClass));
                    }
                }
            } catch (BeanDefinitionStoreException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new BeanDefinitionStoreException(
                        "Failed to process import candidates for configuration class [" +
                                configClass.getMetadata().getClassName() + "]", ex);
            } finally {
                this.importStack.pop();
            }
        }
    }

    private boolean isChainedImportOnStack(ConfigurationClass configClass) {
        if (this.importStack.contains(configClass)) {
            String configClassName = configClass.getMetadata().getClassName();
            AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
            while (importingClass != null) {
                if (configClassName.equals(importingClass.getClassName())) {
                    return true;
                }
                importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
            }
        }
        return false;
    }

    ImportRegistry getImportRegistry() {
        return this.importStack;
    }


    /**
     * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
     */
    private SourceClass asSourceClass(ConfigurationClass configurationClass) throws IOException {
        AnnotationMetadata metadata = configurationClass.getMetadata();
        if (metadata instanceof StandardAnnotationMetadata) {
            return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass());
        }
        return asSourceClass(metadata.getClassName());
    }

    /**
     * Factory method to obtain a {@link SourceClass} from a {@link Class}.
     */
    SourceClass asSourceClass(@Nullable Class<?> classType) throws IOException {
        if (classType == null) {
            return new SourceClass(Object.class);
        }
        try {
            // Sanity test that we can reflectively read annotations,
            // including Class attributes; if not -> fall back to ASM
            for (Annotation ann : classType.getAnnotations()) {
                AnnotationUtils.validateAnnotation(ann);
            }
            return new SourceClass(classType);
        } catch (Throwable ex) {
            // Enforce ASM via class name resolution
            return asSourceClass(classType.getName());
        }
    }

    /**
     * Factory method to obtain {@link SourceClass SourceClasss} from class names.
     */
    private Collection<SourceClass> asSourceClasses(String... classNames) throws IOException {
        List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
        for (String className : classNames) {
            annotatedClasses.add(asSourceClass(className));
        }
        return annotatedClasses;
    }

    /**
     * Factory method to obtain a {@link SourceClass} from a class name.
     */
    SourceClass asSourceClass(@Nullable String className) throws IOException {
        if (className == null) {
            return new SourceClass(Object.class);
        }
        if (className.startsWith("java")) {
            // Never use ASM for core java types
            try {
                return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
            } catch (ClassNotFoundException ex) {
                throw new NestedIOException("Failed to load class [" + className + "]", ex);
            }
        }
        return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
    }


    @SuppressWarnings("serial")
    private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

        private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

        public void registerImport(AnnotationMetadata importingClass, String importedClass) {
            this.imports.add(importedClass, importingClass);
        }

        @Override
        @Nullable
        public AnnotationMetadata getImportingClassFor(String importedClass) {
            return CollectionUtils.lastElement(this.imports.get(importedClass));
        }

        @Override
        public void removeImportingClass(String importingClass) {
            for (List<AnnotationMetadata> list : this.imports.values()) {
                for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext(); ) {
                    if (iterator.next().getClassName().equals(importingClass)) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }

        /**
         * Given a stack containing (in order)
         * <ul>
         * <li>com.acme.Foo</li>
         * <li>com.acme.Bar</li>
         * <li>com.acme.Baz</li>
         * </ul>
         * return "[Foo->Bar->Baz]".
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("[");
            Iterator<ConfigurationClass> iterator = iterator();
            while (iterator.hasNext()) {
                builder.append(iterator.next().getSimpleName());
                if (iterator.hasNext()) {
                    builder.append("->");
                }
            }
            return builder.append(']').toString();
        }
    }


    private static class DeferredImportSelectorHolder {

        private final ConfigurationClass configurationClass;

        private final DeferredImportSelector importSelector;

        public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
            this.configurationClass = configClass;
            this.importSelector = selector;
        }

        public ConfigurationClass getConfigurationClass() {
            return this.configurationClass;
        }

        public DeferredImportSelector getImportSelector() {
            return this.importSelector;
        }
    }


    /**
     * getImports方法-->selectImports
     */
    private static class DeferredImportSelectorGrouping {

        private final DeferredImportSelector.Group group;

        private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

        DeferredImportSelectorGrouping(Group group) {
            this.group = group;
        }

        public void add(DeferredImportSelectorHolder deferredImport) {
            this.deferredImports.add(deferredImport);
        }

        /**
         * Return the imports defined by the group.
         *
         * @return each import with its associated configuration class
         */
        public Iterable<Group.Entry> getImports() {
            for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
                this.group.process(deferredImport.getConfigurationClass().getMetadata(),
                        deferredImport.getImportSelector());
            }
            /**
             * 进行selectImports方法
             */
            return this.group.selectImports();
        }
    }


    private static class DefaultDeferredImportSelectorGroup implements Group {

        private final List<Entry> imports = new ArrayList<>();

        @Override
        public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
            for (String importClassName : selector.selectImports(metadata)) {
                this.imports.add(new Entry(metadata, importClassName));
            }
        }

        @Override
        public Iterable<Entry> selectImports() {
            return this.imports;
        }
    }


    /**
     * Simple wrapper that allows annotated source classes to be dealt with
     * in a uniform manner, regardless of how they are loaded.
     */
    private class SourceClass implements Ordered {

        private final Object source;  // Class or MetadataReader

        private final AnnotationMetadata metadata;

        public SourceClass(Object source) {
            this.source = source;
            if (source instanceof Class) {
                this.metadata = new StandardAnnotationMetadata((Class<?>) source, true);
            } else {
                this.metadata = ((MetadataReader) source).getAnnotationMetadata();
            }
        }

        public final AnnotationMetadata getMetadata() {
            return this.metadata;
        }

        @Override
        public int getOrder() {
            Integer order = ConfigurationClassUtils.getOrder(this.metadata);
            return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
        }

        public Class<?> loadClass() throws ClassNotFoundException {
            if (this.source instanceof Class) {
                return (Class<?>) this.source;
            }
            String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
            return ClassUtils.forName(className, resourceLoader.getClassLoader());
        }

        public boolean isAssignable(Class<?> clazz) throws IOException {
            if (this.source instanceof Class) {
                return clazz.isAssignableFrom((Class<?>) this.source);
            }
            return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
        }

        public ConfigurationClass asConfigClass(ConfigurationClass importedBy) throws IOException {
            if (this.source instanceof Class) {
                return new ConfigurationClass((Class<?>) this.source, importedBy);
            }
            return new ConfigurationClass((MetadataReader) this.source, importedBy);
        }

        public Collection<SourceClass> getMemberClasses() throws IOException {
            Object sourceToProcess = this.source;
            if (sourceToProcess instanceof Class) {
                Class<?> sourceClass = (Class<?>) sourceToProcess;
                try {
                    Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
                    List<SourceClass> members = new ArrayList<>(declaredClasses.length);
                    for (Class<?> declaredClass : declaredClasses) {
                        members.add(asSourceClass(declaredClass));
                    }
                    return members;
                } catch (NoClassDefFoundError err) {
                    // getDeclaredClasses() failed because of non-resolvable dependencies
                    // -> fall back to ASM below
                    sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
                }
            }

            // ASM-based resolution - safe for non-resolvable classes as well
            MetadataReader sourceReader = (MetadataReader) sourceToProcess;
            String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
            List<SourceClass> members = new ArrayList<>(memberClassNames.length);
            for (String memberClassName : memberClassNames) {
                try {
                    members.add(asSourceClass(memberClassName));
                } catch (IOException ex) {
                    // Let's skip it if it's not resolvable - we're just looking for candidates
                    if (logger.isDebugEnabled()) {
                        logger.debug("Failed to resolve member class [" + memberClassName +
                                "] - not considering it as a configuration class candidate");
                    }
                }
            }
            return members;
        }

        public SourceClass getSuperClass() throws IOException {
            if (this.source instanceof Class) {
                return asSourceClass(((Class<?>) this.source).getSuperclass());
            }
            return asSourceClass(((MetadataReader) this.source).getClassMetadata().getSuperClassName());
        }

        public Set<SourceClass> getInterfaces() throws IOException {
            Set<SourceClass> result = new LinkedHashSet<>();
            if (this.source instanceof Class) {
                Class<?> sourceClass = (Class<?>) this.source;
                for (Class<?> ifcClass : sourceClass.getInterfaces()) {
                    result.add(asSourceClass(ifcClass));
                }
            } else {
                for (String className : this.metadata.getInterfaceNames()) {
                    result.add(asSourceClass(className));
                }
            }
            return result;
        }

        public Set<SourceClass> getAnnotations() throws IOException {
            Set<SourceClass> result = new LinkedHashSet<>();
            for (String className : this.metadata.getAnnotationTypes()) {
                try {
                    result.add(getRelated(className));
                } catch (Throwable ex) {
                    // An annotation not present on the classpath is being ignored
                    // by the JVM's class loading -> ignore here as well.
                }
            }
            return result;
        }

        public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
            Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
            if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
                return Collections.emptySet();
            }
            String[] classNames = (String[]) annotationAttributes.get(attribute);
            Set<SourceClass> result = new LinkedHashSet<>();
            for (String className : classNames) {
                result.add(getRelated(className));
            }
            return result;
        }

        private SourceClass getRelated(String className) throws IOException {
            if (this.source instanceof Class) {
                try {
                    Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
                    return asSourceClass(clazz);
                } catch (ClassNotFoundException ex) {
                    // Ignore -> fall back to ASM next, except for core java types.
                    if (className.startsWith("java")) {
                        throw new NestedIOException("Failed to load class [" + className + "]", ex);
                    }
                    return new SourceClass(metadataReaderFactory.getMetadataReader(className));
                }
            }
            return asSourceClass(className);
        }

        @Override
        public boolean equals(Object other) {
            return (this == other || (other instanceof SourceClass &&
                    this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
        }

        @Override
        public int hashCode() {
            return this.metadata.getClassName().hashCode();
        }

        @Override
        public String toString() {
            return this.metadata.getClassName();
        }
    }


    /**
     * {@link Problem} registered upon detection of a circular {@link Import}.
     */
    private static class CircularImportProblem extends Problem {

        public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
            super(String.format("A circular @Import has been detected: " +
                            "Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
                            "already present in the current import stack %s", importStack.element().getSimpleName(),
                    attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
                    new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
        }
    }

}
