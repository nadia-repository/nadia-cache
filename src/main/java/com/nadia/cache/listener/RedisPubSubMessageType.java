package com.nadia.cache.listener;

public enum RedisPubSubMessageType {
    EVICT("删除缓存"),
    CLEAR("清空缓存");

    private String label;

    RedisPubSubMessageType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}