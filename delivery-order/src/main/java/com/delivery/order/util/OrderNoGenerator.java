package com.delivery.order.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单号生成器。
 *
 * <p>格式：{@code DLV + yyyyMMddHHmmss + 6位随机数}，共 23 位。
 * <p>对外只暴露订单号，不暴露自增 id —— 避免泄露业务量，也便于后续分库分表。
 */
public final class OrderNoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private OrderNoGenerator() {
    }

    public static String next() {
        return "DLV" + LocalDateTime.now().format(FMT)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }
}
