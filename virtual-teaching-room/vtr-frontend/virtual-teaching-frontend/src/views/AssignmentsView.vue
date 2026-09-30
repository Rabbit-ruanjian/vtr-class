<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import * as XLSX from 'xlsx'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, EditPen, Plus, RefreshRight, View } from '@element-plus/icons-vue'
import { assignmentApi, courseApi, learningResourceApi, submissionApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, splitLines } from '@/utils/format'
import { normalizePage } from '@/utils/page'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const answerLoading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref(null)
const activeFilter = ref('ALL')
const list = ref([])
const courseInfo = ref(null)
const courseChapters = ref([])
const courseSections = ref([])
const questionBank = ref([])
const questionChapterFilter = ref('')
const questionSectionFilter = ref('')
const detail = ref(null)
const submissions = ref([])
const submissionDetailVisible = ref(false)
const selectedSubmission = ref(null)
const reviewSaving = ref(false)
const reviewForm = reactive({ score: null, comment: '' })
const mySubmissions = ref([])
const questionFileRef = ref(null)
const answerMap = reactive({})

const query = reactive({ page: 1, size: 100, courseId: Number(route.query.courseId) || undefined })
const editor = reactive({ title: '', description: '', deadline: '', maxSubmitTimes: 1, totalScore: 100, questionIds: [], sourceMode: 'library' })
const newQuestion = reactive({ title: '', stem: '', questionType: 'SINGLE_CHOICE', options: '', referenceAnswer: '', analysis: '', difficulty: 3, knowledgePoint: '', sectionId: null })
const submitForm = reactive({ assignmentId: null, content: '' })

const canManage = computed(() => authStore.isTeacher)
const isStudent = computed(() => authStore.isStudent)
const isAnswerPage = computed(() => isStudent.value && Boolean(route.query.assignmentId))
const displayedAssignments = computed(() => list.value.filter((item) => {
  const completed = isStudent.value ? Boolean(item.mySubmitCount) : Number(item.totalSubmissions || 0) > 0
  if (activeFilter.value === 'COMPLETED') return completed
  if (activeFilter.value === 'INCOMPLETE') return !completed
  return true
}))
const completedAssignmentCount = computed(() => list.value.filter((item) => isStudent.value ? Boolean(item.mySubmitCount) : Number(item.totalSubmissions || 0) > 0).length)
const assignmentProgress = computed(() => list.value.length ? Math.round((completedAssignmentCount.value / list.value.length) * 100) : 0)
const selectedQuestions = computed(() => editor.questionIds.map((id) => questionBank.value.find((question) => String(question.id) === String(id))).filter(Boolean))
function questionChapterName(question) {
  const section = courseSections.value.find((item) => String(item.id) === String(question?.sectionId))
  return question?.chapter || (section ? sectionChapterName(section) : '')
}
const assignmentQuestionBank = computed(() => questionBank.value.filter((question) => (question.status === 'PUBLISHED' || selectedAssignmentQuestionIds.value.has(String(question.id))) && (!questionChapterFilter.value || questionChapterName(question) === questionChapterFilter.value) && (!questionSectionFilter.value || String(question.sectionId) === String(questionSectionFilter.value))))
const questionChapterOptions = computed(() => [...new Set([
  ...courseChapters.value.map((chapter) => chapter.title || chapter.name),
  ...questionBank.value.map((question) => question.chapter)
].filter(Boolean))])
const questionSectionOptions = computed(() => courseSections.value.filter((section) => !questionChapterFilter.value || sectionChapterName(section) === questionChapterFilter.value))
const selectedAssignmentQuestionIds = computed(() => new Set(editor.questionIds.map((id) => String(id))))
const detailQuestions = computed(() => (detail.value?.questionIds || []).map((id) => questionBank.value.find((question) => String(question.id) === String(id))).filter(Boolean))
const sourceSection = computed(() => courseSections.value.find((section) => String(section.id) === String(newQuestion.sectionId)))

function sectionLabel(section) {
  return [sectionChapterName(section), section.title || section.name || '未命名小节'].filter(Boolean).join(' · ')
}

function sectionChapterName(section) {
  const chapter = courseChapters.value.find((item) => String(item.chapterId || item.id) === String(section?.chapterId))
  return section?.chapterTitle || section?.chapterName || section?.chapter || chapter?.title || chapter?.name || ''
}

function resetQuestionFilters() {
  questionSectionFilter.value = ''
}

function isAssignmentQuestionSelected(id) {
  return selectedAssignmentQuestionIds.value.has(String(id))
}

function updateAssignmentQuestion(id, checked) {
  const ids = editor.questionIds.filter((item) => String(item) !== String(id))
  if (checked) ids.push(id)
  editor.questionIds = ids
}

function questionTypeLabel(type) {
  return ({ SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', JUDGMENT: '判断题', FILL: '填空题', TEXT: '简答题', PROGRAMMING: '编程题' })[type] || type
}

function assignmentState(row) {
  if (row.status === 'CLOSED') return '已关闭'
  if (row.isExpired) return '已截止'
  return row.status === 'PUBLISHED' ? '进行中' : '草稿'
}

function canEnterAssignment(row) {
  if (!isStudent.value) return false
  if (row.canView === false) return false
  // 已提交的作业即使截止或关闭，也要允许进入查看答案和批改结果。
  if (Number(row.mySubmitCount || 0) > 0) return ['PUBLISHED', 'CLOSED'].includes(row.status)
  return row.status === 'PUBLISHED' && !row.isExpired
}

function resetAnswers() {
  Object.keys(answerMap).forEach((key) => delete answerMap[key])
  detailQuestions.value.forEach((question) => {
    answerMap[question.id] = question.questionType === 'MULTIPLE_CHOICE' ? [] : ''
  })
}

function hasAnswer(question) {
  const value = answerMap[question.id]
  return Array.isArray(value) ? value.length > 0 : String(value || '').trim().length > 0
}

function answerText(question) {
  const value = answerMap[question.id]
  return Array.isArray(value) ? value.join('、') : String(value || '').trim()
}

function answerPlaceholder(question) {
  if (question.questionType === 'FILL') return '请输入填空答案'
  if (question.questionType === 'PROGRAMMING') return '请粘贴或输入程序代码'
  return '请输入你的答案'
}

function formatSubmissionContent() {
  return detailQuestions.value.map((question, index) => `第 ${index + 1} 题：${answerText(question)}`).join('\n')
}

function resetNewQuestion() {
  Object.assign(newQuestion, { title: '', stem: '', questionType: 'SINGLE_CHOICE', options: '', referenceAnswer: '', analysis: '', difficulty: 3, knowledgePoint: '', sectionId: courseSections.value[0]?.id || null })
}

function resetEditor() {
  editingId.value = null
  Object.assign(editor, { title: '', description: '', deadline: '', maxSubmitTimes: 1, totalScore: 100, questionIds: [], sourceMode: 'library' })
  resetNewQuestion()
  questionChapterFilter.value = ''
  questionSectionFilter.value = ''
}

function normalizedText(value) { return String(value == null ? '' : value).trim() }
function questionCell(row, keys) {
  const found = Object.entries(row || {}).find(([key]) => keys.includes(normalizedText(key).toLowerCase()))
  return normalizedText(found?.[1])
}
function questionType(value) {
  const text = normalizedText(value).toUpperCase()
  return ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'JUDGMENT', 'FILL', 'TEXT', 'PROGRAMMING'].includes(text) ? text : 'SINGLE_CHOICE'
}
async function uploadQuestions(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !newQuestion.sectionId) { ElMessage.warning('请先选择题目所属小节'); return }
  try {
    const buffer = await file.arrayBuffer()
    const workbook = XLSX.read(buffer, { type: 'array' })
    const sheet = workbook.Sheets[workbook.SheetNames[0]]
    const rows = XLSX.utils.sheet_to_json(sheet, { defval: '', blankrows: false })
    const section = sourceSection.value
    const importedIds = []
    for (const row of rows) {
      const title = questionCell(row, ['title', '题目标题', '题目', '名称'])
      const stem = questionCell(row, ['stem', '题干', '题目内容', '内容'])
      const referenceAnswer = questionCell(row, ['referenceanswer', '参考答案', '正确答案', '答案'])
      if (!title && !stem && !referenceAnswer) continue
      const createdId = await learningResourceApi.createQuestion({
        title,
        stem,
        questionType: questionType(questionCell(row, ['questiontype', '题型', '类型'])),
        options: questionCell(row, ['options', '选项', '选项内容']),
        referenceAnswer,
        analysis: questionCell(row, ['analysis', '解析', '答案解析']),
        difficulty: Number(questionCell(row, ['difficulty', '难度'])) || 3,
        knowledgePoint: questionCell(row, ['knowledgepoint', '知识点', '关联知识点']),
        courseId: query.courseId,
        chapter: section?.chapterTitle || section?.chapterName || section?.chapter || '',
        sectionId: newQuestion.sectionId
      })
      importedIds.push(createdId)
    }
    const refreshed = await learningResourceApi.questions({ courseId: query.courseId })
    questionBank.value = Array.isArray(refreshed) ? refreshed : (refreshed?.list || [])
    editor.questionIds = [...new Set([...editor.questionIds, ...importedIds])]
    editor.sourceMode = 'library'
    ElMessage.success(`已上传 ${importedIds.length} 道题目，并加入当前作业`)
  } catch (error) {
    ElMessage.error(error?.message || '题目文件解析失败')
  }
}

async function loadCourseContext() {
  if (!query.courseId) return
  const [course, chapters, sections] = await Promise.all([
    canManage.value ? courseApi.detail(query.courseId) : Promise.resolve(null),
    courseApi.chapters(query.courseId),
    courseApi.sections(query.courseId)
  ])
  courseInfo.value = course
  courseChapters.value = Array.isArray(chapters) ? chapters : (chapters?.list || [])
  const sectionList = Array.isArray(sections) ? sections : (sections?.list || [])
  courseSections.value = sectionList.filter((section) => section.status == null || section.status === 'ACTIVE')

  // 题库只是教师发布作业时的选题来源，题库服务异常不应阻断课程作业列表。
  try {
    const questions = await learningResourceApi.questions({ courseId: query.courseId }, { silent: true })
    questionBank.value = Array.isArray(questions) ? questions : (questions?.list || [])
  } catch (error) {
    questionBank.value = []
    console.warn('题库暂时不可用，作业列表继续加载', error)
  }
}

async function loadList() {
  loading.value = true
  try {
    const payload = isStudent.value ? await assignmentApi.studentVisible(query) : await assignmentApi.teacherManage(query)
    list.value = normalizePage(payload).list
  } finally {
    loading.value = false
  }
}

function openCreate() { resetEditor(); editorVisible.value = true }

function openEdit(row) {
  editingId.value = row.id
  Object.assign(editor, { title: row.title, description: row.description || '', deadline: row.deadline, maxSubmitTimes: row.maxSubmitTimes || 1, totalScore: row.totalScore || 100, questionIds: [...(row.questionIds || [])], sourceMode: 'library' })
  resetNewQuestion()
  editorVisible.value = true
}

async function addDirectQuestion() {
  if (!newQuestion.sectionId || !newQuestion.title.trim() || !newQuestion.stem.trim() || !newQuestion.referenceAnswer.trim()) {
    ElMessage.warning('请填写题目所属小节、标题、题干和参考答案')
    return
  }
  if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(newQuestion.questionType) && !newQuestion.options.trim()) {
    ElMessage.warning('选择题需要填写选项，每行一个选项')
    return
  }
  const section = sourceSection.value
  const id = await learningResourceApi.createQuestion({ ...newQuestion, courseId: query.courseId, chapter: section?.chapterTitle || section?.chapterName || section?.chapter || '' })
  const refreshed = await learningResourceApi.questions({ courseId: query.courseId })
  questionBank.value = Array.isArray(refreshed) ? refreshed : (refreshed?.list || [])
  editor.questionIds = [...new Set([...editor.questionIds, id])]
  resetNewQuestion()
  editor.sourceMode = 'library'
  ElMessage.success('题目已保存到题库，并加入当前作业')
}

async function saveAssignment() {
  if (!editor.title.trim() || !editor.deadline || !editor.questionIds.length) {
    ElMessage.warning('请填写作业标题、截止时间，并至少加入一道题目')
    return
  }
  saving.value = true
  const payload = {
    title: editor.title.trim(),
    description: editor.description.trim() || '请完成本次作业中的全部题目，并在截止时间前提交。',
    deadline: editor.deadline,
    assignmentType: 'TEXT',
    maxSubmitTimes: editor.maxSubmitTimes,
    totalScore: editor.totalScore,
    autoTestRatio: 0,
    manualReviewRatio: 100,
    publishType: 'ALL',
    courseId: query.courseId,
    questionIds: editor.questionIds
  }
  try {
    if (editingId.value) {
      await assignmentApi.update(editingId.value, payload)
      ElMessage.success('作业已更新')
    } else {
      await assignmentApi.create(payload)
      ElMessage.success('作业已发布给课程学生')
    }
    editorVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

async function openDetail(row) {
  detail.value = await assignmentApi.detail(row.id)
  if (isStudent.value) {
    mySubmissions.value = await submissionApi.mySubmissions(row.id)
  } else {
    const payload = await submissionApi.allByAssignment(row.id, { page: 1, size: 100 })
    submissions.value = normalizePage(payload).list
  }
  detailVisible.value = true
}

async function openSubmission(row) {
  try {
    selectedSubmission.value = await submissionApi.detail(row.id)
    reviewForm.score = selectedSubmission.value.manualReviewScore ?? null
    reviewForm.comment = selectedSubmission.value.manualReview || ''
    submissionDetailVisible.value = true
  } catch (error) {
    ElMessage.error(error?.message || '提交详情加载失败')
  }
}

async function saveReview() {
  if (!selectedSubmission.value || reviewForm.score == null || reviewForm.score < 0) {
    ElMessage.warning('请输入简答题得分')
    return
  }
  reviewSaving.value = true
  try {
    await submissionApi.review(selectedSubmission.value.id, { score: Number(reviewForm.score), comment: reviewForm.comment.trim() })
    ElMessage.success('批改结果已保存')
    submissionDetailVisible.value = false
    const payload = await submissionApi.allByAssignment(detail.value.id, { page: 1, size: 100 })
    submissions.value = normalizePage(payload).list
  } catch (error) {
    ElMessage.error(error?.message || '批改保存失败')
  } finally {
    reviewSaving.value = false
  }
}

async function openSubmit(row) {
  await router.push({ name: 'assignment-answer', params: { courseId: query.courseId, assignmentId: row.id } })
}

async function loadAnswerPage(assignmentId) {
  if (!assignmentId || !isStudent.value) return
  answerLoading.value = true
  try {
    detail.value = await assignmentApi.detail(assignmentId)
    submitForm.assignmentId = assignmentId
    mySubmissions.value = await submissionApi.mySubmissions(assignmentId)
    resetAnswers()
  } finally {
    answerLoading.value = false
  }
}

async function backToAssignmentList() {
  const nextQuery = { ...route.query }
  delete nextQuery.assignmentId
  await router.push({ query: nextQuery })
}

async function submitAssignment() {
  const unanswered = detailQuestions.value
    .map((question, index) => hasAnswer(question) ? null : index + 1)
    .filter(Boolean)
  if (unanswered.length) {
    ElMessage.warning(`还有第 ${unanswered.join('、')} 题未作答`)
    return
  }
  submitForm.content = formatSubmissionContent()
  await submissionApi.submit(submitForm.assignmentId, { assignmentId: submitForm.assignmentId, language: 'TEXT', code: submitForm.content, content: submitForm.content })
  ElMessage.success('作业已提交')
  await backToAssignmentList()
}

function handleAssignmentClick(row) {
  if (canEnterAssignment(row)) openSubmit(row)
}

async function closeAssignment(row) {
  await ElMessageBox.confirm(`确认关闭“${row.title}”吗？学生将不能继续提交。`, '关闭作业', { type: 'warning' })
  await assignmentApi.close(row.id)
  ElMessage.success('作业已关闭')
  await loadList()
}

async function reopenAssignment(row) {
  await assignmentApi.reopen(row.id)
  ElMessage.success('作业已重新开放')
  await loadList()
}

async function deleteAssignment(row) {
  try {
    await ElMessageBox.confirm(`确认删除作业“${row.title}”吗？删除后无法恢复。`, '删除作业草稿', { type: 'warning' })
    await assignmentApi.remove(row.id)
    ElMessage.success('作业已删除')
    await loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除作业失败')
  }
}

watch(() => [route.query.courseId, route.query.assignmentId], async ([courseId, assignmentId]) => {
  if (assignmentId && isStudent.value) {
    await router.replace({ name: 'assignment-answer', params: { courseId: Number(courseId), assignmentId: Number(assignmentId) } })
    return
  }
  query.courseId = Number(courseId) || undefined
  await loadCourseContext()
  await loadList()
  if (assignmentId) await loadAnswerPage(Number(assignmentId))
  if (assignmentId && !isStudent.value) {
    const target = list.value.find((item) => String(item.id) === String(assignmentId)) || { id: Number(assignmentId) }
    await openDetail(target)
  }
  else detail.value = null
}, { immediate: true })

onMounted(resetEditor)
</script>

<template>
  <main class="assignment-page">
    <section v-if="isAnswerPage" v-loading="answerLoading" class="assignment-answer-page">
      <header class="answer-page-header">
        <el-button text :icon="ArrowLeft" @click="backToAssignmentList">返回作业列表</el-button>
        <div class="answer-page-heading">
          <h1>{{ detail?.title || '作业' }}</h1>
          <p v-if="detail">{{ detailQuestions.length }} 道题 · {{ detail.totalScore || 100 }} 分 · 截止 {{ formatDateTime(detail.deadline) }}</p>
        </div>
      </header>
      <template v-if="detail">
        <section class="answer-page-intro">
          <div><el-tag>{{ assignmentState(detail) }}</el-tag><span>请按题目顺序完成作答</span></div>
          <p>{{ detail.description }}</p>
        </section>
        <section class="answer-page-questions">
          <article v-for="(question, index) in detailQuestions" :key="question.id" class="answer-page-question">
            <div class="answer-question-label">第 {{ index + 1 }} 题 · {{ questionTypeLabel(question.questionType) }}</div>
            <h2>{{ question.stem }}</h2>
            <div class="answer-area">
              <el-radio-group v-if="['SINGLE_CHOICE', 'JUDGMENT'].includes(question.questionType)" v-model="answerMap[question.id]" class="answer-options">
                <el-radio v-for="option in (question.questionType === 'JUDGMENT' ? ['正确', '错误'] : splitLines(question.options))" :key="option" :label="option" border>{{ option }}</el-radio>
              </el-radio-group>
              <el-checkbox-group v-else-if="question.questionType === 'MULTIPLE_CHOICE'" v-model="answerMap[question.id]" class="answer-options">
                <el-checkbox v-for="option in splitLines(question.options)" :key="option" :label="option" border>{{ option }}</el-checkbox>
              </el-checkbox-group>
              <el-input v-else v-model="answerMap[question.id]" type="textarea" :rows="question.questionType === 'PROGRAMMING' ? 12 : 5" :placeholder="answerPlaceholder(question)" />
            </div>
          </article>
        </section>
        <footer class="answer-page-footer">
          <span>完成全部题目后提交作业，提交后将计入本次作业。</span>
          <el-button type="primary" :disabled="detail.status !== 'PUBLISHED' || detail.isExpired || detail.myRemainingSubmits === 0" @click="submitAssignment">提交作业</el-button>
        </footer>
      </template>
    </section>
    <template v-else>
    <header class="assignment-header">
      <div class="assignment-filter" aria-label="作业筛选">
        <span>筛选</span>
        <el-radio-group v-model="activeFilter">
          <el-radio-button label="ALL">全部</el-radio-button>
          <el-radio-button label="COMPLETED">已完成</el-radio-button>
          <el-radio-button label="INCOMPLETE">未完成</el-radio-button>
        </el-radio-group>
        <div class="assignment-progress" aria-label="作业完成进度">
          <span class="assignment-progress-track"><i :style="{ width: `${assignmentProgress}%` }"></i></span>
          <strong>{{ completedAssignmentCount }}/{{ list.length }}{{ canManage ? ' 有提交' : '' }}</strong>
        </div>
      </div>
      <div class="assignment-header-actions">
        <el-button :icon="RefreshRight" circle aria-label="刷新作业" title="刷新作业" :loading="loading" @click="loadList" />
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">发布作业</el-button>
      </div>
    </header>

    <section v-loading="loading" class="assignment-list">
      <article v-for="row in displayedAssignments" :key="row.id" class="assignment-row" :class="{ 'assignment-row-clickable': canEnterAssignment(row) }" :role="canEnterAssignment(row) ? 'button' : undefined" :tabindex="canEnterAssignment(row) ? 0 : undefined" @click="handleAssignmentClick(row)" @keydown.enter="handleAssignmentClick(row)">
        <div class="assignment-mark" :class="{ 'is-active': !row.isExpired }">作业</div>
        <div class="assignment-main">
          <div class="assignment-title-line">
            <h2>{{ row.title }}</h2>
            <el-tag size="small" :type="assignmentState(row) === '进行中' ? 'success' : 'info'">{{ assignmentState(row) }}</el-tag>
          </div>
          <p>{{ row.questionIds?.length || 0 }} 道题 · {{ row.totalScore || 100 }} 分 · 截止 {{ formatDateTime(row.deadline) }}<span v-if="canManage"> · {{ row.totalSubmissions || 0 }} 人已提交</span></p>
        </div>
        <div class="assignment-actions">
          <template v-if="canManage">
            <el-button :icon="View" circle aria-label="查看作业" title="查看作业" @click.stop="openDetail(row)" />
            <el-button :icon="EditPen" circle aria-label="编辑作业" title="编辑作业" @click="openEdit(row)" />
            <el-button v-if="row.status === 'DRAFT'" :icon="Delete" circle aria-label="删除作业草稿" title="删除作业草稿" @click.stop="deleteAssignment(row)" />
            <el-button v-if="row.status === 'PUBLISHED'" plain type="warning" @click.stop="closeAssignment(row)">关闭</el-button>
            <el-button v-else-if="row.status === 'CLOSED'" plain type="primary" @click.stop="reopenAssignment(row)">重新开放</el-button>
          </template>
        </div>
      </article>
      <div v-if="!loading && !displayedAssignments.length" class="assignment-empty">
        <p>暂无作业</p>
      </div>
    </section>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑作业' : '发布作业'" width="min(980px, 94vw)" destroy-on-close>
      <el-form label-position="top" class="assignment-editor">
        <div class="editor-grid">
          <el-form-item label="作业标题" required><el-input v-model="editor.title" placeholder="例如：简单排序课后练习" /></el-form-item>
          <el-form-item label="截止时间" required><el-date-picker v-model="editor.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%;" /></el-form-item>
          <el-form-item label="总分"><el-input-number v-model="editor.totalScore" :min="1" :max="1000" /></el-form-item>
          <el-form-item label="允许提交次数"><el-input-number v-model="editor.maxSubmitTimes" :min="1" :max="20" /></el-form-item>
        </div>
        <el-form-item label="作业说明"><el-input v-model="editor.description" type="textarea" :rows="3" placeholder="可填写完成要求、提交说明或评分要求。" /></el-form-item>
        <section class="question-source">
          <div class="source-heading"><div><h3>作业题目</h3><span>已选 {{ editor.questionIds.length }} 题</span></div><div class="question-source-filters"><el-select v-model="questionChapterFilter" clearable placeholder="按章节筛选" @change="resetQuestionFilters"><el-option v-for="chapter in questionChapterOptions" :key="chapter" :label="chapter" :value="chapter" /></el-select><el-select v-model="questionSectionFilter" clearable placeholder="按小节筛选"><el-option v-for="section in questionSectionOptions" :key="section.id" :label="sectionLabel(section)" :value="section.id" /></el-select></div></div>
          <el-tabs v-model="editor.sourceMode">
            <el-tab-pane label="从题库选题" name="library">
              <div class="question-picker">
                <el-checkbox v-for="question in assignmentQuestionBank" :key="question.id" :model-value="isAssignmentQuestionSelected(question.id)" class="question-picker-row" @change="(checked) => updateAssignmentQuestion(question.id, checked)">
                  <span class="question-picker-title">{{ question.title }}</span>
                  <span>{{ questionTypeLabel(question.questionType) }}<template v-if="question.knowledgePoint"> · {{ question.knowledgePoint }}</template></span>
                </el-checkbox>
              </div>
              <div v-if="!assignmentQuestionBank.length" class="question-picker-empty">当前筛选条件下暂无可选题目，请调整章节/小节或先录入题目。</div>
            </el-tab-pane>
            <el-tab-pane label="直接录题" name="new">
              <div class="upload-question-line"><span>已有题目文件？选择当前小节后可批量加入。</span><el-button plain @click="questionFileRef?.click()">上传题目文件</el-button><input ref="questionFileRef" type="file" accept=".xlsx,.xls,.csv" hidden @change="uploadQuestions" /></div>
              <div class="editor-grid direct-question-grid">
                <el-form-item label="所属小节" required><el-select v-model="newQuestion.sectionId" placeholder="选择小节"><el-option v-for="section in courseSections" :key="section.id" :label="sectionLabel(section)" :value="section.id" /></el-select></el-form-item>
                <el-form-item label="题型"><el-select v-model="newQuestion.questionType"><el-option label="单选题" value="SINGLE_CHOICE" /><el-option label="多选题" value="MULTIPLE_CHOICE" /><el-option label="判断题" value="JUDGMENT" /><el-option label="填空题" value="FILL" /><el-option label="简答题" value="TEXT" /></el-select></el-form-item>
                <el-form-item label="题目标题" required><el-input v-model="newQuestion.title" /></el-form-item>
                <el-form-item v-if="newQuestion.knowledgePoint" label="知识点"><el-input v-model="newQuestion.knowledgePoint" /></el-form-item>
              </div>
              <el-form-item label="题干" required><el-input v-model="newQuestion.stem" type="textarea" :rows="3" /></el-form-item>
              <el-form-item v-if="['SINGLE_CHOICE', 'MULTIPLE_CHOICE'].includes(newQuestion.questionType)" label="选项（每行一个）" required><el-input v-model="newQuestion.options" type="textarea" :rows="4" placeholder="A. ...&#10;B. ..." /></el-form-item>
              <div class="editor-grid"><el-form-item label="参考答案" required><el-input v-model="newQuestion.referenceAnswer" /></el-form-item><el-form-item label="解析"><el-input v-model="newQuestion.analysis" /></el-form-item></div>
              <el-button type="primary" :icon="Plus" @click="addDirectQuestion">保存到题库并加入作业</el-button>
            </el-tab-pane>
          </el-tabs>
          <div v-if="selectedQuestions.length" class="selected-question-summary">本次作业：{{ selectedQuestions.map((question) => question.title).join('、') }}</div>
        </section>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveAssignment">{{ editingId ? '保存修改' : '发布作业' }}</el-button></template>
    </el-dialog>

    <el-drawer v-if="canManage" v-model="detailVisible" :title="detail?.title || '作业详情'" size="min(780px, 92vw)" class="assignment-drawer">
      <template v-if="detail">
        <section class="detail-meta"><el-tag>{{ assignmentState(detail) }}</el-tag><span>{{ detailQuestions.length }} 道题 · {{ detail.totalScore || 100 }} 分 · 截止 {{ formatDateTime(detail.deadline) }}</span></section>
        <p class="detail-description">{{ detail.description }}</p>
        <article v-for="(question, index) in detailQuestions" :key="question.id" class="assignment-question" :class="{ 'assignment-answer-question': isStudent }">
          <p class="question-number">第 {{ index + 1 }} 题 · {{ questionTypeLabel(question.questionType) }}</p>
          <h3>{{ question.stem }}</h3>
          <ul v-if="question.options"><li v-for="option in splitLines(question.options)" :key="option">{{ option }}</li></ul>
          <div v-if="canManage" class="answer-reference"><strong>参考答案：</strong>{{ question.referenceAnswer || '暂无' }}<p v-if="question.analysis"><strong>解析：</strong>{{ question.analysis }}</p></div>
        </article>
        <section v-if="canManage" class="submission-overview">
          <div class="submission-overview-heading"><div><h3>提交情况</h3><p>{{ detail.totalSubmissions || 0 }} 名学生已提交，共 {{ detail.totalSubmitCount || 0 }} 次提交。</p></div><el-tag v-if="detailQuestions.some((question) => question.questionType === 'TEXT')" type="warning">简答题需批改</el-tag></div>
          <div v-if="submissions.length" class="submission-list">
            <article v-for="submission in submissions" :key="submission.id" class="submission-row">
              <div><strong>{{ submission.studentName || submission.student?.nickname || submission.student?.username || '学生' }}</strong><span>{{ formatDateTime(submission.submittedAt || submission.submitTime) }}</span></div>
              <div class="submission-score"><span>客观题 {{ submission.autoTestScore ?? 0 }} 分</span><strong v-if="submission.manualReviewScore != null">总分 {{ submission.totalScore ?? submission.score ?? 0 }} 分</strong><el-tag v-else type="warning" size="small">待批改</el-tag><el-button type="primary" link @click="openSubmission(submission)">{{ submission.manualReviewScore != null ? '查看批改' : '查看并批改' }}</el-button></div>
            </article>
          </div>
          <p v-else class="submission-empty">还没有学生提交。</p>
        </section>
      </template>
    </el-drawer>

    <el-dialog v-model="submissionDetailVisible" title="查看提交与批改" width="min(860px, 94vw)" destroy-on-close>
      <template v-if="selectedSubmission">
        <section class="submission-detail-meta"><strong>{{ selectedSubmission.studentName || selectedSubmission.student?.nickname || selectedSubmission.student?.username || '学生' }}</strong><span>提交于 {{ formatDateTime(selectedSubmission.submittedAt || selectedSubmission.submitTime) }}</span><span>客观题得分：{{ selectedSubmission.autoTestScore ?? 0 }} 分</span></section>
        <div class="submission-answer-text">{{ selectedSubmission.code || '学生未填写答案' }}</div>
        <el-form label-position="top" class="review-form"><el-form-item label="简答题得分"><el-input-number v-model="reviewForm.score" :min="0" :max="detail?.totalScore || 100" /></el-form-item><el-form-item label="批改评语"><el-input v-model="reviewForm.comment" type="textarea" :rows="4" placeholder="填写批改意见，可选" /></el-form-item></el-form>
      </template>
      <template #footer><el-button @click="submissionDetailVisible = false">取消</el-button><el-button type="primary" :loading="reviewSaving" @click="saveReview">保存批改</el-button></template>
    </el-dialog>
    </template>
  </main>
</template>

<style scoped>
.assignment-page { width: 100%; min-height: 620px; color: #28344a; }
.assignment-answer-page { width: min(980px, 100%); min-height: 720px; margin: 0 auto; padding: 24px 0 96px; }.answer-page-header { display: flex; align-items: flex-start; gap: 24px; padding: 0 0 22px; border-bottom: 1px solid #e6ebf2; }.answer-page-heading { min-width: 0; }.answer-page-heading h1 { margin: 2px 0 8px; color: #243b64; font-size: 25px; line-height: 1.35; }.answer-page-heading p { margin: 0; color: #748198; font-size: 14px; }.answer-page-intro { margin: 22px 0; padding: 18px 20px; border: 1px solid #dce6f5; border-radius: 6px; background: #f7faff; }.answer-page-intro > div { display: flex; align-items: center; gap: 12px; color: #667085; font-size: 14px; }.answer-page-intro p { margin: 12px 0 0; color: #475467; line-height: 1.75; white-space: pre-wrap; }.answer-page-questions { display: grid; gap: 18px; }.answer-page-question { padding: 24px; border: 1px solid #e0e7ef; border-radius: 6px; background: #fff; }.answer-question-label { color: #3b82f6; font-size: 14px; }.answer-page-question h2 { margin: 10px 0 16px; color: #253a62; font-size: 18px; line-height: 1.65; }.answer-page-footer { position: sticky; bottom: 0; display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-top: 22px; padding: 16px 20px; border: 1px solid #dfe7f1; border-radius: 6px; background: rgba(255, 255, 255, .97); box-shadow: 0 -4px 16px rgba(38, 70, 112, .07); color: #667085; font-size: 13px; }
.assignment-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; min-height: 76px; border-bottom: 1px solid #edf0f3; }
.assignment-header-actions { display: flex; align-items: center; gap: 10px; flex: 0 0 auto; }.assignment-filter { display: flex; align-items: center; gap: 18px; color: #9aa3b1; font-size: 14px; }.assignment-filter :deep(.el-radio-button__inner) { padding: 0; border: 0 !important; background: transparent; box-shadow: none !important; color: #28344a; font-size: 16px; }.assignment-filter :deep(.el-radio-button + .el-radio-button .el-radio-button__inner) { border-left: 0 !important; }.assignment-filter :deep(.el-radio-button:first-child .el-radio-button__inner), .assignment-filter :deep(.el-radio-button:last-child .el-radio-button__inner) { border-radius: 0; }.assignment-filter :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { color: #28344a; background: transparent; box-shadow: none; }.assignment-filter :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner::before) { content: ''; display: inline-block; width: 12px; height: 12px; margin-right: 9px; border: 4px solid #4a90f8; border-radius: 50%; vertical-align: -2px; }.assignment-filter :deep(.el-radio-button__inner::before) { content: ''; display: inline-block; width: 18px; height: 18px; margin-right: 9px; border: 1px solid #d7dde7; border-radius: 50%; vertical-align: -4px; }.assignment-progress { display: flex; align-items: center; gap: 16px; margin-left: 20px; }.assignment-progress-track { display: block; width: 180px; height: 18px; overflow: hidden; border-radius: 12px; background: #eaf0f6; }.assignment-progress-track i { display: block; height: 100%; border-radius: inherit; background: #4388f7; transition: width .2s ease; }.assignment-progress strong { color: #465166; font-size: 16px; }.integrity-notice { display: flex; align-items: center; gap: 8px; max-width: 630px; padding: 9px 16px; border-radius: 24px; background: #f2f5f9; color: #657085; font-size: 14px; white-space: nowrap; }.integrity-notice strong { color: #3d6bb1; font-size: 16px; }.integrity-mark { color: #d75151; font-size: 18px; }.assignment-list { min-height: 540px; }
.assignment-row { display: flex; align-items: center; gap: 28px; min-height: 103px; padding: 16px 4px; border-bottom: 1px solid #edf0f3; }.assignment-row-clickable { cursor: pointer; }.assignment-row-clickable:hover { background: #fbfdff; }.assignment-row-clickable:focus-visible { outline: 2px solid #4a90f8; outline-offset: -2px; }.assignment-mark { display: grid; place-items: center; width: 62px; height: 62px; flex: 0 0 62px; border-radius: 6px; background: #c8ced5; color: #fff; font-size: 17px; font-weight: 600; }.assignment-mark.is-active { background: #39aaf4; }.assignment-main { min-width: 0; flex: 1; }.assignment-title-line { display: flex; align-items: center; gap: 10px; }.assignment-title-line h2 { margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 17px; font-weight: 500; letter-spacing: 0; }.assignment-main p { margin: 7px 0 0; color: #9aa3b1; font-size: 14px; }.assignment-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; flex: 0 0 auto; }
.assignment-empty { display: grid; place-items: center; min-height: 460px; color: #a7afbd; font-size: 16px; }.assignment-empty p { margin: 0; }.assignment-editor :deep(.el-form-item) { margin-bottom: 16px; }.editor-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }.question-source { padding: 18px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fbfcfe; }.source-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 6px; }.source-heading h3 { margin: 0; font-size: 16px; }.source-heading span { color: #3b82f6; font-size: 13px; }.question-source-filters { display: flex; gap: 8px; }.question-source-filters :deep(.el-select) { width: 170px; }.question-picker { display: grid; max-height: 310px; overflow: auto; padding: 2px; }.question-picker-row { display: flex; align-items: flex-start; height: auto; min-height: 56px; margin: 0; padding: 10px 4px; border-bottom: 1px solid #ebeff5; white-space: normal; }.question-picker-row :deep(.el-checkbox__label) { display: grid; gap: 5px; min-width: 0; color: #667085; font-size: 13px; }.question-picker-title { color: #253041; font-size: 14px; font-weight: 600; }.question-picker-empty { padding: 34px 0; text-align: center; color: #98a2b3; }.selected-question-summary { margin-top: 14px; padding-top: 12px; border-top: 1px solid #e5e7eb; color: #4b5563; font-size: 13px; }
.detail-meta { display: flex; align-items: center; gap: 12px; color: #667085; font-size: 14px; }.detail-description { margin: 20px 0 24px; color: #475467; line-height: 1.8; white-space: pre-wrap; }.assignment-question { padding: 17px 0; border-top: 1px solid #e5e7eb; }.assignment-question h3 { margin: 5px 0 12px; font-size: 16px; line-height: 1.65; font-weight: 600; }.question-number { margin: 0; color: #3b82f6; font-size: 13px; }.assignment-question ul { margin: 0; padding-left: 20px; color: #4b5563; line-height: 1.85; }.answer-reference { margin-top: 12px; padding: 10px 12px; border-radius: 5px; background: #f3f8ff; color: #355d98; line-height: 1.75; white-space: pre-wrap; }.answer-reference p { margin: 7px 0 0; color: #617087; }.answer-area { margin-top: 14px; padding: 14px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fbfcfe; }.answer-options { display: grid; gap: 10px; }.answer-options :deep(.el-radio.is-bordered), .answer-options :deep(.el-checkbox.is-bordered) { width: 100%; height: auto; min-height: 40px; margin: 0; padding: 9px 12px; white-space: normal; }.answer-options :deep(.el-radio__label), .answer-options :deep(.el-checkbox__label) { line-height: 1.5; white-space: normal; }.assignment-drawer-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; color: #667085; font-size: 13px; }.submission-overview { margin-top: 26px; padding: 16px; background: #f7f9fc; border-radius: 6px; }.submission-overview h3, .submission-overview p { margin: 0; }.submission-overview p { margin-top: 8px; color: #667085; font-size: 14px; }.submission-overview-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.submission-list { display: grid; gap: 8px; margin-top: 14px; }.submission-row { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 12px; border: 1px solid #e5eaf1; border-radius: 6px; background: #fff; }.submission-row > div:first-child { display: grid; gap: 4px; min-width: 0; }.submission-row span { color: #8a96a7; font-size: 12px; }.submission-score { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; color: #657085; font-size: 13px; }.submission-score strong { color: #238353; }.submission-empty { color: #8a96a7; }.submission-detail-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 14px; padding-bottom: 14px; border-bottom: 1px solid #e5eaf1; color: #667085; font-size: 13px; }.submission-detail-meta strong { color: #28344a; font-size: 16px; }.submission-answer-text { min-height: 180px; margin: 16px 0; padding: 16px; border: 1px solid #e0e7ef; border-radius: 6px; background: #fbfcfe; color: #344054; line-height: 1.75; white-space: pre-wrap; }.review-form { margin-top: 10px; }
.upload-question-line { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin: 3px 0 16px; padding: 11px 13px; color: #667085; background: #f2f6fb; border-radius: 5px; font-size: 13px; }
@media (max-width: 900px) { .assignment-answer-page { padding-top: 16px; }.assignment-header { align-items: flex-start; flex-direction: column; padding: 14px 0; }.assignment-header-actions { width: 100%; justify-content: flex-end; }.integrity-notice { max-width: 100%; overflow: hidden; }.assignment-filter { flex-wrap: wrap; gap: 10px; }.assignment-progress { margin-left: 0; } }
@media (max-width: 720px) { .assignment-answer-page { padding: 12px 0 82px; }.answer-page-header { gap: 12px; flex-direction: column; }.answer-page-heading h1 { font-size: 21px; }.answer-page-question { padding: 18px 15px; }.answer-page-question h2 { font-size: 16px; }.answer-page-footer { align-items: stretch; flex-direction: column; gap: 12px; padding: 14px; }.answer-page-footer .el-button { width: 100%; }.assignment-header-actions { align-items: flex-end; flex-wrap: wrap; }.integrity-notice { order: 3; width: 100%; white-space: normal; }.assignment-row { align-items: flex-start; gap: 12px; }.assignment-mark { width: 48px; height: 48px; flex-basis: 48px; font-size: 14px; }.assignment-actions { flex-wrap: wrap; }.editor-grid { grid-template-columns: 1fr; }.assignment-title-line { align-items: flex-start; flex-direction: column; gap: 5px; }.assignment-title-line h2 { white-space: normal; }.assignment-drawer-footer { align-items: stretch; flex-direction: column; gap: 10px; }.assignment-drawer-footer .el-button { width: 100%; } }
</style>
