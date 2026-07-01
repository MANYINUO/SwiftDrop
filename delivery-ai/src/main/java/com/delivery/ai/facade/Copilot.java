package com.delivery.ai.facade;

/**
 * 调度 Copilot 的业务门面。
 *
 * <p><b>为什么要有这层接口</b>：Controller 只依赖 {@code Copilot}，不依赖 Spring AI 的
 * {@code ChatClient}。这样「换框架」或「换实现」不会波及调用方，也让单元测试
 * 可以塞一个假实现进来——不需要真的连模型。
 *
 * <p>这也是整个 AI 模块唯一的对外契约：输入订单号，输出结构化解释。
 */
public interface Copilot {

    /**
     * 解释一笔订单为什么派给了现在的骑手。
     *
     * <p>实现必须保证：<b>永不抛异常</b>。模型超时、开关关闭、输出被守卫拦下时，
     * 一律返回模板答案。AI 是增值功能，它的失败不能变成后台页面的错误。
     *
     * @param orderNo 业务订单号
     * @return 解释结果，{@code mode} 标明来源
     */
    DispatchExplainVO explain(String orderNo);
}
