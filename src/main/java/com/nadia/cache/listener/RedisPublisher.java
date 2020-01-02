package com.nadia.cache.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

public class RedisPublisher {
    private static final Logger logger = LoggerFactory.getLogger(RedisPublisher.class);

    private RedisPublisher() {
    }

    /**
     * 发布消息到频道（Channel）
     *
     * @param redisTemplate redis客户端
     * @param channelTopic  发布预订阅的频道
     * @param message       消息内容
     */
    public static void publisher(RedisTemplate<Object, Object> redisTemplate, ChannelTopic channelTopic, Object message) {
        redisTemplate.convertAndSend(channelTopic.toString(), message);
        logger.debug("redis消息发布者向频道【{}】发布了【{}】消息", channelTopic.toString(), message.toString());
    }
}
