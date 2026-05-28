package com.delivery.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等记录，对应 idempotent_record 表
 * 用途：用户重复提交下单请求时，只产生一笔订单
 * 原理：用 bizType:userId:clientKey 做唯一键，第一次插入成功=首次请求，
 *       后续重复请求插入失败（主键冲突）→ 直接返回首次结果
 */
@Data
@TableName("idempotent_record")
public class IdempotentRecord {

    // 注意：主键是 String 类型的幂等键，不是自增ID
    // 所以 @TableId 不加 type = IdType.AUTO
    @TableId
    private String idemKey;     // 幂等键：bizType:userId:clientKey

    private String bizType;     // 业务类型，如 ORDER_CREATE
    private String resultRef;   // 首次执行的业务结果引用（如订单号）
    private LocalDateTime createdAt;
    private LocalDateTime expireAt; // 过期时间，便于定期清理
}
