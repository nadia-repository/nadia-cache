package com.nadia.cache.setting;

import com.alibaba.fastjson.annotation.JSONField;
import com.nadia.cache.support.CacheMode;
import lombok.Data;

import java.io.Serializable;

@Data
public class LayeringCacheSetting implements Serializable {
    private static final String SPLIT = "-";
    /**
     * 内部缓存名，由[一级缓存有效时间-二级缓存有效时间-二级缓存自动刷新时间]组成
     */
    private String internalKey;

    private CacheMode cacheMode;

    /**
     * 一级缓存配置
     */
    private FirstCacheSetting firstCacheSetting;

    /**
     * 二级缓存配置
     */
    private SecondaryCacheSetting secondaryCacheSetting;

    public LayeringCacheSetting(FirstCacheSetting firstCacheSetting, SecondaryCacheSetting secondaryCacheSetting,CacheMode cacheMode) {
        this.firstCacheSetting = firstCacheSetting;
        this.secondaryCacheSetting = secondaryCacheSetting;
        this.cacheMode = cacheMode;
        internalKey();
    }

    @JSONField(serialize = false, deserialize = false)
    private void internalKey() {
        // 一级缓存有效时间-二级缓存有效时间-二级缓存自动刷新时间
        StringBuilder sb = new StringBuilder();
        if (firstCacheSetting != null) {
            sb.append(firstCacheSetting.getTimeUnit().toMillis(firstCacheSetting.getExpireTime()));
        }
        sb.append(SPLIT);
        if (secondaryCacheSetting != null) {
            sb.append(secondaryCacheSetting.getTimeUnit().toMillis(secondaryCacheSetting.getExpiration()));
            sb.append(SPLIT);
            sb.append(secondaryCacheSetting.getTimeUnit().toMillis(secondaryCacheSetting.getPreloadTime()));
        }
        internalKey = sb.toString();
    }
}
