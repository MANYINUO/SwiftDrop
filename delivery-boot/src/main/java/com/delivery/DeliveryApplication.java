package com.delivery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 同城即时配送调度平台 · 启动类。
 *
 * <p>模块划分：
 * <pre>
 *   delivery-boot      启动模块（本类所在）
 *   delivery-admin     调度后台
 *   delivery-notify    WebSocket 推送
 *   delivery-dispatch  调度引擎
 *   delivery-order     订单与状态机
 *   delivery-rider     骑手与位置
 *   delivery-common    公共基础设施
 * </pre>
 *
 * <p>依赖方向：boot → admin/notify → dispatch → order/rider → common，不允许反向依赖。
 */
@SpringBootApplication(scanBasePackages = "com.delivery")
@MapperScan("com.delivery.**.mapper")
@EnableScheduling
public class DeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryApplication.class, args);
    }
}
