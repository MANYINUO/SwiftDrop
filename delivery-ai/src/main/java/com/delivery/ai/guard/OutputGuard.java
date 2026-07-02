package com.delivery.ai.guard;

import com.delivery.ai.context.FactSheet;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 输出守卫 —— 检查模型答案里的每个数字是否都能在事实表中找到出处。
 *
 * <p><b>它防的是什么</b>：模型把 {@code 0.814} 说成 {@code 0.81}，把「远 180 米」说成
 * 「远 200 米」。这类错误读起来毫无破绽，但调度员会据此做判断，危害比明显胡说更大。
 *
 * <p><b>为什么用「数字白名单」而不是让模型自我检查</b>：让模型检查自己等于让它再猜一次。
 * 白名单是确定性的——数字要么在集合里，要么不在，可判定、可回归、可解释。
 *
 * <p><b>它的局限（必须知道）</b>：只能保证数字「出现过」，不能保证「用对了」。
 * 模型完全可能把 A 骑手的分数安到 B 骑手头上，而数字本身是真实的。
 * 所以它是<b>降级触发器</b>，不是正确性证明。真正的正确性靠 Prompt 约束 + 模板兜底。
 */
public class OutputGuard {

    /** 与 FactSheet 用同一套抽取规则，保证两边口径一致 */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    /** 拒答套话：模型说了这些等于没回答，应降级为模板 */
    private static final String[] REFUSAL_MARKERS = {
            "作为 AI", "作为AI", "抱歉，我无法", "抱歉,我无法", "我无法回答", "无法提供帮助"
    };

    private final int maxAnswerChars;

    public OutputGuard(int maxAnswerChars) {
        this.maxAnswerChars = maxAnswerChars;
    }

    /**
     * 执行校验。
     *
     * @param answer 模型原始输出
     * @param facts  本次请求的事实表
     * @return 校验结果；{@code passed=false} 时调用方应降级为模板答案
     */
    public GuardResult check(String answer, FactSheet facts) {
        if (answer == null || answer.isBlank()) {
            return GuardResult.reject("答案为空");
        }

        // ---------- ① 拒答套话 ----------
        for (String marker : REFUSAL_MARKERS) {
            if (answer.contains(marker)) {
                return GuardResult.reject("命中拒答套话：" + marker);
            }
        }

        // ---------- ② 数字白名单 ----------
        Set<String> allowed = facts == null ? Collections.emptySet() : facts.allowedNumbers();
        Set<String> violations = new LinkedHashSet<>();

        Matcher m = NUMBER.matcher(answer);
        while (m.find()) {
            String n = FactSheet.normalize(m.group());
            if (!allowed.contains(n)) {
                violations.add(m.group());
            }
        }

        if (!violations.isEmpty()) {
            GuardResult r = GuardResult.reject("存在事实表中没有的数字");
            r.getViolations().addAll(violations);
            return r;
        }

        // ---------- ③ 长度 ----------
        String finalAnswer = answer;
        if (answer.length() > maxAnswerChars) {
            finalAnswer = truncateAtSentence(answer, maxAnswerChars);
        }

        GuardResult ok = GuardResult.pass();
        ok.setAnswer(finalAnswer);
        return ok;
    }

    /**
     * 截断到最后一个完整句，避免把一句话从中间切断。
     *
     * <p>找不到句号时直接硬截——总比返回一段断句强，但会加省略号表明被截断。
     */
    private String truncateAtSentence(String text, int limit) {
        String head = text.substring(0, limit);
        int lastStop = Math.max(
                Math.max(head.lastIndexOf('。'), head.lastIndexOf('.')),
                Math.max(head.lastIndexOf('；'), head.lastIndexOf('\n')));
        if (lastStop > limit / 2) {
            return head.substring(0, lastStop + 1);
        }
        return head + "…";
    }

    /** 校验结果 */
    @Getter
    @ToString
    public static class GuardResult {

        /** 是否通过 */
        private boolean passed;

        /** 越界的数字（未通过时用于日志排查） */
        private final Set<String> violations = new LinkedHashSet<>();

        /** 通过时的最终答案（可能已截断） */
        private String answer;

        /** 未通过原因 */
        private String reason;

        static GuardResult pass() {
            GuardResult r = new GuardResult();
            r.passed = true;
            return r;
        }

        static GuardResult reject(String reason) {
            GuardResult r = new GuardResult();
            r.passed = false;
            r.reason = reason;
            return r;
        }

        void setAnswer(String answer) {
            this.answer = answer;
        }
    }
}
