package com.delivery.ai.controller;

import com.delivery.ai.facade.Copilot;
import com.delivery.ai.facade.DispatchExplainVO;
import com.delivery.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调度后台 · AI 分析接口。
 *
 * <p><b>为什么路由挂在 {@code /api/admin/**} 下</b>：这样 {@code AuthFilter} 已有的
 * ADMIN 分支会直接生效，无需在本模块重写鉴权。<b>这是刻意的</b>——AI 接口能读到订单
 * 与骑手数据，如果挂在未受保护的路径下（例如新建 {@code /api/ai/**}），会变成匿名可访问，
 * 那是一个真实的数据泄露面。
 *
 * <p><b>为什么 {@code X-Admin-Id} 可以直接用</b>：该请求头由 {@code AuthFilter} 在
 * 校验令牌后用 {@code HttpServletRequestWrapper} 覆写，前端传什么都不作数。
 * 这里保留它是为了日志追溯（谁问的），不参与任何权限判断。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final Copilot copilot;

    /**
     * 解释一笔订单为什么派给了现在的骑手。
     *
     * <p>永不抛业务异常：{@link Copilot#explain} 保证降级返回。
     * 因此这里不需要 try-catch——真出现未预期异常，交给 GlobalExceptionHandler 兜底即可。
     */
    @GetMapping("/explain/{orderNo}")
    public Result<DispatchExplainVO> explain(
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId,
            @PathVariable String orderNo) {

        log.info("AI 解释请求 adminId={} orderNo={}", adminId, orderNo);
        return Result.ok(copilot.explain(orderNo));
    }
}
