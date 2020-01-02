package com.nadia.cache.listener;

import com.alibaba.fastjson.JSON;
import com.nadia.cache.cache.LayeringCache;
import com.nadia.cache.manager.LayeringCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.Collection;

@Slf4j
public class RedisMessageListener extends MessageListenerAdapter {

    /**
     * 缓存管理器
     */
    private LayeringCacheManager cacheManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        super.onMessage(message, pattern);
        // 解析订阅发布的信息，获取缓存的名称和缓存的key
        RedisPubSubMessage redisPubSubMessage = (RedisPubSubMessage) cacheManager.getRedisTemplate()
                .getValueSerializer().deserialize(message.getBody());
        log.debug("redis消息订阅者接收到频道【{}】发布的消息。消息内容：{}", new String(message.getChannel()), JSON.toJSONString(redisPubSubMessage));

        // 根据缓存名称获取多级缓存，可能有多个
        Collection<Cache> caches = cacheManager.getCaches(redisPubSubMessage.getCacheName());
        for (Cache cache : caches) {
            // 判断缓存是否是多级缓存
            if (cache != null && cache instanceof LayeringCache) {
                switch (redisPubSubMessage.getMessageType()) {
                    case EVICT:
                        // 获取一级缓存，并删除一级缓存数据
                        ((LayeringCache) cache).getFirstCache().evict(redisPubSubMessage.getKey());
                        log.info("删除一级缓存{}数据,key={}", redisPubSubMessage.getCacheName(), redisPubSubMessage.getKey());
                        break;

                    case CLEAR:
                        // 获取一级缓存，并删除一级缓存数据
                        ((LayeringCache) cache).getFirstCache().clear();
                        log.info("清除一级缓存{}数据", redisPubSubMessage.getCacheName());
                        break;

                    default:
                        log.error("接收到没有定义的订阅消息频道数据");
                        break;
                }

            }
        }
    }

    public void setCacheManager(LayeringCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
}
