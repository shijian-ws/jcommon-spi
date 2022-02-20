package com.github.jcommon.spi;


import com.github.jcommon.spi.support.ProviderManager;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SPI配置
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2019-11-16
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface SPI {
    /**
     * 默认服务提供者name标识
     */
    String defaultName() default "";

    /**
     * 指定当前SPI实现的依赖对象的获取方式
     */
    Class<? extends ProviderFactory> providerFactory() default ProviderFactory.class;

    /**
     * 当指定或默认{@link SPI#providerFactory(), ProviderManager <ProviderFactory>#get()}的ProviderFactory未找到时
     * 是否从其他{@link ProviderManager<ProviderFactory>#iterator()}的ProviderFactory查找
     * 未配置注解或默认则不检索其他ProviderFactory
     */
    boolean lookupOther() default false;
}
