package com.delivery.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务可调参数，对应 application.yml 里的 {@code delivery.*}。
 *
 * <p>把超时、半径阶梯、权重这类「会调的参数」集中在这里，
 * 好处是不用改代码就能调参，改完 yml 重启即生效。
 */
@Data
@Component
@ConfigurationProperties(prefix = "delivery")
public class DeliveryProperties {

    private Dispatch dispatch = new Dispatch();
    private Location location = new Location();
    private Auth auth = new Auth();

    @Data
    public static class Dispatch {
        /** 骑手响应超时（秒） */
        private int responseTimeoutSeconds = 15;
        /** 每轮最多推送的候选骑手数 */
        private int candidateLimit = 10;
        /** 最大派单轮次，超过转人工 */
        private int maxRound = 3;
        /** 搜索半径阶梯（米） */
        private List<Integer> radiusSteps = new ArrayList<>(List.of(1000, 3000, 5000));
        /** 超时扫描间隔（毫秒） */
        private long timeoutScanIntervalMs = 5000;
        /** 是否开启自动派单 */
        private boolean autoDispatchEnabled = true;

        /** 取第 round 轮（从 1 开始）对应的搜索半径；超出阶梯长度则用最后一个 */
        public int radiusOfRound(int round) {
            if (radiusSteps == null || radiusSteps.isEmpty()) {
                return 3000;
            }
            int idx = Math.min(Math.max(round, 1), radiusSteps.size()) - 1;
            return radiusSteps.get(idx);
        }
    }

    @Data
    public static class Location {
        /** 位置上报最小间隔（毫秒），低于此间隔的重复上报丢弃（防抖） */
        private long reportMinIntervalMs = 2000;
        /** 批量落库间隔（毫秒） */
        private long flushIntervalMs = 1000;
        /** 单批最大条数 */
        private int flushBatchSize = 500;
    }

    /**
     * 登录鉴权相关配置。
     *
     * <p><b>演示取舍</b>：后台管理员账号直接写在配置文件里，免建表、方便演示；
     * 真实项目应把账号落库、密码用 BCrypt 加密存储，并支持多角色 / 多员工。
     */
    @Data
    public static class Auth {
        private Admin admin = new Admin();

        @Data
        public static class Admin {
            /** 管理员业务 ID（会写入操作日志的 X-Admin-Id） */
            private long id = 1L;
            private String username = "admin";
            private String password = "123456";
            /** 展示用名称 */
            private String name = "调度管理员";
        }
    }
}
