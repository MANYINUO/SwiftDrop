import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
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
 * F1 阶段：后端还没有鉴权，直接用固定用户 ID 1001。
 * 后面 F5 鉴权模块做完后，再改成从 /auth/guest 领游客令牌。
 */
if (!auth.isLoggedIn(auth.USER)) {
  auth.set(auth.USER, { token: 'dev-user-token', id: 1001, name: '演示用户', role: 'USER' })
}
app.mount('#app')
