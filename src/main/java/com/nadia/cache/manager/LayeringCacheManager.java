package com.nadia.cache.manager;

import com.nadia.cache.cache.LayeringCache;
import com.nadia.cache.cache.caffeine.CaffeineCache;
import com.nadia.cache.cache.redis.RedisCache;
import com.nadia.cache.listener.RedisMessageListener;
import com.nadia.cache.setting.LayeringCacheSetting;
import com.nadia.cache.util.SettingUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class LayeringCacheManager implements CacheManager, InitializingBean, DisposableBean, BeanNameAware, SmartLifecycle {

    private RedisTemplate<Object, Object> redisTemplate;

    private static Set<LayeringCacheManager> cacheManagers = new LinkedHashSet<>();

    private final ConcurrentMap<String, ConcurrentMap<String, Cache>> cacheContainer = new ConcurrentHashMap<>(16);

    private volatile Set<String> cacheNames = new LinkedHashSet<>();

    private final RedisMessageListenerContainer container = new RedisMessageListenerContainer();

    private final RedisMessageListener messageListener = new RedisMessageListener();


    public LayeringCacheManager(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        cacheManagers.add(this);
    }

    @Override
    public Cache getCache(String name) {
        LayeringCacheSetting setting = SettingUtils.getSetting(name);
        return getCache(name,setting);
    }

    public Collection<Cache> getCaches(String name) {
        ConcurrentMap<String, Cache> cacheMap = this.cacheContainer.get(name);
        if (CollectionUtils.isEmpty(cacheMap)) {
            return Collections.emptyList();
        }
        return cacheMap.values();
    }

    public Cache getCache(String name, LayeringCacheSetting layeringCacheSetting) {
        // 第一次获取缓存Cache，如果有直接返回,如果没有加锁往容器里里面放Cache
        ConcurrentMap<String, Cache> cacheMap = this.cacheContainer.get(name);
        if (!CollectionUtils.isEmpty(cacheMap)) {
            if (cacheMap.size() > 1) {
                log.warn("缓存名称为 {} 的缓存,存在两个不同的过期时间配置，请一定注意保证缓存的key唯一性，否则会出现缓存过期时间错乱的情况", name);
            }
            Cache cache = cacheMap.get(layeringCacheSetting.getInternalKey());
            if (cache != null) {
                return cache;
            }
        }

        // 第二次获取缓存Cache，加锁往容器里里面放Cache
        synchronized (this.cacheContainer) {
            cacheMap = this.cacheContainer.get(name);
            if (!CollectionUtils.isEmpty(cacheMap)) {
                // 从容器中获取缓存
                Cache cache = cacheMap.get(layeringCacheSetting.getInternalKey());
                if (cache != null) {
                    return cache;
                }
            } else {
                cacheMap = new ConcurrentHashMap<>(16);
                cacheContainer.put(name, cacheMap);
                // 更新缓存名称
                updateCacheNames(name);
                // 创建redis监听
                addMessageListener(name);
            }

            // 新建一个Cache对象
            Cache cache = getMissingCache(name, layeringCacheSetting);
            if (cache != null) {
                // 装饰Cache对象
                cache = decorateCache(cache);
                // 将新的Cache对象放到容器
                cacheMap.put(layeringCacheSetting.getInternalKey(), cache);
                if (cacheMap.size() > 1) {
                    log.warn("缓存名称为 {} 的缓存,存在两个不同的过期时间配置，请一定注意保证缓存的key唯一性，否则会出现缓存过期时间错乱的情况", name);
                }
            }

            return cache;
        }
    }

    private Cache getMissingCache(String name, LayeringCacheSetting layeringCacheSetting) {
        // 创建一级缓存
        CaffeineCache caffeineCache = new CaffeineCache(name, layeringCacheSetting.getFirstCacheSetting());
        // 创建二级缓存
        RedisCache redisCache = new RedisCache(name, redisTemplate, layeringCacheSetting.getSecondaryCacheSetting());
        return new LayeringCache(redisTemplate, caffeineCache, redisCache, layeringCacheSetting);
    }

    @Override
    public Collection<String> getCacheNames() {
        return this.cacheNames;
    }

    /**
     * 更新缓存名称容器
     *
     * @param name 需要添加的缓存名称
     */
    private void updateCacheNames(String name) {
        cacheNames.add(name);
    }


    /**
     * 获取Cache对象的装饰示例
     *
     * @param cache 需要添加到CacheManager的Cache实例
     * @return 装饰过后的Cache实例
     */
    protected Cache decorateCache(Cache cache) {
        return cache;
    }

    /**
     * 获取缓存容器
     *
     * @return 返回缓存容器
     */
    public ConcurrentMap<String, ConcurrentMap<String, Cache>> getCacheContainer() {
        return cacheContainer;
    }

    /**
     * 添加消息监听
     *
     * @param name 缓存名称
     */
    protected void addMessageListener(String name) {
        container.addMessageListener(messageListener, new ChannelTopic(name));
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        messageListener.setCacheManager(this);
        container.setConnectionFactory(getRedisTemplate().getConnectionFactory());
        container.afterPropertiesSet();
        messageListener.afterPropertiesSet();

    }

    public RedisTemplate<Object, Object> getRedisTemplate() {
        return redisTemplate;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public void setBeanName(String name) {
        container.setBeanName("redisMessageListenerContainer");
    }

    @Override
    public void destroy() throws Exception {
        container.destroy();
    }

    @Override
    public boolean isAutoStartup() {
        return container.isAutoStartup();
    }

    @Override
    public void stop(Runnable callback) {
        container.stop(callback);
    }

    @Override
    public void start() {
        container.start();
    }

    @Override
    public void stop() {
        container.stop();
    }

    @Override
    public boolean isRunning() {
        return container.isRunning();
    }

    @Override
    public int getPhase() {
        return container.getPhase();
    }
}
