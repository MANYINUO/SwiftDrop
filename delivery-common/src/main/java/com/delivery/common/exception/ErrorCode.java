package com.delivery.common.exception;

import lombok.Getter;

/**
 * 全局错误码。
 *
 * <p>分段约定：
 * <ul>
 *   <li>0        成功</li>
 *   <li>400xx    客户端 / 业务错误</li>
 *   <li>500xx    服务端错误</li>
 * </ul>
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),

    // ---------- 通用 ----------
    PARAM_INVALID(40000, "参数不合法"),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试"),
    DATA_NOT_FOUND(40013, "数据不存在"),

    // ---------- 订单 ----------
    ORDER_NOT_FOUND(40001, "订单不存在"),
    /** 状态机拦截：当前状态不允许该操作 */
    ILLEGAL_TRANSITION(40002, "当前订单状态不允许该操作"),
    /** CAS 更新失败：并发下状态已被他人改变 */
    CONCURRENT_MODIFIED(40008, "操作冲突，请刷新后重试"),
    ORDER_ALREADY_HAS_RIDER(40010, "订单已分配骑手"),
    ORDER_CANNOT_CANCEL(40014, "当前状态不允许取消订单"),

    // ---------- 骑手 ----------
    RIDER_NOT_FOUND(40012, "骑手不存在"),
    RIDER_OFFLINE(40003, "骑手当前不在线"),
    RIDER_CAPACITY_FULL(40004, "骑手已达接单上限"),
    RIDER_NOT_OWNER(40009, "骑手无权操作该订单"),

    // ---------- 派单 ----------
    DISPATCH_TASK_NOT_FOUND(40015, "派单任务不存在"),
    DISPATCH_TASK_EXPIRED(40005, "派单任务已过期"),
    ORDER_ALREADY_TAKEN(40006, "该单已被其他骑手接走"),
    NO_CANDIDATE_RIDER(40011, "附近暂无可用骑手，已转人工调度"),
    DISPATCH_TASK_NOT_PENDING(40016, "该派单任务已处理"),

    // ---------- 幂等 ----------
    DUPLICATE_REQUEST(40007, "重复请求，已返回首次处理结果"),

    // ---------- 鉴权 ----------
    /** 未携带有效登录凭证，或凭证已过期：对应 HTTP 401 */
    UNAUTHORIZED(40101, "未登录或登录已过期，请重新登录"),
    /** 登录时账号 / 密码 / 校验码错误 */
    AUTH_LOGIN_FAILED(40102, "账号或校验信息错误"),
    /** 已登录但无权访问该资源（如骑手操作他人订单）：对应 HTTP 403 */
    FORBIDDEN(40103, "没有操作权限"),

    // ---------- 服务 ----------
    /** 依赖的基础服务（如 Redis）不可用：对应 HTTP 503 */
    SERVICE_UNAVAILABLE(50301, "登录服务暂不可用，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
