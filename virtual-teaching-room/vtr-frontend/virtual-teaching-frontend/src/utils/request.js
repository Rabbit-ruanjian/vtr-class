import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearPersistedAuth, getToken } from '@/utils/auth'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

let authRedirecting = false

function handleUnauthorized() {
  clearPersistedAuth()

  if (authRedirecting) {
    return
  }

  authRedirecting = true
  window.setTimeout(() => {
    authRedirecting = false
  }, 800)

  if (!window.location.pathname.startsWith('/login')) {
    window.location.assign(`/login?redirect=${encodeURIComponent(window.location.pathname)}`)
  }
}

request.interceptors.request.use((config) => {
  const token = getToken()

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

request.interceptors.response.use(
  (response) => {
    const payload = response.data

    if (!payload || typeof payload !== 'object' || !('code' in payload)) {
      return payload
    }

    if (payload.code === 200) {
      return payload.data
    }

    if (payload.code === 401) {
      handleUnauthorized()
    }

    if (!response.config.silent) ElMessage.error(payload.message || '请求失败')
    return Promise.reject(new Error(payload.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const message =
      error.response?.data?.message ||
      error.message ||
      '网络请求失败'

    if (status === 401) {
      handleUnauthorized()
    } else if (status === 403 && !error.config?.silent) {
      ElMessage.error('当前账号没有执行该操作的权限')
    } else if (!error.config?.silent) {
      ElMessage.error(message)
    }

    // AI 组件需要把后端给出的可执行提示（例如 Key、模型或网络问题）显示在重试气泡中，
    // 而不是只显示 Axios 的“Request failed with status code ...”。
    if (error && message) {
      error.userMessage = message
      error.message = message
    }

    return Promise.reject(error)
  }
)

export function postForm(url, formData, config = {}) {
  // 让浏览器/axios 自动补 multipart boundary，手动写 Content-Type 会导致部分浏览器上传失败。
  const headers = { ...(config.headers || {}) }
  delete headers['Content-Type']
  delete headers['content-type']
  return request.post(url, formData, { ...config, headers })
}

export default request
