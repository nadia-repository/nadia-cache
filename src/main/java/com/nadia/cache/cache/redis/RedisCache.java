package com.nadia.cache.cache.redis;

import com.alibaba.fastjson.JSON;
import com.nadia.cache.setting.SecondaryCacheSetting;
import com.nadia.cache.support.AwaitThreadContainer;
import com.nadia.cache.support.Lock;
import com.nadia.cache.support.ThreadTaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisCache extends AbstractValueAdaptingCache {

    private static final int RETRY_COUNT = 20;

    private static final long WAIT_TIME = 20;

    private AwaitThreadContainer container = new AwaitThreadContainer();

    private RedisTemplate<Object, Object> redisTemplate;

    private long expiration;

    private long preloadTime = 0;

    private boolean forceRefresh = false;

    private boolean usePrefix;

    private final int magnification;

    private String name;

    public RedisCache(String name, RedisTemplate<Object, Object> redisTemplate, SecondaryCacheSetting secondaryCacheSetting) {

        this(name, redisTemplate, secondaryCacheSetting.getTimeUnit().toMillis(secondaryCacheSetting.getExpiration()),
                secondaryCacheSetting.getTimeUnit().toMillis(secondaryCacheSetting.getPreloadTime()),
                secondaryCacheSetting.isForceRefresh(), secondaryCacheSetting.isUsePrefix(),
                secondaryCacheSetting.isAllowNullValue(), secondaryCacheSetting.getMagnification());
    }


    public RedisCache(String name, RedisTemplate<Object, Object> redisTemplate, long expiration, long preloadTime,
                      boolean forceRefresh, boolean usePrefix, boolean allowNullValues, int magnification) {
        super(allowNullValues);
        this.name  = name;
        Assert.notNull(redisTemplate, "RedisTemplate can not NULL");
        this.redisTemplate = redisTemplate;
        this.expiration = expiration;
        this.preloadTime = preloadTime;
        this.forceRefresh = forceRefresh;
        this.usePrefix = usePrefix;
        this.magnification = magnification;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public RedisTemplate<Object, Object> getNativeCache() {
        return this.redisTemplate;
    }

    @Override
    public ValueWrapper get(Object key) {
        return toValueWrapper(lookup(key));
    }

    @Override
    protected Object lookup(Object key) {
        RedisCacheKey redisCacheKey = getRedisCacheKey(key);
        log.debug("redis缓存 key= {} 查询redis缓存", redisCacheKey.getKey());
        return redisTemplate.opsForValue().get(redisCacheKey.getKey());
    }


    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        RedisCacheKey redisCacheKey = getRedisCacheKey(key);
        log.debug("redis缓存 key= {} 查询redis缓存如果没有命中，从数据库获取数据", redisCacheKey.getKey());
        // 先获取缓存，如果有直接返回
        Object result = redisTemplate.opsForValue().get(redisCacheKey.getKey());
        if (result != null || redisTemplate.hasKey(redisCacheKey.getKey())) {
            // 刷新缓存
            refreshCache(redisCacheKey, valueLoader, result);
            return (T) fromStoreValue(result);
        }
        // 执行缓存方法
        return executeCacheMethod(redisCacheKey, valueLoader);
    }

    @Override
    public void put(Object key, Object value) {
        RedisCacheKey redisCacheKey = getRedisCacheKey(key);
        log.debug("redis缓存 key= {} put缓存，缓存值：{}", redisCacheKey.getKey(), JSON.toJSONString(value));
        putValue(redisCacheKey, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        log.debug("redis缓存 key= {} putIfAbsent缓存，缓存值：{}", getRedisCacheKey(key).getKey(), JSON.toJSONString(value));
        Object reult = get(key);
        if (reult != null) {
            return toValueWrapper(reult);
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        RedisCacheKey redisCacheKey = getRedisCacheKey(key);
        log.info("清除redis缓存 key= {} ", redisCacheKey.getKey());
        redisTemplate.delete(redisCacheKey.getKey());
    }

    @Override
    public void clear() {
        // 必须开启了使用缓存名称作为前缀，clear才有效
        if (usePrefix) {
            log.info("清空redis缓存 ，缓存前缀为{}", getName());
            Set<Object> keys = redisTemplate.keys(getName() + "*");
            if (!CollectionUtils.isEmpty(keys)) {
                redisTemplate.delete(keys);
            }
        }
    }


    /**
     * 获取 RedisCacheKey
     *
     * @param key 缓存key
     * @return RedisCacheKey
     */
    public RedisCacheKey getRedisCacheKey(Object key) {
        return new RedisCacheKey(key, redisTemplate.getKeySerializer())
                .cacheName(getName()).usePrefix(usePrefix);
    }

    /**
     * 同一个线程循环5次查询缓存，每次等待20毫秒，如果还是没有数据直接去执行被缓存的方法
     */
    private <T> T executeCacheMethod(RedisCacheKey redisCacheKey, Callable<T> valueLoader) {
        Lock redisLock = new Lock(redisTemplate, redisCacheKey.getKey() + "_sync_lock");
        // 同一个线程循环20次查询缓存，每次等待20毫秒，如果还是没有数据直接去执行被缓存的方法
        for (int i = 0; i < RETRY_COUNT; i++) {
            try {
                // 先取缓存，如果有直接返回，没有再去做拿锁操作
                Object result = redisTemplate.opsForValue().get(redisCacheKey.getKey());
                if (result != null) {
                    log.debug("redis缓存 key= {} 获取到锁后查询查询缓存命中，不需要执行被缓存的方法", redisCacheKey.getKey());
                    return (T) fromStoreValue(result);
                }

                // 获取分布式锁去后台查询数据
                if (redisLock.lock()) {
                    T t = loaderAndPutValue(redisCacheKey, valueLoader, true);
                    log.debug("redis缓存 key= {} 从数据库获取数据完毕，唤醒所有等待线程", redisCacheKey.getKey());
                    // 唤醒线程
                    container.signalAll(redisCacheKey.getKey());
                    return t;
                }
                // 线程等待
                log.debug("redis缓存 key= {} 从数据库获取数据未获取到锁，进入等待状态，等待{}毫秒", redisCacheKey.getKey(), WAIT_TIME);
                container.await(redisCacheKey.getKey(), WAIT_TIME);
            } catch (Exception e) {
                container.signalAll(redisCacheKey.getKey());
                throw new RuntimeException(redisCacheKey.getKey(), e);
            } finally {
                redisLock.unlock();
            }
        }
        log.debug("redis缓存 key={} 等待{}次，共{}毫秒，任未获取到缓存，直接去执行被缓存的方法", redisCacheKey.getKey(), RETRY_COUNT, RETRY_COUNT * WAIT_TIME, WAIT_TIME);
        return loaderAndPutValue(redisCacheKey, valueLoader, true);
    }

    /**
     * 加载并将数据放到redis缓存
     */
    private <T> T loaderAndPutValue(RedisCacheKey key, Callable<T> valueLoader, boolean isLoad) {
        long start = System.currentTimeMillis();
        try {
            // 加载数据
            Object result = putValue(key, valueLoader.call());
            log.debug("redis缓存 key={} 执行被缓存的方法，并将其放入缓存, 耗时：{}。数据:{}", key.getKey(), System.currentTimeMillis() - start, JSON.toJSONString(result));

            return (T) fromStoreValue(result);
        } catch (Exception e) {
            throw new RuntimeException(key.getKey(), e);
        }
    }

    private Object putValue(RedisCacheKey key, Object value) {
        Object result = toStoreValue(value);
        // redis 缓存不允许直接存NULL，如果结果返回NULL需要删除缓存
        if (result == null) {
            redisTemplate.delete(key.getKey());
            return result;
        }
        // 不允许缓存NULL值，删除缓存
        if (!isAllowNullValues() && result instanceof NullValue) {
            redisTemplate.delete(key.getKey());
            return result;
        }

        // 允许缓存NULL值
        long expirationTime = this.expiration;
        // 允许缓存NULL值且缓存为值为null时需要重新计算缓存时间
        if (isAllowNullValues() && result instanceof NullValue) {
            expirationTime = expirationTime / getMagnification();
        }
        // 将数据放到缓存
        redisTemplate.opsForValue().set(key.getKey(), result, expirationTime, TimeUnit.MILLISECONDS);
        return result;
    }

    /**
     * 刷新缓存数据
     */
    private <T> void refreshCache(RedisCacheKey redisCacheKey, Callable<T> valueLoader, Object result) {
        Long ttl = redisTemplate.getExpire(redisCacheKey.getKey());
        Long preload = preloadTime;
        // 允许缓存NULL值，则自动刷新时间也要除以倍数
        boolean flag = isAllowNullValues() && (result instanceof NullValue || result == null);
        if (flag) {
            preload = preload / getMagnification();
        }
        if (null != ttl && ttl > 0 && TimeUnit.SECONDS.toMillis(ttl) <= preload) {
            // 判断是否需要强制刷新在开启刷新线程
            if (!getForceRefresh()) {
                log.debug("redis缓存 key={} 软刷新缓存模式", redisCacheKey.getKey());
                softRefresh(redisCacheKey);
            } else {
                log.debug("redis缓存 key={} 强刷新缓存模式", redisCacheKey.getKey());
                forceRefresh(redisCacheKey, valueLoader);
            }
        }
    }

    /**
     * 软刷新，直接修改缓存时间
     *
     * @param redisCacheKey {@link RedisCacheKey}
     */
    private void softRefresh(RedisCacheKey redisCacheKey) {
        // 加一个分布式锁，只放一个请求去刷新缓存
        Lock redisLock = new Lock(redisTemplate, redisCacheKey.getKey() + "_lock");
        try {
            if (redisLock.tryLock()) {
                redisTemplate.expire(redisCacheKey.getKey(), this.expiration, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            redisLock.unlock();
        }
    }

    /**
     * 硬刷新（执行被缓存的方法）
     *
     * @param redisCacheKey {@link RedisCacheKey}
     * @param valueLoader   数据加载器
     */
    private <T> void forceRefresh(RedisCacheKey redisCacheKey, Callable<T> valueLoader) {
        // 尽量少的去开启线程，因为线程池是有限的
        ThreadTaskUtils.run(() -> {
            // 加一个分布式锁，只放一个请求去刷新缓存
            Lock redisLock = new Lock(redisTemplate, redisCacheKey.getKey() + "_lock");
            try {
                if (redisLock.lock()) {
                    // 获取锁之后再判断一下过期时间，看是否需要加载数据
                    Long ttl = redisTemplate.getExpire(redisCacheKey.getKey());
                    if (null != ttl && ttl > 0 && TimeUnit.SECONDS.toMillis(ttl) <= preloadTime) {
                        // 加载数据并放到缓存
                        loaderAndPutValue(redisCacheKey, valueLoader, false);
                    }
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            } finally {
                redisLock.unlock();
            }
        });
    }

    /**
     * 是否强制刷新（执行被缓存的方法），默认是false
     *
     * @return boolean
     */
    private boolean getForceRefresh() {
        return forceRefresh;
    }

    /**
     * 非空值和null值之间的时间倍率，默认是1。
     *
     * @return int
     */
    public int getMagnification() {
        return magnification;
    }

}
