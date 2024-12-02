// 统一 axios 实例：JWT 自动携带 / 401 跳登录 / 统一解包 {code,message,data}
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const http = axios.create({ baseURL: '/api/v1', timeout: 15000 })

http.interceptors.request.use(cfg => {
  const t = localStorage.getItem('examforge_token')
  if (t) cfg.headers.Authorization = 'Bearer ' + t
  return cfg
})

http.interceptors.response.use(
  res => {
    const j = res.data
    if (j.code !== 0) { ElMessage.error(j.message || '请求失败'); return Promise.reject(j) }
    return j.data
  },
  err => {
    if (err.response?.status === 401) { localStorage.removeItem('examforge_token'); router.push('/login') }
    // 本地演示模式允许服务端暂不可用，页面会回退到内置演示数据。
    return Promise.reject(err)
  }
)

export default http
