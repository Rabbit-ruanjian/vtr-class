export function normalizePage(raw) {
  if (!raw) {
    return {
      list: [],
      total: 0,
      page: 1,
      size: 10,
      totalPages: 0
    }
  }

  if (Array.isArray(raw)) {
    return {
      list: raw,
      total: raw.length,
      page: 1,
      size: raw.length || 10,
      totalPages: raw.length ? 1 : 0
    }
  }

  if (Array.isArray(raw.list)) {
    return {
      list: raw.list,
      total: raw.total || 0,
      page: raw.page || 1,
      size: raw.size || 10,
      totalPages: raw.totalPages || 0
    }
  }

  if (Array.isArray(raw.content)) {
    return {
      list: raw.content,
      total: raw.totalElements || 0,
      page: (raw.number || 0) + 1,
      size: raw.size || 10,
      totalPages: raw.totalPages || 0
    }
  }

  return {
    list: [],
    total: 0,
    page: 1,
    size: 10,
    totalPages: 0
  }
}
