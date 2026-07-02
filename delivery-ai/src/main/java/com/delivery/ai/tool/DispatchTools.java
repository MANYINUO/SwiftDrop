package com.delivery.ai.tool;

import com.delivery.ai.context.FactSheet;
import com.delivery.ai.context.FactSheetHolder;
import com.delivery.dispatch.dto.DispatchTaskVO;
import com.delivery.dispatch.service.DispatchService;
import com.delivery.order.entity.OrderEvent;
import com.delivery.order.service.OrderService;
import com.delivery.rider.entity.Rider;
import com.delivery.rider.mapper.RiderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 调度域只读工具集。
 *
 * <p><b>只有两个工具，是刻意的</b>：解释「为什么派给这个骑手」只需要打分明细和事件流水。
 * 每多一个工具，模型选错的概率和调试成本都会上升；工具越少，行为越可预测。
 *
 * <p><b>三个设计约束</b>：
 * <ol>
 *   <li><b>只读</b>：没有任何写操作。AI 在这套系统里的定位是「解释」而非「决策」，
 *       写操作需要人工确认门，不在本次范围。</li>
 *   <li><b>不抛异常</b>：工具内部捕获所有异常并返回带 error 字段的结构，
 *       让模型看到错误后自行换策略。抛给框架只会让整次请求失败。</li>
 *   <li><b>返回精简结构</b>：禁止直接返回 VO/实体。整实体塞进上下文既贵又会让模型
 *       抓错重点，字段挑过的返回能让答案更聚焦。</li>
 * </ol>
 *
 * <p><b>为什么 scoreDetail 要解析成对象</b>：它本身是 JSON 字符串。原样返回时模型
 * 会把它当普通文本「念」出来（"distanceScore冒号0.62逗号…"），而不是当作结构化数据理解。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 事件流水最多返回条数，防止长订单把上下文撑爆 */
    private static final int MAX_EVENTS = 20;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DispatchService dispatchService;
    private final OrderService orderService;
    private final RiderMapper riderMapper;

    /**
     * 查询订单每一轮、每个候选骑手的打分明细。
     *
     * <p>这是解释「为什么派给他」的核心数据源：{@code scoreDetail} 里有五个因子的
     * 逐项得分，模型据此才能说出「顺路分 0.91 而另一位只有 0.12」这种有信息量的话。
     */
    @Tool(description = "查询订单每一轮、每个候选骑手的打分明细，用于解释为什么派给某个骑手")
    public List<Map<String, Object>> getDispatchTasks(
            @ToolParam(description = "业务订单号，形如 SD20261019001") String orderNo) {

        long start = System.currentTimeMillis();
        try {
            List<DispatchTaskVO> tasks = dispatchService.listByOrderNo(orderNo);
            if (tasks == null || tasks.isEmpty()) {
                return List.of();
            }

            // 一次批量查骑手，避免 N+1
            List<Long> riderIds = tasks.stream()
                    .map(DispatchTaskVO::getRiderId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            Map<Long, Rider> riderMap = riderIds.isEmpty()
                    ? Collections.emptyMap()
                    : riderMapper.selectBatchIds(riderIds).stream()
                            .collect(Collectors.toMap(Rider::getId, r -> r));

            List<Map<String, Object>> result = new ArrayList<>(tasks.size());
            for (DispatchTaskVO t : tasks) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("round", t.getRound());
                Rider r = riderMap.get(t.getRiderId());
                row.put("riderNo", r == null ? null : r.getRiderNo());
                row.put("rank", t.getCandidateRank());
                row.put("score", toDouble(t.getCandidateScore()));
                row.put("detail", parseDetail(t.getScoreDetail()));
                row.put("status", t.getStatus());
                result.add(row);
            }

            // 写入事实表：数字白名单靠它
            FactSheet sheet = FactSheetHolder.get();
            if (sheet != null) {
                sheet.absorb("getDispatchTasks", result);
            }

            log.debug("getDispatchTasks orderNo={} rows={} elapsed={}ms",
                    orderNo, result.size(), System.currentTimeMillis() - start);
            return result;

        } catch (Exception e) {
            // 工具失败不回抛：让模型看到错误并换策略，比整次请求失败体验好
            log.warn("getDispatchTasks 执行失败 orderNo={}", orderNo, e);
            return List.of(Map.of("error", "查询派单明细失败：" + e.getMessage()));
        }
    }

    /**
     * 查询订单事件流水。
     *
     * <p>事件流水回答的是「过程」而非「结果」：什么时候创建的、第几轮派单、谁拒了单、
     * 是否转人工。单看打分明细解释不了「为什么这单转人工了」，需要事件补齐。
     */
    @Tool(description = "查询订单的事件流水，用于解释派单过程与异常")
    public List<Map<String, Object>> getOrderEvents(
            @ToolParam(description = "业务订单号") String orderNo) {

        long start = System.currentTimeMillis();
        try {
            List<OrderEvent> events = orderService.events(orderNo);
            if (events == null || events.isEmpty()) {
                return List.of();
            }

            List<Map<String, Object>> result = events.stream()
                    .limit(MAX_EVENTS)
                    .map(e -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("eventType", e.getEventType());
                        row.put("remark", e.getRemark());
                        row.put("createdAt", e.getCreatedAt() == null
                                ? null : e.getCreatedAt().format(TIME_FMT));
                        return row;
                    })
                    .collect(Collectors.toList());

            FactSheet sheet = FactSheetHolder.get();
            if (sheet != null) {
                sheet.absorb("getOrderEvents", result);
            }

            log.debug("getOrderEvents orderNo={} rows={} elapsed={}ms",
                    orderNo, result.size(), System.currentTimeMillis() - start);
            return result;

        } catch (Exception e) {
            log.warn("getOrderEvents 执行失败 orderNo={}", orderNo, e);
            return List.of(Map.of("error", "查询事件流水失败：" + e.getMessage()));
        }
    }

    /** BigDecimal → double，模型对 double 更敏感；null 安全 */
    private Double toDouble(java.math.BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    /**
     * 把 scoreDetail 的 JSON 字符串解析成对象。
     *
     * <p>解析失败时返回原字符串而不是抛异常——宁可让模型看到原始文本，
     * 也不要因为一条脏数据让整个解释功能不可用。
     */
    private Object parseDetail(String scoreDetail) {
        if (scoreDetail == null || scoreDetail.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(scoreDetail, Map.class);
        } catch (Exception e) {
            log.debug("scoreDetail 解析失败，返回原文：{}", scoreDetail);
            return scoreDetail;
        }
    }
}
