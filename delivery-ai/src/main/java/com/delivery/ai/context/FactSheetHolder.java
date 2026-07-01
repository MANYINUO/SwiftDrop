package com.delivery.ai.context;

/**
 * 请求级 FactSheet 的传递通道。
 *
 * <p><b>为什么用 ThreadLocal 而不是 {@code toolContext}</b>：Spring AI 1.0.0-M6 的
 * {@code ToolContext} 注入方式在里程碑版本间有过变动，工具方法签名容易对不上。
 * ThreadLocal 是任务书给出的兜底方案，行为等价且不依赖框架版本细节。
 *
 * <p><b>为什么必须清理</b>：Tomcat 线程池会复用线程。如果请求结束后不 remove，
 * 下一个请求可能读到上一个订单的事实表——那不是「数字校验失效」，而是
 * <b>拿别人的数据做白名单</b>，比不校验更危险。所以 {@link #clear()} 必须放在 finally 里。
 */
public final class FactSheetHolder {

    private static final ThreadLocal<FactSheet> CURRENT = new ThreadLocal<>();

    private FactSheetHolder() {
    }

    /** 绑定本次请求的事实表 */
    public static void set(FactSheet sheet) {
        CURRENT.set(sheet);
    }

    /** 取当前请求的事实表；工具方法用它写入，未绑定时返回 null 由调用方判空 */
    public static FactSheet get() {
        return CURRENT.get();
    }

    /** 请求结束必须调用，否则线程复用会串数据 */
    public static void clear() {
        CURRENT.remove();
    }
}
