<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Document, Picture, Promotion, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi } from '@/api'
import { renderAiMarkdown as renderSharedAiMarkdown } from '@/utils/aiMarkdown'
import { historyForMessages, restoreConversationMessages } from '@/utils/aiConversation'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  contextTitle: { type: String, default: '当前内容' },
  contextMeta: { type: String, default: '' },
  questionType: { type: String, default: '' },
  questionId: { type: [Number, String], default: null },
  contextType: { type: String, default: 'question' },
  contextExcerpt: { type: String, default: '' },
  courseId: { type: [Number, String], default: null },
  chapter: { type: String, default: '' },
  agentTask: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])
const draft = ref('')
const attachment = ref(null)
const fileInput = ref(null)
const uploadMode = ref('media')
const messages = ref([])
const messageList = ref(null)
const savedConversations = ref([])
const historyLoading = ref(false)
const historyExpanded = ref(false)
const sending = ref(false)
const activeDocumentId = ref(null)
const supportedDocumentExtensions = ['pdf', 'docx', 'pptx', 'txt', 'md']
let conversationGeneration = 0
let historyLoadSequence = 0
let historyRestoreGeneration = 0

const uploadAccept = computed(() => uploadMode.value === 'image'
  ? 'image/png,image/jpeg,image/webp,image/gif'
  : uploadMode.value === 'document'
    ? '.pdf,.docx,.pptx,.txt,.md'
    : 'image/png,image/jpeg,image/webp,image/gif,video/mp4,video/mpeg,video/quicktime,video/x-msvideo,video/x-flv,video/x-ms-wmv,video/3gpp,video/webm,.mp4,.mpeg,.mpg,.mov,.avi,.flv,.wmv,.3gp,.3gpp')

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

const contextTypeLabel = computed(() => ({
  question: '题目讲解',
  research: '教研协助',
  document: '资料分析'
}[props.contextType] || 'AI助手'))

const contextKey = computed(() => [
  props.contextTitle,
  props.contextMeta,
  props.questionType,
  props.questionId,
  props.contextType,
  props.contextExcerpt,
  props.courseId,
  props.chapter,
  props.agentTask
].map((value) => String(value ?? '')).join('|'))

function resetConversation() {
  conversationGeneration += 1
  historyRestoreGeneration += 1
  draft.value = ''
  attachment.value = null
  sending.value = false
  activeDocumentId.value = null
  messages.value = [{
    role: 'assistant',
    content: `你好，我是 AI 讲解助手。接下来可以围绕“${props.contextTitle || '当前内容'}”帮你梳理思路、解释知识点。`,
    intro: true
  }]
  scrollToBottom()
}

function welcomeMessage() {
  return `你好，我是 AI 讲解助手。接下来可以围绕“${props.contextTitle || '当前内容'}”帮你梳理思路、解释知识点。`
}

async function loadHistory({ restore = false } = {}) {
  const requestId = ++historyLoadSequence
  const restoreGeneration = historyRestoreGeneration
  historyLoading.value = true
  try {
    const history = await aiApi.history()
    if (requestId !== historyLoadSequence) return
    savedConversations.value = Array.isArray(history) ? history : []
    if (restore && props.modelValue && !sending.value && restoreGeneration === historyRestoreGeneration) {
      messages.value = restoreConversationMessages(savedConversations.value, welcomeMessage())
      scrollToBottom()
    }
  } catch {
    if (requestId === historyLoadSequence) savedConversations.value = []
  } finally {
    if (requestId === historyLoadSequence) historyLoading.value = false
  }
}

function beforeClose(done) {
  if (sending.value) {
    ElMessage.info('AI 正在回答，请等待回答完成后再关闭窗口')
    return
  }
  done()
}

function scrollToBottom() {
  nextTick(() => {
    const element = messageList.value
    if (element) element.scrollTop = element.scrollHeight
  })
}

function updateVisibility(value) {
  if (!value && sending.value) {
    ElMessage.info('AI 正在回答，请等待回答完成后再关闭窗口')
    return
  }
  emit('update:modelValue', value)
}

function triggerUpload(mode = 'media') {
  uploadMode.value = mode
  fileInput.value?.click()
}

function handleUpload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  if (uploadMode.value === 'media') {
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
  } else {
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!supportedDocumentExtensions.includes(extension)) {
      ElMessage.warning('请选择 PDF、DOCX、PPTX、TXT 或 MD 文档')
      return
    }
    if (file.size > 20 * 1024 * 1024) {
      ElMessage.warning('文档不能超过 20MB，请压缩后重试')
      return
    }
  }

  attachment.value = file
}

function removeAttachment() {
  attachment.value = null
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderAssistantContent(value) {
  const raw = String(value || '')
    .replace(/&#x9;|&#9;/gi, '\t')
    .replace(/&#x20;|&#32;|&nbsp;/gi, ' ')
    .replace(new RegExp('\\\\{1,2}[ \\t]*(?=\\r?\\n|$)', 'g'), '')
    .replace(/^\s*\\+\s*$/gm, '')
    .replace(/\\([\\`*_{}[\]()#+.!-])/g, '$1')
    .split(/\r?\n/)
    .map((line) => line.replace(/[ \t]+$/g, ''))
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .replace(/\[([^\]]+)\]\(\s*\[\s*(https?:\/\/[^\s\]\)。，！？；：、]+)[^\]]*\]\s*\)\s*\(\s*\2[^)]*\)/gi, '[$1]($2)')
  const links = []
  const markdownProtectedText = raw.replace(/\[([^\]]+)\]\(\s*(https?:\/\/[^\s<>"'，。！？；：、)\]}]+)\s*\)/gi, (_, label, url) => {
    const index = links.length
    links.push({ label, url })
    return `@@AI_URL_${index}@@`
  })
  const protectedText = markdownProtectedText.replace(/https?:\/\/[^\s<>"'，。！？；：、)\]}]+/gi, (url) => {
    const index = links.length
    links.push({ label: url, url })
    return `@@AI_URL_${index}@@`
  })

  const html = escapeHtml(protectedText)
    .replace(/@@AI_URL_(\d+)@@/g, (_, index) => {
      const link = links[Number(index)]
      return `<a href="${escapeHtml(link.url)}" target="_blank" rel="noreferrer">${escapeHtml(link.label)}</a>`
    })
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')

  return html
}

async function sendMessage(retryRequest = null) {
  const retrying = Boolean(retryRequest)
  const text = (retryRequest?.text ?? draft.value).trim()
  const currentAttachment = retrying ? retryRequest.attachment : attachment.value
  if (!text && !currentAttachment) return

  const attachmentType = detectMediaType(currentAttachment)
  const isImage = attachmentType.startsWith('image/')
  const isVideo = attachmentType.startsWith('video/')
  const prompt = text || (isImage
    ? '请分析这张图片中的题目，先提取题目内容，再给出解题思路和答案。'
    : isVideo
      ? '请分析这个视频中的教学内容，提取重点、关键步骤和需要注意的地方。'
      : '请分析这份文档，提取重点并给出清晰的学习讲解。')
  const attachmentLabel = currentAttachment
    ? `\n[待分析${isImage ? '图片' : isVideo ? '视频' : '文档'}：${currentAttachment.name}]`
    : ''
  // 用户已经开始本地会话，不能让异步历史恢复覆盖当前消息。
  historyRestoreGeneration += 1
  const history = historyForMessages(messages.value)
  const requestGeneration = conversationGeneration
  const userContent = `${prompt}${attachmentLabel}`.trim()
  if (!retrying) messages.value.push({ role: 'user', content: userContent })
  draft.value = ''
  attachment.value = null

  sending.value = true
  scrollToBottom()
  try {
    const commonPayload = {
      message: prompt,
      contextTitle: props.contextTitle,
      contextMeta: props.contextMeta,
      questionType: props.questionType,
      questionId: props.questionId,
      contextExcerpt: props.contextExcerpt,
      courseId: props.courseId || null,
      chapter: props.chapter || '',
      // 没有课程时只能走普通助手，避免把“备课任务”误当成全局对话指令。
      agentTask: props.courseId && props.agentTask ? props.agentTask : '',
      history,
      documentId: !currentAttachment ? activeDocumentId.value : null
    }
    let response
    if (currentAttachment && (isImage || isVideo)) {
      response = await aiApi.media(currentAttachment, commonPayload)
    } else if (currentAttachment) {
      response = await aiApi.document(currentAttachment, commonPayload)
    } else {
      response = await (commonPayload.agentTask && props.courseId ? aiApi.agent(commonPayload) : aiApi.chat(commonPayload))
    }
    if (requestGeneration !== conversationGeneration) return
    if (currentAttachment && !isImage && response?.documentId) {
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
    // 回答已由服务端保存；只刷新历史摘要，不重置当前对话窗口。
    void loadHistory()
  } catch (error) {
    if (requestGeneration !== conversationGeneration) return
    messages.value.push({
      role: 'assistant',
      content: error?.message || 'AI 服务暂时不可用，请稍后重试。',
      error: true,
      retryable: true,
      retryRequest: { text: prompt, attachment: currentAttachment }
    })
  } finally {
    sending.value = false
    if (requestGeneration !== conversationGeneration) resetConversation()
    scrollToBottom()
  }
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
    DOCUMENT_RAG: '上传文档',
    RAG: '课程资料',
    WEB_SEARCH: '联网核验',
    RAG_AND_WEB: '课程资料 + 联网',
    TEACHING_AGENT: '教研 Agent'
  }[message?.answerMode || message?.route] || ''
}

function confidenceLabel(message) {
  return {
    HIGH: '依据充分',
    MEDIUM: '通用回答',
    LOW: '资料未充分覆盖',
    UNVERIFIED: '需自行核验'
  }[message?.confidence] || ''
}

function qualityLabel(message) {
  return {
    PASS: '已通过基础校验',
    REVIEW_REQUIRED: '建议核验来源',
    NO_EVIDENCE: '课程资料未覆盖'
  }[message?.qualityStatus] || ''
}

async function submitFeedback(message, helpful, feedbackType = '') {
  if (!message?.requestId || message.feedback || sending.value) return
  let comment = ''
  if (feedbackType === 'REPORT') {
    try {
      const result = await ElMessageBox.prompt('请说明哪里需要改进（可选）', '报告回答问题', {
        confirmButtonText: '提交',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: (value) => !value || value.length <= 1000 || '说明不能超过 1000 个字符'
      })
      comment = result.value || ''
    } catch {
      return
    }
  }
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
  }
}

watch(
  () => props.modelValue,
  (visible, previousVisible) => {
    if (visible && previousVisible !== true) {
      resetConversation()
      void loadHistory({ restore: true })
    }
  },
  { immediate: true }
)

watch(contextKey, (value, previousValue) => {
  if (props.modelValue && previousValue && value !== previousValue) {
    if (sending.value) {
      // 让未完成请求失效；finally 会按新题目重置窗口。
      conversationGeneration += 1
    } else {
      resetConversation()
    }
  }
})
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    direction="rtl"
    size="min(430px, 94vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :before-close="beforeClose"
    class="ai-assistant-drawer"
    @update:model-value="updateVisibility"
  >
    <template #header>
      <div class="ai-drawer-header">
        <div>
          <span class="ai-drawer-kicker">KIMI AI</span>
          <strong>AI讲解助手</strong>
        </div>
        <span class="ai-drawer-status">{{ contextTypeLabel }}</span>
      </div>
    </template>

    <div class="ai-drawer-body">
      <section class="ai-context-card">
        <div class="ai-context-label">当前上下文</div>
        <strong>{{ contextTitle || '当前内容' }}</strong>
        <span v-if="contextMeta">{{ contextMeta }}</span>
        <p v-if="contextExcerpt">{{ contextExcerpt }}</p>
      </section>

      <section class="ai-history-card">
        <button type="button" class="ai-history-toggle" @click="historyExpanded = !historyExpanded">
          <span>账号历史记忆</span>
          <small>{{ historyLoading ? '读取中…' : `${savedConversations.length} 条` }}</small>
          <span>{{ historyExpanded ? '收起' : '展开' }}</span>
        </button>
        <div v-if="historyExpanded" class="ai-history-list">
          <div v-if="!historyLoading && !savedConversations.length" class="ai-history-empty">还没有保存的 AI 对话</div>
          <article v-for="item in savedConversations.slice(0, 8)" :key="item.id" class="ai-history-item">
            <time>{{ item.createdAt || '' }}</time>
            <strong>{{ item.question }}</strong>
            <p>{{ item.answer }}</p>
          </article>
        </div>
      </section>

      <section ref="messageList" class="ai-message-list" aria-live="polite">
        <article v-for="(message, index) in messages" :key="index" class="ai-message" :class="message.role">
          <div class="ai-message-role">{{ message.role === 'assistant' ? 'AI助手' : '你' }}</div>
          <div v-if="message.role === 'assistant'" class="ai-message-markdown" v-html="renderSharedAiMarkdown(message.content)" />
          <p v-else>{{ message.content }}</p>
          <div v-if="message.role === 'assistant' && message.requestId" class="ai-message-meta">
            <span v-if="modeLabel(message)">{{ modeLabel(message) }}</span>
            <span v-if="confidenceLabel(message)">{{ confidenceLabel(message) }}</span>
            <span v-if="qualityLabel(message)">{{ qualityLabel(message) }}</span>
            <span v-else-if="!confidenceLabel(message) && message.hasEvidence">已找到课程依据</span>
            <span v-else-if="message.route === 'RAG'">课程资料未直接命中</span>
            <span v-if="message.retrievedChunkCount">命中 {{ message.retrievedChunkCount }} 个片段</span>
            <span v-if="message.latencyMs !== undefined">{{ message.latencyMs }} ms</span>
          </div>
          <div v-if="message.role === 'assistant' && message.sources?.length" class="ai-source-list">
            <div class="ai-source-title">回答依据</div>
            <div v-for="source in message.sources" :key="`${source.documentName || '课程资源'}-${source.chunkIndex}`" class="ai-source-item">
              <strong>{{ source.sourceKind === 'WEB' ? (source.documentName || '联网来源') : `${source.documentName || '课程资源'} · 片段 ${source.chunkIndex}` }}</strong>
              <a v-if="source.sourceUrl" :href="source.sourceUrl" target="_blank" rel="noreferrer">打开来源</a>
              <span>{{ source.excerpt }}</span>
            </div>
          </div>
          <div v-if="message.role === 'assistant' && message.retryable" class="ai-message-retry">
            <span>回答没有完成，窗口已保留当前内容。</span>
            <button type="button" :disabled="sending" @click="retryMessage(message)">重试</button>
          </div>
          <div v-if="message.role === 'assistant' && message.requestId" class="ai-message-feedback">
            <button type="button" :class="{ selected: message.feedback === 'HELPFUL' }" :disabled="Boolean(message.feedback) || sending" @click="submitFeedback(message, true)">有帮助</button>
            <button type="button" :class="{ selected: message.feedback === 'NOT_HELPFUL' }" :disabled="Boolean(message.feedback) || sending" @click="submitFeedback(message, false)">没帮助</button>
            <button type="button" :class="{ selected: message.feedback === 'REPORT' }" :disabled="Boolean(message.feedback) || sending" @click="submitFeedback(message, false, 'REPORT')">报告问题</button>
          </div>
        </article>
        <div v-if="sending" class="ai-typing" aria-live="polite">
          <span /><span /><span />
          <em>AI 正在回答，请稍候…</em>
        </div>
      </section>

      <div class="ai-composer">
        <div v-if="attachment" class="ai-attachment">
          <Picture v-if="attachmentIsImage" />
          <VideoCamera v-else-if="attachmentIsVideo" />
          <Document v-else />
          <span>{{ attachment.name }}</span>
          <button type="button" aria-label="移除附件" @click="removeAttachment">×</button>
        </div>
        <el-input
          v-model="draft"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入你想了解的内容..."
          :disabled="sending"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <input ref="fileInput" class="ai-file-input" type="file" :accept="uploadAccept" @change="handleUpload" />
        <div class="ai-composer-actions">
          <div class="ai-composer-tools">
            <button type="button" title="添加图片或视频" @click="triggerUpload"><Picture />图片/视频</button>
            <button type="button" title="添加文档" @click="triggerUpload('document')"><Document />文档</button>
          </div>
          <el-button type="primary" :loading="sending" :disabled="(!draft.trim() && !attachment) || sending" :icon="Promotion" @click="sendMessage">发送</el-button>
        </div>
        <p class="ai-composer-hint">AI 生成内容仅供学习参考，请结合教材和教师讲解核对。</p>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.ai-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.ai-drawer-header > div {
  display: grid;
  gap: 3px;
}

.ai-drawer-kicker {
  color: #7468ed;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .14em;
}

.ai-drawer-header strong {
  color: #243858;
  font-size: 18px;
}

.ai-drawer-status {
  padding: 4px 9px;
  border-radius: 999px;
  background: #f0efff;
  color: #655ad9;
  font-size: 12px;
  white-space: nowrap;
}

.ai-drawer-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 16px;
}

.ai-context-card {
  padding: 13px 14px;
  border: 1px solid #e2e6f4;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8f7ff, #f7fbff);
}

.ai-context-label {
  margin-bottom: 6px;
  color: #7d88a1;
  font-size: 11px;
}

.ai-context-card strong {
  display: block;
  color: #2d4164;
  font-size: 14px;
  line-height: 1.55;
}

.ai-context-card > span {
  display: block;
  margin-top: 4px;
  color: #78869d;
  font-size: 12px;
}

.ai-context-card p {
  display: -webkit-box;
  margin: 9px 0 0;
  overflow: hidden;
  color: #52627a;
  font-size: 13px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.ai-history-card {
  border: 1px solid #e2e6f4;
  border-radius: 8px;
  background: #fbfcff;
}

.ai-history-toggle {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 8px;
  padding: 9px 11px;
  border: 0;
  background: transparent;
  color: #566680;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  text-align: left;
}

.ai-history-toggle small {
  margin-left: auto;
  color: #929db1;
  font-size: 10px;
}

.ai-history-toggle > span:last-child {
  color: #665ed0;
  font-size: 11px;
}

.ai-history-list {
  display: grid;
  gap: 7px;
  max-height: 210px;
  overflow: auto;
  padding: 0 10px 10px;
}

.ai-history-item {
  padding: 7px 8px;
  border-radius: 6px;
  background: #f1f4fb;
}

.ai-history-item time {
  display: block;
  margin-bottom: 3px;
  color: #9aa5b8;
  font-size: 10px;
}

.ai-history-item strong,
.ai-history-item p {
  display: -webkit-box;
  overflow: hidden;
  color: #586781;
  font-size: 11px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.ai-history-item strong { color: #4d5d7a; }
.ai-history-item p { margin: 3px 0 0; }
.ai-history-empty { padding: 4px 0; color: #9aa5b8; font-size: 11px; }

.ai-message-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  min-height: 160px;
  overflow: auto;
  padding: 2px 2px 12px;
}

.ai-message {
  max-width: 90%;
  padding: 10px 12px;
  border-radius: 9px;
}

.ai-message.assistant {
  align-self: flex-start;
  background: #f3f5fb;
}

.ai-message.user {
  align-self: flex-end;
  background: #eef0ff;
}

.ai-message-role {
  margin-bottom: 4px;
  color: #7069b6;
  font-size: 11px;
  font-weight: 600;
}

.ai-message p,
.ai-message-markdown {
  margin: 0;
  color: #40516e;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.ai-message-markdown :deep(p),
.ai-message-markdown :deep(h1),
.ai-message-markdown :deep(h2),
.ai-message-markdown :deep(h3) {
  margin: 0 0 7px;
}

.ai-message-markdown :deep(p:last-child),
.ai-message-markdown :deep(h1:last-child),
.ai-message-markdown :deep(h2:last-child),
.ai-message-markdown :deep(h3:last-child) {
  margin-bottom: 0;
}

.ai-message-markdown :deep(h1),
.ai-message-markdown :deep(h2),
.ai-message-markdown :deep(h3) {
  color: #4355a0;
  font-size: 14px;
}

.ai-message-markdown :deep(ul),
.ai-message-markdown :deep(ol) {
  margin: 4px 0 8px;
  padding-left: 19px;
}

.ai-message-markdown :deep(li) { margin: 3px 0; }
.ai-message-markdown :deep(a) {
  color: #4b63c6;
  text-decoration: underline;
  overflow-wrap: anywhere;
}

.ai-message-markdown :deep(code) {
  padding: 1px 4px;
  border-radius: 3px;
  background: #e8ebf6;
  font-family: Consolas, monospace;
  font-size: 12px;
}

.ai-message-markdown :deep(pre) {
  margin: 7px 0;
  padding: 8px;
  overflow: auto;
  border-radius: 7px;
  background: #202943;
  color: #f4f7ff;
}

.ai-message-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.ai-message-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
  color: #8793aa;
  font-size: 10px;
}

.ai-message-meta span {
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(224, 230, 245, .72);
}

.ai-message-feedback {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 9px;
}

.ai-message-feedback button {
  padding: 3px 7px;
  border: 1px solid #dce3f1;
  border-radius: 999px;
  background: #fff;
  color: #7a88a2;
  cursor: pointer;
  font: inherit;
  font-size: 10px;
}

.ai-message-feedback button:hover:not(:disabled),
.ai-message-feedback button.selected {
  border-color: #9a98e8;
  background: #f1f0ff;
  color: #5c56bd;
}

.ai-message-feedback button:disabled {
  cursor: default;
  opacity: .65;
}

.ai-typing {
  display: flex;
  align-items: center;
  align-self: flex-start;
  gap: 4px;
  color: #8795ad;
  font-size: 11px;
}

.ai-typing span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #7086d7;
  animation: ai-typing-bounce 1.2s infinite ease-in-out;
}

.ai-typing span:nth-child(2) { animation-delay: .15s; }
.ai-typing span:nth-child(3) { animation-delay: .3s; }

.ai-typing em {
  margin-left: 3px;
  font-style: normal;
}

@keyframes ai-typing-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: .5; }
  30% { transform: translateY(-3px); opacity: 1; }
}

.ai-message-retry {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 8px;
  color: #a15d6d;
  font-size: 11px;
}

.ai-message-retry button {
  padding: 3px 8px;
  border: 1px solid #e5aeb9;
  border-radius: 999px;
  background: #fff;
  color: #a24f62;
  cursor: pointer;
  font: inherit;
}

.ai-message-retry button:hover:not(:disabled) {
  background: #fff0f3;
}

.ai-message-retry button:disabled {
  cursor: default;
  opacity: .55;
}

.ai-source-list {
  display: grid;
  gap: 6px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #dfe4f0;
}

.ai-source-title {
  color: #626d86;
  font-size: 11px;
  font-weight: 700;
}

.ai-source-item {
  display: grid;
  gap: 2px;
  padding: 6px 7px;
  border-radius: 5px;
  background: rgba(255, 255, 255, .72);
}

.ai-source-item strong {
  color: #6b64c8;
  font-size: 11px;
}

.ai-source-item span {
  display: -webkit-box;
  overflow: hidden;
  color: #728099;
  font-size: 11px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.ai-composer {
  flex: 0 0 auto;
  padding-top: 12px;
  border-top: 1px solid #e8ebf1;
}

.ai-file-input {
  display: none;
}

.ai-attachment {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding: 7px 9px;
  border-radius: 6px;
  background: #f5f7fb;
  color: #566680;
  font-size: 12px;
}

.ai-attachment svg {
  flex: 0 0 auto;
  width: 15px;
  height: 15px;
  color: #7068dc;
}

.ai-attachment span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-attachment button {
  border: 0;
  background: transparent;
  color: #8995a8;
  cursor: pointer;
  font-size: 17px;
  line-height: 1;
}

.ai-composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 9px;
}

.ai-composer-tools {
  display: flex;
  gap: 10px;
}

.ai-composer-tools button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 0;
  border: 0;
  background: transparent;
  color: #7b879b;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.ai-composer-tools button:hover {
  color: #5b55d9;
}

.ai-composer-tools svg {
  width: 15px;
  height: 15px;
}

.ai-composer-hint {
  margin: 8px 0 0;
  color: #a0aabc;
  font-size: 11px;
  line-height: 1.5;
}
</style>
