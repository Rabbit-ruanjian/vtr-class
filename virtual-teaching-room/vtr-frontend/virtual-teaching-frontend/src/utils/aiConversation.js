/**
 * 将服务端保存的 AI 问答记录转换为前端消息。
 * 服务端返回的是按时间倒序的记录；展示和再次发送时必须恢复为正序。
 */
export function parseConversationSources(value) {
  if (Array.isArray(value)) return value
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function turnToMessages(item) {
  const messages = []
  const question = String(item?.question || '').trim()
  const answer = String(item?.answer || '').trim()

  if (question) messages.push({ role: 'user', content: question })
  if (answer) {
    messages.push({
      role: 'assistant',
      content: answer,
      requestId: item?.requestId,
      route: item?.route,
      answerMode: item?.answerMode,
      confidence: item?.confidence,
      hasEvidence: item?.hasEvidence,
      latencyMs: item?.latencyMs,
      retrievalMode: item?.retrievalMode,
      sources: parseConversationSources(item?.sourcesJson)
    })
  }
  return messages
}

/**
 * 兼容两种输入：
 * - 聚合后的会话摘要（含 turns 数组，代表一个对话窗口的多轮问答）
 * - 旧的单行问答记录
 * 统一展开为界面消息，确保点击侧边栏能恢复整段对话。
 */
export function conversationToMessages(item) {
  if (Array.isArray(item?.turns)) {
    return item.turns.flatMap(turnToMessages)
  }
  return turnToMessages(item)
}

/**
 * 找出最近活跃的一条会话（用于打开窗口时默认恢复的对话）。
 * 兼容聚合摘要与旧的单行记录。
 */
export function latestConversation(conversations) {
  const list = Array.isArray(conversations) ? conversations : []
  if (!list.length) return null
  return [...list].sort((left, right) => {
    const leftTime = Date.parse(left?.createdAt || '') || 0
    const rightTime = Date.parse(right?.createdAt || '') || 0
    if (leftTime !== rightTime) return rightTime - leftTime
    return Number(right?.id || 0) - Number(left?.id || 0)
  })[0]
}

/**
 * 恢复某一个对话窗口的可见消息。传入单条会话摘要（或旧的单行记录）即可，
 * 不再跨会话拼接，避免把不同侧边栏对话混在一起。
 * 欢迎语不属于模型上下文，但保留在界面中帮助用户识别窗口。
 */
export function restoreConversationMessages(conversation, welcomeContent, maxMessages = 12) {
  // 兼容旧调用：若传入的是数组，则取其中最近活跃的一条会话。
  const target = Array.isArray(conversation) ? latestConversation(conversation) : conversation
  const restored = target ? conversationToMessages(target) : []
  const limit = Math.max(2, Number(maxMessages) || 12)
  return [
    // 欢迎语只用于界面引导，不能作为模型上下文再次发送。
    { role: 'assistant', content: welcomeContent, intro: true },
    ...restored.slice(-limit)
  ]
}

/**
 * 从当前窗口提取发送给后端的上下文，忽略欢迎语和错误消息。
 */
export function historyForMessages(messages, maxMessages = 10) {
  return (Array.isArray(messages) ? messages : [])
    .filter((message) => (
      (message?.role === 'user' || message?.role === 'assistant')
      && !message?.error
      && !message?.intro
      && !message?.welcome
    ))
    .slice(-Math.max(1, Number(maxMessages) || 10))
    .map((message) => ({ role: message.role, content: String(message.content || '').slice(0, 8000) }))
}
