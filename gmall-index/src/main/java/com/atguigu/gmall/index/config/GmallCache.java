package com.atguigu.gmall.index.config;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmallCache {

    /**
     * 自定义缓存的前缀
     * @return
     */
    String prefix() default "gmall:";

    /**
     * 缓存的过期时间：单位是min
     * @return
     */
    int timeout() default 30;

    /**
     * 为了防止缓存雪崩，给缓存时间添加随机值
     * 这里是随机值范围：单位min
     * @return
     */
    int random() default 30;

    /**
     * 为了防止缓存击穿，给缓存添加分布式锁
     * 这里制定分布式锁的前缀
     * @return
     */
    String lock() default "lock:";
}
