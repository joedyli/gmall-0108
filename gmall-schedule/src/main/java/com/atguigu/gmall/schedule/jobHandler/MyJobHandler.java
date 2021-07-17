package com.atguigu.gmall.schedule.jobHandler;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.log.XxlJobLogger;
import org.springframework.stereotype.Component;

@Component
public class MyJobHandler {
    /**
     * 1.方法的返回值必须时ReturnT<String>
     * 2.方法必须有一个String类型的形参
     * 3.给方法添加@XxlJob("唯一标识")注解
     * 4.如果给调度中心输出日志：XxlJobLogger.log("xxx")
     */
    @XxlJob("myJobHandler")
    public ReturnT<String> test(String param){
        System.out.println("这是定时任务：" + System.currentTimeMillis());
        XxlJobLogger.log("this is a log：" + param);
        return ReturnT.SUCCESS;
    }
}
