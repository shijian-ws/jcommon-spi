package com.github.jcommon.spi.support;

import com.github.jcommon.constant.CommonConstant;
import com.github.jcommon.holder.Holder;
import com.github.jcommon.logger.Logger;
import com.github.jcommon.logger.support.LoggerFactory;
import com.github.jcommon.spi.Provider;
import com.github.jcommon.spi.ProviderFactory;
import com.github.jcommon.spi.ProviderNameExtractor;
import com.github.jcommon.spi.SPI;
import com.github.jcommon.tuple.LazyPair;
import com.github.jcommon.type.TypeResolver;
import com.github.jcommon.type.TypeResolverUtil;
import com.github.jcommon.util.AnnotationUtil;
import com.github.jcommon.util.Assert;
import com.github.jcommon.util.IterableUtil;
import com.github.jcommon.util.PropertiesUtil;
import com.github.jcommon.util.ReflectUtil;
import com.github.jcommon.util.Safes;
import com.github.jcommon.util.StringUtil;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * SPI服务提供者管理器
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2019-11-10
 */
public final class ProviderManager<T> implements Iterable<Map.Entry<ProviderDescriptor<T>, T>> {
    /**
     * SPI服务提供者目录
     */
    private static final String SPI_DIRECTORY = "META-INF/spi/";
    /**
     * JDK5.0 SPI 服务提供者目录
     */
    private static final String JDK_SPI_DIRECTORY = "META-INF/services/";
    /**
     * 服务提供者name字符正则
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z_]");
    /**
     * 服务提供者管理器
     */
    private static final Map<Class<?>, ProviderManager<?>> PROVIDER_MANAGER_MAP = new ConcurrentHashMap<>(64);
    /**
     * 缓存创建中的Provider
     */
    private static final Map<Class<?>, Object> IN_CREATING_PROVIDER_MAP = new ConcurrentHashMap<>(32);

    /**
     * 日志输出对象, 需要定义在PROVIDER_MANAGER_MAP之后, 否则PROVIDER_MANAGER_MAP未初始化为null
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderManager.class);

    /**
     * 默认服务名称提取
     */
    private static final ProviderNameExtractor DEFAULT_NAME_EXTRACTOR = ProviderNameExtractor.DEFAULT;

    /**
     * 当前是否存在指定的服务提供者管理器
     */
    public static boolean exists(Class<?> clazz) {
        return PROVIDER_MANAGER_MAP.containsKey(clazz);
    }

    /**
     * 获取服务提供者管理器
     */
    @SuppressWarnings("unchecked")
    public static <T> ProviderManager<T> load(Class<T> clazz) {
        return (ProviderManager<T>) PROVIDER_MANAGER_MAP.computeIfAbsent(clazz, key -> new ProviderManager<>(clazz));
    }

    /**
     * SPI服务类型
     */
    private final Class<T> providerClass;
    /**
     * 默认服务名称
     */
    private final String defaultName;
    /**
     * 当前服务类型的默认依赖注入工厂, 如果未指定则使用{@link ProviderManager<ProviderFactory>#get()}
     */
    private final Class<? extends ProviderFactory> providerFactoryClass;
    /**
     * 如果当前依赖注入工厂未找到指定服务提供者实现是否检索其他{@link ProviderManager<ProviderFactory>#iterator()}依赖注入工厂
     */
    private final boolean lookupOther;
    /**
     * 依赖注入工厂
     */
    private final Holder<ProviderFactory> injectProviderFactoryHolder;
    /**
     * 服务名称提取
     */
    private final Holder<ProviderNameExtractor> nameExtractorHolder = new Holder<>();
    /**
     * 服务提供者描述
     */
    private final Holder<SortedSet<ProviderDescriptor<T>>> descriptorsHolder = new Holder<>();
    /**
     * 提供者实例Map
     */
    private final Map<ProviderDescriptor<T>, Holder<T>> providerHolderMap = new ConcurrentHashMap<>();
    /**
     * 提供者创建失败异常
     */
    private final Map<String, Holder<Throwable>> createProviderErrorHolderMap = new ConcurrentHashMap<>();
    /**
     * 服务提供者视图
     */
    private final Holder<ProviderInstanceView<T>> entrySetHolder = new Holder<>();

    private ProviderManager(Class<T> providerClass) {
        Assert.notNull(providerClass, "provider class must be not null");

        String defaultName = null;
        Class<? extends ProviderFactory> providerFactoryClass = null;
        boolean lookupOther = false;

        SPI spi = providerClass.getAnnotation(SPI.class);
        if (spi != null) {
            Assert.isTrue(StringUtil.isBlank(defaultName = spi.defaultName().trim()) || NAME_PATTERN.matcher(defaultName).matches(), providerClass.getName() + "@SPI defaultName is illegal");
            providerFactoryClass = ProviderFactory.class == spi.providerFactory() ? null : spi.providerFactory();
            lookupOther = spi.lookupOther();
        }

        this.providerClass = providerClass;
        this.defaultName = defaultName;
        this.providerFactoryClass = providerFactoryClass;
        this.lookupOther = lookupOther;

        // ProviderFactory自身不需要依赖注入, 延迟初始化
        this.injectProviderFactoryHolder = ProviderFactory.class.isAssignableFrom(this.providerClass) ? null : new Holder<>();
    }

    /**
     * 是否存在指定类型的Provider实现
     */
    public boolean contains(Class<?> providerClass) {
        return providerClass != null && this.getProviderDescriptor(providerClass, false) != null;
    }

    /**
     * 是否存在指定类型或指定类型的子类型的Provider
     */
    public boolean containsAssignable(Class<?> providerClass) {
        return providerClass != null && this.getProviderDescriptor(providerClass, true) != null;
    }

    /**
     * 注册一个类型到管理器
     */
    public ProviderManager<T> register(Class<? extends T> providerClass) {
        if (!this.contains(providerClass)) {
            SortedSet<ProviderDescriptor<T>> descriptors = this.getProviderDescriptors();
            synchronized (descriptors) {
                descriptors.add(this.buildProviderDescriptor(null, providerClass));
            }
        }
        return this;
    }

    /**
     * 获取defaultName对应服务提供者, 如果不存在defaultName则返回空Optional
     */
    public Optional<T> getDefault() {
        if (StringUtil.isBlank(defaultName)) {
            return Optional.empty();
        }
        return this.get(defaultName);
    }

    /**
     * 根据服务标识获取服务提供者
     */
    public Optional<T> get(String name) {
        Assert.notBlank(name, "name must be not blank");

        // 获取提供者描述
        ProviderDescriptor<T> descriptor = this.getProviderDescriptor(name);
        if (descriptor == null) {
            // name没有对应的服务提供者
            return Optional.empty();
        }

        return Optional.ofNullable(this.get(descriptor));
    }

    /**
     * 获取指定类型的服务提供者, 非多态, 精准匹配获取providerClass == provider.getClass()
     */
    public Optional<T> get(Class<? extends T> providerClass) {
        if (providerClass == null) {
            return Optional.empty();
        }

        // 获取提供者描述
        ProviderDescriptor<T> descriptor = this.getProviderDescriptor(providerClass, false);
        if (descriptor == null) {
            // providerClass没有对应的服务提供者
            return Optional.empty();
        }

        return Optional.ofNullable(this.get(descriptor));
    }

    /**
     * 获取指定类型的服务提供者, 先进行精准匹配获取providerClass == provider.getClass(), 如果未匹配到则尝试寻找providerClass子类型
     */
    public Optional<T> getAssignable(Class<? extends T> providerClass) {
        if (providerClass == null) {
            return Optional.empty();
        }

        // 精准匹配
        ProviderDescriptor<T> descriptor = this.getProviderDescriptor(providerClass, false);
        if (descriptor == null) {
            // 多态匹配
            descriptor = this.getProviderDescriptor(providerClass, true);
        }
        if (descriptor == null) {
            // 没有providerClass对应的服务提供者
            return Optional.empty();
        }

        return Optional.ofNullable(this.get(descriptor));
    }

    /**
     * 获取可用的服务提供者, 首先按照defaultName寻找, 如果没有找到则优先级从高到低寻找服务提供者
     */
    public Optional<T> get() {
        Optional<T> optional = this.getDefault();
        if (optional.isPresent()) {
            return optional;
        }

        for (ProviderDescriptor<T> descriptor : this.getProviderDescriptors()) {
            T provider = this.get(descriptor);
            if (provider != null) {
                return Optional.of(provider);
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private T get(ProviderDescriptor<T> descriptor) {
        String providerName = descriptor.getProviderName();
        Class<? extends T> providerClass = descriptor.getProviderClass();

        // 获取缓存
        Holder<T> holder = providerHolderMap.computeIfAbsent(descriptor, key -> new Holder<>());
        if (holder.isPresent()) {
            return holder.get();
        }

        // 检查是否已创建失败
        Holder<Throwable> errorHolder = createProviderErrorHolderMap.computeIfAbsent(providerName, key -> new Holder<>());
        checkedThrowableHolder(errorHolder);

        T creating = (T) IN_CREATING_PROVIDER_MAP.get(providerClass);
        if (creating != null) {
            // 引用对象循环依赖
            return creating;
        }

        if (holder.isAbsent()) {
            synchronized (holder) {
                if (holder.isAbsent()) {
                    checkedThrowableHolder(errorHolder);

                    try {
                        T provider = this.createProvider(descriptor);
                        if (provider != null) {
                            holder.set(provider);
                        }
                    } catch (Throwable e) {
                        errorHolder.set(e);
                        checkedThrowableHolder(errorHolder);
                    }
                }
            }
        }

        return holder.get();
    }

    /**
     * 检查是否存在异常
     */
    private void checkedThrowableHolder(Holder<Throwable> errorHolder) {
        Throwable throwable;
        if (errorHolder == null || (throwable = errorHolder.get()) == null) {
            return;
        }
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        throw new IllegalStateException(throwable.getMessage(), throwable);
    }

    /**
     * 获取服务提供者描述
     */
    private ProviderDescriptor<T> getProviderDescriptor(String name) {
        return this.getProviderDescriptors().stream()
                .filter(d -> Objects.equals(name, d.getProviderName()))
                .findFirst().orElse(null);
    }

    /**
     * 获取服务提供者描述
     */
    private ProviderDescriptor<T> getProviderDescriptor(Class<?> providerClass, boolean containsAssignable) {
        if (!containsAssignable) {
            return this.getProviderDescriptors().stream()
                    .filter(d -> Objects.equals(providerClass, d.getProviderClass()))
                    .findFirst().orElse(null);
        }

        return this.getProviderDescriptors().stream()
                .filter(d -> providerClass.isAssignableFrom(d.getProviderClass()))
                // providerClass是d.clazz的超类那么检索d.clazz的所有类肯定能找到providerClass
                .min(Comparator.comparing(d -> Optional.ofNullable(ReflectUtil.getLevel(d.getProviderClass(), providerClass)).orElse(Integer.MAX_VALUE))).orElse(null);
    }

    /**
     * 获取服务提供者描述集
     */
    public SortedSet<ProviderDescriptor<T>> getProviderDescriptors() {
        if (descriptorsHolder.get() == null) {
            synchronized (descriptorsHolder) {
                if (descriptorsHolder.get() == null) {
                    descriptorsHolder.set(this.loadProviderDescriptors());
                }
            }
        }
        return descriptorsHolder.get();
    }

    /**
     * 加载当前服务类型的所有提供者描述信息, 返回可变的有序集合
     */
    private SortedSet<ProviderDescriptor<T>> loadProviderDescriptors() {
        ClassLoader classLoader = getClassLoader();

        SortedSet<ProviderDescriptor<T>> descriptorSet = new TreeSet<>();
        Map<String, Iterable<List<Map.Entry<String, String>>>> propertiesMap = PropertiesUtil.readAsList(classLoader, SPI_DIRECTORY + providerClass.getName(), JDK_SPI_DIRECTORY + providerClass.getName());
        if (!propertiesMap.isEmpty()) {
            for (Iterable<List<Map.Entry<String, String>>> iterable : propertiesMap.values()) {
                // 每个path对应所有资源文件
                for (List<Map.Entry<String, String>> properties : iterable) {
                    // 每个资源文件的配置参数
                    this.loadProviderDescriptor(descriptorSet, classLoader, properties);
                }
            }
        }
        return descriptorSet;
    }

    /**
     * 加载资源文件中服务提供者描述信息
     */
    private void loadProviderDescriptor(Set<ProviderDescriptor<T>> descriptors, ClassLoader classLoader, List<Map.Entry<String, String>> properties) {
        if (Safes.isEmpty(properties)) {
            return;
        }

        for (Map.Entry<String, String> entry : properties) {
            String name = entry.getKey();
            String className = entry.getValue();

            if (StringUtil.isNotBlank(className) || name.contains(",")) {
                // name=className或className,className...格式

                // 截取,分割的类全限定名称
                for (int start = 0; ; ) {
                    int pos = name.indexOf(',', start);
                    String sub;
                    if (pos < 0) {
                        // 取最后一部分
                        sub = name.substring(start);
                    } else {
                        sub = name.substring(start, pos);
                    }
                    if (StringUtil.isNotBlank(sub)) {
                        descriptors.add(this.buildProviderDescriptor(classLoader, CommonConstant.STRING_EMPTY, sub));
                    }
                    if (pos < 0) {
                        break;
                    }
                    start = pos + 1;
                }
                continue;
            }

            // name为类全限定名称格式的配置
            descriptors.add(this.buildProviderDescriptor(classLoader, name, className));
        }
    }

    /**
     * 提供者描述信息
     *
     * @param classLoader
     * @param name        服务提供者标识
     * @param className   服务提供者全限定名称
     * @return
     */
    @SuppressWarnings("unchecked")
    private ProviderDescriptor<T> buildProviderDescriptor(ClassLoader classLoader, String name, String className) {
        if (StringUtil.isBlank(className)) {
            // 当className为空代表没有配置服务提供者类型全限定名称
            className = name;
            name = CommonConstant.STRING_EMPTY;
        }

        Class<? extends T> clazz;
        try {
            clazz = (Class<? extends T>) Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("class " + className + " not found", e);
        }

        return this.buildProviderDescriptor(name, clazz);
    }

    /**
     * 构建提供者描述信息
     *
     * @param name  服务提供者标识
     * @param clazz 服务提供者类型
     * @return
     */
    private ProviderDescriptor<T> buildProviderDescriptor(String name, Class<? extends T> clazz) {
        Assert.isTrue(providerClass.isAssignableFrom(clazz), "class {} is not subtype of {}", clazz, providerClass.getName());

        int modifiers = clazz.getModifiers();
        Assert.isTrue(!Modifier.isInterface(modifiers), "class {} is interface", clazz);
        Assert.isTrue(!Modifier.isAbstract(modifiers), "class {} is abstract", clazz);

        // 默认优先级
        int priority = Integer.MAX_VALUE;
        // 指定的依赖注入工厂
        Class<? extends ProviderFactory> providerFactoryClass = null;
        // 当指定的依赖注入工厂未找到时是否检索其他依赖注入工厂
        Boolean lookupOther = null;
        Provider annotation = AnnotationUtil.findAnnotation(clazz, Provider.class);
        if (annotation != null) {
            String annName = annotation.name();
            if (StringUtil.isNotBlank(annName)) {
                Assert.isTrue(NAME_PATTERN.matcher(annName).matches(), "{} @Provider name is illega", clazz.getName());
                if (StringUtil.isNotBlank(name)) {
                    LOGGER.info("provider {} service name from {} to {}", clazz, name, annName);
                }
                name = annName;
            }
            int annPriority = annotation.priority();
            if (annPriority >= 0) {
                priority = annPriority;
            }
            providerFactoryClass = annotation.providerFactory();
            lookupOther = annotation.searchOther();
        }

        if (StringUtil.isBlank(name)) {
            // 未配置name也未配置注解name
            name = extractServiceName(clazz);
        }

        return ProviderDescriptor.of(clazz, name, priority, providerFactoryClass, lookupOther);
    }

    /**
     * 获取类加载器
     */
    private ClassLoader getClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            return classLoader;
        }
        return this.getClass().getClassLoader();
    }

    /**
     * 根据服务标志创建服务提供者
     */
    @SuppressWarnings("unchecked")
    private T createProvider(ProviderDescriptor<T> descriptor) {
        Class<? extends T> providerClass = descriptor.getProviderClass();

        Assert.isTrue(!Modifier.isAbstract(providerClass.getModifiers()), "class {} is abstract", providerClass.getName());

        T provider = this.newInstance(providerClass);
        // 放入缓存, 防止依赖注入循环引用
        IN_CREATING_PROVIDER_MAP.put(providerClass, provider);
        try {
            // 依赖注入@Resource
            injectProvider(provider, descriptor);
            // 执行@PostConstruct
            initializingProvider(provider);
            // 注册执行@PreDestroy钩子
            registerPreDestroyHook(provider);
        } finally {
            IN_CREATING_PROVIDER_MAP.remove(providerClass);
        }

        return provider;
    }

    /**
     * 创建实例
     */
    private T newInstance(Class<? extends T> providerClass) {
        // TODO 目前只实现无参构造方法创建
        return ReflectUtil.newInstance(providerClass);
    }

    /**
     * 获取ProviderFactory
     */
    private ProviderFactory getProviderFactory(ProviderDescriptor<T> descriptor) {
        if (injectProviderFactoryHolder == null) {
            return null;
        }

        // 不能再构造方法直接赋值, 会出现类似: LoggerContext -> ProviderFactory -> LoggerContext, 形成PROVIDER_MANAGER_MAP.computeIfAbsent()死锁
        if (injectProviderFactoryHolder.get() == null) {
            synchronized (injectProviderFactoryHolder) {
                if (injectProviderFactoryHolder.get() == null) {
                    injectProviderFactoryHolder.set(AdaptProviderFactory.of(providerFactoryClass, lookupOther, null));
                }
            }
        }

        if (descriptor.getProviderFactoryClass() == null || descriptor.getProviderFactoryClass() == ProviderFactory.class) {
            // 未指定
            return injectProviderFactoryHolder.get();
        }

        // 具体服务提供者指定的依赖注入工厂
        return AdaptProviderFactory.of(descriptor.getProviderFactoryClass(), descriptor.getLookupOther(), injectProviderFactoryHolder.get());
    }

    /**
     * 依赖注入
     */
    private void injectProvider(T provider, ProviderDescriptor<T> descriptor) {
        ProviderFactory injectProviderFactory;
        if (provider == null || (injectProviderFactory = getProviderFactory(descriptor)) == null) {
            return;
        }

        // 属性依赖注入
        injectProviderByField(provider, injectProviderFactory, descriptor.getProviderClass());
        // 方法依赖注入
        injectProviderByMethod(provider, injectProviderFactory, descriptor.getProviderClass());
    }

    /**
     * 获取服务名称
     */
    private String getResourceName(AnnotatedElement type) {
        Resource annotation = AnnotationUtil.findAnnotation(type, Resource.class);
        if (annotation != null) {
            if (StringUtil.isNotBlank(annotation.name())) {
                return annotation.name();
            }
        }
        return this.extractServiceName(type);
    }

    /**
     * 提取服务名称
     */
    private String extractServiceName(AnnotatedElement type) {
        if (nameExtractorHolder.get() == null) {
            synchronized (nameExtractorHolder) {
                if (nameExtractorHolder.get() == null) {
                    nameExtractorHolder.set(ProviderManager.load(ProviderNameExtractor.class).get().orElse(DEFAULT_NAME_EXTRACTOR));
                }
            }
        }
        ProviderNameExtractor extractor = nameExtractorHolder.get();
        if (type instanceof Class) {
            return extractor.extract((Class<?>) type);
        }
        if (type instanceof Field) {
            return extractor.extract((Field) type);
        }
        if (type instanceof Method) {
            return extractor.extract((Method) type);
        }
        return null;
    }

    /**
     * 属性依赖注入
     */
    private void injectProviderByField(T provider, ProviderFactory injectProviderFactory, Class<?> providerClass) {
        List<Field> fields = ReflectUtil.findFields(providerClass, field -> field.isAnnotationPresent(Resource.class));

        for (Field injectField : fields) {
            if (Modifier.isFinal(injectField.getModifiers())) {
                throw new IllegalStateException(injectField.getName() + " is final");
            }

            TypeResolver reference = TypeResolverUtil.resolverActualType(injectField);
            String resourceName = this.getResourceName(injectField);

            // 通过工厂获取实例
            Object value = injectProviderFactory.getProvider(reference, resourceName).orElse(null);
            if (value == null) {
                throw new IllegalStateException("Failed inject: dependency resource: " + resourceName + " not found");
            }

            // 依赖注入
            try {
                ReflectUtil.setValue(provider, injectField, value);
            } catch (Exception e) {
                throw new IllegalStateException("Failed inject: ", e);
            }
        }
    }

    /**
     * 方法依赖注入
     */
    private void injectProviderByMethod(T provider, ProviderFactory injectProviderFactory, Class<?> providerClass) {
        List<Method> methods = ReflectUtil.findConcreteMethods(providerClass, method -> method.getParameterCount() == 1 &&
                AnnotationUtil.findAnnotation(method, Resource.class) != null);

        for (Method injectMethod : methods) {
            TypeResolver reference = TypeResolverUtil.resolverActualParamType(injectMethod);
            String resourceName = this.getResourceName(injectMethod);

            // 通过工厂获取实例
            Object value = injectProviderFactory.getProvider(reference, resourceName).orElse(null);
            if (value == null) {
                throw new IllegalStateException("Failed inject: dependency resource: " + resourceName + " not found");
            }

            // 依赖注入
            try {
                ReflectUtil.invoke(provider, injectMethod, value);
            } catch (Exception e) {
                throw new IllegalStateException("Failed inject: ", e);
            }
        }
    }

    /**
     * 初始化
     */
    private void initializingProvider(T provider) {
        if (provider == null) {
            return;
        }

        List<Method> methods = ReflectUtil.findConcreteMethods(provider.getClass(), method -> method.getParameterCount() == 0 && method.isAnnotationPresent(PostConstruct.class));
        for (Method initMethod : methods) {
            try {
                ReflectUtil.invoke(provider, initMethod);
            } catch (Exception e) {
                throw new IllegalStateException("Failed initial: ", e);
            }
        }
    }

    /**
     * 注册销毁钩子
     */
    private void registerPreDestroyHook(T provider) {
        if (provider == null) {
            return;
        }

        List<Method> methods = ReflectUtil.findConcreteMethods(provider.getClass(), method -> method.getParameterCount() == 0 && method.isAnnotationPresent(PreDestroy.class));
        if (Safes.isEmpty(methods)) {
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Method initMethod : methods) {
                try {
                    ReflectUtil.invoke(provider, initMethod);
                } catch (Exception e) {
                    LOGGER.error("Failed destroy: {} {}", initMethod, e.toString());
                }
            }
        }));
    }

    /**
     * 服务提供者管理器当前实例的视图
     */
    private static class ProviderInstanceView<T> extends AbstractSet<Map.Entry<String, T>> {
        private final ProviderManager<T> providerManager;

        private ProviderInstanceView(ProviderManager<T> providerManager) {
            this.providerManager = providerManager;
        }

        @Override
        public int size() {
            return providerManager.providerHolderMap.size();
        }

        @Override
        public Iterator<Map.Entry<String, T>> iterator() {
            return IterableUtil.transform(providerManager.providerHolderMap.entrySet().iterator(), entry -> LazyPair.of(entry.getKey().getProviderName(), entry.getValue()::get));
        }
    }

    @Override
    public Iterator<Map.Entry<ProviderDescriptor<T>, T>> iterator() {
        return new Iterator<Map.Entry<ProviderDescriptor<T>, T>>() {
            private final Iterator<ProviderDescriptor<T>> iterator = Safes.of(getProviderDescriptors()).iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Map.Entry<ProviderDescriptor<T>, T> next() {
                if (this.hasNext()) {
                    ProviderDescriptor<T> descriptor = iterator.next();
                    return LazyPair.of(descriptor, (Function<ProviderDescriptor<T>, T>) ProviderManager.this::get);
                }
                throw new NoSuchElementException();
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProviderManager<?> that = (ProviderManager<?>) o;
        return Objects.equals(providerClass, that.providerClass) &&
                Objects.equals(defaultName, that.defaultName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerClass, defaultName);
    }

    @Override
    public String toString() {
        return "ProviderManager{" +
                "providerClass=" + providerClass +
                ", defaultName='" + defaultName + '\'' +
                ", providerFactoryClass=" + providerFactoryClass +
                ", lookupOther=" + lookupOther +
                ", injectProviderFactoryHolder=" + injectProviderFactoryHolder +
                ", descriptorsHolder=" + descriptorsHolder +
                ", providerHolderMap=" + providerHolderMap +
                ", createProviderErrorHolderMap=" + createProviderErrorHolderMap +
                ", entrySetHolder=" + entrySetHolder +
                '}';
    }
}
