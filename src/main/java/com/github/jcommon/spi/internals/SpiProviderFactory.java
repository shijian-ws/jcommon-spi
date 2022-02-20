package com.github.jcommon.spi.internals;

import com.github.jcommon.collect.support.CollectionFactory;
import com.github.jcommon.spi.support.ProviderDescriptor;
import com.github.jcommon.logger.Logger;
import com.github.jcommon.logger.support.LoggerFactory;
import com.github.jcommon.spi.support.ProviderManager;
import com.github.jcommon.spi.ProviderFactory;
import com.github.jcommon.collect.CollectionBean;
import com.github.jcommon.type.TypeResolver;
import com.github.jcommon.util.Safes;
import com.github.jcommon.util.StringUtil;

import java.util.Map;
import java.util.Optional;

/**
 * 从SPI加载服务提供者
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2019-11-10
 */
public class SpiProviderFactory implements ProviderFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpiProviderFactory.class);

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getProvider(TypeResolver reference, String name) {
        if (reference == null || Safes.isEmpty(reference.getElementTypes())) {
            return Optional.empty();
        }

        Class<T> elementClass = reference.getElementClass();
        Class<?> containerType = reference.getContainerType();
        if (containerType == null) {
            return getProvider(elementClass, name);
        }
        if (!CollectionFactory.isSupportType(containerType)) {
            // 不支持容器, 可能为具体类型
            return getProvider((Class<T>) containerType, name);
        }

        // 处理集合
        CollectionBean collectionBean = CollectionFactory.of(containerType);
        if (collectionBean == null) {
            LOGGER.error("无法创建{}类型集合容器, 跳过获取服务提供者", containerType);
            return Optional.empty();
        }

        ProviderManager<T> providerManager = ProviderManager.load(elementClass);
        if (StringUtil.isNotBlank(name)) {
            // 按照name寻找
            providerManager.get(name).ifPresent(provider -> collectionBean.add(name, provider));
        } else {
            // 所有
            for (Map.Entry<ProviderDescriptor<T>, T> entry : providerManager) {
                T value;
                if (entry == null || (value = entry.getValue()) == null) {
                    continue;
                }
                collectionBean.add(entry.getKey(), value);
            }
        }
        if (collectionBean.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(collectionBean.getCollection());
    }

    /**
     * 单一对象
     */
    private <T> Optional<T> getProvider(Class<T> type, String name) {
        ProviderManager<T> providerManager = ProviderManager.load(type);
        if (StringUtil.isNotBlank(name)) {
            // 按照name寻找
            Optional<T> optional = providerManager.get(name);
            if (optional.isPresent()) {
                return optional;
            }
        }

        // 如果未找到则找defaultName或优先级最高的
        return providerManager.get();
    }
}
