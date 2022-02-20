package com.github.jcommon.spi;


import java.beans.Introspector;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Provider名称提取接口
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2021-01-23
 */
public interface ProviderNameExtractor {
    ProviderNameExtractor DEFAULT = new ProviderNameExtractor() {};

    /**
     * 根据Class返回服务名称
     */
    default String extract(Class<?> clazz) {
        return decapitalize(clazz.getSimpleName());
    }

    /**
     * 根据Field返回服务名称
     */
    default String extract(Field field) {
        return decapitalize(field.getName());
    }

    /**
     * 根据JavaBean属性规则返回服务名称
     */
    default String extract(Method method) {
        String methodName = method.getName();
        if (methodName.length() > 3) {
            // setter getter
            if (methodName.startsWith("set") || methodName.startsWith("get")) {
                methodName = methodName.substring(3);
            }
        }
        return decapitalize(methodName);
    }

    /**
     * 使用JavaBean规则转换服务名称
     */
    static String decapitalize(String name) {
        return Introspector.decapitalize(name);
    }
}
