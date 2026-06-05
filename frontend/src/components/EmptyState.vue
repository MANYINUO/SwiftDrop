<script setup>
import AppIcon from './AppIcon.vue'

/**
 * 空状态。
 *
 * 空状态是"教育用户的免费位置"，所以这里不用 Element 那张灰色插画，
 * 而是给一句能解释"为什么是空的 / 接下来该做什么"的说明。
 */
defineProps({
  icon: { type: String, default: 'box' },
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  /** 紧凑模式：用于卡片内部 */
  compact: { type: Boolean, default: false },
})
</script>

<template>
  <div class="empty" :class="{ compact }">
    <span class="empty-icon">
      <AppIcon :name="icon" :size="compact ? 18 : 22" :stroke-width="1.6" />
    </span>
    <p class="empty-title">{{ title }}</p>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--sp-7) var(--sp-5);
  gap: 4px;
}

.empty.compact {
  padding: var(--sp-5) var(--sp-4);
}

.empty-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--r-lg);
  display: grid;
  place-items: center;
  background: var(--n-100);
  color: var(--n-400);
  border: 1px solid var(--border-subtle);
  margin-bottom: 6px;
}

.compact .empty-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--r-md);
}

.empty-title {
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--text-muted);
}

.empty-desc {
  font-size: var(--fs-12);
  color: var(--text-faint);
  line-height: 1.6;
  max-width: 34ch;
}

.empty-action {
  margin-top: var(--sp-3);
}
</style>
