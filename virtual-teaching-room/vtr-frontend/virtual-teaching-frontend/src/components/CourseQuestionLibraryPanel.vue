<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import * as XLSX from 'xlsx'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentChecked, EditPen, UploadFilled } from '@element-plus/icons-vue'
import { learningResourceApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import AiAssistantButton from '@/components/AiAssistantButton.vue'
import AiAssistantDrawer from '@/components/AiAssistantDrawer.vue'

const props = defineProps({
  courseId: { type: [Number, String], required: true },
  chapter: { type: String, required: true },
  sectionId: { type: [Number, String], required: true },
  sectionTitle: { type: String, default: '' }
})

const authStore = useAuthStore()
const loading = ref(false)
const questions = ref([])
const statistics = ref(null)
const editorVisible = ref(false)
const uploadVisible = ref(false)
const uploadLoading = ref(false)
const uploadFileName = ref('')
const uploadDrafts = ref([])
const fileInputRef = ref(null)
const editingId = ref(null)
const aiVisible = ref(false)
const aiContext = reactive({ title: '', meta: '', questionType: '', questionId: null, type: 'question', excerpt: '' })
const questionForm = reactive({
  title: '',
  stem: '',
  questionType: 'SINGLE_CHOICE',
  options: '',
  referenceAnswer: '',
  analysis: '',
  difficulty: 3,
  knowledgePoint: ''
})

const isAdmin = computed(() => authStore.isAdmin)
const isTeacher = computed(() => authStore.isTeacher)
const canManage = computed(() => isTeacher.value)
const canReview = computed(() => isAdmin.value)
const canSeeStatistics = computed(() => isTeacher.value || isAdmin.value)

const typeOptions = [
  { value: 'SINGLE_CHOICE', label: '单选题' },
  { value: 'MULTIPLE_CHOICE', label: '多选题' },
  { value: 'JUDGMENT', label: '判断题' },
  { value: 'FILL', label: '填空题' },
  { value: 'TEXT', label: '简答题' },
  { value: 'PROGRAMMING', label: '编程题' }
]

const visibleQuestions = computed(() => {
  return (canManage.value ? questions.value : questions.value.filter((item) => item.status === 'PUBLISHED'))
    .filter((item) => item.status !== 'DELETED')
})

const editorActionLabel = computed(() => (editingId.value ? '保存修改' : '保存到题库'))
const editorHint = computed(() => (editingId.value
  ? '修改后将更新当前小节的题目内容。'
  : '新题会自动归入当前小节，保存后即可用于作业和测验。'))

function labelOfType(value) {
  return (typeOptions.find((item) => item.value === value) || {}).label || '练习题'
}

function statusInfo(value) {
  return {
    DRAFT: ['草稿', 'info'],
    PUBLISHED: ['已发布', 'success'],
    ARCHIVED: ['已归档', 'warning'],
    PENDING: ['待审核', 'warning'],
    REJECTED: ['已驳回', 'danger']
  }[value] || [value, 'info']
}

function canEdit(item) {
  return isTeacher.value && Number(item.teacherId) === Number(authStore.user?.id)
}

function openAiAssistant(item) {
  Object.assign(aiContext, {
    title: item.title || '当前题目',
    meta: `${labelOfType(item.questionType)} · 难度 ${item.difficulty || 3}/5`,
    questionType: item.questionType || '',
    questionId: item.id || null,
    type: 'question',
    excerpt: questionExcerpt(item)
  })
  aiVisible.value = true
}

function questionExcerpt(item) {
  const parts = [item.stem || '']
  if (item.options) parts.push(`选项：\n${item.options}`)
  if (item.knowledgePoint) parts.push(`知识点：${item.knowledgePoint}`)
  return parts.filter(Boolean).join('\n\n')
}

function payloadBase() {
  return { courseId: Number(props.courseId), chapter: props.chapter, sectionId: Number(props.sectionId) }
}

function resetQuestion() {
  editingId.value = null
  Object.assign(questionForm, {
    title: '',
    stem: '',
    questionType: 'SINGLE_CHOICE',
    options: '',
    referenceAnswer: '',
    analysis: '',
    difficulty: 3,
    knowledgePoint: ''
  })
}

function questionToForm(item, duplicate = false) {
  const baseTitle = normalizeText(item?.title) || '未命名题目'
  return {
    title: duplicate ? baseTitle + '（副本）' : baseTitle,
    stem: normalizeText(item?.stem),
    questionType: normalizeQuestionType(item?.questionType),
    options: normalizeText(item?.options),
    referenceAnswer: normalizeText(item?.referenceAnswer),
    analysis: normalizeText(item?.analysis) || (normalizeQuestionType(item?.questionType) === 'TEXT' ? normalizeText(item?.referenceAnswer) : ''),
    difficulty: normalizeDifficulty(item?.difficulty),
    knowledgePoint: normalizeText(item?.knowledgePoint)
  }
}

function normalizeText(value) {
  return String(value == null ? '' : value).trim()
}

function normalizeDifficulty(value) {
  const num = Number.parseInt(String(value || '3'), 10)
  if (Number.isNaN(num)) return 3
  return Math.min(5, Math.max(1, num))
}

function normalizeQuestionType(value) {
  const target = normalizeText(value).toUpperCase()
  const item = typeOptions.find((option) => [option.value, option.label].some((label) => normalizeText(label).toUpperCase() === target))
  return item ? item.value : 'SINGLE_CHOICE'
}

function pickCell(row, keys) {
  for (const key of keys) {
    const found = Object.entries(row || {}).find(([raw]) => normalizeText(raw).toLowerCase() === normalizeText(key).toLowerCase())
    if (found && normalizeText(found[1])) return normalizeText(found[1])
  }
  return ''
}

function parseRow(row) {
  const title = pickCell(row, ['title', '题目标题', '题目', '名称'])
  const stem = pickCell(row, ['stem', '题干', '题目内容', '内容'])
  const referenceAnswer = pickCell(row, ['referenceAnswer', '参考答案', '正确答案', '答案'])
  if (!title && !stem && !referenceAnswer) return null
  return {
    title,
    stem,
    questionType: normalizeQuestionType(pickCell(row, ['questionType', '题型', '类型'])),
    options: pickCell(row, ['options', '选项', '选项内容']),
    referenceAnswer,
    analysis: pickCell(row, ['analysis', '解析', '答案解析']),
    difficulty: normalizeDifficulty(pickCell(row, ['difficulty', '难度'])),
    knowledgePoint: pickCell(row, ['knowledgePoint', '知识点', '关联知识点'])
  }
}

function parseFile(file, content) {
  const workbook = /\.(xlsx|xls)$/i.test(file.name)
    ? XLSX.read(content, { type: 'array' })
    : XLSX.read(content, { type: 'string' })
  const sheet = workbook.Sheets[workbook.SheetNames[0]]
  return XLSX.utils.sheet_to_json(sheet, { defval: '', blankrows: false }).map(parseRow).filter(Boolean)
}

async function load() {
  if (!props.courseId || !props.sectionId) {
    questions.value = []
    statistics.value = null
    return
  }
  loading.value = true
  try {
    const payload = await learningResourceApi.questions(payloadBase())
    questions.value = Array.isArray(payload) ? payload : []
    statistics.value = canSeeStatistics.value ? (await learningResourceApi.statistics(payloadBase())) || null : null
  } catch (error) {
    ElMessage.error(error?.message || '题目库加载失败')
    questions.value = []
    statistics.value = null
  } finally {
    loading.value = false
  }
}

function openQuestion(item, duplicate = false) {
  if (!item) {
    resetQuestion()
    editorVisible.value = true
    return
  }
  editingId.value = duplicate ? null : item.id
  Object.assign(questionForm, questionToForm(item, duplicate))
  editorVisible.value = true
}

function duplicateQuestion(item) {
  openQuestion(item, true)
}

async function saveQuestion() {
  const isEditing = Boolean(editingId.value)
  try {
    const payload = { ...payloadBase(), ...questionForm }
    if (editingId.value) await learningResourceApi.updateQuestion(editingId.value, payload)
    else await learningResourceApi.createQuestion(payload)
    ElMessage.success(isEditing ? '题目已保存' : '题目已加入题库')
    editorVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '题目保存失败')
  }
}

async function removeQuestion(item) {
  try {
    await ElMessageBox.confirm('删除后题目会从题库隐藏，历史作业和测验记录仍会保留。', '确认删除题目', { type: 'warning' })
    await learningResourceApi.removeQuestion(item.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除失败')
  }
}

async function reviewQuestion(item, action) {
  try {
    const result = await ElMessageBox.prompt(action === 'APPROVE' ? '可填写审核备注。' : '请填写驳回原因。', action === 'APPROVE' ? '审核通过' : '驳回题目', {
      inputType: 'textarea',
      inputValidator: (text) => action === 'REJECT' && !normalizeText(text) ? '驳回原因不能为空' : true
    })
    await learningResourceApi.review('questions', item.id, { action, remark: result.value || '' })
    ElMessage.success('审核结果已提交')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '审核失败')
  }
}

async function archiveQuestion(item) {
  try {
    await learningResourceApi.archive('questions', item.id)
    ElMessage.success('已归档')
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '归档失败')
  }
}

function openUploadPicker() {
  fileInputRef.value && fileInputRef.value.click()
}

async function handleUploadFile(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file) return
  try {
    const drafts = /\.(pdf|docx|md)$/i.test(file.name)
      ? await learningResourceApi.parseQuestionDocument(file, { courseId: Number(props.courseId), sectionId: Number(props.sectionId) })
      : parseFile(file, /\.(xlsx|xls)$/i.test(file.name) ? new Uint8Array(await file.arrayBuffer()) : await file.text())
    if (!drafts.length) throw new Error('没有识别到有效题目，请确认表头包含题目、题干和参考答案')
    uploadFileName.value = file.name
    uploadDrafts.value = drafts
    uploadVisible.value = true
  } catch (error) {
    ElMessage.error(error?.message || '题目文件解析失败')
  }
}

async function submitUpload() {
  if (!uploadDrafts.value.length) return
  uploadLoading.value = true
  try {
    let created = 0
    for (const draft of uploadDrafts.value) {
      await learningResourceApi.createQuestion({ ...payloadBase(), ...draft })
      created += 1
    }
    ElMessage.success('已导入 ' + created + ' 道题目')
    uploadVisible.value = false
    uploadDrafts.value = []
    uploadFileName.value = ''
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '题目导入失败')
  } finally {
    uploadLoading.value = false
  }
}

watch(() => [props.courseId, props.chapter, props.sectionId], load, { immediate: true })
onMounted(load)
</script>

<template>
  <section class="question-library-panel" v-loading="loading">
    <div class="library-header">
      <div>
        <h4>{{ sectionTitle || '题目库' }}</h4>
        <p>{{ chapter }} · 这里只维护当前小节的题目，支持新建、复制和批量导入。</p>
      </div>
      <div v-if="canManage" class="library-actions">
        <el-button type="primary" :icon="EditPen" @click="openQuestion()">新建题目</el-button>
        <el-button plain :icon="UploadFilled" @click="openUploadPicker()">批量导入</el-button>
        <input ref="fileInputRef" class="question-file-input" type="file" accept=".pdf,.docx,.xlsx,.xls,.csv,.txt,.md,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" @change="handleUploadFile" />
      </div>
    </div>

    <div class="library-workflow">
      <span>录题流程</span>
      <span>1 新建单题</span>
      <span>2 批量导题</span>
      <span>3 复制现有题目继续改</span>
    </div>

    <div v-if="statistics && canSeeStatistics" class="library-stats">
      <span>{{ statistics.questionCount || 0 }} 题</span>
      <span>{{ statistics.correctRate || 0 }}% 客观题正确率</span>
    </div>

    <div v-if="visibleQuestions.length" class="library-list">
      <article v-for="(item, index) in visibleQuestions" :key="item.id" class="library-item">
        <div class="library-item-main">
          <div class="question-row-tags">
            <span>{{ index + 1 }}</span>
            <el-tag size="small" effect="plain">{{ labelOfType(item.questionType) }}</el-tag>
            <el-tag v-if="canManage || canReview" size="small" :type="statusInfo(item.status)[1]">{{ statusInfo(item.status)[0] }}</el-tag>
            <span>难度 {{ item.difficulty || 3 }}/5</span>
          </div>
          <strong>{{ item.title || '未命名题目' }}</strong>
          <p>{{ item.stem || '暂无题干' }}</p>
          <p v-if="item.knowledgePoint" class="library-meta">知识点：{{ item.knowledgePoint }}</p>
        </div>
        <div class="library-item-actions">
          <AiAssistantButton @click="openAiAssistant(item)" />
          <el-button v-if="canEdit(item)" link @click="openQuestion(item)">编辑</el-button>
          <el-button v-if="canEdit(item)" link type="primary" @click="duplicateQuestion(item)">复制</el-button>
          <el-button v-if="canEdit(item)" link type="danger" @click="removeQuestion(item)">删除</el-button>
          <el-button v-if="canReview && (item.status === 'PENDING' || item.status === 'REJECTED')" link type="success" @click="reviewQuestion(item, 'APPROVE')">通过</el-button>
          <el-button v-if="canReview && item.status === 'PENDING'" link type="danger" @click="reviewQuestion(item, 'REJECT')">驳回</el-button>
          <el-button v-if="canReview && item.status === 'PUBLISHED'" link type="warning" @click="archiveQuestion(item)">归档</el-button>
        </div>
      </article>
    </div>
    <div v-else class="library-empty">
      <DocumentChecked />
      <span>暂无题目</span>
      <div v-if="canManage" class="library-empty-actions">
        <el-button type="primary" @click="openQuestion()">新建题目</el-button>
        <el-button plain @click="openUploadPicker()">批量导入</el-button>
      </div>
    </div>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑题目' : '新建题目'" width="min(760px, 94vw)">
      <el-form label-position="top">
        <el-alert :title="editorHint" type="info" :closable="false" show-icon class="library-editor-tip" />
        <div class="question-form-grid">
          <el-form-item label="题目标题" required><el-input v-model="questionForm.title" /></el-form-item>
          <el-form-item label="题型"><el-select v-model="questionForm.questionType"><el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="难度"><el-rate v-model="questionForm.difficulty" :max="5" /></el-form-item>
          <el-form-item v-if="questionForm.knowledgePoint" label="关联知识点"><el-input v-model="questionForm.knowledgePoint" /></el-form-item>
        </div>
        <el-form-item label="题干" required><el-input v-model="questionForm.stem" type="textarea" :rows="4" /></el-form-item>
        <el-form-item v-if="questionForm.questionType.indexOf('CHOICE') > -1" label="选项（每行一个）"><el-input v-model="questionForm.options" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="参考答案" required><el-input v-model="questionForm.referenceAnswer" /></el-form-item>
        <el-form-item label="答案解析"><el-input v-model="questionForm.analysis" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" @click="saveQuestion">{{ editorActionLabel }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="uploadVisible" title="批量导入题目" width="min(860px, 94vw)">
      <div class="upload-panel">
        <p>文件：{{ uploadFileName }}</p>
        <p>识别到 {{ uploadDrafts.length }} 道题目。系统已自动区分题号、题干、选项、正确答案和解析；确认后会按当前小节归入题库。</p>
        <el-table :data="uploadDrafts" height="360" border>
          <el-table-column type="index" width="60" />
          <el-table-column prop="title" label="标题" min-width="160" />
          <el-table-column prop="questionType" label="题型" width="120">
            <template #default="{ row }">{{ labelOfType(row.questionType) }}</template>
          </el-table-column>
          <el-table-column prop="difficulty" label="难度" width="90" />
          <el-table-column prop="knowledgePoint" label="知识点" min-width="140" />
          <el-table-column label="操作" width="90">
            <template #default="{ $index }"><el-button link type="danger" @click="uploadDrafts.splice($index, 1)">移除</el-button></template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadLoading" @click="submitUpload">导入题目</el-button>
      </template>
    </el-dialog>
    <AiAssistantDrawer v-model="aiVisible" :course-id="props.courseId" :chapter="props.chapter" :context-title="aiContext.title" :context-meta="aiContext.meta" :question-type="aiContext.questionType" :question-id="aiContext.questionId" :context-type="aiContext.type" :context-excerpt="aiContext.excerpt" />
  </section>
</template>

<style scoped>
.question-library-panel { display: grid; gap: 14px; }
.library-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.library-header h4 { margin: 0; color: var(--text-main); font-size: 20px; }
.library-header p { margin: 6px 0 0; color: var(--text-muted); font-size: 13px; }
.library-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.library-workflow { display: flex; flex-wrap: wrap; gap: 8px; color: #6f7f90; font-size: 12px; }
.library-workflow span { padding: 4px 10px; border: 1px solid #e3e9f0; border-radius: 999px; background: #fafcff; }
.question-file-input { display: none; }
.library-stats { display: flex; gap: 12px; color: var(--text-muted); font-size: 12px; }
.library-list { display: grid; gap: 10px; }
.library-item { display: flex; justify-content: space-between; gap: 18px; padding: 17px; border: 1px solid var(--line); border-radius: 6px; background: #fff; }
.library-item-main { min-width: 0; }
.library-item-main strong { display: block; margin-top: 8px; color: var(--text-main); font-size: 15px; }
.library-item-main p { margin: 6px 0 0; color: var(--text-muted); font-size: 13px; line-height: 1.6; }
.library-meta { color: #78869a; font-size: 12px; }
.library-item-actions { display: flex; align-items: center; gap: 4px; flex: 0 0 auto; }
.question-row-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; color: #78869a; font-size: 12px; }
.library-empty { display: flex; min-height: 220px; align-items: center; justify-content: center; flex-direction: column; gap: 10px; border: 1px dashed #cbd6e2; color: #728095; }
.library-empty :deep(svg) { width: 34px; height: 34px; color: #9ab6d0; }
.library-empty-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.library-editor-tip { margin-bottom: 14px; }
.question-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.question-form-grid :deep(.el-select), .question-form-grid :deep(.el-date-editor), .question-form-grid :deep(.el-input-number) { width: 100%; }
.upload-panel { display: grid; gap: 12px; }
.upload-panel p { margin: 0; color: var(--text-muted); font-size: 13px; }
@media (max-width: 760px) {
  .library-header, .library-item { flex-direction: column; align-items: flex-start; }
  .library-item-actions { width: 100%; flex-wrap: wrap; }
  .question-form-grid { grid-template-columns: 1fr; }
}
</style>
