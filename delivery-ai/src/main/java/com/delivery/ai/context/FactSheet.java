package com.delivery.ai.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事实表 —— 一次请求内的「数字白名单」。
 *
 * <p><b>它解决什么问题</b>：大模型最危险的失败模式不是胡说八道，而是<b>把数字说错</b>。
 * 「综合得分 0.814」写成「0.81」、「远 180 米」写成「远 200 米」，读起来毫无破绽，
 * 但调度员据此做决策就会出错。
 *
 * <p><b>思路</b>：把工具返回的原始数据全部收进来，抽取出所有出现过的数字作为允许集合；
 * 模型输出后再抽一次数字，凡是不在这个集合里的就是编造，直接判不合格。
 * 这样就把「模型是否诚实」变成了「字符串集合是否包含」，是可判定的。
 *
 * <p><b>为什么用 StringBuilder 拼原文而不是保留对象</b>：校验只需要「数字出现过」，
 * 不需要理解语义。保留原文最省事，也最不容易漏——任何嵌套层级的数字都会被抽到。
 *
 * <p><b>线程安全</b>：每个请求一个实例，不做共享，因此不加锁。
 */
@Slf4j
public class FactSheet {

    /** 从文本中抽取数字：整数或小数 */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 所有工具返回结果的原文拼接，用于抽取允许出现的数字 */
    private final StringBuilder raw = new StringBuilder();

    /**
     * 记录一次工具调用结果。
     *
     * @param toolName 工具名，仅用于日志排查
     * @param data     工具返回的结构化数据，会被序列化后追加
     */
    public void absorb(String toolName, Object data) {
        if (data == null) {
            return;
        }
        try {
            raw.append(MAPPER.writeValueAsString(data)).append('\n');
        } catch (Exception e) {
            // 序列化失败不能影响主流程：退化为 toString，至少数字还能被抽到
            log.warn("事实表序列化失败 tool={}", toolName, e);
            raw.append(data).append('\n');
        }
    }

    /**
     * 允许在答案中出现的数字集合。
     *
     * <p>已做归一化：去掉千分位、去掉末尾的 0，因此 {@code 0.8140} 与 {@code 0.814}
     * 会被视为同一个数字，避免误杀。
     */
    public Set<String> allowedNumbers() {
        Set<String> set = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(raw);
        while (m.find()) {
            set.add(normalize(m.group()));
        }
        return set;
    }

    /** 事实表原文，模板答案在没有结构化数据时可以直接读它 */
    public String text() {
        return raw.toString();
    }

    /** 是否一条数据都没收到（用于判断「这单还没派单」这类场景） */
    public boolean isEmpty() {
        return raw.length() == 0;
    }

    /**
     * 数字归一化。
     *
     * <p>必须做，否则误杀率极高：
     * <ul>
     *   <li>{@code 8.00} 与 {@code 8} —— 去掉小数末尾的 0</li>
     *   <li>{@code 1,234} —— 去掉千分位（抽取前已排除逗号，这里兜底）</li>
     *   <li>{@code 0.8140} 与 {@code 0.814} —— 同上</li>
     * </ul>
     */
    public static String normalize(String number) {
        if (number == null || number.isBlank()) {
            return "";
        }
        String n = number.replace(",", "").trim();
        if (n.contains(".")) {
            // 去掉末尾的 0，再去掉可能剩下的孤立小数点
            n = n.replaceAll("0+$", "");
            if (n.endsWith(".")) {
                n = n.substring(0, n.length() - 1);
            }
        }
        return n;
    }
}
