package com.nadia.cache.setting;

import com.nadia.cache.support.ExpireMode;
import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@Data
public class FirstCacheSetting implements Serializable {

    private String name;

    private int initialCapacity = 10;

    private int maximumSize = 500;

    private int expireTime = 600;

    private TimeUnit timeUnit = TimeUnit.SECONDS;

    private ExpireMode expireMode = ExpireMode.WRITE;

    public FirstCacheSetting(){}

    public FirstCacheSetting(int initialCapacity, int maximumSize, int expireTime, TimeUnit timeUnit, ExpireMode expireMode) {
        this.initialCapacity = initialCapacity;
        this.maximumSize = maximumSize;
        this.expireTime = expireTime;
        this.timeUnit = timeUnit;
        this.expireMode = expireMode;
    }
}
