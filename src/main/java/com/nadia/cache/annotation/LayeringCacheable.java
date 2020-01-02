package com.nadia.cache.annotation;

import com.nadia.cache.support.CacheMode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Cacheable
public @interface LayeringCacheable {
    @AliasFor("cacheNames")
    String[] value() default {};

    @AliasFor("value")
    String[] cacheNames() default {};

    String key() default "";

    CacheMode cacheMode() default CacheMode.ALL;

    FirstCache firstCache() default @FirstCache;

    SecondaryCache secondaryCache() default @SecondaryCache;
}
