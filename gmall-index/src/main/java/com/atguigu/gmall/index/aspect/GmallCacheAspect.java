package com.atguigu.gmall.index.aspect;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.index.config.GmallCache;
import com.atguigu.gmall.pms.entity.CategoryBrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import jdk.nashorn.internal.runtime.regexp.JoniRegExp;
import org.aopalliance.intercept.Joinpoint;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class GmallCacheAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RBloomFilter filter;

//    @Pointcut("execution(* com.atguigu.gmall.index.service.*.*(..))")
//    public void pointcut(){}

    /**
     * 切点表达式：
     *  第一个*：代表返回值为任意类型
     *  第二个*：代表是任意类
     *  第三个*：代表类中的任意方法
     *  ..：代表任意参数
     *
     *  获取目标信息：
     *      目标类：joinPoint.getTarget().getClass()
     *      目标方法签名：(MethodSignature) joinPoint.getSignature()
     *      目标方法：signature.getMethod()
     *      目标方法参数列表：joinPoint.getArgs()
     */
//    @Before("pointcut()")
//    public void before(JoinPoint joinPoint){
//        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
//        System.out.println("这是前置方法。。。。。。。。。。。。。。" + joinPoint.getTarget().getClass().getName());
//        System.out.println("目标方法：" + signature.getMethod().getName());
//        System.out.println("目标方法的参数列表：" + joinPoint.getArgs());
//    }
//
//    @AfterReturning(value = "pointcut()", returning = "result")
//    public void afterReturning(JoinPoint joinPoint, Object result){
//        System.out.println("这是一个返回后通知：");
//        ((List<CategoryEntity>)result).forEach(System.out::println);
//    }
//
//    @AfterThrowing(value = "pointcut()", throwing = "ex")
//    public void afterThrowing(Exception ex){
//
//    }

    @Around("@annotation(com.atguigu.gmall.index.config.GmallCache)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 获取目标方法
        Method method = signature.getMethod();
        // 获取目标方法上的GmallCache注解
        GmallCache gmallCache = method.getAnnotation(GmallCache.class);
        // 获取目标方法上的返回值类型
        Class returnType = signature.getReturnType();

        // 获取缓存注解中的前缀
        String prefix = gmallCache.prefix();
        // 获取目标方法的参数列表
        List<Object> args = Arrays.asList(joinPoint.getArgs());
        // 组装缓存的key
        String key = prefix + args;

        // 为了解决缓存穿透，使用了bloom过滤器
        if (!this.filter.contains(key)) {
            return null;
        }

        // 1.先查询缓存，如果缓存中可以命中，直接返回
        String json = this.redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(json)){
            return JSON.parseObject(json, returnType);
        }

        // 2.如果缓存中没有，为了防止缓存的击穿，添加分布式锁
        RLock fairLock = this.redissonClient.getFairLock(gmallCache.lock() + args);
        fairLock.lock();

        try {
            // 3.再次查询缓存，因为在获取分布式锁的过程中，可以有其他请求把数据放入了缓存
            String json2 = this.redisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(json2)){
                return JSON.parseObject(json2, returnType);
            }

            // 4.执行目标方法，远程调用或者查询数据库
            Object result = joinPoint.proceed(joinPoint.getArgs());

            // 5.把目标方法的返回值放入缓存（缓存穿透 和缓存雪崩）
            if (result != null) {
                int timeout = gmallCache.timeout() + new Random().nextInt(gmallCache.random());
                this.redisTemplate.opsForValue().set(key, JSON.toJSONString(result), timeout, TimeUnit.MINUTES);
            }

            return result;
        } finally {
            fairLock.unlock();
        }
    }
}
