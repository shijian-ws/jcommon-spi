package com.github.jcommon.spi.internals;

import com.github.jcommon.collect.support.CollectionFactory;
import com.github.jcommon.logger.Logger;
import com.github.jcommon.logger.support.LoggerFactory;
import com.github.jcommon.spi.ProviderFactory;
import com.github.jcommon.collect.CollectionBean;
import com.github.jcommon.type.TypeResolver;
import com.github.jcommon.util.StringUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 从Spring容器加载服务提供者
 * 1, 需要在META-INF/spi/com.github.jcommon.spi.factory.ProviderFactory配置com.github.jcommon.spi.factory.internals.SpringProviderFactory
 * 2, 在SpringBoot环境中因为配置了META-INF/spring.factories会自动注入
 * 其他环境需要手动调用SpringProviderFactory#addBeanFactory()添加BeanFactory
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2019-11-10
 */
@Configuration
public class SpringProviderFactory implements ProviderFactory, BeanFactoryPostProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringProviderFactory.class);

    /**
     * BeanFactory实例
     */
    private static final Set<BeanFactory> BEAN_FACTORY_SET = new CopyOnWriteArraySet<>();

    /**
     * 添加一个BeanFactory
     */
    public static boolean addBeanFactory(BeanFactory beanFactory) {
        return beanFactory != null && BEAN_FACTORY_SET.add(beanFactory);
    }

    /**
     * 移除一个BeanFactory
     */
    public static boolean removeBeanFactory(BeanFactory beanFactory) {
        return !BEAN_FACTORY_SET.isEmpty() && BEAN_FACTORY_SET.remove(beanFactory);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        addBeanFactory(beanFactory);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getBean(BeanFactory beanFactory, String name, TypeResolver reference) {
        Class<?> containerType;
        if (reference == null || (containerType = reference.getContainerType()) == null) {
            return getBeanAsRaw(beanFactory, name, reference == null ? null : reference.getElementClass());
        }
        if (!CollectionFactory.isSupportType(containerType)) {
            return getBeanAsRaw(beanFactory, name, (Class<T>) containerType);
        }
        // 集合处理
        CollectionBean collectionBean = CollectionFactory.of(containerType);
        if (collectionBean == null) {
            LOGGER.error("无法创建{}类型集合容器, 跳过获取服务提供者", containerType);
            return null;
        }

        Class<T> elementClass = reference.getElementClass();
        if (StringUtil.isBlank(name) && beanFactory instanceof ListableBeanFactory) {
            ListableBeanFactory listableBeanFactory = (ListableBeanFactory) beanFactory;
            Map<String, T> beanMap = BeanFactoryUtils.beansOfTypeIncludingAncestors(listableBeanFactory, elementClass);
            for (Map.Entry<String, T> entry : beanMap.entrySet()) {
                collectionBean.add(entry.getKey(), entry.getValue());
            }
        } else {
            // 单一查找
            collectionBean.add(name, getBeanAsRaw(beanFactory, name, elementClass));
        }
        if (collectionBean.isEmpty()) {
            return null;
        }
        return collectionBean.getCollection();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getBeanAsRaw(BeanFactory beanFactory, String name, Class<T> type) {
        if (type != null) {
            if (StringUtil.isNotBlank(name)) {
                // 按照name, type寻找
                return beanFactory.getBean(name, type);
            }
            // 没有name, 按照type寻找
            return beanFactory.getBean(type);
        }

        // 没有type, 按照name寻找
        return (T) beanFactory.getBean(name);
    }

    @Override
    public <T> Optional<T> getProvider(TypeResolver reference, String name) {
        if (StringUtil.isBlank(name)) {
            if (reference == null || reference.getElementTypes() == null) {
                return Optional.empty();
            }
        }
        if (BEAN_FACTORY_SET.isEmpty()) {
            return Optional.empty();
        }

        for (BeanFactory beanFactory : BEAN_FACTORY_SET) {
            T bean = getBean(beanFactory, name, reference);
            if (bean != null) {
                return Optional.of(bean);
            }
        }
        return Optional.empty();
    }
}
