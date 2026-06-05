<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../../api'
import auth from '../../utils/auth'
import AppIcon from '../../components/AppIcon.vue'

const route = useRoute()
const router = useRouter()

const username = ref('admin')
const password = ref('')
const loading = ref(false)

onMounted(() => {
  // 已登录则直接进后台，不必再登录
  if (auth.isLoggedIn(auth.ADMIN)) {
    router.replace('/admin/dashboard')
  }
})

async function onSubmit() {
  if (loading.value) return
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const data = await authApi.adminLogin(username.value.trim(), password.value)
    auth.set(auth.ADMIN, { token: data.token, id: data.id, name: data.name, role: data.role })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/admin/')
      ? route.query.redirect
      : '/admin/dashboard'
    router.replace(redirect)
  } catch (e) {
    ElMessage.error(e?.message || '登录失败，请检查账号和密码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <span class="auth-badge">
        <AppIcon name="gauge" :size="20" :stroke-width="1.9" />
      </span>
      <h1 class="auth-title">调度后台登录</h1>
      <p class="auth-sub">面向调度管理人员，请使用管理员账号登录。</p>

      <form class="auth-form" @submit.prevent="onSubmit">
        <label class="field">
          <span class="field-label">账号</span>
          <input
            v-model="username"
            class="field-input"
            type="text"
            placeholder="请输入账号"
            autocomplete="username"
          />
        </label>
        <label class="field">
          <span class="field-label">密码</span>
          <input
            v-model="password"
            class="field-input"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </label>
        <button class="auth-submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
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
  max-width: 400px;
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
  margin: 6px 0 var(--sp-6);
  line-height: 1.6;
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

.field-input::placeholder {
  color: var(--text-faint);
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
