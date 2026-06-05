<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { riderApi, authApi } from '../../api'
import auth from '../../utils/auth'
import AppIcon from '../../components/AppIcon.vue'

const route = useRoute()
const router = useRouter()

const riders = ref([])
const loadingList = ref(true)
const selectedId = ref(null)
const pin = ref('')
const loading = ref(false)

onMounted(async () => {
  if (auth.isLoggedIn(auth.RIDER)) {
    router.replace(`/rider/${auth.getId(auth.RIDER)}`)
    return
  }
  await loadOptions()
})

async function loadOptions() {
  loadingList.value = true
  try {
    const list = await riderApi.options()
    riders.value = Array.isArray(list) ? list : []
    // 默认选中第一位骑手
    if (riders.value.length) {
      selectedId.value = riders.value[0].riderId
    }
  } catch (e) {
    ElMessage.error(e?.message || '骑手名单加载失败，请稍后重试')
  } finally {
    loadingList.value = false
  }
}

function choose(id) {
  selectedId.value = id
}

function onPinInput(v) {
  // 只保留数字、最多 4 位
  pin.value = v.replace(/\D/g, '').slice(0, 4)
}

async function onSubmit() {
  if (loading.value) return
  if (!selectedId.value) {
    ElMessage.warning('请先选择骑手账号')
    return
  }
  if (pin.value.length !== 4) {
    ElMessage.warning('请输入 4 位校验码（手机号后 4 位）')
    return
  }
  loading.value = true
  try {
    const data = await authApi.riderLogin(selectedId.value, pin.value)
    auth.set(auth.RIDER, {
      token: data.token,
      id: data.id,
      name: data.name,
      riderNo: data.riderNo,
      role: data.role,
    })
    ElMessage.success(`欢迎，${data.name}`)
    // 只在 redirect 指向本人工作台时回跳，避免登录后落到他人编号
    const own = `/rider/${data.id}`
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith(own)
      ? route.query.redirect
      : own
    router.replace(redirect)
  } catch (e) {
    ElMessage.error(e?.message || '登录失败，请检查骑手编号和校验码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card rider-card">
      <span class="auth-badge">
        <AppIcon name="scooter" :size="20" :stroke-width="1.9" />
      </span>
      <h1 class="auth-title">骑手登录</h1>
      <p class="auth-sub">选择你的账号，并输入手机号后 4 位校验码。</p>

      <div class="rider-list">
        <div v-if="loadingList" class="state">正在加载骑手名单…</div>
        <div v-else-if="!riders.length" class="state">暂无可登录的骑手账号</div>
        <button
          v-for="r in riders"
          v-else
          :key="r.riderId"
          type="button"
          class="rider-item"
          :class="{ active: r.riderId === selectedId }"
          @click="choose(r.riderId)"
        >
          <span class="rider-name">{{ r.name }}</span>
          <span class="rider-no">{{ r.riderNo }}</span>
          <span class="rider-online" :class="r.online === 1 ? 'on' : 'off'">
            {{ r.online === 1 ? '在线' : '离线' }}
          </span>
          <span v-if="r.riderId === selectedId" class="rider-check">
            <AppIcon name="check" :size="14" :stroke-width="2.6" />
          </span>
        </button>
      </div>

      <form class="auth-form" @submit.prevent="onSubmit">
        <label class="field">
          <span class="field-label">校验码（手机号后 4 位）</span>
          <input
            :value="pin"
            class="field-input pin-input"
            type="text"
            inputmode="numeric"
            placeholder="例如 0001"
            maxlength="4"
            @input="onPinInput($event.target.value)"
          />
        </label>
        <button class="auth-submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '进入工作台' }}
        </button>
      </form>

      <router-link class="auth-back" to="/">返回首页</router-link>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 72vh;
  display: grid;
  place-items: center;
  padding: var(--sp-7) var(--gutter);
}

.auth-card {
  width: 100%;
  max-width: 460px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--r-xl);
  box-shadow: var(--sh-md);
  padding: var(--sp-7) var(--sp-6) var(--sp-6);
  display: flex;
  flex-direction: column;
}

.auth-badge {
  width: 46px;
  height: 46px;
  border-radius: var(--r-lg);
  display: grid;
  place-items: center;
  background: linear-gradient(150deg, var(--brand-50), var(--brand-100));
  color: var(--brand-600);
  border: 1px solid var(--brand-100);
  margin-bottom: var(--sp-4);
}

.auth-title {
  font-size: var(--fs-22);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.02em;
}

.auth-sub {
  font-size: var(--fs-13);
  color: var(--text-muted);
  margin: 6px 0 var(--sp-5);
  line-height: 1.6;
}

.rider-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
  margin-bottom: var(--sp-5);
}

.state {
  grid-column: 1 / -1;
  padding: var(--sp-5);
  text-align: center;
  font-size: var(--fs-13);
  color: var(--text-faint);
  border: 1px dashed var(--border-strong);
  border-radius: var(--r-md);
}

.rider-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid var(--border);
  background: var(--n-0);
  cursor: pointer;
  text-align: left;
  transition: border-color var(--dur-fast), background var(--dur-fast), box-shadow var(--dur-fast);
}

.rider-item:hover {
  border-color: var(--brand-300);
}

.rider-item.active {
  border-color: var(--brand);
  background: var(--brand-50);
  box-shadow: 0 0 0 3px var(--brand-ring);
}

.rider-name {
  font-size: var(--fs-15);
  font-weight: 700;
  color: var(--text-strong);
}

.rider-no {
  font-size: var(--fs-12);
  color: var(--text-muted);
  font-family: var(--num-font);
}

.rider-online {
  margin-top: 2px;
  font-size: var(--fs-11);
  padding: 1px 8px;
  border-radius: var(--r-full);
}

.rider-online.on {
  color: var(--ok);
  background: var(--ok-bg);
}

.rider-online.off {
  color: var(--text-faint);
  background: var(--n-100);
}

.rider-check {
  position: absolute;
  top: 10px;
  right: 10px;
  color: var(--brand);
  display: grid;
  place-items: center;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.field-label {
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--text);
}

.field-input {
  height: 42px;
  padding: 0 13px;
  border-radius: var(--r-md);
  border: 1px solid var(--border-strong);
  background: var(--n-0);
  font-size: var(--fs-14);
  color: var(--text-strong);
  font-family: inherit;
  transition: border-color var(--dur-fast), box-shadow var(--dur-fast);
}

.pin-input {
  font-family: var(--num-font);
  letter-spacing: 0.3em;
  font-size: var(--fs-16);
}

.field-input::placeholder {
  color: var(--text-faint);
  letter-spacing: normal;
}

.field-input:focus {
  outline: none;
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-ring);
}

.auth-submit {
  margin-top: var(--sp-2);
  height: 44px;
  border: none;
  border-radius: var(--r-md);
  background: var(--brand);
  color: #fff;
  font-size: var(--fs-15);
  font-weight: 600;
  letter-spacing: 0.08em;
  cursor: pointer;
  transition: background var(--dur-fast), box-shadow var(--dur-fast), transform var(--dur-fast);
}

.auth-submit:hover:not(:disabled) {
  background: var(--brand-hover);
  box-shadow: var(--sh-brand);
}

.auth-submit:active:not(:disabled) {
  transform: scale(0.985);
}

.auth-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.auth-back {
  margin-top: var(--sp-4);
  text-align: center;
  font-size: var(--fs-13);
  color: var(--text-muted);
}

.auth-back:hover {
  color: var(--brand-600);
}
</style>
