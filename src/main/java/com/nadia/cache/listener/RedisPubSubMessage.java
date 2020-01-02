package com.nadia.cache.listener;

import java.io.Serializable;

public class RedisPubSubMessage implements Serializable {
    /**
     * 缓存名称
     */
    private String cacheName;

    /**
     * 缓存key
     */
    private Object key;

    /**
     * 消息类型
     */
    private RedisPubSubMessageType messageType;

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public Object getKey() {
        return key;
    }

    public void setKey(Object key) {
        this.key = key;
    }

    public RedisPubSubMessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(RedisPubSubMessageType messageType) {
        this.messageType = messageType;
    }
}