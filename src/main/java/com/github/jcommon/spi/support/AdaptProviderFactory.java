package com.github.jcommon.spi.support;

import com.github.jcommon.spi.ProviderFactory;
import com.github.jcommon.type.TypeResolver;
import com.github.jcommon.util.IterableUtil;

import java.util.Map;
import java.util.Optional;

/**
 * 服务提供者工厂适配
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2021-01-30
 */
public class AdaptProviderFactory implements ProviderFactory {
    private final Class<? extends ProviderFactory> providerFactoryClass;
    private final boolean lookupOther;
    private final Iterable<ProviderFactory> providerFactories;

    private AdaptProviderFactory(Class<? extends ProviderFactory> providerFactoryClass, boolean lookupOther, ProviderFactory reference) {
        this.providerFactoryClass = providerFactoryClass;
        this.lookupOther = lookupOther;

        ProviderManager<ProviderFactory> providerManager = ProviderManager.load(ProviderFactory.class);
        // 获取设置服务提供者工厂
        ProviderFactory allocateProviderFactory = providerFactoryClass == null || providerFactoryClass == ProviderFactory.class ? null : providerManager.register(providerFactoryClass).get(providerFactoryClass).orElse(null);
        if (!this.lookupOther) {
            // 不检索其他
            if (allocateProviderFactory == null) {
                // 也未设置, 获取一个默认的
                this.providerFactories = IterableUtil.asIterable(() -> providerManager.get().orElse(null));
                return;
            }
            // 已设置
            this.providerFactories = IterableUtil.asIterable(allocateProviderFactory);
            return;
        }

        // 检索其他
        if (allocateProviderFactory == null) {
            // 也未设置
            if (reference == null) {
                // 也未引用, 使用管理器
                this.providerFactories = IterableUtil.transform(providerManager, Map.Entry::getValue);
                return;
            }
            // 存在引用则使用指定引用检索
            this.providerFactories = IterableUtil.asIterable(reference);
            return;
        }

        // 已设置
        if (reference == null) {
            // 未引用, 使用管理器
            this.providerFactories = IterableUtil.concat(IterableUtil.asIterable(allocateProviderFactory), IterableUtil.transform(providerManager, Map.Entry::getValue));
            return;
        }
        // 存在引用则使用指定引用检索
        this.providerFactories = IterableUtil.concat(IterableUtil.asIterable(allocateProviderFactory), IterableUtil.asIterable(reference));
    }

    @Override
    public <T> Optional<T> getProvider(TypeResolver reference, String providerName) {
        for (ProviderFactory factory : providerFactories) {
            if (factory == null) {
                continue;
            }
            Optional<T> optional = factory.getProvider(reference, providerName);
            if (optional.isPresent()) {
                return optional;
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "AdaptProviderFactory{" +
                "providerFactoryClass=" + providerFactoryClass +
                ", lookupOther=" + lookupOther +
                ", providerFactories=" + providerFactories +
                '}';
    }

    public static ProviderFactory of(Class<? extends ProviderFactory> providerFactoryClass, Boolean lookupOther, ProviderFactory reference) {
        return new AdaptProviderFactory(providerFactoryClass, Boolean.TRUE.equals(lookupOther), reference);
    }
}
