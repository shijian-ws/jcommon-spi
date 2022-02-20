package com.github.jcommon.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provider配置
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2019-11-16
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Provider {
    /**
     * 服务标识name, 如果指定则会覆盖META-INF/spi/配置的服务名称
     */
    String name() default "";

    /**
     * 实例优先级, 数值越小优先级越高
     */
    int priority() default Integer.MAX_VALUE;

    /**
     * 指定当前Provider依赖对象的获取方式
     */
    Class<? extends ProviderFactory> providerFactory() default ProviderFactory.class;

    /**
     * 当指定{@link Provider#providerFactory()}的ProviderFactory未找到时
     * 是否从根据{@link SPI#providerFactory(),SPI#lookupOther()}的配置继续查找
     * 未配置注解Provider或未指定{@link Provider#providerFactory()}则根据接口是否配置注解SPI来确定怎么从ProviderFactory查找
     */
    boolean searchOther() default false;
}
