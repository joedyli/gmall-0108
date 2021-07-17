package com.atguigu.gmall.schedule;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.atguigu.gmall.schedule.mapper")
public class GmallScheduleApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmallScheduleApplication.class, args);
    }

}
