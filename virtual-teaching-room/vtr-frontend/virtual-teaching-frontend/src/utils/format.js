import dayjs from 'dayjs'

export function formatDateTime(value, template = 'YYYY-MM-DD HH:mm') {
  if (!value) {
    return '--'
  }

  return dayjs(value).format(template)
}

export function formatFileSize(size) {
  if (size === null || size === undefined || Number.isNaN(Number(size))) {
    return '--'
  }

  const units = ['B', 'KB', 'MB', 'GB']
  let current = Number(size)
  let index = 0

  while (current >= 1024 && index < units.length - 1) {
    current /= 1024
    index += 1
  }

  return `${current.toFixed(current >= 100 || index === 0 ? 0 : 1)} ${units[index]}`
}

export function optionLabel(options, value, fallback = '--') {
  return options.find((item) => item.value === value)?.label || value || fallback
}

export function resolveAssetUrl(url) {
  if (!url) {
    return ''
  }

  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url
  }

  return url
}

export function resolveAvatarUrl(url) {
  return resolveAssetUrl(url) || '/uploads/default-avatar.png'
}

export function parseTags(value) {
  if (Array.isArray(value)) {
    return value.filter(Boolean)
  }

  return String(value || '')
    .split(/[，,]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export function toMultilineText(value) {
  if (Array.isArray(value)) {
    return value.join('\n')
  }

  return value || ''
}

export function splitLines(value) {
  return String(value || '')
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean)
}
