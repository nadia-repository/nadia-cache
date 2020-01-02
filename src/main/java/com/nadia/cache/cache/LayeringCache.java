package com.nadia.cache.cache;

import com.alibaba.fastjson.JSON;
import com.nadia.cache.listener.RedisPubSubMessage;
import com.nadia.cache.listener.RedisPubSubMessageType;
import com.nadia.cache.listener.RedisPublisher;
import com.nadia.cache.setting.LayeringCacheSetting;
import com.nadia.cache.support.CacheMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

import java.util.concurrent.Callable;

@Slf4j
public class LayeringCache extends AbstractValueAdaptingCache {

    private RedisTemplate<Object, Object> redisTemplate;

    private AbstractValueAdaptingCache firstCache;

    private AbstractValueAdaptingCache secondCache;

    private LayeringCacheSetting layeringCacheSetting;

    private String name;


    public LayeringCache(RedisTemplate<Object, Object> redisTemplate, AbstractValueAdaptingCache firstCache,
                         AbstractValueAdaptingCache secondCache, LayeringCacheSetting layeringCacheSetting) {
        this(redisTemplate, firstCache, secondCache, secondCache.getName(), layeringCacheSetting);
    }

    public LayeringCache(RedisTemplate<Object, Object> redisTemplate, AbstractValueAdaptingCache firstCache,
                         AbstractValueAdaptingCache secondCache, String name, LayeringCacheSetting layeringCacheSetting) {
        super(false);
        this.name = name;
        this.redisTemplate = redisTemplate;
        this.firstCache = firstCache;
        this.secondCache = secondCache;
        this.layeringCacheSetting = layeringCacheSetting;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public LayeringCache getNativeCache() {
        return this;
    }

    private boolean userFirstCache() {
        CacheMode cacheMode = layeringCacheSetting.getCacheMode();
        boolean userFirstCache = true;
        log.debug("userFirstCache.cacheMode={}", cacheMode);
        if (CacheMode.ALL.equals(cacheMode)) {
            userFirstCache = true;
        } else if (CacheMode.ONLY_FIRST.equals(cacheMode)) {
            userFirstCache = true;
        } else if (CacheMode.ONLY_SECOND.equals(cacheMode)) {
            userFirstCache = false;
        }
        log.debug("name={} userSecondCache={}", this.name, userFirstCache);
        return userFirstCache;
    }

    private boolean userSecondCache() {
        CacheMode cacheMode = layeringCacheSetting.getCacheMode();
        boolean userSecondCache = true;
        log.debug("userSecondCache.cacheMode={}", cacheMode);
        if (CacheMode.ALL.equals(cacheMode)) {
            userSecondCache = true;
        } else if (CacheMode.ONLY_FIRST.equals(cacheMode)) {
            userSecondCache = false;
        } else if (CacheMode.ONLY_SECOND.equals(cacheMode)) {
            userSecondCache = true;
        }
        log.debug("name={} userSecondCache={}", this.name, userSecondCache);
        return true;
    }

    @Override
    public ValueWrapper get(Object key) {
        return toValueWrapper(lookup(key));
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T result = null;
        if (this.userFirstCache()) {
            result = firstCache.get(key, type);
            log.debug("查询一级缓存。 key={},返回值是:{}", key, JSON.toJSONString(result));
            if (result != null) {
                return result;
            }
        }

        if (this.userSecondCache()) {
            result = secondCache.get(key, type);
            firstCache.putIfAbsent(key, result);
            log.debug("查询二级缓存,并将数据放到一级缓存。 key={},返回值是:{}", key, JSON.toJSONString(result));
            return result;
        }
        return result;
    }

    @Override
    protected Object lookup(Object key) {
        Object result = null;
        if (this.userFirstCache()) {
            result = firstCache.get(key);
            log.debug("查询一级缓存。 key={},返回值是:{}", key, JSON.toJSONString(result));
            if (result != null && ((ValueWrapper) result).get() != null) {
                return fromStoreValue(((ValueWrapper) result).get());
            }
        }
        if (this.userSecondCache()) {
            result = secondCache.get(key);
            result = result == null ? null : fromStoreValue(((ValueWrapper) result).get());
            if (result != null) {
                firstCache.putIfAbsent(key, result);
                log.debug("查询二级缓存,并将数据放到一级缓存。 key={},返回值是:{}", key, JSON.toJSONString(result));
            }
        }
        return result;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        T result = null;
        if (this.userFirstCache()) {
            result = firstCache.get(key, valueLoader);
            log.debug("查询一级缓存。 key={},返回值是:{}", key, JSON.toJSONString(result));
            if (result != null) {
                return result;
            }
        }
        if (this.userSecondCache()) {
            result = secondCache.get(key, valueLoader);
            firstCache.putIfAbsent(key, result);
            log.debug("查询二级缓存,并将数据放到一级缓存。 key={},返回值是:{}", key, JSON.toJSONString(result));
            if (result != null) {
                return result;
            }
        }
        return result;
    }

    @Override
    public void put(Object key, Object value) {
        if (this.userSecondCache()) {
            secondCache.put(key, value);
            // 删除一级缓存
            if (this.userFirstCache()) {
                deleteFirstCache(key);
            }
        } else if (this.userFirstCache()) {
            firstCache.putIfAbsent(key, value);

        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        ValueWrapper result = null;
        if (this.userSecondCache()) {
            result = secondCache.putIfAbsent(key, value);
            // 删除一级缓存
            if (this.userFirstCache()) {
                deleteFirstCache(key);
            }
        }
        if (this.userFirstCache()) {
            result = firstCache.putIfAbsent(key, value);
        }
        return toValueWrapper(result);
    }

    @Override
    public void evict(Object key) {
        // 删除的时候要先删除二级缓存再删除一级缓存，否则有并发问题
        secondCache.evict(key);
        // 删除一级缓存
        if (this.userFirstCache()) {
            deleteFirstCache(key);
        }
    }

    @Override
    public void clear() {
        // 删除的时候要先删除二级缓存再删除一级缓存，否则有并发问题
        secondCache.clear();
        if (this.userFirstCache()) {
            clearFirstCache();
        }
    }

    private void clearFirstCache() {
        // 清除一级缓存需要用到redis的订阅/发布模式，否则集群中其他服服务器节点的一级缓存数据无法删除
        RedisPubSubMessage message = new RedisPubSubMessage();
        message.setCacheName(getName());
        message.setMessageType(RedisPubSubMessageType.CLEAR);
        // 发布消息
        RedisPublisher.publisher(redisTemplate, new ChannelTopic(getName()), message);
    }

    private void deleteFirstCache(Object key) {
        // 删除一级缓存需要用到redis的Pub/Sub（订阅/发布）模式，否则集群中其他服服务器节点的一级缓存数据无法删除
        RedisPubSubMessage message = new RedisPubSubMessage();
        message.setCacheName(getName());
        message.setKey(key);
        message.setMessageType(RedisPubSubMessageType.EVICT);
        // 发布消息
        RedisPublisher.publisher(redisTemplate, new ChannelTopic(getName()), message);
    }

    /**
     * 获取一级缓存
     *
     * @return FirstCache
     */
    public Cache getFirstCache() {
        return firstCache;
    }

}
