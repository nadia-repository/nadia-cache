package com.nadia.cache.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface SecondaryCache {

    long expireTime() default 3600;

    long preloadTime() default 1800;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    boolean forceRefresh() default false;

    boolean isAllowNullValue() default false;

    int magnification() default 1;
}
