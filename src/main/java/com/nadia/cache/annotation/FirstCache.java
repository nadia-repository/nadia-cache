package com.nadia.cache.annotation;


import com.nadia.cache.support.ExpireMode;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface FirstCache {

    int initialCapacity() default 10;

    int maximumSize() default 5000;

    int expireTime() default 600;

    TimeUnit timeUnit() default TimeUnit.SECONDS;

    ExpireMode expireMode() default ExpireMode.WRITE;
}
