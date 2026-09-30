const AUTH_KEY = 'vtr_auth'

export function persistAuth(payload) {
  localStorage.setItem(AUTH_KEY, JSON.stringify(payload))
}

export function getPersistedAuth() {
  try {
    const raw = localStorage.getItem(AUTH_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (error) {
    return null
  }
}

export function getToken() {
  return getPersistedAuth()?.token || ''
}

export function clearPersistedAuth() {
  localStorage.removeItem(AUTH_KEY)
}
