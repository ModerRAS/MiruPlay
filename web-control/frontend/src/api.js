export async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      ...(options.headers || {})
    },
    ...options
  })
  const envelope = await response.json()
  if (!response.ok || !envelope.ok) {
    throw new Error(envelope.error || `HTTP ${response.status}`)
  }
  return envelope.data
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
