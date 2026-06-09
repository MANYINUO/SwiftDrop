import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import { authApi } from './api'
import auth from './utils/auth'
import './styles/index.css'

/*
 * 入场动画渐进增强：
 * 仅当页面动画时间轴确认在正常推进（处于前台、没有被浏览器节流或冻结）时，才给 <html> 加 .motion，
 * 样式中的错位淡入（rise-in）才会启用。这样在后台标签、无头 / 截图等动画被冻结的环境，
 * 内容默认就是可见的，不会停在透明首帧；用户开启系统"减弱动效"时同样直接显示。
 */
;(function () {
  let firstTs = null
  function tick(t) {
    if (firstTs === null) {
      firstTs = t
      requestAnimationFrame(tick)
    } else if (t - firstTs > 0) {
      document.documentElement.classList.add('motion')
    }
  }
  requestAnimationFrame(tick)
})()

const app = createApp(App)

/*
 * 这里刻意不再全量注册 @element-plus/icons-vue。
 * 图标统一走自研的 <AppIcon />（见 components/AppIcon.vue）：
 * 描边粗细、网格、圆角都由我们控制，视觉更统一，也省掉几百 KB。
 */
app.use(ElementPlus, { locale: zhCn })
app.use(router)

/*
 * 顾客端免登录：挂载前确保有一个匿名游客身份。
 * 领不到（如 Redis 没启动）也先挂载，首页和登录页照常可看，进入用户端时再提示。
 */
async function ensureGuestThenMount() {
  if (!auth.isLoggedIn(auth.USER)) {
    try {
      const data = await authApi.guest()
      auth.set(auth.USER, { token: data.token, id: data.id, name: data.name, role: data.role })
      // 成功拿到游客身份，重置 401 自愈标记
      sessionStorage.removeItem('delivery_guest_reloaded')
    } catch (e) {
      console.warn('游客身份领取失败，顾客端暂不可用：', e?.message || e)
    }
  } else {
    sessionStorage.removeItem('delivery_guest_reloaded')
  }
  app.mount('#app')
}

ensureGuestThenMount()
