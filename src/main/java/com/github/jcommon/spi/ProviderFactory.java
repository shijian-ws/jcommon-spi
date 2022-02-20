package com.github.jcommon.spi;

import com.github.jcommon.type.TypeResolver;

import java.util.Optional;

/**
 * 服务提供者依赖注入工厂
 *
 * @author shijian
 * @email shijianws@163.com
 * @date 2019-11-10
 */
public interface ProviderFactory {
    /**
     * 根据类型与名称获取Provider, 未获取到应该返回Optional.value=null, 即使获取一组只要未获取到都应该返回Optional.value=null
     */
    <T> Optional<T> getProvider(TypeResolver reference, String name);
}
