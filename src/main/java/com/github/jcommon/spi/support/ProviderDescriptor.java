package com.github.jcommon.spi.support;

import com.github.jcommon.spi.ProviderFactory;

import java.util.Objects;

/**
 * 服务提供者描述模型
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2021-01-30
 */
public class ProviderDescriptor<T> implements Comparable<ProviderDescriptor<T>> {
    private final Class<? extends T> providerClass;
    private final String providerName;
    private final int priority;
    private final Class<? extends ProviderFactory> providerFactoryClass;
    private final boolean lookupOther;

    private ProviderDescriptor(Class<? extends T> providerClass, String providerName, int priority, Class<? extends ProviderFactory> providerFactoryClass, boolean lookupOther) {
        this.providerClass = providerClass;
        this.providerName = providerName;
        this.priority = priority;
        this.providerFactoryClass = providerFactoryClass;
        this.lookupOther = lookupOther;
    }

    @Override
    public int compareTo(ProviderDescriptor o) {
        if (o == null) {
            return 1;
        }

        if (priority != o.priority) {
            // priority越小, 优先级越高, 排序越靠前
            return priority - o.priority;
        }

        // 按照字母比较, 字母越靠前, 排序越靠前
        int remainder = providerName.compareTo(o.providerName);
        if (remainder != 0) {
            return remainder;
        }

        return hashCode() - o.hashCode();
    }

    public Class<? extends T> getProviderClass() {
        return providerClass;
    }

    public String getProviderName() {
        return providerName;
    }

    public int getPriority() {
        return priority;
    }

    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return providerFactoryClass;
    }

    public Boolean getLookupOther() {
        return lookupOther;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProviderDescriptor<?> that = (ProviderDescriptor<?>) o;
        return Objects.equals(providerClass, that.providerClass) && Objects.equals(providerName, that.providerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerClass, providerName);
    }

    @Override
    public String toString() {
        return "ProviderDescriptor{" +
                "providerClass=" + providerClass +
                ", providerName='" + providerName + '\'' +
                ", priority=" + priority +
                ", factory=" + providerFactoryClass +
                ", lookupOther=" + lookupOther +
                '}';
    }

    public static <T> ProviderDescriptor<T> of(Class<? extends T> providerClass, String providerName, int priority, Class<? extends ProviderFactory> providerFactoryClass, Boolean lookupOther) {
        return new ProviderDescriptor<>(providerClass, providerName, priority, providerFactoryClass, Boolean.TRUE.equals(lookupOther));
    }
}
