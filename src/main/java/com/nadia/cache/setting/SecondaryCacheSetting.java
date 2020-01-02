package com.nadia.cache.setting;

import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@Data
public class SecondaryCacheSetting implements Serializable {

    private String name;
    /**
     * 缓存有效时间
     */
    private long expiration = 1000000000;

    /**
     * 缓存主动在失效前强制刷新缓存的时间
     */
    private long preloadTime = 1000000000;

    /**
     * 时间单位 {@link TimeUnit}
     */
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    /**
     * 是否强制刷新（走数据库），默认是false
     */
    private boolean forceRefresh = false;

    /**
     * 是否使用缓存名称作为 redis key 前缀
     */
    private boolean usePrefix = true;

    /**
     * 是否允许存NULL值
     */
    boolean allowNullValue = false;

    /**
     * 非空值和null值之间的时间倍率，默认是1。allowNullValue=true才有效
     * <p>
     * 如配置缓存的有效时间是200秒，倍率这设置成10，
     * 那么当缓存value为null时，缓存的有效时间将是20秒，非空时为200秒
     * </p>
     */
    int magnification = 1000000000;

    public SecondaryCacheSetting() {
    }

    /**
     * @param expiration      缓存有效时间
     * @param preloadTime     缓存刷新时间
     * @param timeUnit        时间单位 {@link TimeUnit}
     * @param forceRefresh    是否强制刷新
     * @param allowNullValues 是否允许存NULL值，模式允许
     * @param magnification   非空值和null值之间的时间倍率
     */
    public SecondaryCacheSetting(long expiration, long preloadTime, TimeUnit timeUnit, boolean forceRefresh,
                                 boolean allowNullValues, int magnification) {
        this.expiration = expiration;
        this.preloadTime = preloadTime;
        this.timeUnit = timeUnit;
        this.forceRefresh = forceRefresh;
        this.allowNullValue = allowNullValues;
        this.magnification = magnification;
        this.usePrefix = true;
    }
}
