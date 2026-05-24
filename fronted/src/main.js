/**
 * 应用入口（学生演示项目）
 *
 * 作用：创建 Vue 应用，注册插件，挂载到页面根节点。
 * 插件顺序注意：状态管理必须在路由之前，因为路由守卫里会读取登录状态。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import './assets/styles/style.css'
import App from './App.vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

const app = createApp(App)
app.use(createPinia())   // 状态管理（登录态等），务必在路由前
app.use(router)          // 路由（内部守卫会依赖状态管理）
app.use(ElementPlus)     // 界面组件库
app.mount('#app')
