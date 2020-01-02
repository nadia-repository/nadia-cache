package com.nadia.cache.util;

import com.nadia.cache.annotation.FirstCache;
import com.nadia.cache.annotation.LayeringCacheable;
import com.nadia.cache.annotation.SecondaryCache;
import com.nadia.cache.setting.FirstCacheSetting;
import com.nadia.cache.setting.LayeringCacheSetting;
import com.nadia.cache.setting.SecondaryCacheSetting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SettingUtils  implements BeanPostProcessor {

    public static Map<String, LayeringCacheSetting> layeringCacheSettingMap = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(bean.getClass());
        if (methods != null) {
            for (Method method : methods) {
                LayeringCacheable layeringCacheable = AnnotationUtils.findAnnotation(method, LayeringCacheable.class);
                if (null != layeringCacheable) {
                    FirstCache firstCache = layeringCacheable.firstCache();
                    SecondaryCache secondaryCache = layeringCacheable.secondaryCache();
                    FirstCacheSetting firstCacheSetting = new FirstCacheSetting(firstCache.initialCapacity(),firstCache.maximumSize(),firstCache.expireTime(),firstCache.timeUnit(),firstCache.expireMode());
                    SecondaryCacheSetting secondaryCacheSetting = new SecondaryCacheSetting(secondaryCache.expireTime(),secondaryCache.preloadTime(),secondaryCache.timeUnit(),secondaryCache.forceRefresh(),secondaryCache.isAllowNullValue(),secondaryCache.magnification());
                    LayeringCacheSetting layeringCacheSetting = new LayeringCacheSetting(firstCacheSetting,secondaryCacheSetting,layeringCacheable.cacheMode());
                    layeringCacheSettingMap.put(layeringCacheable.value()[0], layeringCacheSetting);
                }
            }
        }
        return bean;
    }

    public static LayeringCacheSetting getSetting(String name){
        return layeringCacheSettingMap.get(name);
    }
}
