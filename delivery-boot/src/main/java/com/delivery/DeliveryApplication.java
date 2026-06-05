package com.delivery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类，运行 main 方法启动整个应用（内嵌 Tomcat + Spring 容器）
 */
@SpringBootApplication(scanBasePackages = "com.delivery")
// 扫描所有模块的 mapper 包，** 表示任意层级子包
@MapperScan("com.delivery.**.mappe" +
        "" +
        "r")
public class DeliveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeliveryApplication.class, args);
    }
}