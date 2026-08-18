const TOKEN_KEY = 'miruplay_web_token'

export function getWebControlToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || ''
  } catch {
    return ''
  }
}

export function setWebControlToken(token) {
  try {
    if (token) {
      localStorage.setItem(TOKEN_KEY, token)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  } catch {
    // localStorage 不可用时降级为仅 cookie 认证
  }
}

export async function api(path, options = {}) {
  const isBlobBody = typeof Blob !== 'undefined' && options.body instanceof Blob
  const token = getWebControlToken()
  const headers = {
    ...(isBlobBody ? {} : { 'Content-Type': 'application/json; charset=utf-8' }),
    ...(token ? { 'X-MiruPlay-Token': token } : {}),
    ...(options.headers || {})
  }
  const response = await fetch(path, {
    ...options,
    headers
  })
  const envelope = await response.json()
  if (!response.ok || !envelope.ok) {
    if (response.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('miruplay:unauthorized', { detail: { token } }))
    }
    const error = new Error(envelope.error || `HTTP ${response.status}`)
    error.status = response.status
    throw error
  }
  return envelope.data
}

export function getTranslationSettings() {
  return api('/api/settings/translation')
}

export function setTranslationSettings(payload) {
  return api('/api/settings/translation', {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function formatTime(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return '00:00'
  const total = Math.floor(ms / 1000)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export function titleOf(anime) {
  return anime?.title || anime?.titleCn || anime?.id || '未知番剧'
}

export function originalTitleOf(anime) {
  const primary = titleOf(anime)
  const candidates = [anime?.titleCn, anime?.id].filter(Boolean)
  return candidates.find((value) => value && value !== primary) || ''
}
