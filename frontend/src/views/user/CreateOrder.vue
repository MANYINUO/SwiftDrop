<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '../../api'
import { toastError } from '../../api/http'
import { distanceMeters } from '../../utils/geo'
import { fenToYuan, formatDistance } from '../../utils/format'
import AppIcon from '../../components/AppIcon.vue'
import MiniMap from '../../components/MiniMap.vue'

/**
 * 创建订单。
 *
 * 响应式：
 *   ≥1024px  左（取件 / 送达 / 物品三张卡） + 右（路线预览粘性侧栏，含提交按钮）
 *   <1024px  单列；路线预览排在表单之后；提交按钮吸底
 *
 * 取件 / 送达坐标由用户在右侧示意地图上点选（PICK_BOUNDS 为固定城区窗口），
 * 详细地址与联系人信息手动填写；提交走 userApi.createOrder。
 */
const router = useRouter()
const loading = ref(false)

/**
 * 选点地图的可选区域边界。
 *
 * 地图基于高德 JS API，用户在范围内点选即可获得 GCJ-02 经纬度，详细地址与
 * 联系人信息仍需手动填写。边界限定在骑手当前覆盖的北京城区（约 7km × 6km），
 * 避免选到暂无骑手服务的区域而无法派单。
 */
const PICK_BOUNDS = {
  minLng: 116.375,
  maxLng: 116.445,
  minLat: 39.89,
  maxLat: 39.94,
}

const form = reactive({
  pickup: { lng: null, lat: null, address: '', contact: '', phone: '' },
  deliver: { lng: null, lat: null, address: '', contact: '', phone: '' },
  goodsDesc: '',
  goodsWeightG: null,
})

/** 当前要在地图上标注的环节：pickup / deliver */
const picking = ref('pickup')

function hasPoint(leg) {
  return form[leg].lng != null && form[leg].lat != null
}

function setPicking(leg) {
  picking.value = leg
}

/** 在地图上点击后回填坐标，并自动切到另一个尚未选择的环节 */
function onMapPick(ll) {
  const leg = picking.value
  form[leg].lng = ll.lng
  form[leg].lat = ll.lat
  const other = leg === 'pickup' ? 'deliver' : 'pickup'
  if (!hasPoint(other)) picking.value = other
}

const pickHint = computed(() => {
  if (hasPoint('pickup') && hasPoint('deliver')) return '取送点已选，点击地图可重新选择'
  return picking.value === 'pickup' ? '点击地图选择取件点' : '点击地图选择送达点'
})

/* ==================== 路线预览 ==================== */
/* 纯前端估算，只为下单前给个量级感受；真实金额以后端返回为准。 */

const previewPickup = computed(() =>
  hasPoint('pickup')
    ? { lng: Number(form.pickup.lng), lat: Number(form.pickup.lat) }
    : null,
)
const previewDeliver = computed(() =>
  hasPoint('deliver')
    ? { lng: Number(form.deliver.lng), lat: Number(form.deliver.lat) }
    : null,
)

const estDistanceM = computed(() => {
  if (!previewPickup.value || !previewDeliver.value) return null
  return distanceMeters(
    previewPickup.value.lng,
    previewPickup.value.lat,
    previewDeliver.value.lng,
    previewDeliver.value.lat,
  )
})

/** 起步价 8 元含 3 公里，超出每公里 2 元；未选齐两点时不估价 */
const estPriceFen = computed(() => {
  if (estDistanceM.value == null) return null
  const km = estDistanceM.value / 1000
  const yuan = 8 + Math.max(0, km - 3) * 2
  return Math.round(yuan * 100)
})

/* ==================== 提交 ==================== */

const submitting = ref(false)

const PHONE_RE = /^1[3-9]\d{9}$/

async function submit() {
  if (!hasPoint('pickup') || !hasPoint('deliver')) {
    ElMessage.warning('请先在右侧地图上选取取件点和送达点')
    return
  }
  const legs = [
    ['pickup', '取件'],
    ['deliver', '送达'],
  ]
  for (const [leg, label] of legs) {
    if (!form[leg].address?.trim()) {
      ElMessage.warning(`${label}详细地址不能为空`)
      return
    }
    if (!form[leg].contact?.trim()) {
      ElMessage.warning(`${label}联系人不能为空`)
      return
    }
    if (!form[leg].phone?.trim()) {
      ElMessage.warning(`${label}联系电话不能为空`)
      return
    }
    if (!PHONE_RE.test(form[leg].phone.trim())) {
      ElMessage.warning(`${label}手机号格式不正确`)
      return
    }
  }

  loading.value = true
  submitting.value = true
  try {
    // 幂等键：同一次提交用同一个 key，重复点击不会产生重复订单。
    // 这里每次点击生成新 key（代表"新的一次下单"）；
    const idempotencyKey = `web-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

    const order = await userApi.createOrder(
      {
        pickup: pick(form.pickup),
        deliver: pick(form.deliver),
        goodsDesc: form.goodsDesc,
        goodsWeightG: Number(form.goodsWeightG) || undefined,
      },
      idempotencyKey,
    )

    ElMessage.success(`下单成功 · ${order.orderNo}`)
    setTimeout(() => router.push(`/user/orders/${order.orderNo}`), 420)
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
    submitting.value = false
  }
}

function pick(a) {
  return {
    lng: a.lng,
    lat: a.lat,
    address: a.address,
    contact: a.contact,
    phone: a.phone,
  }
}
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head-main">
        <span class="eyebrow">在线下单</span>
        <h1 class="page-title">创建订单</h1>
      </div>
      <div class="page-head-actions">
        <router-link to="/user/orders" class="ghost-btn">
          <AppIcon name="list" :size="14" />
          我的订单
        </router-link>
      </div>
    </header>

    <div class="split">
      <!-- ==================== 主列：表单 ==================== -->
      <div class="stack-5">
        <!-- ---------- 取件 ---------- -->
        <section class="card" style="--i: 0">
          <div class="block-head">
            <span class="leg-tag start">
              <AppIcon name="pin" :size="12" :stroke-width="2.2" />
            </span>
            <span class="block-title">取件信息</span>
          </div>

          <el-form label-position="top">
            <el-form-item label="详细地址">
              <el-input v-model="form.pickup.address" placeholder="例如：望京SOHO T1 3 号楼">
                <template #prefix>
                  <AppIcon name="pin" :size="14" class="ipt-icon" />
                </template>
              </el-input>
            </el-form-item>

            <button
              type="button"
              class="coord-status"
              :class="hasPoint('pickup') ? 'set' : 'unset'"
              @click="setPicking('pickup')"
            >
              <AppIcon name="pin" :size="13" :stroke-width="2.2" />
              <span v-if="hasPoint('pickup')" class="num">
                取件点 {{ Number(form.pickup.lng).toFixed(5) }}, {{ Number(form.pickup.lat).toFixed(5) }}（点击重选）
              </span>
              <span v-else>尚未在地图选取件点，点击在此标注</span>
            </button>

            <div class="grid-2">
              <el-form-item label="联系人">
                <el-input v-model="form.pickup.contact" placeholder="姓名">
                  <template #prefix>
                    <AppIcon name="user" :size="14" class="ipt-icon" />
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="电话">
                <el-input v-model="form.pickup.phone" placeholder="手机号">
                  <template #prefix>
                    <AppIcon name="phone" :size="14" class="ipt-icon" />
                  </template>
                </el-input>
              </el-form-item>
            </div>
          </el-form>
        </section>

        <!-- ---------- 送达 ---------- -->
        <section class="card" style="--i: 1">
          <div class="block-head">
            <span class="leg-tag end">
              <AppIcon name="flag" :size="12" :stroke-width="2.2" />
            </span>
            <span class="block-title">送达信息</span>
          </div>

          <el-form label-position="top">
            <el-form-item label="详细地址">
              <el-input v-model="form.deliver.address" placeholder="例如：酒仙桥路 10 号 A 座">
                <template #prefix>
                  <AppIcon name="flag" :size="14" class="ipt-icon" />
                </template>
              </el-input>
            </el-form-item>

            <button
              type="button"
              class="coord-status"
              :class="hasPoint('deliver') ? 'set' : 'unset'"
              @click="setPicking('deliver')"
            >
              <AppIcon name="flag" :size="13" :stroke-width="2.2" />
              <span v-if="hasPoint('deliver')" class="num">
                送达点 {{ Number(form.deliver.lng).toFixed(5) }}, {{ Number(form.deliver.lat).toFixed(5) }}（点击重选）
              </span>
              <span v-else>尚未在地图选送达点，点击在此标注</span>
            </button>

            <div class="grid-2">
              <el-form-item label="联系人">
                <el-input v-model="form.deliver.contact" placeholder="姓名">
                  <template #prefix>
                    <AppIcon name="user" :size="14" class="ipt-icon" />
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="电话">
                <el-input v-model="form.deliver.phone" placeholder="手机号">
                  <template #prefix>
                    <AppIcon name="phone" :size="14" class="ipt-icon" />
                  </template>
                </el-input>
              </el-form-item>
            </div>
          </el-form>
        </section>

        <!-- ---------- 物品 ---------- -->
        <section class="card" style="--i: 2">
          <div class="block-head">
            <span class="leg-tag item">
              <AppIcon name="box" :size="12" :stroke-width="2.2" />
            </span>
            <span class="block-title">物品信息</span>
          </div>

          <el-form label-position="top">
            <div class="grid-2">
              <el-form-item label="物品描述">
                <el-input v-model="form.goodsDesc" placeholder="例如：文件一份">
                  <template #prefix>
                    <AppIcon name="box" :size="14" class="ipt-icon" />
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="重量（克）">
                <el-input v-model="form.goodsWeightG" type="number" placeholder="500" />
              </el-form-item>
            </div>
          </el-form>
        </section>
      </div>

      <!-- ==================== 侧栏：路线预览 + 提交 ==================== -->
      <aside class="split-aside">
        <div class="card preview-card">
          <div class="section-title">路线预览</div>

          <div class="pick-switch">
            <button
              type="button"
              class="pick-leg start"
              :class="{ active: picking === 'pickup', done: hasPoint('pickup') }"
              @click="setPicking('pickup')"
            >
              <AppIcon name="pin" :size="13" :stroke-width="2.3" />
              {{ hasPoint('pickup') ? '取件点已选' : '① 点选取件点' }}
            </button>
            <button
              type="button"
              class="pick-leg end"
              :class="{ active: picking === 'deliver', done: hasPoint('deliver') }"
              @click="setPicking('deliver')"
            >
              <AppIcon name="flag" :size="13" :stroke-width="2.3" />
              {{ hasPoint('deliver') ? '送达点已选' : '② 点选送达点' }}
            </button>
          </div>

          <MiniMap
            :pickup="previewPickup"
            :deliver="previewDeliver"
            :height="240"
            pickable
            :bounds="PICK_BOUNDS"
            :pick-hint="pickHint"
            @pick="onMapPick"
          />

          <div class="preview-stats">
            <div class="stat">
              <span class="stat-label">预估距离</span>
              <span class="stat-value num">{{ formatDistance(estDistanceM) }}</span>
            </div>
            <div class="stat-sep"></div>
            <div class="stat">
              <span class="stat-label">预估费用</span>
              <span class="stat-value num brand">¥{{ fenToYuan(estPriceFen) }}</span>
            </div>
          </div>

          <!-- 桌面端：提交按钮放在侧栏里 -->
          <div class="submit-inline only-desktop">
            <el-button
              type="primary"
              size="large"
              class="submit-btn"
              :loading="loading"
              :disabled="submitting"
              @click="submit"
            >
              立即下单
              <AppIcon v-if="!loading" name="arrow-right" :size="16" :stroke-width="2.4" />
            </el-button>
          </div>
        </div>
      </aside>
    </div>

    <!-- ==================== 移动端吸底提交 ==================== -->
    <div class="sticky-actions only-mobile">
      <div class="submit-price">
        <span class="submit-label">预估</span>
        <span class="submit-amount num">¥{{ fenToYuan(estPriceFen) }}</span>
      </div>
      <el-button
        type="primary"
        size="large"
        class="submit-btn"
        :loading="loading"
        :disabled="submitting"
        @click="submit"
      >
        立即下单
        <AppIcon v-if="!loading" name="arrow-right" :size="16" :stroke-width="2.4" />
      </el-button>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 分区头 ---------- */
.block-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
}

.block-title {
  font-size: var(--fs-14);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.01em;
}

.leg-tag {
  width: 22px;
  height: 22px;
  border-radius: var(--r-sm);
  display: grid;
  place-items: center;
  flex: 0 0 22px;
}

.leg-tag.start {
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.leg-tag.end {
  background: var(--brand-50);
  color: var(--brand-600);
  border: 1px solid var(--brand-100);
}

.leg-tag.item {
  background: var(--n-100);
  color: var(--text-muted);
  border: 1px solid var(--border);
}

/* ---------- 地图选点切换 ---------- */
.pick-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.pick-leg {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  padding: 0 10px;
  border-radius: var(--r-md);
  border: 1px solid var(--border-strong);
  background: var(--n-0);
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out-quart);
  white-space: nowrap;
}

.pick-leg:hover {
  color: var(--text-strong);
}

.pick-leg.start.active {
  border-color: #059669;
  color: #059669;
  background: #ecfdf5;
}

.pick-leg.end.active {
  border-color: var(--brand);
  color: var(--brand-600);
  background: var(--brand-50);
}

/* ---------- 坐标状态行 ---------- */
.coord-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  margin: -2px 0 var(--sp-3);
  padding: 0;
  border: none;
  background: transparent;
  font-size: var(--fs-12);
  line-height: 1.5;
  cursor: pointer;
  text-align: left;
}

.coord-status .num {
  font-family: var(--num-font);
}

.coord-status.set {
  color: var(--ok);
}

.coord-status.unset {
  color: var(--brand-600);
}

.coord-status:hover {
  text-decoration: underline;
}

.grid-2 {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--sp-3);
}

@media (min-width: 640px) {
  .grid-2 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.ipt-icon {
  color: var(--n-400);
}

/* ---------- 路线预览 ---------- */
.preview-stats {
  display: flex;
  align-items: center;
  margin-top: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--n-50);
  border: 1px solid var(--border-subtle);
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.stat-label {
  font-size: var(--fs-11);
  color: var(--text-faint);
}

.stat-value {
  font-size: var(--fs-15);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.02em;
}

.stat-value.brand {
  color: var(--brand-600);
}

.stat-sep {
  width: 1px;
  align-self: stretch;
  background: var(--border);
  margin: 0 var(--sp-4);
}

/* ---------- 提交 ---------- */
.submit-inline {
  margin-top: var(--sp-5);
  padding-top: var(--sp-5);
  border-top: 1px solid var(--border-subtle);
}

.submit-btn {
  width: 100%;
}

.submit-btn :deep(span) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.submit-price {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 0 0 auto;
}

.submit-label {
  font-size: var(--fs-11);
  color: var(--text-faint);
}

.submit-amount {
  font-size: var(--fs-18);
  font-weight: 700;
  color: var(--brand-600);
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.sticky-actions .submit-btn {
  flex: 1;
  width: auto;
}

/* ---------- 通用按钮 ---------- */
.ghost-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--r-md);
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--text);
  background: var(--bg-surface);
  border: 1px solid var(--border-strong);
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.ghost-btn:hover {
  color: var(--text-strong);
  background: var(--n-50);
}
</style>
