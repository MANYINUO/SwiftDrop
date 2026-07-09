package com.delivery.ai.guard;

import com.delivery.ai.context.FactSheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 输出守卫测试 —— 验证「数字白名单」这条防幻觉底线。
 *
 * <p>守卫的作用不是让答案变好，而是<b>在答案不可信时触发降级</b>。
 * 因此测试重点有两个方向：
 * <ul>
 *   <li><b>不误杀</b>：合法数字（含 0.8140 / 0.814 这类等价写法）必须通过</li>
 *   <li><b>不放行</b>：事实表里没有的数字必须被拦下</li>
 * </ul>
 * 误杀会让功能不可用，漏放会让错误答案流向用户，两者都要守住。
 */
@DisplayName("输出守卫")
class OutputGuardTest {

    private final OutputGuard guard = new OutputGuard(400);

    private FactSheet factsOf(Object... values) {
        FactSheet sheet = new FactSheet();
        for (Object v : values) {
            sheet.absorb("test", v);
        }
        return sheet;
    }

    @Nested
    @DisplayName("数字校验")
    class NumberCheck {

        @Test
        @DisplayName("事实表里出现过的数字：通过")
        void allowedNumbersPass() {
            // 注意：这里必须把 round 也放进事实表，因为答案里会出现「第 1 轮」。
            // 真实场景中 DispatchTools 返回的每条记录都带 round 字段，所以不会误杀。
            FactSheet facts = factsOf(Map.of(
                    "round", 1,
                    "score", 0.814,
                    "distanceToPickupM", 820,
                    "deliveringCount", 0));

            String answer = "第 1 轮综合得分 0.814 排第一，距离取件点 820 米，手上有 0 单。";
            assertThat(guard.check(answer, facts).isPassed()).isTrue();
        }

        @Test
        @DisplayName("事实表里没有的数字：拦下并记录")
        void unknownNumbersAreRejected() {
            FactSheet facts = factsOf(Map.of("score", 0.814));

            String answer = "综合得分 0.95，距离 999 米。";
            OutputGuard.GuardResult r = guard.check(answer, facts);

            assertThat(r.isPassed()).isFalse();
            assertThat(r.getViolations()).contains("0.95", "999");
        }

        @Test
        @DisplayName("0.8140 与 0.814 视为同一个数字，不误杀")
        void trailingZerosAreNormalized() {
            FactSheet facts = factsOf(Map.of("score", 0.814));

            // 模型输出 0.8140，事实表是 0.814
            assertThat(guard.check("得分 0.8140。", facts).isPassed()).isTrue();
        }

        @Test
        @DisplayName("整数 8 与 8.00 视为同一个数字")
        void integerAndDecimalEquivalent() {
            FactSheet facts = factsOf(Map.of("amount", 8));

            assertThat(guard.check("金额 8.00 元。", facts).isPassed()).isTrue();
        }

        @Test
        @DisplayName("事实表为空时，任何数字都算越界")
        void emptyFactSheetRejectsAllNumbers() {
            FactSheet facts = new FactSheet();

            OutputGuard.GuardResult r = guard.check("得分 0.814。", facts);

            assertThat(r.isPassed()).isFalse();
            assertThat(r.getViolations()).contains("0.814");
        }

        @Test
        @DisplayName("不含数字的纯文字答案：通过")
        void textWithoutNumbersPasses() {
            FactSheet facts = factsOf(Map.of("score", 0.814));

            assertThat(guard.check("这单还没有派单记录。", facts).isPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("拒答与空答案")
    class Refusal {

        @Test
        @DisplayName("空答案：拦下")
        void blankAnswerIsRejected() {
            assertThat(guard.check(null, factsOf()).isPassed()).isFalse();
            assertThat(guard.check("", factsOf()).isPassed()).isFalse();
            assertThat(guard.check("   ", factsOf()).isPassed()).isFalse();
        }

        @Test
        @DisplayName("命中拒答套话：拦下")
        void refusalMarkerIsRejected() {
            FactSheet facts = factsOf(Map.of("score", 0.814));

            assertThat(guard.check("抱歉，我无法回答这个问题。", facts).isPassed()).isFalse();
            assertThat(guard.check("作为 AI，我不能判断。", facts).isPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("长度控制")
    class Length {

        @Test
        @DisplayName("超长答案截断到最后一个完整句")
        void longAnswerIsTruncated() {
            OutputGuard shortGuard = new OutputGuard(30);
            FactSheet facts = factsOf(Map.of("score", 0.814));

            String answer = "得分 0.814。这是第一句话的内容比较长。后面还有更多内容需要被截断掉。";
            OutputGuard.GuardResult r = shortGuard.check(answer, facts);

            assertThat(r.isPassed()).isTrue();
            assertThat(r.getAnswer().length()).isLessThanOrEqualTo(31);
        }
    }

    @Nested
    @DisplayName("数字归一化")
    class Normalize {

        @Test
        @DisplayName("去尾零")
        void stripTrailingZeros() {
            assertThat(FactSheet.normalize("8.00")).isEqualTo("8");
            assertThat(FactSheet.normalize("0.8140")).isEqualTo("0.814");
            assertThat(FactSheet.normalize("0.814")).isEqualTo("0.814");
        }

        @Test
        @DisplayName("整数原样保留")
        void integerUnchanged() {
            assertThat(FactSheet.normalize("820")).isEqualTo("820");
            assertThat(FactSheet.normalize("0")).isEqualTo("0");
        }
    }
}
