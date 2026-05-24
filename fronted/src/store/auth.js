/**
 * 登录状态仓库（学生演示项目）
 *
 * 作用：集中存放令牌与用户信息，并持久化到本地存储，刷新页面不丢失。
 * - 令牌：请求头认证信息会用到（见请求拦截器）。
 * - 用户信息：如用户ID、用户名、邮箱，可用于顶栏展示。
 * - 写入登录信息：登录成功后调用，写入状态并同步到本地存储。
 * - 清空登录信息：退出登录时调用，清空状态与本地存储。
 */
import { defineStore } from 'pinia'

const TOKEN_KEY = 'mengtu_token'
const USER_KEY = 'mengtu_user'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: (() => {
      const raw = localStorage.getItem(USER_KEY)
      try {
        return raw ? JSON.parse(raw) : null
      } catch {
        return null
      }
    })(),
  }),
  getters: {
    /** 是否已登录（路由守卫与顶栏根据此判断） */
    isLoggedIn: (state) => !!state.token,
  },
  actions: {
    /** 登录成功：写入令牌与用户信息，并持久化 */
    setAuth(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem(TOKEN_KEY, token)
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },
    /** 退出登录：清空内存与本地存储 */
    clear() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})
