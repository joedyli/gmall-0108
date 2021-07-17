package com.atguigu.gmall.order.interceptor;

import com.atguigu.gmall.order.pojo.UserInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    
    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // userKey获取
        String userId = request.getHeader("userId");
        String username = request.getHeader("username");

        THREAD_LOCAL.set(new UserInfo(Long.valueOf(userId), null, username));

        return true;
    }

    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //System.out.println("这是完成方法。。。");
        // 由于使用的是tomcat线程池，所有请求结束，线程并没有结束，只是回到了线程池，如果不手动释放资源，会导致内存泄漏
        THREAD_LOCAL.remove();
    }
}
