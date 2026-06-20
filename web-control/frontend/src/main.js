import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import { setWebControlToken } from './api.js'
import './styles.css'

// ponytail: 首次通过 ?token= 进入时存入 localStorage，供后续 API 头携带；轮换令牌后也能复用。
try {
  const params = new URLSearchParams(window.location.search)
  const token = params.get('token')
  if (token) {
    setWebControlToken(token)
    const cleanUrl = window.location.origin + window.location.pathname
    window.history.replaceState({}, document.title, cleanUrl)
  }
} catch {
  // 忽略 token 解析失败
}

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus)
app.mount('#app')
