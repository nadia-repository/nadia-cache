package com.nadia.cache.cache.caffeine;

import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.nadia.cache.setting.FirstCacheSetting;
import com.nadia.cache.support.ExpireMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;

import java.util.concurrent.Callable;

@Slf4j
public class CaffeineCache extends AbstractValueAdaptingCache {
    private String name;

    private final Cache<Object, Object> cache;


    public CaffeineCache(String name, FirstCacheSetting firstCacheSetting) {
        super(false);
        this.name = name;
        this.cache = getCache(firstCacheSetting);
    }

    private Cache<Object, Object> getCache(FirstCacheSetting firstCacheSetting) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        builder.initialCapacity(firstCacheSetting.getInitialCapacity());
        builder.maximumSize(firstCacheSetting.getMaximumSize());
        if (ExpireMode.WRITE.equals(firstCacheSetting.getExpireMode())) {
            builder.expireAfterWrite(firstCacheSetting.getExpireTime(), firstCacheSetting.getTimeUnit());
        } else if (ExpireMode.ACCESS.equals(firstCacheSetting.getExpireMode())) {
            builder.expireAfterAccess(firstCacheSetting.getExpireTime(), firstCacheSetting.getTimeUnit());
        }
        return builder.build();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Cache<Object, Object> getNativeCache() {
        return this.cache;
    }

    @Override
    public ValueWrapper get(Object key) {
        log.debug("CaffeineCache.get key={}", JSON.toJSONString(key));
        return toValueWrapper(lookup(key));
    }

    @Override
    public Object lookup(Object key) {
        if (this.cache instanceof LoadingCache) {
            return ((LoadingCache<Object, Object>) this.cache).get(key);
        }
        return cache.getIfPresent(key);
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        log.debug("CaffeineCache.get cassable key={}", JSON.toJSONString(key));

        Object result = this.cache.get(key, (k) -> loaderValue(key, valueLoader));
        // 如果不允许存NULL值 直接删除NULL值缓存
        boolean isEvict = !isAllowNullValues() && (result == null || result instanceof NullValue);
        if (isEvict) {
            evict(key);
        }
        return (T) fromStoreValue(result);
    }

    @Override
    public void put(Object key, Object value) {
        // 允许存NULL值
        if (isAllowNullValues() ||
                (value != null && value instanceof NullValue)) {
            log.debug("CaffeineCache.put key={} ，value：{}", JSON.toJSONString(key), JSON.toJSONString(value));
            this.cache.put(key, toStoreValue(value));
            return;
        }
        log.debug("CaffeineCache.put allowNullValues is false and value is null");
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        log.debug("CaffeineCache.putIfAbsent key={} ，value：{}", JSON.toJSONString(key), JSON.toJSONString(value));
        boolean flag = !isAllowNullValues() && (value == null || value instanceof NullValue);
        if (flag) {
            return null;
        }
        Object result = this.cache.get(key, k -> toStoreValue(value));
        return toValueWrapper(fromStoreValue(result));
    }

    @Override
    public void evict(Object key) {
        log.debug("CaffeineCache.evict key={}", JSON.toJSONString(key));
        this.cache.invalidate(key);
    }

    @Override
    public void clear() {
        log.debug("CaffeineCache.clear");
        this.cache.invalidateAll();
    }

    private <T> Object loaderValue(Object key, Callable<T> valueLoader) {
        log.debug("CaffeineCache.loaderValue key={}", JSON.toJSONString(key));
        try {
            T t = valueLoader.call();
            return toStoreValue(t);
        } catch (Exception e) {
            throw new RuntimeException(JSON.toJSONString(key),e);
        }
    }
}
