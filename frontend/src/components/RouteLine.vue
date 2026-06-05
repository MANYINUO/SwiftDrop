<script setup>
/**
 * 取件 → 送达 路线。
 *
 * 订单列表、骑手工作台、后台表格都要展示同一条路线，
 * 抽出来统一"起终点标记 + 连接线"的画法，避免每个页面各写一套。
 */
defineProps({
  pickup: { type: String, default: '' },
  deliver: { type: String, default: '' },
  /** 右侧补充信息（距离、剩余距离…） */
  pickupMeta: { type: String, default: '' },
  deliverMeta: { type: String, default: '' },
  compact: { type: Boolean, default: false },
})
</script>

<template>
  <div class="route" :class="{ compact }">
    <span class="rail" aria-hidden="true"></span>

    <div class="node">
      <span class="mark start" aria-hidden="true"></span>
      <span class="text">{{ pickup || '—' }}</span>
      <span v-if="pickupMeta" class="meta num">{{ pickupMeta }}</span>
    </div>

    <div class="node">
      <span class="mark end" aria-hidden="true"></span>
      <span class="text">{{ deliver || '—' }}</span>
      <span v-if="deliverMeta" class="meta num">{{ deliverMeta }}</span>
    </div>
  </div>
</template>

<style scoped>
.route {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding-left: 18px;
  min-width: 0;
}

/* 两个标记之间的虚线 */
.rail {
  position: absolute;
  left: 4.5px;
  top: 10px;
  bottom: 10px;
  width: 0;
  border-left: 1.5px dashed var(--n-300);
}

.node {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-width: 0;
}

.mark {
  position: absolute;
  left: -18px;
  width: 10px;
  height: 10px;
  border-radius: 3px;
  border: 2px solid var(--bg-surface);
  box-shadow: 0 0 0 1px currentColor;
}

.mark.start {
  color: #34d399;
  background: #34d399;
}

.mark.end {
  color: var(--brand);
  background: var(--brand);
  border-radius: 50%;
}

.text {
  font-size: var(--fs-13);
  color: var(--text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.meta {
  margin-left: auto;
  flex: 0 0 auto;
  font-size: var(--fs-11);
  color: var(--text-faint);
  padding-left: var(--sp-2);
}

.compact .text {
  font-size: var(--fs-12);
}

.compact {
  gap: 7px;
}

.compact .rail {
  top: 9px;
  bottom: 9px;
}

.compact .mark {
  width: 8px;
  height: 8px;
  left: -17px;
}
</style>
