<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import aiHanfuAssistant from '@/assets/ai-hanfu-assistant.png'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChatDotRound, Close, Delete, Document, FullScreen, Minus, Plus, Search, Picture, Promotion, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { renderAiMarkdown } from '@/utils/aiMarkdown'
import { historyForMessages, latestConversation, restoreConversationMessages } from '@/utils/aiConversation'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const expanded = ref(false)
const fullScreen = ref(false)
const workspaceSidebarOpen = ref(false)
const draft = ref('')
const sending = ref(false)
const messages = ref([])
const savedConversations = ref([])
const historyExpanded = ref(false)
const historyLoading = ref(false)
const messageList = ref(null)
const workspaceMessageList = ref(null)
const inputRef = ref(null)
const workspaceInputRef = ref(null)
const fileInput = ref(null)
const workspaceFileInput = ref(null)
const floatingRoot = ref(null)
const attachment = ref(null)
const uploadMode = ref('media')
const activeDocumentId = ref(null)
const feedbackSending = ref({})
const historySearch = ref('')
const activeHistoryId = ref(null)
// 当前对话窗口的会话 ID：仅在“新对话”时更换，用于把同一窗口的问答聚合成一条侧边栏记录。
const conversationId = ref(null)
// 正在删除的会话 key，避免重复点击。
const deletingHistoryKey = ref(null)
// 当前高亮的侧边栏会话 key（真实会话 ID 或旧数据的 legacy-row-{id}）。
const activeHistoryKey = ref(null)
// 需要二次确认删除的会话 key。用行内确认，避免弹窗被 z-index 更高的面板遮挡。
const confirmingDeleteKey = ref(null)

function generateConversationId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `conv-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}
const floatingPosition = ref(loadFloatingPosition())
const dragging = ref(false)
const suppressClick = ref(false)
let dragState = null
let conversationGeneration = 0
let historyLoadSequence = 0
let historyRestoreGeneration = 0
let activeRequestController = null
const isLoggedIn = computed(() => authStore.isLoggedIn)
const supportedDocumentExtensions = ['pdf', 'docx', 'pptx', 'txt', 'md']
let previousBodyOverflow = ''

const filteredConversations = computed(() => {
  const keyword = historySearch.value.trim().toLowerCase()
  if (!keyword) return savedConversations.value
  return savedConversations.value.filter((item) =>
    `${item?.question || ''} ${item?.answer || ''}`.toLowerCase().includes(keyword)
  )
})

// 只有这些页面的参数或查询串明确代表课程，不能把公告/活动/论坛详情页的 id 当成课程 ID。
const courseScopedRouteNames = new Set([
  'course-workspace',
  'courseware',
  'courseware-category',
  'assignments',
  'assignment-answer',
  'exam-detail',
  'research-tools',
  'research-tasks'
])

const pageLabels = {
  dashboard: '首页',
  notices: '公告中心',
  forum: '教研社区',
  activities: '教研活动',
  'activity-detail': '教研活动详情',
  courseware: '教学中心',
  'courseware-category': '教学中心',
  'course-workspace': '课程工作台',
  assignments: '课程作业',
  'assignment-answer': '作业作答',
  'research-tools': '教研工具',
  'research-tasks': '教研任务',
  notifications: '消息通知',
  profile: '个人资料',
  admin: '管理中心',
  'academic-classes': '行政班管理'
}

const courseId = computed(() => {
  if (!courseScopedRouteNames.has(route.name)) return null
  const value = route.name === 'course-workspace'
    ? route.params.id
    : route.params.courseId || route.query.courseId
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : null
})

const chapter = computed(() => courseId.value && route.query.chapter
  ? String(route.query.chapter)
  : '')

const pageTitle = computed(() => pageLabels[route.name] || '虚拟教研室')
const contextText = computed(() => {
  if (!isLoggedIn.value) return '登录后即可提问，课程页面会自动连接本地知识库。'
  return courseId.value
    ? `当前位于“${pageTitle.value}”，AI 会优先参考当前课程的本地资料。`
    : `当前位于“${pageTitle.value}”，你可以直接询问平台功能或学习问题。`
})

const quickPrompts = computed(() => courseId.value
  ? ['总结本课程重点', '解释当前知识点', '帮我制定学习计划']
  : ['虚拟教研室可以做什么？', '帮我制定学习计划', '如何使用课程知识库？'])

function loadFloatingPosition() {
  try {
    const saved = JSON.parse(window.localStorage.getItem('vtr-ai-floating-position') || '{}')
    return {
      right: Number.isFinite(saved.right) ? saved.right : 24,
      bottom: Number.isFinite(saved.bottom) ? saved.bottom : 24
    }
  } catch {
    return { right: 24, bottom: 24 }
  }
}

const floatingStyle = computed(() => ({
  right: `${floatingPosition.value.right}px`,
  bottom: `${floatingPosition.value.bottom}px`
}))

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), Math.max(min, max))
}

function constrainFloatingToViewport() {
  const bounds = floatingRoot.value?.getBoundingClientRect()
  if (!bounds) return
  floatingPosition.value = {
    right: clamp(floatingPosition.value.right, 8, window.innerWidth - bounds.width - 8),
    bottom: clamp(floatingPosition.value.bottom, 8, window.innerHeight - bounds.height - 8)
  }
}

function handleTriggerPointerDown(event) {
  if (event.button !== 0) return
  beginFloatingDrag(event, 'trigger')
}

function handlePanelPointerDown(event) {
  if (event.button !== 0 || event.target.closest('button, input, textarea, a')) return
  beginFloatingDrag(event, 'panel')
}

function beginFloatingDrag(event, source) {
  const bounds = floatingRoot.value?.getBoundingClientRect()
  dragState = {
    source,
    startX: event.clientX,
    startY: event.clientY,
    startRight: floatingPosition.value.right,
    startBottom: floatingPosition.value.bottom,
    width: bounds?.width || 62,
    height: bounds?.height || 62,
    moved: false
  }
  dragging.value = true
  event.currentTarget?.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', handleTriggerPointerMove)
  window.addEventListener('pointerup', stopTriggerDragging, { once: true })
}

function handleTriggerPointerMove(event) {
  if (!dragState) return
  const deltaX = event.clientX - dragState.startX
  const deltaY = event.clientY - dragState.startY
  if (Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4) dragState.moved = true
  floatingPosition.value = {
    right: clamp(dragState.startRight - deltaX, 8, window.innerWidth - dragState.width - 8),
    bottom: clamp(dragState.startBottom - deltaY, 8, window.innerHeight - dragState.height - 8)
  }
}

function stopTriggerDragging() {
  if (!dragState) return
  suppressClick.value = dragState.source === 'trigger' && dragState.moved
  if (dragState.moved) {
    window.localStorage.setItem('vtr-ai-floating-position', JSON.stringify(floatingPosition.value))
  }
  dragState = null
  dragging.value = false
  window.removeEventListener('pointermove', handleTriggerPointerMove)
}

function welcomeMessage() {
  if (!isLoggedIn.value) return '你好，我是虚拟教研室 AI 助手。登录后即可提问课程知识、平台使用和教研设计问题。'
  return courseId.value
    ? `你好，我是虚拟教研室 AI 助手。当前已连接课程 ${courseId.value} 的本地知识库，你可以直接问我课程内容、学习方法或教研问题。`
    : '你好，我是虚拟教研室 AI 助手。你可以随时问我平台使用、课程学习和教研设计问题。'
}

function resetConversation() {
  conversationGeneration += 1
  historyRestoreGeneration += 1
  draft.value = ''
  attachment.value = null
  activeDocumentId.value = null
  activeHistoryId.value = null
  activeHistoryKey.value = null
  confirmingDeleteKey.value = null
  // 新建对话窗口时才生成新的会话 ID，之后本窗口内的问答都归入这一条侧边栏记录。
  conversationId.value = generateConversationId()
  sending.value = false
  messages.value = [{ role: 'assistant', content: welcomeMessage(), intro: true }]
  scrollToBottom()
}

async function loadHistory({ restore = false } = {}) {
  if (!isLoggedIn.value) {
    savedConversations.value = []
    return
  }
  const requestId = ++historyLoadSequence
  const restoreGeneration = historyRestoreGeneration
  historyLoading.value = true
  try {
    const history = await aiApi.history()
    if (requestId !== historyLoadSequence) return
    savedConversations.value = Array.isArray(history) ? history : []
    if (restore && (expanded.value || fullScreen.value) && !sending.value && restoreGeneration === historyRestoreGeneration) {
      const latest = latestConversation(savedConversations.value)
      if (latest) {
        // 打开窗口时默认恢复“最近活跃的那一条会话”，并接管它的会话 ID，
        // 之后继续提问会落回同一条侧边栏记录，而不是新开一条。
        conversationId.value = latest.conversationId || generateConversationId()
        activeHistoryKey.value = latest.key || null
        activeHistoryId.value = latest.id ?? null
        activeDocumentId.value = latest.documentId || null
        messages.value = restoreConversationMessages(latest, welcomeMessage())
      } else {
        messages.value = restoreConversationMessages(null, welcomeMessage())
      }
      scrollToBottom()
    }
  } catch {
    if (requestId === historyLoadSequence) savedConversations.value = []
  } finally {
    if (requestId === historyLoadSequence) historyLoading.value = false
  }
}

function toggleAssistant() {
  if (suppressClick.value) {
    suppressClick.value = false
    return
  }
  if (expanded.value && sending.value) {
    ElMessage.info('AI 正在回答，请等待回答完成后再收起窗口')
    return
  }
  expanded.value = !expanded.value
  if (expanded.value) {
    if (!messages.value.length) {
      resetConversation()
      void loadHistory({ restore: true })
    } else {
      void loadHistory()
    }
    nextTick(() => {
      constrainFloatingToViewport()
      inputRef.value?.focus()
    })
  }
}

function openFullScreen() {
  if (!messages.value.length) resetConversation()
  fullScreen.value = true
  expanded.value = false
  workspaceSidebarOpen.value = false
  void loadHistory({ restore: messages.value.length <= 1 })
  nextTick(() => workspaceInputRef.value?.focus())
}

function closeFullScreen() {
  fullScreen.value = false
  workspaceSidebarOpen.value = false
  expanded.value = true
  nextTick(() => {
    constrainFloatingToViewport()
    inputRef.value?.focus()
  })
}

function openHistoryConversation(item) {
  if (!item || sending.value) return
  conversationGeneration += 1
  historyRestoreGeneration += 1
  activeHistoryId.value = item.id
  activeHistoryKey.value = conversationKeyOf(item)
  confirmingDeleteKey.value = null
  // 只接管“真实会话 ID”；旧数据没有会话 ID（conversationId 为空）时生成新的，
  // 绝不能把 legacy-row-* 这类合成 key 当作会话 ID 写回。
  conversationId.value = item.conversationId || generateConversationId()
  draft.value = ''
  attachment.value = null
  activeDocumentId.value = item.documentId || null
  // 恢复该会话窗口的完整多轮问答（turns），而不是只取一轮。
  messages.value = restoreConversationMessages(item, welcomeMessage(), 40)
  workspaceSidebarOpen.value = false
  scrollToBottom()
}

function conversationKeyOf(item) {
  // 后端已提供稳定 key（真实会话 ID 或旧数据的 legacy-row-{id}）。
  return item?.key || item?.conversationId || (item?.id != null ? `legacy-row-${item.id}` : null)
}

// 第一次点击垃圾桶：进入行内确认态，不弹全局弹窗（面板 z-index 高，弹窗会被遮住）。
function requestDeleteConversation(item) {
  if (!item || sending.value || deletingHistoryKey.value) return
  const key = conversationKeyOf(item)
  if (!key) return
  confirmingDeleteKey.value = key
}

function cancelDeleteConversation() {
  confirmingDeleteKey.value = null
}

// 确认后真正调用后端删除。
async function confirmDeleteConversation(item) {
  if (!item || sending.value) return
  const key = conversationKeyOf(item)
  if (!key || deletingHistoryKey.value) return
  deletingHistoryKey.value = key
  try {
    await aiApi.deleteConversation(key)
    // 若删除的是当前正在查看的会话，则回到一个干净的新对话窗口。
    const removingActive = activeHistoryKey.value
      ? activeHistoryKey.value === key
      : (item.conversationId && item.conversationId === conversationId.value)
    savedConversations.value = savedConversations.value.filter(
      (row) => conversationKeyOf(row) !== key
    )
    if (removingActive) resetConversation()
    confirmingDeleteKey.value = null
    ElMessage.success('已删除该对话')
    void loadHistory()
  } catch (error) {
    ElMessage.error(error?.message || '删除对话失败，请稍后重试')
  } finally {
    deletingHistoryKey.value = null
  }
}

function closeAssistant() {
  if (sending.value) {
    ElMessage.info('AI 正在回答，请等待回答完成后再收起窗口')
    return
  }
  expanded.value = false
}

function clearConversation() {
  if (sending.value) return
  resetConversation()
  ElMessage.success('已新建本地对话，账号历史记忆仍会保留')
}

function scrollToBottom() {
  nextTick(() => {
    for (const element of [messageList.value, workspaceMessageList.value]) {
      if (element) element.scrollTop = element.scrollHeight
    }
  })
}

function scrollToLatestTurn() {
  nextTick(() => {
    for (const element of [messageList.value, workspaceMessageList.value]) {
      if (!element) continue
      const userMessages = element.querySelectorAll('[data-ai-role="user"]')
      const latestUserMessage = userMessages[userMessages.length - 1]
      if (!latestUserMessage) continue
      const containerTop = element.getBoundingClientRect().top
      const messageTop = latestUserMessage.getBoundingClientRect().top
      element.scrollTop += messageTop - containerTop - 12
    }
  })
}

async function useQuickPrompt(prompt) {
  if (sending.value) return
  draft.value = prompt
  await nextTick()
  sendMessage()
}

function detectMediaType(file) {
  const declaredType = String(file?.type || '').toLowerCase()
  const declaredTypeAliases = { 'image/jpg': 'image/jpeg' }
  const supportedTypes = new Set([
    'image/png', 'image/jpeg', 'image/webp', 'image/gif',
    'video/mp4', 'video/mpeg', 'video/webm', 'video/quicktime',
    'video/x-msvideo', 'video/x-flv', 'video/x-ms-wmv', 'video/3gpp'
  ])
  const canonicalType = declaredTypeAliases[declaredType] || declaredType
  if (supportedTypes.has(canonicalType)) return canonicalType
  const extension = String(file?.name || '').split('.').pop()?.toLowerCase()
  return {
    png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp', gif: 'image/gif',
    mp4: 'video/mp4', mpeg: 'video/mpeg', mpg: 'video/mpeg', webm: 'video/webm', mov: 'video/quicktime',
    avi: 'video/x-msvideo', flv: 'video/x-flv', wmv: 'video/x-ms-wmv', '3gp': 'video/3gpp', '3gpp': 'video/3gpp'
  }[extension] || ''
}

const attachmentIsImage = computed(() => detectMediaType(attachment.value).startsWith('image/'))
const attachmentIsVideo = computed(() => detectMediaType(attachment.value).startsWith('video/'))

function uploadAccept() {
  return uploadMode.value === 'document'
    ? '.pdf,.docx,.pptx,.txt,.md'
    : 'image/png,image/jpeg,image/webp,image/gif,video/mp4,video/mpeg,video/quicktime,video/x-msvideo,video/x-flv,video/x-ms-wmv,video/3gpp,video/webm,.mp4,.mpeg,.mpg,.mov,.avi,.flv,.wmv,.3gp,.3gpp'
}

function triggerUpload(mode = 'media') {
  uploadMode.value = mode
  const target = fullScreen.value ? workspaceFileInput.value : fileInput.value
  target?.click()
}

function handleUpload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  if (uploadMode.value === 'document') {
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!supportedDocumentExtensions.includes(extension)) {
      ElMessage.warning('请选择 PDF、DOCX、PPTX、TXT 或 MD 文档')
      return
    }
    if (file.size > 20 * 1024 * 1024) {
      ElMessage.warning('文档不能超过 20MB，请压缩后重试')
      return
    }
  } else {
    const mediaType = detectMediaType(file)
    if (mediaType.startsWith('image/')) {
      if (file.size > 8 * 1024 * 1024) {
        ElMessage.warning('图片不能超过 8MB，请压缩后重试')
        return
      }
    } else if (mediaType.startsWith('video/')) {
      if (!['video/mp4', 'video/mpeg', 'video/webm', 'video/quicktime', 'video/x-msvideo', 'video/x-flv', 'video/x-ms-wmv', 'video/3gpp'].includes(mediaType)) {
        ElMessage.warning('请选择 MP4、MPEG、WEBM、MOV、AVI、FLV、WMV 或 3GP 视频')
        return
      }
      if (file.size > 30 * 1024 * 1024) {
        ElMessage.warning('视频不能超过 30MB，请压缩后重试')
        return
      }
    } else {
      ElMessage.warning('请选择图片或视频文件')
      return
    }
  }

  attachment.value = file
}

function removeAttachment() {
  attachment.value = null
}

function historyForRequest() {
  return historyForMessages(messages.value)
}

async function sendMessage(retryRequest = null) {
  const retrying = Boolean(retryRequest)
  const text = (retryRequest?.text ?? draft.value).trim()
  const currentAttachment = retrying ? retryRequest.attachment : attachment.value
  if ((!text && !currentAttachment) || sending.value) return

  if (!isLoggedIn.value) {
    messages.value.push({
      role: 'assistant',
      content: '请先登录，再使用 AI 助手提问。登录成功后会返回当前页面。',
      error: true
    })
    draft.value = ''
    attachment.value = null
    scrollToBottom()
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  // 用户已开始新的输入，不能让尚未完成的历史恢复覆盖当前窗口。
  historyRestoreGeneration += 1
  const history = historyForRequest()
  const requestGeneration = conversationGeneration
  const attachmentType = detectMediaType(currentAttachment)
  const isImage = attachmentType.startsWith('image/')
  const isVideo = attachmentType.startsWith('video/')
  const attachmentLabel = currentAttachment
    ? `\n[待分析${isImage ? '图片' : isVideo ? '视频' : '文档'}：${currentAttachment.name}]`
    : ''
  const messagePrompt = text || (isImage
    ? '请分析这张图片中的内容，并结合我的问题给出清晰讲解。'
    : isVideo
      ? '请分析这个视频中的内容，并结合我的问题给出清晰讲解。'
      : '请分析这份文档，提取重点并给出清晰的学习讲解。')
  if (!retrying) messages.value.push({ role: 'user', content: `${messagePrompt}${attachmentLabel}` })
  draft.value = ''
  attachment.value = null
  sending.value = true
  const requestController = new AbortController()
  activeRequestController = requestController
  scrollToLatestTurn()

  try {
    // 首轮发送前若窗口尚无会话 ID（例如直接输入而未触发 reset），补一个稳定 ID。
    if (!conversationId.value) conversationId.value = generateConversationId()
    const payload = {
      message: messagePrompt,
      courseId: courseId.value,
      contextTitle: pageTitle.value,
      contextMeta: courseId.value
        ? `课程 ID：${courseId.value}${chapter.value ? ` · ${chapter.value}` : ''}`
        : '全局 AI 助手',
      chapter: chapter.value || null,
      conversationId: conversationId.value,
      history,
      documentId: !currentAttachment ? activeDocumentId.value : null
    }
    const requestConfig = { signal: requestController.signal }
    const response = currentAttachment && (isImage || isVideo)
      ? await aiApi.media(currentAttachment, payload, requestConfig)
      : currentAttachment
        ? await aiApi.document(currentAttachment, payload, requestConfig)
      : await aiApi.chat(payload, requestConfig)

    // 页面/题目已经变化时，旧请求的回答不能写入新的会话。
    if (requestGeneration !== conversationGeneration) return

    // 以服务端归一化后的会话 ID 为准，保证后续问答继续落在同一条侧边栏记录里。
    if (response?.conversationId) conversationId.value = response.conversationId

    if (currentAttachment && !isImage && !isVideo && response?.documentId) {
      activeDocumentId.value = response.documentId
    }

    messages.value.push({
      role: 'assistant',
      content: response?.reply || response?.content || 'AI 没有返回有效回答。',
      sources: response?.sources || [],
      requestId: response?.requestId,
      route: response?.route,
      answerMode: response?.answerMode,
      confidence: response?.confidence,
      hasEvidence: response?.hasEvidence,
      latencyMs: response?.latencyMs,
      retrievalMode: response?.retrievalMode,
      retrievedChunkCount: response?.retrievedChunkCount,
      qualityStatus: response?.qualityStatus
    })
    // 服务端会在回答成功后记录账号历史；刷新右侧摘要，但不覆盖当前本地窗口。
    void loadHistory()
  } catch (error) {
    if (requestGeneration !== conversationGeneration) return
    if (requestController.signal.aborted || error?.code === 'ERR_CANCELED') {
      messages.value.push({
        role: 'assistant',
        content: '已停止本次回答。你可以调整问题后重新发送。',
        stopped: true
      })
      return
    }
    messages.value.push({
      role: 'assistant',
      content: error?.message || 'AI 服务暂时不可用，请稍后重试。',
      error: true,
      retryable: true,
      retryRequest: { text: messagePrompt, attachment: currentAttachment }
    })
  } finally {
    if (activeRequestController === requestController) {
      activeRequestController = null
      sending.value = false
    }
    if (requestGeneration !== conversationGeneration) {
      resetConversation()
    }
    // 长回答完成后仍停留在本轮提问处，避免用户气泡被自动滚出窗口。
    scrollToLatestTurn()
  }
}

function stopGenerating() {
  if (!sending.value || !activeRequestController) return
  activeRequestController.abort()
}

function retryMessage(message) {
  if (sending.value || !message?.retryable || !message.retryRequest) return
  if (draft.value.trim() || attachment.value) {
    ElMessage.warning('请先发送或清空当前输入，再重试上一条回答')
    return
  }
  const index = messages.value.indexOf(message)
  if (index >= 0) messages.value.splice(index, 1)
  sendMessage(message.retryRequest)
}

function modeLabel(message) {
  return {
    DIRECT: '通用知识',
    COURSE_RAG: '课程资料',
    RAG: '课程资料',
    WEB_SEARCH: '联网核验',
    RAG_AND_WEB: '课程资料 + 联网',
    TEACHING_AGENT: '教研 Agent'
  }[message?.answerMode || message?.route] || ''
}

function evidenceLabel(message) {
  if (message?.hasEvidence) return '已找到课程依据'
  if (message?.route === 'RAG') return '课程资料未直接命中'
  return ''
}

function qualityLabel(message) {
  return {
    PASS: '已通过基础校验',
    REVIEW_REQUIRED: '建议核验来源',
    NO_EVIDENCE: '课程资料未覆盖'
  }[message?.qualityStatus] || ''
}

async function submitFeedback(message, helpful, feedbackType = '') {
  if (!message?.requestId || message.feedback || feedbackSending.value[message.requestId]) return
  let comment = ''
  if (feedbackType === 'REPORT') {
    try {
      const result = await ElMessageBox.prompt('请说明哪里需要改进（可选）', '报告回答问题', {
        confirmButtonText: '提交',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '例如：课程资料引用不准确、回答与问题不匹配……',
        inputValidator: (value) => !value || value.length <= 1000 || '说明不能超过 1000 个字符'
      })
      comment = result.value || ''
    } catch {
      return
    }
  }
  feedbackSending.value = { ...feedbackSending.value, [message.requestId]: true }
  try {
    await aiApi.feedback({
      requestId: message.requestId,
      helpful,
      feedbackType: feedbackType || (helpful ? 'HELPFUL' : 'NOT_HELPFUL'),
      comment
    })
    message.feedback = helpful ? 'HELPFUL' : feedbackType || 'NOT_HELPFUL'
    ElMessage.success('感谢反馈，已记录本次回答')
  } catch {
    ElMessage.error('反馈提交失败，请稍后重试')
  } finally {
    const next = { ...feedbackSending.value }
    delete next[message.requestId]
    feedbackSending.value = next
  }
}

function handleInputKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

watch(
  () => route.fullPath,
  () => {
    conversationGeneration += 1
    if (expanded.value && !sending.value) resetConversation()
  }
)

watch(expanded, (value) => {
  if (value && !messages.value.length) resetConversation()
})

watch(fullScreen, (value) => {
  if (value) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    scrollToBottom()
  } else {
    document.body.style.overflow = previousBodyOverflow
  }
})

onMounted(() => {
  constrainFloatingToViewport()
  window.addEventListener('resize', constrainFloatingToViewport)
})

onBeforeUnmount(() => {
  activeRequestController?.abort()
  document.body.style.overflow = previousBodyOverflow
  window.removeEventListener('resize', constrainFloatingToViewport)
  window.removeEventListener('pointermove', handleTriggerPointerMove)
  window.removeEventListener('pointerup', stopTriggerDragging)
})
</script>

<template>
  <div ref="floatingRoot" class="ai-floating-assistant" :class="{ expanded, dragging }" :style="floatingStyle">
    <transition name="ai-floating-panel">
      <section v-if="expanded" class="ai-floating-panel" aria-label="AI助手对话窗口">
        <header class="ai-floating-header" title="按住这里可拖动助手" @pointerdown="handlePanelPointerDown">
          <div class="ai-floating-identity">
            <div class="ai-floating-avatar">塾</div>
            <div>
              <strong>AI 学习助手</strong>
              <span><i />在线 · {{ courseId ? '课程 RAG 已连接' : '随时为你答疑' }}</span>
            </div>
          </div>
          <div class="ai-floating-header-actions">
            <button type="button" title="进入全屏 AI 工作台" aria-label="进入全屏 AI 工作台" @click="openFullScreen"><FullScreen /></button>
            <button type="button" title="新建本地对话（不删除账号历史）" aria-label="新建本地对话" :disabled="sending" @click="clearConversation"><Delete /></button>
            <button type="button" title="收起窗口" aria-label="收起窗口" :disabled="sending" @click="closeAssistant"><Minus /></button>
          </div>
        </header>

        <div class="ai-floating-context">
          <span class="ai-floating-context-dot" />
          <span>{{ contextText }}</span>
        </div>

        <section v-if="isLoggedIn" class="ai-floating-history">
          <button type="button" @click="historyExpanded = !historyExpanded">
            <span>账号历史记忆</span>
            <small>{{ historyLoading ? '读取中…' : `${savedConversations.length} 条` }}</small>
            <b>{{ historyExpanded ? '收起' : '展开' }}</b>
          </button>
          <div v-if="historyExpanded" class="ai-floating-history-list">
            <div v-if="!savedConversations.length && !historyLoading">还没有保存的对话</div>
            <article v-for="item in savedConversations.slice(0, 6)" :key="conversationKeyOf(item)">
              <time>{{ item.createdAt || '' }}</time>
              <strong>{{ item.question }}</strong>
              <p>{{ item.answer }}</p>
            </article>
          </div>
        </section>

        <div ref="messageList" class="ai-floating-messages" aria-live="polite">
          <article
            v-for="(message, index) in messages"
            :key="index"
            :data-ai-role="message.role"
            class="ai-floating-message"
            :class="[message.role, { error: message.error }]"
          >
            <span v-if="message.role === 'assistant'" class="ai-floating-message-label">AI 助手</span>
            <div v-if="message.role === 'assistant'" class="ai-floating-markdown" v-html="renderAiMarkdown(message.content)" />
            <p v-else>{{ message.content }}</p>
            <div v-if="message.role === 'assistant' && message.requestId && (modeLabel(message) || evidenceLabel(message) || message.latencyMs !== undefined)" class="ai-floating-meta">
              <span v-if="modeLabel(message)">{{ modeLabel(message) }}</span>
              <span v-if="evidenceLabel(message)">{{ evidenceLabel(message) }}</span>
              <span v-if="qualityLabel(message)">{{ qualityLabel(message) }}</span>
              <span v-if="message.retrievedChunkCount">命中 {{ message.retrievedChunkCount }} 个片段</span>
              <span v-if="message.latencyMs !== undefined">{{ message.latencyMs }} ms</span>
            </div>
            <div v-if="message.role === 'assistant' && message.sources?.length" class="ai-floating-sources">
              <span>回答依据</span>
              <em v-for="source in message.sources.slice(0, 2)" :key="`${source.documentName}-${source.chunkIndex}`">
                 {{ source.sourceKind === 'WEB' ? (source.documentName || '联网来源') : `${source.documentName || '课程资源'} · 片段 ${source.chunkIndex}` }}
                 <a v-if="source.sourceUrl" :href="source.sourceUrl" target="_blank" rel="noreferrer">打开来源</a>
               </em>
            </div>
            <div v-if="message.role === 'assistant' && message.retryable" class="ai-floating-retry">
              <span>回答没有完成，窗口已保留当前内容。</span>
              <button type="button" :disabled="sending" @click="retryMessage(message)">重试</button>
            </div>
            <div v-if="message.role === 'assistant' && message.requestId" class="ai-floating-feedback">
              <button type="button" :class="{ selected: message.feedback === 'HELPFUL' }" :disabled="Boolean(message.feedback) || feedbackSending[message.requestId]" @click="submitFeedback(message, true)">有帮助</button>
              <button type="button" :class="{ selected: message.feedback === 'NOT_HELPFUL' }" :disabled="Boolean(message.feedback) || feedbackSending[message.requestId]" @click="submitFeedback(message, false)">没帮助</button>
              <button type="button" :class="{ selected: message.feedback === 'REPORT' }" :disabled="Boolean(message.feedback) || feedbackSending[message.requestId]" @click="submitFeedback(message, false, 'REPORT')">报告问题</button>
            </div>
          </article>
          <div v-if="sending" class="ai-floating-typing">
            <span /><span /><span />
            <em>AI 正在思考…</em>
          </div>
        </div>

        <div v-if="!messages.some((message) => message.role === 'user')" class="ai-floating-quick-prompts">
          <button v-for="prompt in quickPrompts" :key="prompt" type="button" @click="useQuickPrompt(prompt)">{{ prompt }}</button>
        </div>

        <footer class="ai-floating-composer">
          <div v-if="attachment" class="ai-floating-attachment">
            <Picture v-if="attachmentIsImage" />
            <VideoCamera v-else-if="attachmentIsVideo" />
            <Document v-else />
            <span>{{ attachment.name }}</span>
            <button type="button" aria-label="移除附件" title="移除附件" @click="removeAttachment">×</button>
          </div>
          <el-input
            ref="inputRef"
            v-model="draft"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="8000"
            show-word-limit
            placeholder="输入问题，按 Enter 发送…"
            :disabled="sending"
            @keydown="handleInputKeydown"
          />
          <input
            ref="fileInput"
            class="ai-floating-file-input"
            type="file"
            :accept="uploadAccept()"
            @change="handleUpload"
          />
          <div class="ai-floating-composer-bottom">
            <div class="ai-floating-composer-tools">
              <button type="button" title="添加图片或视频" @click="triggerUpload('media')"><Picture /> 图片/视频</button>
              <button type="button" title="添加文档" @click="triggerUpload('document')"><Document /> 文档</button>
              <span>Shift + Enter 换行</span>
            </div>
            <el-button v-if="sending" class="ai-floating-stop" type="danger" title="停止 AI 回答" @click="stopGenerating">
              <span class="ai-pause-icon" aria-hidden="true"><i /><i /></span>
              停止
            </el-button>
            <el-button v-else type="primary" :disabled="!draft.trim() && !attachment" :icon="Promotion" @click="sendMessage">发送</el-button>
          </div>
        </footer>
      </section>
    </transition>

    <button
      type="button"
      class="ai-floating-trigger"
      :aria-label="expanded ? '收起 AI 助手' : '打开 AI 助手'"
      :title="expanded ? '收起 AI 助手' : '打开 AI 助手（可拖动）'"
      :disabled="sending"
      @pointerdown="handleTriggerPointerDown"
      @click="toggleAssistant"
    >
      <Close v-if="expanded" class="ai-floating-trigger-icon" />
      <img v-else class="ai-floating-trigger-avatar" :src="aiHanfuAssistant" alt="云塾 AI 助手" />
    </button>
  </div>

  <Teleport to="body">
    <transition name="ai-workspace-fade">
      <div v-if="fullScreen" class="ai-workspace" aria-label="全屏 AI 工作台">
        <aside class="ai-workspace-sidebar" :class="{ open: workspaceSidebarOpen }">
          <div class="ai-workspace-brand">
            <span class="ai-workspace-brand-icon"><ChatDotRound /></span>
            <div>
              <strong>AI 学习工作台</strong>
              <small>虚拟教研室智能助手</small>
            </div>
          </div>

          <button class="ai-workspace-new" type="button" :disabled="sending" @click="clearConversation">
            <Plus />
            <span>新对话</span>
          </button>

          <label class="ai-workspace-search">
            <Search />
            <input v-model="historySearch" type="search" placeholder="搜索历史对话" />
          </label>

          <div class="ai-workspace-history-heading">
            <span>最近对话</span>
            <small>{{ historyLoading ? '读取中…' : `${filteredConversations.length} 条` }}</small>
          </div>
          <div class="ai-workspace-history-list">
            <p v-if="!filteredConversations.length && !historyLoading" class="ai-workspace-empty-history">
              {{ historySearch ? '没有匹配的对话' : '还没有保存的对话' }}
            </p>
            <div
              v-for="item in filteredConversations"
              :key="conversationKeyOf(item)"
              class="ai-workspace-history-item"
              :class="{ active: activeHistoryKey === conversationKeyOf(item) }"
            >
              <button
                type="button"
                class="ai-workspace-history-open"
                @click="openHistoryConversation(item)"
              >
                <span class="ai-workspace-history-icon"><ChatDotRound /></span>
                <span class="ai-workspace-history-copy">
                  <strong>{{ item.title || item.question || '未命名对话' }}</strong>
                  <small>{{ item.createdAt || '账号历史记忆' }}</small>
                </span>
              </button>
              <button
                v-if="confirmingDeleteKey !== conversationKeyOf(item)"
                type="button"
                class="ai-workspace-history-delete"
                title="删除该对话"
                aria-label="删除该对话"
                :disabled="sending || deletingHistoryKey === conversationKeyOf(item)"
                @click.stop="requestDeleteConversation(item)"
              >
                <Delete />
              </button>
              <span v-else class="ai-workspace-history-confirm" @click.stop>
                <button
                  type="button"
                  class="ai-workspace-history-confirm-yes"
                  :disabled="deletingHistoryKey === conversationKeyOf(item)"
                  @click.stop="confirmDeleteConversation(item)"
                >{{ deletingHistoryKey === conversationKeyOf(item) ? '删除中' : '删除' }}</button>
                <button
                  type="button"
                  class="ai-workspace-history-confirm-no"
                  :disabled="deletingHistoryKey === conversationKeyOf(item)"
                  @click.stop="cancelDeleteConversation()"
                >取消</button>
              </span>
            </div>
          </div>

          <div class="ai-workspace-sidebar-footer">
            <span class="ai-workspace-user-avatar">{{ (authStore.user?.nickname || authStore.user?.username || 'U').slice(0, 1).toUpperCase() }}</span>
            <span>
              <strong>{{ authStore.user?.nickname || authStore.user?.username || '当前用户' }}</strong>
              <small>历史记忆已同步</small>
            </span>
          </div>
        </aside>

        <button
          v-if="workspaceSidebarOpen"
          class="ai-workspace-sidebar-mask"
          type="button"
          aria-label="关闭历史侧栏"
          @click="workspaceSidebarOpen = false"
        />

        <main class="ai-workspace-main">
          <header class="ai-workspace-header">
            <button class="ai-workspace-mobile-menu" type="button" aria-label="打开历史侧栏" @click="workspaceSidebarOpen = true">
              <span /><span /><span />
            </button>
            <div class="ai-workspace-title">
              <span class="ai-workspace-online" />
              <div>
                <strong>AI 学习助手</strong>
                <small>{{ courseId ? `课程 ${courseId} · 本地知识库已连接` : '通用问答 · 账号记忆已连接' }}</small>
              </div>
            </div>
            <button class="ai-workspace-return" type="button" :disabled="sending" @click="closeFullScreen">
              <ArrowLeft />
              <span>返回悬浮窗</span>
            </button>
          </header>

          <section ref="workspaceMessageList" class="ai-workspace-conversation" aria-live="polite">
            <div v-if="!messages.some((message) => message.role === 'user')" class="ai-workspace-welcome">
              <span class="ai-workspace-welcome-icon"><ChatDotRound /></span>
              <p>虚拟教研室 AI</p>
              <h1>有什么我能帮你？</h1>
              <span>可以提问课程知识、上传资料分析，或让 AI 帮你规划学习与教研工作。</span>
              <div class="ai-workspace-suggestions">
                <button v-for="prompt in quickPrompts" :key="prompt" type="button" @click="useQuickPrompt(prompt)">{{ prompt }}</button>
              </div>
            </div>

            <div v-else class="ai-workspace-message-list">
              <article
                v-for="(message, index) in messages"
                :key="index"
                :data-ai-role="message.role"
                class="ai-workspace-message"
                :class="[message.role, { error: message.error, intro: message.intro }]"
              >
                <span class="ai-workspace-message-avatar">
                  <ChatDotRound v-if="message.role === 'assistant'" />
                  <b v-else>{{ (authStore.user?.nickname || authStore.user?.username || '我').slice(0, 1).toUpperCase() }}</b>
                </span>
                <div class="ai-workspace-message-body">
                  <strong>{{ message.role === 'assistant' ? 'AI 学习助手' : '我' }}</strong>
                  <div v-if="message.role === 'assistant'" class="ai-workspace-markdown" v-html="renderAiMarkdown(message.content)" />
                  <p v-else>{{ message.content }}</p>
                  <div v-if="message.role === 'assistant' && message.requestId && (modeLabel(message) || evidenceLabel(message) || message.latencyMs !== undefined)" class="ai-workspace-meta">
                    <span v-if="modeLabel(message)">{{ modeLabel(message) }}</span>
                    <span v-if="evidenceLabel(message)">{{ evidenceLabel(message) }}</span>
                    <span v-if="qualityLabel(message)">{{ qualityLabel(message) }}</span>
                    <span v-if="message.retrievedChunkCount">命中 {{ message.retrievedChunkCount }} 个片段</span>
                    <span v-if="message.latencyMs !== undefined">{{ message.latencyMs }} ms</span>
                  </div>
                  <div v-if="message.role === 'assistant' && message.sources?.length" class="ai-workspace-sources">
                    <span>回答依据</span>
                    <a
                      v-for="source in message.sources.slice(0, 4)"
                      :key="`${source.documentName}-${source.chunkIndex}`"
                      :href="source.sourceUrl || undefined"
                      :target="source.sourceUrl ? '_blank' : undefined"
                      rel="noreferrer"
                    >{{ source.documentName || '课程资源' }}{{ source.chunkIndex !== undefined ? ` · 片段 ${source.chunkIndex}` : '' }}</a>
                  </div>
                  <div v-if="message.role === 'assistant' && message.retryable" class="ai-workspace-retry">
                    <span>回答没有完成，请重试。</span>
                    <button type="button" :disabled="sending" @click="retryMessage(message)">重新回答</button>
                  </div>
                  <div v-if="message.role === 'assistant' && message.requestId" class="ai-workspace-feedback">
                    <button type="button" :class="{ selected: message.feedback === 'HELPFUL' }" :disabled="Boolean(message.feedback) || feedbackSending[message.requestId]" @click="submitFeedback(message, true)">有帮助</button>
                    <button type="button" :class="{ selected: message.feedback === 'NOT_HELPFUL' }" :disabled="Boolean(message.feedback) || feedbackSending[message.requestId]" @click="submitFeedback(message, false)">没帮助</button>
                    <button type="button" :class="{ selected: message.feedback === 'REPORT' }" :disabled="Boolean(message.feedback) || feedbackSending[message.requestId]" @click="submitFeedback(message, false, 'REPORT')">报告问题</button>
                  </div>
                </div>
              </article>
              <div v-if="sending" class="ai-workspace-typing">
                <span class="ai-workspace-message-avatar"><ChatDotRound /></span>
                <div><i /><i /><i /><em>AI 正在思考…</em></div>
              </div>
            </div>
          </section>

          <footer class="ai-workspace-composer-wrap">
            <div class="ai-workspace-composer">
              <div v-if="attachment" class="ai-workspace-attachment">
                <Picture v-if="attachmentIsImage" />
                <VideoCamera v-else-if="attachmentIsVideo" />
                <Document v-else />
                <span>{{ attachment.name }}</span>
                <button type="button" aria-label="移除附件" @click="removeAttachment">×</button>
              </div>
              <el-input
                ref="workspaceInputRef"
                v-model="draft"
                type="textarea"
                :rows="2"
                resize="none"
                maxlength="8000"
                placeholder="发消息或按住 Shift + Enter 换行…"
                :disabled="sending"
                @keydown="handleInputKeydown"
              />
              <input
                ref="workspaceFileInput"
                class="ai-floating-file-input"
                type="file"
                :accept="uploadAccept()"
                @change="handleUpload"
              />
              <div class="ai-workspace-composer-actions">
                <div>
                  <button type="button" title="添加图片或视频" @click="triggerUpload('media')"><Picture /><span>图片/视频</span></button>
                  <button type="button" title="添加文档" @click="triggerUpload('document')"><Document /><span>上传文档</span></button>
                  <small>{{ courseId ? '将优先参考当前课程资料' : '账号上下文记忆已开启' }}</small>
                </div>
                <el-button v-if="sending" class="ai-workspace-stop" type="danger" title="停止 AI 回答" aria-label="停止 AI 回答" circle @click="stopGenerating">
                  <span class="ai-pause-icon" aria-hidden="true"><i /><i /></span>
                </el-button>
                <el-button v-else type="primary" :disabled="!draft.trim() && !attachment" :icon="Promotion" circle @click="sendMessage" />
              </div>
            </div>
            <p>AI 生成内容仅供学习参考，重要信息请结合课程资料核验。</p>
          </footer>
        </main>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.ai-floating-assistant {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1200;
  font-family: inherit;
}

.ai-floating-assistant.dragging .ai-floating-trigger {
  cursor: grabbing;
  user-select: none;
}

.ai-floating-trigger {
  position: relative;
  display: grid;
  width: 62px;
  height: 62px;
  place-items: center;
  border: 0;
  border-radius: 22px;
  overflow: visible;
  background: transparent;
  color: #fff;
  box-shadow: none;
  cursor: pointer;
  transition: transform .2s ease, box-shadow .2s ease, border-radius .2s ease;
}

.ai-floating-trigger::before { content: none; }

.ai-floating-trigger:hover {
  transform: translateY(-3px);
  filter: brightness(1.04);
}

.ai-floating-trigger:active {
  transform: translateY(-1px);
}

.ai-floating-trigger-icon {
  position: relative;
  z-index: 1;
  width: 27px;
  height: 27px;
}

.ai-floating-trigger-avatar {
  position: absolute;
  right: -8px;
  bottom: -9px;
  z-index: 1;
  width: 86px;
  height: 104px;
  object-fit: contain;
  object-position: bottom center;
  pointer-events: none;
  filter: drop-shadow(0 5px 5px rgba(76, 52, 35, .2));
}

.ai-floating-panel {
  display: flex;
  flex-direction: column;
  width: min(390px, calc(100vw - 32px));
  height: min(600px, calc(100vh - 112px));
  margin-bottom: 14px;
  overflow: hidden;
  border: 1px solid #ded7c9;
  border-radius: 20px;
  background: #faf8f2;
  box-shadow: 0 24px 55px rgba(71, 61, 45, .18), 0 4px 12px rgba(71, 61, 45, .08);
  backdrop-filter: none;
}

.ai-floating-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 17px 17px 15px;
  background: #f7f3eb;
  border-bottom: 1px solid #e5ddcf;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.ai-floating-assistant.dragging .ai-floating-header { cursor: grabbing; }

.ai-floating-identity {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.ai-floating-avatar {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  place-items: center;
  border-radius: 12px;
  background: #315b7d;
  color: #fff;
  font-family: "Songti SC", "STSong", serif;
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 6px 14px rgba(49, 91, 125, .2);
}

.ai-floating-avatar svg {
  width: 21px;
  height: 21px;
}

.ai-floating-identity strong,
.ai-floating-identity span {
  display: block;
}

.ai-floating-identity strong {
  overflow: hidden;
  color: #243b4d;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-floating-identity span {
  margin-top: 4px;
  color: #7c8a91;
  font-size: 11px;
}

.ai-floating-identity i {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 4px;
  border-radius: 50%;
  background: #23b681;
  vertical-align: 1px;
}

.ai-floating-header-actions {
  display: flex;
  gap: 3px;
}

.ai-floating-header-actions button {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #8796b3;
  cursor: pointer;
}

.ai-floating-header-actions button:hover:not(:disabled) {
  background: #ebe4d7;
  color: #315b7d;
}

.ai-floating-header-actions button:disabled {
  cursor: not-allowed;
  opacity: .45;
}

.ai-floating-header-actions svg {
  width: 16px;
  height: 16px;
}

.ai-floating-context {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 10px 16px;
  background: #f5f1e8;
  color: #6f7f87;
  font-size: 11px;
  line-height: 1.5;
}

.ai-floating-context-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  margin-top: 5px;
  border-radius: 50%;
  background: #caa85a;
}

.ai-floating-history {
  margin: 0 16px;
  border: 1px solid #e3d5b9;
  border-radius: 8px;
  background: #fbf8f0;
}

.ai-floating-history > button {
  display: flex;
  width: 100%;
  gap: 8px;
  padding: 7px 9px;
  border: 0;
  background: transparent;
  color: #5f6b70;
  cursor: pointer;
  font: inherit;
  font-size: 11px;
  text-align: left;
}

.ai-floating-history small { margin-left: auto; color: #9a8c70; }
.ai-floating-history b { color: #9c7a2c; font-size: 10px; font-weight: 500; }
.ai-floating-history-list { display: grid; gap: 5px; max-height: 150px; overflow: auto; padding: 0 8px 8px; }
.ai-floating-history-list article { padding: 5px 7px; border-radius: 5px; background: #f3ecdd; }
.ai-floating-history-list time { display: block; color: #9aa6ba; font-size: 9px; }
.ai-floating-history-list strong, .ai-floating-history-list p { display: -webkit-box; overflow: hidden; color: #536480; font-size: 10px; line-height: 1.4; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.ai-floating-history-list p { margin: 2px 0 0; color: #75829a; }
.ai-floating-history-list > div { padding: 4px 0; color: #9aa6ba; font-size: 10px; }

.ai-floating-messages {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  scrollbar-color: #cdbd9c transparent;
  scrollbar-width: thin;
}

.ai-floating-message {
  max-width: 88%;
  padding: 10px 12px;
  border-radius: 14px;
  background: #f3eee4;
  color: #3f5260;
}

.ai-floating-message.assistant {
  align-self: flex-start;
  border-top-left-radius: 5px;
}

.ai-floating-message.user {
  align-self: flex-end;
  border-top-right-radius: 5px;
  background: #e8eef0;
  color: #315b7d;
}

.ai-floating-message.error {
  background: #f9e9e4;
  color: #a55467;
}

.ai-floating-message-label {
  display: block;
  margin-bottom: 4px;
  color: #9c7a2c;
  font-size: 10px;
  font-weight: 700;
}

.ai-floating-message p {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.ai-floating-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
  color: #8490a8;
  font-size: 10px;
}

.ai-floating-meta span {
  padding: 2px 6px;
  border-radius: 999px;
  background: #e9e2d3;
}

.ai-floating-markdown {
  font-size: 13px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.ai-floating-markdown :deep(p),
.ai-floating-markdown :deep(h1),
.ai-floating-markdown :deep(h2),
.ai-floating-markdown :deep(h3) {
  margin: 0 0 7px;
}

.ai-floating-markdown :deep(p:last-child),
.ai-floating-markdown :deep(h1:last-child),
.ai-floating-markdown :deep(h2:last-child),
.ai-floating-markdown :deep(h3:last-child) {
  margin-bottom: 0;
}

.ai-floating-markdown :deep(h1),
.ai-floating-markdown :deep(h2),
.ai-floating-markdown :deep(h3) {
  color: #315b7d;
  font-size: 14px;
}

.ai-floating-markdown :deep(ul),
.ai-floating-markdown :deep(ol) {
  margin: 4px 0 8px;
  padding-left: 19px;
}

.ai-floating-markdown :deep(li) { margin: 3px 0; }
.ai-floating-markdown :deep(a) { color: #315b7d; text-decoration: underline; }
.ai-floating-markdown :deep(code) { padding: 1px 4px; border-radius: 3px; background: #e8e0d1; font-family: Consolas, monospace; font-size: 12px; }
.ai-floating-markdown :deep(pre) { margin: 7px 0; padding: 8px; overflow: auto; border-radius: 7px; background: #2f4f68; color: #f7f3eb; }
.ai-floating-markdown :deep(pre code) { padding: 0; background: transparent; color: inherit; }

.ai-floating-sources {
  display: grid;
  gap: 4px;
  margin-top: 9px;
  padding-top: 7px;
  border-top: 1px solid #e1e7f2;
}

.ai-floating-sources span,
.ai-floating-sources em {
  color: #8491aa;
  font-size: 10px;
  font-style: normal;
}

.ai-floating-sources span {
  font-weight: 700;
}

.ai-floating-retry {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 8px;
  color: #a15d6d;
  font-size: 10px;
}

.ai-floating-retry button {
  padding: 3px 8px;
  border: 1px solid #e5aeb9;
  border-radius: 999px;
  background: #fff;
  color: #a24f62;
  cursor: pointer;
  font: inherit;
}

.ai-floating-retry button:hover:not(:disabled) {
  background: #fff0f3;
}

.ai-floating-retry button:disabled {
  cursor: default;
  opacity: .55;
}

.ai-floating-feedback {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.ai-floating-feedback button {
  padding: 3px 7px;
  border: 1px solid #dce3f1;
  border-radius: 999px;
  background: #fff;
  color: #7b89a3;
  cursor: pointer;
  font: inherit;
  font-size: 10px;
}

.ai-floating-feedback button:hover:not(:disabled),
.ai-floating-feedback button.selected {
  border-color: #98a9e8;
  background: #f0f3ff;
  color: #5368bb;
}

.ai-floating-feedback button:disabled {
  cursor: default;
  opacity: .65;
}

.ai-floating-typing {
  display: flex;
  align-items: center;
  align-self: flex-start;
  gap: 4px;
  color: #8795ad;
  font-size: 11px;
}

.ai-floating-typing span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #7086d7;
  animation: ai-floating-bounce 1.2s infinite ease-in-out;
}

.ai-floating-typing span:nth-child(2) { animation-delay: .15s; }
.ai-floating-typing span:nth-child(3) { animation-delay: .3s; }

.ai-floating-typing em {
  margin-left: 3px;
  font-style: normal;
}

@keyframes ai-floating-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: .5; }
  30% { transform: translateY(-3px); opacity: 1; }
}

.ai-floating-quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding: 0 16px 11px;
}

.ai-floating-quick-prompts button {
  padding: 6px 9px;
  border: 1px solid #d7d9d3;
  border-radius: 999px;
  background: #f8f5ee;
  color: #315b7d;
  cursor: pointer;
  font: inherit;
  font-size: 11px;
  transition: border-color .18s ease, background .18s ease, color .18s ease;
}

.ai-floating-quick-prompts button:hover {
  border-color: #c8a85b;
  background: #f5ecd8;
  color: #8a6d26;
}

.ai-floating-composer {
  padding: 11px 14px 13px;
  border-top: 1px solid #e5ddcf;
  background: #f7f3eb;
}

.ai-floating-file-input {
  display: none;
}

.ai-floating-attachment {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  padding: 7px 9px;
  border: 1px solid #e0d4bd;
  border-radius: 9px;
  background: #f4ecdc;
  color: #6e675c;
  font-size: 11px;
}

.ai-floating-attachment svg {
  width: 15px;
  height: 15px;
  flex: 0 0 auto;
  color: #9c7a2c;
}

.ai-floating-attachment span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-floating-attachment button {
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #8b9ab7;
  cursor: pointer;
  font-size: 16px;
  line-height: 20px;
}

.ai-floating-attachment button:hover {
  background: #eee4d0;
  color: #8a6d26;
}

.ai-floating-composer :deep(.el-textarea__inner) {
  padding: 9px 10px;
  border-color: #ded6c9;
  border-radius: 11px;
  box-shadow: none;
  color: #394b57;
  font-size: 13px;
}

.ai-floating-composer :deep(.el-textarea__inner:focus) {
  border-color: #caa85a;
  box-shadow: 0 0 0 3px rgba(202, 168, 90, .14);
}

.ai-floating-composer-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
}

.ai-floating-composer-tools {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.ai-floating-composer-tools button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 0;
  border: 0;
  background: transparent;
  color: #6f7f87;
  cursor: pointer;
  font: inherit;
  font-size: 11px;
  white-space: nowrap;
}

.ai-floating-composer-tools button:hover {
  color: #315b7d;
}

.ai-floating-composer-tools button svg {
  width: 14px;
  height: 14px;
}

.ai-floating-composer-bottom span {
  color: #9aa7bd;
  font-size: 10px;
}

.ai-floating-composer-bottom :deep(.el-button) {
  min-width: 68px;
  border-radius: 9px;
  --el-button-bg-color: #cdaa5a;
  --el-button-border-color: #cdaa5a;
  --el-button-hover-bg-color: #b99343;
  --el-button-hover-border-color: #b99343;
  --el-button-active-bg-color: #a68135;
  --el-button-active-border-color: #a68135;
  --el-button-text-color: #fffaf0;
}

.ai-floating-composer-bottom :deep(.ai-floating-stop) {
  border-color: #ef6b75;
  background: linear-gradient(135deg, #f27879, #e75a70);
  box-shadow: 0 6px 14px rgba(221, 77, 96, .2);
}

.ai-pause-icon {
  display: inline-flex;
  height: 14px;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.ai-pause-icon i {
  display: block;
  width: 3px;
  height: 12px;
  border-radius: 1px;
  background: currentColor;
}

.ai-floating-panel-enter-active,
.ai-floating-panel-leave-active {
  transform-origin: bottom right;
  transition: opacity .2s ease, transform .2s ease;
}

.ai-floating-panel-enter-from,
.ai-floating-panel-leave-to {
  transform: translateY(12px);
  opacity: 0;
}

.ai-workspace {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  overflow: hidden;
  background: #f7f9fd;
  color: #243858;
  font-family: inherit;
}

.ai-workspace-sidebar {
  position: relative;
  z-index: 2;
  display: flex;
  width: 286px;
  flex: 0 0 286px;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid #e5e9f2;
  background: #fff;
  box-shadow: 5px 0 24px rgba(45, 69, 120, .035);
}

.ai-workspace-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 22px 20px 17px;
}

.ai-workspace-brand-icon,
.ai-workspace-welcome-icon {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 14px;
  background: linear-gradient(145deg, #357ff6, #7462e6);
  color: #fff;
  box-shadow: 0 8px 18px rgba(72, 94, 210, .2);
}

.ai-workspace-brand-icon svg,
.ai-workspace-welcome-icon svg { width: 23px; height: 23px; }
.ai-workspace-brand strong,
.ai-workspace-brand small { display: block; }
.ai-workspace-brand strong { color: #203a68; font-size: 15px; }
.ai-workspace-brand small { margin-top: 3px; color: #93a0b6; font-size: 10px; }

.ai-workspace-new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 2px 18px 14px;
  padding: 11px 14px;
  border: 0;
  border-radius: 12px;
  background: linear-gradient(135deg, #397ff3, #655fdc);
  color: #fff;
  box-shadow: 0 9px 22px rgba(64, 99, 218, .2);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
}

.ai-workspace-new:hover:not(:disabled) { filter: brightness(1.04); transform: translateY(-1px); }
.ai-workspace-new:disabled { cursor: not-allowed; opacity: .55; }
.ai-workspace-new svg { width: 17px; height: 17px; }

.ai-workspace-search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 18px 18px;
  padding: 9px 11px;
  border: 1px solid #e1e6f0;
  border-radius: 10px;
  background: #f8f9fc;
  color: #9aa6ba;
}

.ai-workspace-search:focus-within { border-color: #9bb3ee; box-shadow: 0 0 0 3px rgba(83, 115, 218, .08); }
.ai-workspace-search svg { width: 15px; height: 15px; flex: 0 0 auto; }
.ai-workspace-search input { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: #405477; font: inherit; font-size: 12px; }
.ai-workspace-search input::placeholder { color: #aab3c3; }

.ai-workspace-history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px 8px;
  color: #8b99ae;
  font-size: 11px;
}

.ai-workspace-history-heading small { font-size: 10px; }

.ai-workspace-history-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 10px 12px;
  scrollbar-color: #cdd6e8 transparent;
  scrollbar-width: thin;
}

.ai-workspace-history-item {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 3px;
  border-radius: 10px;
  color: #65758e;
}

.ai-workspace-history-item:hover { background: #f4f6fb; }
.ai-workspace-history-item.active { background: #edf2ff; color: #405fb6; }

.ai-workspace-history-open {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 9px;
  padding: 10px;
  overflow: hidden;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.ai-workspace-history-delete {
  display: grid;
  flex: none;
  place-items: center;
  width: 26px;
  height: 26px;
  margin-right: 6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #aab4c6;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, background 0.15s ease, color 0.15s ease;
}

.ai-workspace-history-item:hover .ai-workspace-history-delete,
.ai-workspace-history-item.active .ai-workspace-history-delete { opacity: 1; }

.ai-workspace-history-delete:hover { background: #fde3e3; color: #e05656; }
.ai-workspace-history-delete:disabled { cursor: not-allowed; opacity: 0.4; }
.ai-workspace-history-delete svg { width: 13px; height: 13px; }

.ai-workspace-history-confirm {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 4px;
  margin-right: 6px;
}

.ai-workspace-history-confirm button {
  border: 0;
  border-radius: 6px;
  padding: 3px 8px;
  font-size: 11px;
  line-height: 1.4;
  cursor: pointer;
}

.ai-workspace-history-confirm button:disabled { cursor: not-allowed; opacity: 0.6; }
.ai-workspace-history-confirm-yes { background: #e05656; color: #fff; }
.ai-workspace-history-confirm-yes:hover { background: #cf4444; }
.ai-workspace-history-confirm-no { background: #e8edf6; color: #5a6b86; }
.ai-workspace-history-confirm-no:hover { background: #dbe3f0; }

.ai-workspace-history-icon {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border: 1px solid #e2e7f1;
  border-radius: 9px;
  background: #fff;
  color: #7183c4;
}

.ai-workspace-history-icon svg { width: 14px; height: 14px; }
.ai-workspace-history-copy { min-width: 0; flex: 1; }
.ai-workspace-history-copy strong,
.ai-workspace-history-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-workspace-history-copy strong { color: inherit; font-size: 12px; font-weight: 600; }
.ai-workspace-history-copy small { margin-top: 4px; color: #a3adbe; font-size: 9px; }
.ai-workspace-empty-history { padding: 18px 10px; color: #a0aabd; font-size: 11px; text-align: center; }

.ai-workspace-sidebar-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 15px 18px;
  border-top: 1px solid #e8ebf2;
}

.ai-workspace-user-avatar {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: 50%;
  background: #e9f1ff;
  color: #5571bb;
  font-size: 13px;
  font-weight: 800;
}

.ai-workspace-sidebar-footer > span:last-child { min-width: 0; }
.ai-workspace-sidebar-footer strong,
.ai-workspace-sidebar-footer small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-workspace-sidebar-footer strong { color: #3c4f70; font-size: 12px; }
.ai-workspace-sidebar-footer small { margin-top: 3px; color: #96a2b7; font-size: 9px; }

.ai-workspace-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  background:
    radial-gradient(circle at 52% 42%, rgba(231, 238, 255, .55), transparent 30%),
    #fbfcfe;
}

.ai-workspace-header {
  position: relative;
  z-index: 1;
  display: flex;
  height: 68px;
  flex: 0 0 68px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 26px;
  border-bottom: 1px solid #e6eaf1;
  background: rgba(255, 255, 255, .9);
  backdrop-filter: blur(14px);
}

.ai-workspace-title { display: flex; align-items: center; gap: 10px; min-width: 0; }
.ai-workspace-title strong,
.ai-workspace-title small { display: block; }
.ai-workspace-title strong { color: #243b64; font-size: 14px; }
.ai-workspace-title small { margin-top: 3px; color: #8997ac; font-size: 10px; }
.ai-workspace-online { width: 8px; height: 8px; border: 2px solid #dff8ed; border-radius: 50%; background: #26bd83; box-shadow: 0 0 0 3px #e9faf3; }

.ai-workspace-return,
.ai-workspace-mobile-menu {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 11px;
  border: 1px solid #dfe5f0;
  border-radius: 9px;
  background: #fff;
  color: #607395;
  cursor: pointer;
  font: inherit;
  font-size: 11px;
}

.ai-workspace-return:hover:not(:disabled) { border-color: #aabbea; background: #f5f7ff; color: #4c64b4; }
.ai-workspace-return:disabled { cursor: not-allowed; opacity: .5; }
.ai-workspace-return svg { width: 15px; height: 15px; }
.ai-workspace-mobile-menu { display: none; width: 36px; height: 36px; flex-direction: column; gap: 3px; padding: 0; }
.ai-workspace-mobile-menu span { width: 15px; height: 1.5px; border-radius: 2px; background: currentColor; }

.ai-workspace-conversation {
  flex: 1;
  min-height: 0;
  overflow: auto;
  scrollbar-color: #ccd5e7 transparent;
  scrollbar-width: thin;
}

.ai-workspace-welcome {
  display: flex;
  width: min(760px, calc(100% - 44px));
  min-height: 100%;
  margin: 0 auto;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 70px 0 170px;
  text-align: center;
}

.ai-workspace-welcome-icon { width: 54px; height: 54px; border-radius: 18px; }
.ai-workspace-welcome p { margin: 14px 0 0; color: #6376ad; font-size: 12px; font-weight: 700; letter-spacing: .04em; }
.ai-workspace-welcome h1 { margin: 8px 0 10px; color: #1f3151; font-size: clamp(25px, 3vw, 38px); letter-spacing: -.04em; }
.ai-workspace-welcome > span { max-width: 560px; color: #8a98ad; font-size: 13px; line-height: 1.7; }
.ai-workspace-suggestions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin-top: 28px; }
.ai-workspace-suggestions button { padding: 10px 15px; border: 1px solid #e0e5ef; border-radius: 12px; background: rgba(255, 255, 255, .88); color: #566b91; box-shadow: 0 5px 16px rgba(59, 78, 120, .04); cursor: pointer; font: inherit; font-size: 12px; }
.ai-workspace-suggestions button:hover { border-color: #a9b9e9; background: #f4f7ff; color: #4961b0; transform: translateY(-1px); }

.ai-workspace-message-list {
  width: min(900px, calc(100% - 52px));
  margin: 0 auto;
  padding: 38px 0 190px;
}

.ai-workspace-message {
  display: flex;
  align-items: flex-start;
  gap: 13px;
  margin-bottom: 28px;
}

.ai-workspace-message.user { flex-direction: row-reverse; }
.ai-workspace-message.intro { opacity: .82; }
.ai-workspace-message-avatar {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 11px;
  background: linear-gradient(145deg, #4a80f4, #7563de);
  color: #fff;
  box-shadow: 0 6px 14px rgba(72, 97, 197, .16);
}
.ai-workspace-message.user .ai-workspace-message-avatar { border-radius: 50%; background: #e9f1ff; color: #5570b5; box-shadow: none; }
.ai-workspace-message-avatar svg { width: 18px; height: 18px; }
.ai-workspace-message-avatar b { font-size: 11px; }
.ai-workspace-message-body { max-width: min(740px, calc(100% - 48px)); }
.ai-workspace-message.user .ai-workspace-message-body { text-align: right; }
.ai-workspace-message-body > strong { display: block; margin: 0 2px 7px; color: #63728a; font-size: 10px; }
.ai-workspace-message-body > p,
.ai-workspace-markdown {
  margin: 0;
  padding: 13px 16px;
  border: 1px solid #e4e8f0;
  border-radius: 5px 15px 15px;
  background: #fff;
  color: #3c4e6c;
  box-shadow: 0 5px 18px rgba(46, 65, 105, .045);
  font-size: 14px;
  line-height: 1.75;
  overflow-wrap: anywhere;
  text-align: left;
}
.ai-workspace-message.user .ai-workspace-message-body > p { border-color: #d9e3fa; border-radius: 15px 5px 15px 15px; background: #ecf2ff; color: #38558c; white-space: pre-wrap; }
.ai-workspace-message.error .ai-workspace-markdown { border-color: #f0d8dc; background: #fff6f7; color: #a25768; }
.ai-workspace-markdown :deep(p),
.ai-workspace-markdown :deep(h1),
.ai-workspace-markdown :deep(h2),
.ai-workspace-markdown :deep(h3) { margin: 0 0 9px; }
.ai-workspace-markdown :deep(:last-child) { margin-bottom: 0; }
.ai-workspace-markdown :deep(h1),
.ai-workspace-markdown :deep(h2),
.ai-workspace-markdown :deep(h3) { color: #2d487b; font-size: 16px; }
.ai-workspace-markdown :deep(ul),
.ai-workspace-markdown :deep(ol) { margin: 7px 0 10px; padding-left: 22px; }
.ai-workspace-markdown :deep(pre) { overflow: auto; padding: 12px; border-radius: 9px; background: #202943; color: #f5f7ff; }
.ai-workspace-markdown :deep(code) { padding: 2px 5px; border-radius: 4px; background: #edf0f7; font-family: Consolas, monospace; }
.ai-workspace-markdown :deep(pre code) { padding: 0; background: transparent; color: inherit; }
.ai-workspace-markdown :deep(a) { color: #5269c6; }

.ai-workspace-meta,
.ai-workspace-feedback { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.ai-workspace-meta span { padding: 3px 7px; border-radius: 999px; background: #e9edf5; color: #7a879c; font-size: 9px; }
.ai-workspace-sources { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; font-size: 10px; }
.ai-workspace-sources span { color: #7f8da3; font-weight: 700; }
.ai-workspace-sources a { color: #566dc0; text-decoration: none; }
.ai-workspace-feedback button,
.ai-workspace-retry button { padding: 4px 8px; border: 1px solid #dce3ef; border-radius: 999px; background: #fff; color: #7a889e; cursor: pointer; font: inherit; font-size: 9px; }
.ai-workspace-feedback button:hover:not(:disabled),
.ai-workspace-feedback button.selected { border-color: #99a9e7; background: #f1f3ff; color: #5368ba; }
.ai-workspace-feedback button:disabled { cursor: default; opacity: .6; }
.ai-workspace-retry { display: flex; align-items: center; gap: 8px; margin-top: 8px; color: #a55e6e; font-size: 10px; }

.ai-workspace-typing { display: flex; align-items: center; gap: 13px; }
.ai-workspace-typing > div { display: flex; align-items: center; gap: 4px; padding: 11px 14px; border: 1px solid #e4e8f0; border-radius: 5px 14px 14px; background: #fff; }
.ai-workspace-typing i { width: 5px; height: 5px; border-radius: 50%; background: #7086d7; animation: ai-floating-bounce 1.2s infinite ease-in-out; }
.ai-workspace-typing i:nth-child(2) { animation-delay: .15s; }
.ai-workspace-typing i:nth-child(3) { animation-delay: .3s; }
.ai-workspace-typing em { margin-left: 5px; color: #8795ad; font-size: 11px; font-style: normal; }

.ai-workspace-composer-wrap {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 286px;
  z-index: 3;
  padding: 18px 34px 12px;
  background: linear-gradient(180deg, rgba(251, 252, 254, 0), #fbfcfe 25%, #fbfcfe 100%);
  pointer-events: none;
}

.ai-workspace-composer {
  width: min(860px, 100%);
  margin: 0 auto;
  padding: 13px 14px 10px;
  border: 1px solid #dfe4ee;
  border-radius: 18px;
  background: rgba(255, 255, 255, .97);
  box-shadow: 0 16px 42px rgba(43, 65, 112, .12);
  pointer-events: auto;
}

.ai-workspace-attachment { display: flex; align-items: center; gap: 8px; margin: 0 2px 9px; padding: 7px 9px; border-radius: 9px; background: #f1f5ff; color: #5c70a0; font-size: 11px; }
.ai-workspace-attachment svg { width: 15px; height: 15px; }
.ai-workspace-attachment span { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-workspace-attachment button { border: 0; background: transparent; color: #8190aa; cursor: pointer; font-size: 17px; }
.ai-workspace-composer :deep(.el-textarea__inner) { min-height: 54px !important; padding: 4px 5px; border: 0; box-shadow: none; color: #314765; font-size: 14px; }
.ai-workspace-composer-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 6px; }
.ai-workspace-composer-actions > div { display: flex; min-width: 0; align-items: center; gap: 15px; }
.ai-workspace-composer-actions button:not(.el-button) { display: inline-flex; align-items: center; gap: 5px; padding: 4px 0; border: 0; background: transparent; color: #65799e; cursor: pointer; font: inherit; font-size: 11px; }
.ai-workspace-composer-actions button:not(.el-button):hover { color: #4c64b3; }
.ai-workspace-composer-actions button svg { width: 15px; height: 15px; }
.ai-workspace-composer-actions small { color: #a0aabc; font-size: 9px; }
.ai-workspace-composer-actions :deep(.el-button) { width: 38px; height: 38px; flex: 0 0 38px; background: linear-gradient(135deg, #387ff3, #665edb); box-shadow: 0 7px 16px rgba(73, 93, 203, .22); }
.ai-workspace-composer-actions :deep(.ai-workspace-stop) { border-color: #ef6b75; background: linear-gradient(135deg, #f27879, #e75a70); box-shadow: 0 7px 16px rgba(221, 77, 96, .22); }
.ai-workspace-composer-wrap > p { margin: 7px 0 0; color: #a3adbc; font-size: 9px; text-align: center; pointer-events: auto; }

.ai-workspace-sidebar-mask { display: none; }
.ai-workspace-fade-enter-active,
.ai-workspace-fade-leave-active { transition: opacity .2s ease; }
.ai-workspace-fade-enter-from,
.ai-workspace-fade-leave-to { opacity: 0; }

@media (max-width: 860px) {
  .ai-workspace-sidebar {
    position: absolute;
    inset: 0 auto 0 0;
    width: min(286px, calc(100vw - 54px));
    transform: translateX(-102%);
    transition: transform .2s ease;
  }

  .ai-workspace-sidebar.open { transform: translateX(0); }
  .ai-workspace-sidebar-mask { position: absolute; inset: 0; z-index: 1; display: block; border: 0; background: rgba(24, 37, 66, .32); }
  .ai-workspace-mobile-menu { display: inline-flex; }
  .ai-workspace-header { padding: 0 16px; }
  .ai-workspace-composer-wrap { left: 0; padding-right: 16px; padding-left: 16px; }
}

@media (max-width: 560px) {
  .ai-floating-assistant {
    right: 16px;
    bottom: 16px;
  }

  .ai-floating-trigger {
    width: 56px;
    height: 56px;
    border-radius: 19px;
  }

  .ai-floating-trigger::before {
    border-radius: 15px;
  }

  .ai-floating-panel {
    height: min(580px, calc(100vh - 96px));
    margin-bottom: 11px;
    border-radius: 18px;
  }

  .ai-workspace-header { height: 62px; flex-basis: 62px; gap: 9px; }
  .ai-workspace-title small { display: none; }
  .ai-workspace-return span { display: none; }
  .ai-workspace-return { width: 36px; height: 36px; padding: 0; }
  .ai-workspace-message-list { width: calc(100% - 28px); padding-top: 24px; padding-bottom: 185px; }
  .ai-workspace-message { gap: 8px; margin-bottom: 20px; }
  .ai-workspace-message-avatar { width: 30px; height: 30px; flex-basis: 30px; border-radius: 9px; }
  .ai-workspace-message-body { max-width: calc(100% - 38px); }
  .ai-workspace-message-body > p,
  .ai-workspace-markdown { padding: 11px 12px; font-size: 13px; }
  .ai-workspace-welcome { width: calc(100% - 30px); padding-bottom: 165px; }
  .ai-workspace-welcome h1 { font-size: 27px; }
  .ai-workspace-suggestions { display: grid; width: 100%; }
  .ai-workspace-suggestions button { width: 100%; }
  .ai-workspace-composer-wrap { padding: 12px 10px 7px; }
  .ai-workspace-composer { padding: 11px 11px 8px; border-radius: 15px; }
  .ai-workspace-composer-actions small { display: none; }
  .ai-workspace-composer-actions button:not(.el-button) span { display: none; }
}
</style>
