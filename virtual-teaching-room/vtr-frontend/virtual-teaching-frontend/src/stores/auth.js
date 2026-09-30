import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '@/api'
import {
  clearPersistedAuth,
  getPersistedAuth,
  persistAuth
} from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref('')
  const tokenType = ref('Bearer')
  const expiresIn = ref(0)
  const user = ref(null)
  const bootstrapped = ref(false)

  const isLoggedIn = computed(() => Boolean(token.value))
  const role = computed(() => user.value?.role || '')
  const isAdmin = computed(() => ['ADMIN', 'ADMINISTRATOR', 'SUPER_ADMIN'].includes(role.value))
  const isTeacher = computed(() => ['TEACHER', 'ADMIN', 'ADMINISTRATOR', 'SUPER_ADMIN'].includes(role.value))
  const isStudent = computed(() => role.value === 'STUDENT')

  function setAuth(payload = {}) {
    token.value = payload.token || ''
    tokenType.value = payload.tokenType || 'Bearer'
    expiresIn.value = payload.expiresIn || 0
    user.value = payload.userInfo || payload.user || null
    persistAuth({
      token: token.value,
      tokenType: tokenType.value,
      expiresIn: expiresIn.value,
      user: user.value
    })
  }

  function patchUser(nextUser) {
    user.value = nextUser
    persistAuth({
      token: token.value,
      tokenType: tokenType.value,
      expiresIn: expiresIn.value,
      user: user.value
    })
  }

  function clear() {
    token.value = ''
    tokenType.value = 'Bearer'
    expiresIn.value = 0
    user.value = null
    clearPersistedAuth()
  }

  async function fetchMe() {
    if (!token.value) {
      return null
    }

    const currentUser = await authApi.me()
    patchUser(currentUser)
    return currentUser
  }

  async function login(payload) {
    const data = await authApi.login(payload)
    setAuth(data)

    if (!user.value) {
      await fetchMe()
    }

    return data
  }

  async function codeLogin(payload) {
    const data = await authApi.codeLogin(payload)
    setAuth(data)

    if (!user.value) {
      await fetchMe()
    }

    return data
  }

  async function register(payload) {
    return authApi.register(payload)
  }

  async function bootstrap() {
    if (bootstrapped.value) {
      return
    }

    const persisted = getPersistedAuth()

    if (persisted?.token) {
      token.value = persisted.token
      tokenType.value = persisted.tokenType || 'Bearer'
      expiresIn.value = persisted.expiresIn || 0
      user.value = persisted.user || null

      try {
        await fetchMe()
      } catch (error) {
        clear()
      }
    }

    bootstrapped.value = true
  }

  return {
    token,
    tokenType,
    expiresIn,
    user,
    bootstrapped,
    isLoggedIn,
    role,
    isAdmin,
    isTeacher,
    isStudent,
    setAuth,
    patchUser,
    clear,
    fetchMe,
    login,
    codeLogin,
    register,
    bootstrap
  }
})
