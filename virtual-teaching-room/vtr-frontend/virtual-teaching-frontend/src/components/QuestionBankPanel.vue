<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheckFilled, CircleCloseFilled, DocumentChecked, EditPen, Plus } from '@element-plus/icons-vue'
import { chapterQuizApi, courseApi, learningResourceApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import AiAssistantButton from '@/components/AiAssistantButton.vue'
import AiAssistantDrawer from '@/components/AiAssistantDrawer.vue'

const props = defineProps({ courseId: { type: [Number, String], required: true }, chapter: { type: String, required: true }, sectionId: { type: [Number, String], default: null }, questionId: { type: [Number, String], default: null } })
const router = useRouter()
const authStore = useAuthStore()
const tab = ref('quiz')
const loading = ref(false)
const questions = ref([])
const statistics = ref(null)
const quizzes = ref([])
const quizEditorVisible = ref(false)
const questionEditorVisible = ref(false)
const questionEditingId = ref(null)
const selectedQuiz = ref(null)
const quizResult = ref(null)
const quizAnswers = reactive({})
const selectedQuestionIds = ref([])
const scoreMap = reactive({})
const remainingSeconds = ref(0)
const aiVisible = ref(false)
const aiContext = reactive({ title: '', meta: '', questionType: '', questionId: null, type: 'question', excerpt: '' })
const courseSections = ref([])
const courseChapters = ref([])
const selectedSectionId = ref(props.sectionId)
let quizTimer = null

const isStudent = computed(() => authStore.isStudent)
const isAdmin = computed(() => authStore.isAdmin)
const isTeacher = computed(() => authStore.role === 'TEACHER')
const canManageQuestions = computed(() => isTeacher.value || isAdmin.value)
const isTeacherPreview = computed(() => isTeacher.value && Boolean(selectedQuiz.value) && !selectedQuiz.value.attempt)
const quizForm = reactive({ title: '', description: '', totalScore: 100, durationMinutes: 30, attemptLimit: 1, startAt: '', shuffleQuestions: false, shuffleOptions: false, showAnswerAfterSubmit: true, editingId: null })
const questionForm = reactive({ title: '', stem: '', questionType: 'SINGLE_CHOICE', options: '', referenceAnswer: '', analysis: '', difficulty: 3, knowledgePoint: '' })
const typeOptions = [{ value: 'SINGLE_CHOICE', label: '单选题' }, { value: 'MULTIPLE_CHOICE', label: '多选题' }, { value: 'JUDGMENT', label: '判断题' }, { value: 'FILL', label: '填空题' }, { value: 'TEXT', label: '简答题' }, { value: 'PROGRAMMING', label: '编程题' }]
const questionGroupLabels = { SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', JUDGMENT: '判断题', FILL: '填空题', TEXT: '简答题', PROGRAMMING: '编程题' }
const selectedSection = computed(() => courseSections.value.find((item) => String(item.id) === String(selectedSectionId.value)) || null)
const selectedQuestionIdSet = computed(() => new Set(selectedQuestionIds.value.map((id) => String(id))))

function labelOfType(value) { return typeOptions.find((item) => item.value === value)?.label || '练习题' }
function statusInfo(value) { return { DRAFT: ['草稿', 'info'], PUBLISHED: ['已发布', 'success'], ARCHIVED: ['已归档', 'warning'], PENDING: ['待审核', 'warning'], REJECTED: ['已驳回', 'danger'] }[value] || [value, 'info'] }
function isChoiceType(question) { return question.questionType === 'SINGLE_CHOICE' || question.questionType === 'MULTIPLE_CHOICE' }
function optionsOf(question) {
  const raw = question.questionType === 'JUDGMENT' && !question.options ? '正确\n错误' : String(question.options || '')
  return raw.split(/\n|\r|\|/).map((item) => item.trim()).filter(Boolean).map((item, index) => {
    const match = item.match(/^([A-Za-z])[.、)）:]?\s*(.*)$/)
    return match ? { value: match[1].toUpperCase(), label: item } : { value: item, label: isChoiceType(question) ? `${String.fromCharCode(65 + index)}. ${item}` : item }
  })
}
function answerValues(value) { return Array.isArray(value) ? value : String(value || '').split(/[,，、;；|]/).map((item) => item.trim()).filter(Boolean) }
function answerLabel(question, value) {
  if (!value && value !== 0) return '未作答'
  const values = answerValues(value)
  if (!isChoiceType(question) && question.questionType !== 'JUDGMENT') return String(value)
  return values.map((token) => {
    const normalized = String(token).trim().match(/^([A-Za-z])/)?.[1]?.toUpperCase() || String(token).trim()
    return optionsOf(question).find((option) => option.value === normalized)?.label || String(token).trim()
  }).join('、')
}
function feedbackFor(item) {
  const result = questionResult(item)
  if (!result) return null
  return { ...result, answerLabel: answerLabel(item, result.answer), referenceLabel: answerLabel(item, result.referenceAnswer) }
}
function payloadBase() { return { courseId: Number(props.courseId), chapter: props.chapter, sectionId: Number(props.sectionId) } }
function isAnswered(item) { const value = quizAnswers[item.id]; return Array.isArray(value) ? value.length > 0 : String(value || '').trim().length > 0 }
function questionResult(item) { return quizResult.value?.answers?.find((answer) => String(answer.questionId) === String(item.id)) }
function formatSeconds(value) { const seconds = Math.max(0, Number(value) || 0); return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}` }
const groupedQuestions = computed(() => {
  const groups = []
  ;(selectedQuiz.value?.questions || []).forEach((question, index) => {
    const key = question.questionType || 'OTHER'
    let group = groups.find((item) => item.key === key)
    if (!group) { group = { key, label: questionGroupLabels[key] || '其他题型', questions: [] }; groups.push(group) }
    group.questions.push({ ...question, displayIndex: index + 1 })
  })
  return groups
})

async function loadAll() {
  if (!props.courseId || !props.sectionId) {
    stopQuizTimer()
    selectedQuiz.value = null
    quizResult.value = null
    quizzes.value = []
    questions.value = []
    statistics.value = null
    return
  }
  selectedQuiz.value = null
  quizResult.value = null
  loading.value = true
  try {
    const [chapterPayload, sectionPayload] = await Promise.all([courseApi.chapters(props.courseId), courseApi.sections(props.courseId)])
    courseChapters.value = Array.isArray(chapterPayload) ? chapterPayload : (chapterPayload?.list || [])
    courseSections.value = Array.isArray(sectionPayload) ? sectionPayload : (sectionPayload?.list || [])
    quizzes.value = await chapterQuizApi.list({ courseId: Number(props.courseId), sectionId: Number(props.sectionId) }) || []
    if (canManageQuestions.value) { questions.value = await learningResourceApi.questions(payloadBase()) || []; statistics.value = await learningResourceApi.statistics(payloadBase()) }
    if (isStudent.value && quizzes.value.length && !selectedQuiz.value) await openQuiz(quizzes.value[0])
  } catch (error) { ElMessage.error(error?.message || '小节测验加载失败') } finally { loading.value = false }
}

function sectionChapterName(section) {
  const chapter = courseChapters.value.find((item) => String(item.chapterId || item.id) === String(section?.chapterId))
  return section?.chapterTitle || section?.chapterName || section?.chapter || chapter?.title || chapter?.name || String(section?.chapterId || '')
}
function sectionLabel(section) { return [sectionChapterName(section), section.title || section.name || '未命名小节'].filter(Boolean).join(' · ') }
async function switchSection(sectionId) {
  const section = courseSections.value.find((item) => String(item.id) === String(sectionId))
  if (!section) return
  await router.push({ path: '/courseware/assignments', query: { courseId: props.courseId, chapter: sectionChapterName(section), sectionId: section.id } })
}

function resetQuizForm() { Object.assign(quizForm, { title: '', description: '', totalScore: 100, durationMinutes: 30, attemptLimit: 1, startAt: '', shuffleQuestions: false, shuffleOptions: false, showAnswerAfterSubmit: true, editingId: null }); selectedQuestionIds.value = []; Object.keys(scoreMap).forEach((key) => delete scoreMap[key]) }
async function openQuizEditor(item = null) {
  resetQuizForm()
  if (item) {
    try { const detail = await chapterQuizApi.detail(item.id); Object.assign(quizForm, { ...detail, editingId: detail.id, startAt: detail.startAt ? String(detail.startAt).slice(0, 19) : '' }); selectedQuestionIds.value = (detail.questions || []).map((question) => question.id); (detail.questions || []).forEach((question) => { scoreMap[question.id] = question.score || 10 }) } catch (error) { ElMessage.error(error?.message || '测验详情加载失败'); return }
  }
  quizEditorVisible.value = true
}
function isQuestionSelected(questionId) { return selectedQuestionIdSet.value.has(String(questionId)) }
function updateQuestionSelection(questionId, checked) {
  const current = selectedQuestionIds.value.filter((id) => String(id) !== String(questionId))
  if (checked) {
    current.push(questionId)
    if (scoreMap[questionId] === undefined) scoreMap[questionId] = 10
  }
  selectedQuestionIds.value = current
}
function scrollToQuestion(index) {
  document.getElementById(`quiz-question-${index}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
async function saveQuiz() {
  if (!quizForm.title.trim()) return ElMessage.warning('请填写测验名称')
  if (!selectedQuestionIds.value.length) return ElMessage.warning('请至少选择一道题目组卷')
  const payload = { ...payloadBase(), title: quizForm.title, description: quizForm.description, totalScore: Number(quizForm.totalScore), durationMinutes: Number(quizForm.durationMinutes), attemptLimit: Number(quizForm.attemptLimit), startAt: quizForm.startAt || null, shuffleQuestions: quizForm.shuffleQuestions, shuffleOptions: quizForm.shuffleOptions, showAnswerAfterSubmit: quizForm.showAnswerAfterSubmit, questions: selectedQuestionIds.value.map((questionId) => ({ questionId, score: Number(scoreMap[questionId] || 10) })) }
  try { if (quizForm.editingId) await chapterQuizApi.update(quizForm.editingId, payload); else await chapterQuizApi.create(payload); ElMessage.success(quizForm.editingId ? '测验已保存' : '测验草稿已创建'); quizEditorVisible.value = false; await loadAll() } catch (error) { ElMessage.error(error?.message || '测验保存失败') }
}
async function publishQuiz(item) { try { await ElMessageBox.confirm('发布后学生可以在开放时间内进入测验。', '确认发布测验', { type: 'warning' }); await chapterQuizApi.publish(item.id); ElMessage.success('测验已发布'); await loadAll() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '发布失败') } }
async function archiveQuiz(item) { try { await ElMessageBox.confirm('归档后学生端将不再展示该测验。', '确认归档测验', { type: 'warning' }); await chapterQuizApi.archive(item.id); ElMessage.success('测验已归档'); await loadAll() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '归档失败') } }
async function removeQuiz(item) { try { await ElMessageBox.confirm('删除后无法恢复该测验草稿，确认删除吗？', '删除测验草稿', { type: 'warning' }); await chapterQuizApi.remove(item.id); ElMessage.success('测验草稿已删除'); await loadAll() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除失败') } }

function stopQuizTimer() { if (quizTimer) { window.clearInterval(quizTimer); quizTimer = null } }
function startQuizTimer() { stopQuizTimer(); const tick = () => { remainingSeconds.value = Math.max(0, Math.floor((new Date(selectedQuiz.value?.attempt?.expiresAt).getTime() - Date.now()) / 1000)); if (!remainingSeconds.value) stopQuizTimer() }; tick(); quizTimer = window.setInterval(tick, 1000) }
function initialAnswer(question) {
  if (!question.answer) return question.questionType === 'MULTIPLE_CHOICE' ? [] : ''
  if (!isChoiceType(question)) return question.answer
  const values = answerValues(question.answer).map((token) => {
    const letter = String(token).match(/^([A-Za-z])/)?.[1]?.toUpperCase()
    return optionsOf(question).find((option) => option.value === letter || option.label === String(token).trim())?.value || String(token).trim()
  })
  return question.questionType === 'MULTIPLE_CHOICE' ? values : values[0] || ''
}
function initQuizAnswers(detail) { Object.keys(quizAnswers).forEach((key) => delete quizAnswers[key]); (detail.questions || []).forEach((question) => { quizAnswers[question.id] = initialAnswer(question) }) }
async function startQuiz(item) { try { selectedQuiz.value = await chapterQuizApi.start(item.id); quizResult.value = null; initQuizAnswers(selectedQuiz.value); startQuizTimer() } catch (error) { ElMessage.error(error?.message || '无法开始测验') } }
async function openQuiz(item) {
  if (item.completed) {
    const attempts = await chapterQuizApi.myAttempts(item.id)
    const completedAttempt = (attempts || []).find((attempt) => ['SUBMITTED', 'REVIEWED', 'PENDING_REVIEW'].includes(attempt.status))
    if (completedAttempt) {
      const detail = await chapterQuizApi.attemptDetail(item.id, completedAttempt.id)
      selectedQuiz.value = detail
      quizResult.value = detail
      initQuizAnswers(detail)
      return
    }
  }
  await startQuiz(item)
}
async function previewQuiz(item) {
  try {
    selectedQuiz.value = await chapterQuizApi.detail(item.id)
    quizResult.value = null
    initQuizAnswers(selectedQuiz.value)
    stopQuizTimer()
  } catch (error) {
    ElMessage.error(error?.message || '测验预览失败')
  }
}
async function saveQuizAnswers(showMessage = true) { if (!selectedQuiz.value?.attempt?.id) return; const answers = (selectedQuiz.value.questions || []).map((question) => ({ questionId: question.id, answer: quizAnswers[question.id] })).filter((item) => Array.isArray(item.answer) ? item.answer.length : String(item.answer || '').trim()); try { await chapterQuizApi.saveAnswers(selectedQuiz.value.attempt.id, { answers }); if (showMessage) ElMessage.success('答案已暂存') } catch (error) { ElMessage.error(error?.message || '答案暂存失败') } }
async function submitQuiz() { try { await ElMessageBox.confirm('提交后将不能修改本次测验答案，确认提交吗？', '统一提交', { type: 'warning' }); const answers = (selectedQuiz.value.questions || []).map((question) => ({ questionId: question.id, answer: quizAnswers[question.id] })).filter((item) => Array.isArray(item.answer) ? item.answer.length : String(item.answer || '').trim()); quizResult.value = await chapterQuizApi.submit(selectedQuiz.value.attempt.id, { answers }); selectedQuiz.value.attempt = { ...selectedQuiz.value.attempt, ...quizResult.value }; const quizCard = quizzes.value.find((item) => String(item.id) === String(selectedQuiz.value.id)); if (quizCard) Object.assign(quizCard, { completed: true, attemptStatus: quizResult.value.status, lastScore: quizResult.value.score, lastAttemptId: quizResult.value.id }); stopQuizTimer(); ElMessage.success(quizResult.value.pendingReview ? '已提交，简答题等待教师批改' : '测验已提交') } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '提交失败') } }
function closeQuiz() { stopQuizTimer(); selectedQuiz.value = null; quizResult.value = null }

function resetQuestion() { questionEditingId.value = null; Object.assign(questionForm, { title: '', stem: '', questionType: 'SINGLE_CHOICE', options: '', referenceAnswer: '', analysis: '', difficulty: 3, knowledgePoint: '' }) }
function openQuestion(item) { if (item) { questionEditingId.value = item.id; Object.assign(questionForm, { ...item, analysis: item.analysis || (item.questionType === 'TEXT' ? item.referenceAnswer : '') }) } else resetQuestion(); questionEditorVisible.value = true }
async function saveQuestion() { try { const payload = { ...payloadBase(), ...questionForm }; if (questionEditingId.value) await learningResourceApi.updateQuestion(questionEditingId.value, payload); else await learningResourceApi.createQuestion(payload); ElMessage.success('题目已保存并在课程内发布'); questionEditorVisible.value = false; await loadAll() } catch (error) { ElMessage.error(error?.message || '题目保存失败') } }
async function removeQuestion(item) { try { await ElMessageBox.confirm('删除后题目会从题库隐藏，历史作业和测验记录仍会保留。', '确认删除题目', { type: 'warning' }); await learningResourceApi.removeQuestion(item.id); ElMessage.success('已删除'); await loadAll() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除失败') } }
async function reviewQuestion(item, action) { try { const { value } = await ElMessageBox.prompt(action === 'APPROVE' ? '可填写审核备注。' : '请填写驳回原因。', action === 'APPROVE' ? '审核通过' : '驳回资源', { inputType: 'textarea', inputValidator: (text) => action === 'REJECT' && !String(text || '').trim() ? '驳回原因不能为空' : true }); await learningResourceApi.review('questions', item.id, { action, remark: value || '' }); ElMessage.success('审核结果已提交'); await loadAll() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '审核失败') } }
async function archiveQuestion(item) { await learningResourceApi.archive('questions', item.id); ElMessage.success('已归档'); await loadAll() }
function canOwn(item) { return isTeacher.value && Number(item.teacherId) === Number(authStore.user?.id) }
function openAiAssistant(item) {
  Object.assign(aiContext, {
    title: item.title || '当前题目',
    meta: `${labelOfType(item.questionType)}${item.score ? ` · ${item.score} 分` : ''}`,
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
watch(() => props.sectionId, (value) => { selectedSectionId.value = value })
watch(() => [props.courseId, props.chapter, props.sectionId], loadAll, { immediate: true })
onBeforeUnmount(stopQuizTimer)
</script>

<template>
  <section class="question-bank-panel" :class="{ 'student-quiz-panel': isStudent }" v-loading="loading">
    <div v-if="!selectedQuiz && isTeacher" class="quiz-toolbar"><div><strong>小节测验</strong><span>每个小节独立组卷，客观题自动评分，简答题由教师批改。</span></div><div class="quiz-toolbar-actions"><el-select v-model="selectedSectionId" placeholder="选择章节/小节" style="width: 220px" @change="switchSection"><el-option v-for="section in courseSections" :key="section.id" :label="sectionLabel(section)" :value="section.id" /></el-select><el-button type="primary" @click="openQuizEditor()"><Plus />新建测验</el-button></div></div>
    <div v-if="!selectedQuiz && !props.sectionId && !loading" class="question-bank-empty"><DocumentChecked /><span>请先进入具体小节查看测验</span></div>
    <div v-else-if="!selectedQuiz && isStudent && !loading && !quizzes.length" class="question-bank-empty"><DocumentChecked /><span>本小节暂无已发布测验</span></div>
    <div v-if="selectedQuiz" class="quiz-run-shell"><header class="quiz-run-header"><div><el-button v-if="isTeacher" link @click="closeQuiz">返回测验列表</el-button><h3>小节测验 · {{ selectedQuiz.title }}</h3><span>{{ selectedQuiz.description || '完成全部题目后统一提交' }} · {{ selectedQuiz.totalScore }} 分</span></div><div v-if="!quizResult && selectedQuiz.attempt?.expiresAt" class="quiz-clock" :class="{ urgent: remainingSeconds < 300 }"><span>答题剩余时间</span><strong>{{ formatSeconds(remainingSeconds) }}</strong></div><div v-else-if="quizResult" class="quiz-total-result"><strong>{{ quizResult.score ?? selectedQuiz.attempt?.score ?? 0 }}</strong><span>/ {{ selectedQuiz.totalScore }} 分</span></div><div v-else-if="isTeacherPreview" class="quiz-total-result teacher-preview-label"><strong>预览</strong><span>仅查看发布内容</span></div></header><div class="quiz-run-layout"><main class="quiz-sheet"><section v-for="group in groupedQuestions" :key="group.key" class="quiz-question-group"><h4 class="quiz-group-title">{{ group.label }}（共{{ group.questions.length }}题）</h4><article v-for="item in group.questions" :id="`quiz-question-${item.displayIndex}`" :key="item.id" class="quiz-question" :class="{ answered: isAnswered(item) }"><div class="quiz-question-header"><span class="quiz-number">{{ item.displayIndex }}</span><div><div class="question-row-tags"><el-tag size="small" effect="plain">{{ labelOfType(item.questionType) }}</el-tag><span>{{ item.score }} 分</span></div><strong v-if="item.title && item.title !== item.stem">{{ item.title }}</strong></div><AiAssistantButton @click="openAiAssistant(item)" /></div><p class="quiz-stem">{{ item.stem }}</p><div class="quiz-answer-area"><el-checkbox-group v-if="item.questionType === 'MULTIPLE_CHOICE'" v-model="quizAnswers[item.id]" :disabled="Boolean(quizResult) || isTeacherPreview" class="practice-options"><el-checkbox v-for="option in optionsOf(item)" :key="option.value" :label="option.value">{{ option.label }}</el-checkbox></el-checkbox-group><el-radio-group v-else-if="item.questionType.includes('CHOICE') || item.questionType === 'JUDGMENT'" v-model="quizAnswers[item.id]" :disabled="Boolean(quizResult) || isTeacherPreview" class="practice-options"><el-radio v-for="option in optionsOf(item)" :key="option.value" :label="option.value">{{ option.label }}</el-radio></el-radio-group><el-input v-else v-model="quizAnswers[item.id]" :disabled="Boolean(quizResult) || isTeacherPreview" type="textarea" :rows="4" placeholder="请输入答案" /><div v-if="questionResult(item)" class="answer-result" :class="{ correct: feedbackFor(item).correct === true }"><div class="answer-result-head"><CircleCheckFilled v-if="feedbackFor(item).correct === true" class="answer-icon correct-icon" /><CircleCloseFilled v-else-if="feedbackFor(item).correct === false" class="answer-icon wrong-icon" /><strong>{{ feedbackFor(item).reviewStatus === 'PENDING' ? '等待教师批改' : feedbackFor(item).correct ? '回答正确' : '回答错误' }}</strong><span v-if="feedbackFor(item).score !== null">{{ feedbackFor(item).score }} 分</span></div><div class="answer-detail"><span>我的答案：{{ feedbackFor(item).answerLabel }}</span><span v-if="feedbackFor(item).reviewStatus !== 'PENDING'">正确答案：{{ feedbackFor(item).referenceLabel }}</span></div><p v-if="feedbackFor(item).analysis">{{ feedbackFor(item).analysis }}</p></div><div v-else-if="isTeacherPreview && (item.referenceAnswer || item.analysis)" class="teacher-preview-answer"><strong>参考答案：{{ answerLabel(item, item.referenceAnswer) }}</strong><p v-if="item.analysis">{{ item.analysis }}</p></div></div></article></section><div v-if="!selectedQuiz.questions?.length" class="question-bank-empty"><DocumentChecked /><span>该测验暂未配置题目</span></div></main><aside v-if="selectedQuiz.questions?.length" class="quiz-nav" aria-label="题号导航"><strong>题号导航</strong><div class="quiz-nav-grid"><button v-for="item in selectedQuiz.questions" :key="item.id" type="button" :class="{ active: isAnswered(item) }" @click="scrollToQuestion(item.displayIndex)">{{ item.displayIndex }}</button></div><div class="quiz-nav-legend"><span><i class="dot done"></i>已作答</span><span><i class="dot"></i>未作答</span></div></aside><footer v-if="selectedQuiz.attempt && !isTeacherPreview" class="quiz-actions"><el-button class="save-button" :disabled="Boolean(quizResult)" @click="saveQuizAnswers()">暂存答案</el-button><el-button type="primary" class="submit-button" :disabled="Boolean(quizResult)" @click="submitQuiz">统一提交</el-button></footer></div></div>
    <template v-else><el-tabs v-model="tab"><el-tab-pane label="小节测验" name="quiz"><div v-if="quizzes.length" class="quiz-list"><article v-for="item in quizzes" :key="item.id" class="quiz-card"><div class="quiz-card-main"><div class="question-row-tags"><el-tag size="small" :type="statusInfo(item.status)[1]">{{ statusInfo(item.status)[0] }}</el-tag><el-tag v-if="isStudent && item.completed" size="small" type="success">已完成</el-tag><span>{{ item.questionCount }} 题</span><span>{{ item.totalScore }} 分</span><span>{{ item.durationMinutes }} 分钟</span><span>最多 {{ item.attemptLimit }} 次</span></div><h4>{{ item.title }}</h4><p>{{ item.description || '暂无测验说明' }}</p></div><div class="quiz-card-actions"><el-button v-if="isStudent && !item.completed" type="primary" @click="startQuiz(item)">开始测验</el-button><el-button v-if="isTeacher" link type="primary" @click="previewQuiz(item)">查看</el-button><div v-else-if="isStudent && item.completed" class="quiz-completed-info"><span>成绩 {{ item.lastScore ?? 0 }} / {{ item.totalScore }}</span></div><template v-if="isTeacher"><el-button v-if="item.status === 'DRAFT'" link @click="openQuizEditor(item)">编辑</el-button><el-button v-if="item.status === 'DRAFT'" link type="success" @click="publishQuiz(item)">发布</el-button><el-button v-if="item.status === 'DRAFT'" link type="danger" @click="removeQuiz(item)">删除</el-button><el-button v-if="item.status === 'PUBLISHED'" link type="warning" @click="archiveQuiz(item)">归档</el-button></template></div></article></div><div v-else class="question-bank-empty"><DocumentChecked /><span>{{ isStudent ? '本小节暂无已发布测验' : '本小节还没有小节测验' }}</span><el-button v-if="isTeacher" type="primary" plain @click="openQuizEditor()">创建第一份测验</el-button></div></el-tab-pane></el-tabs></template>
    <el-dialog v-model="quizEditorVisible" :title="quizForm.editingId ? '编辑小节测验' : '新建小节测验'" width="min(900px, 94vw)"><el-form label-position="top"><div class="question-form-grid"><el-form-item label="测验名称" required><el-input v-model="quizForm.title" placeholder="例如：1.1 基本概念测验" /></el-form-item><el-form-item label="组卷小节"><el-input :model-value="selectedSection ? sectionLabel(selectedSection) : props.chapter" disabled /></el-form-item><el-form-item label="总分"><el-input-number v-model="quizForm.totalScore" :min="1" :max="1000" /></el-form-item><el-form-item label="答题时长（分钟）"><el-input-number v-model="quizForm.durationMinutes" :min="1" :max="600" /></el-form-item><el-form-item label="最多提交次数"><el-input-number v-model="quizForm.attemptLimit" :min="1" :max="20" /></el-form-item><el-form-item label="开始时间"><el-date-picker v-model="quizForm.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="立即开放" /></el-form-item></div><el-form-item label="测验说明"><el-input v-model="quizForm.description" type="textarea" :rows="2" /></el-form-item><div class="quiz-settings"><el-checkbox v-model="quizForm.shuffleQuestions">题目乱序</el-checkbox><el-checkbox v-model="quizForm.shuffleOptions">选项乱序</el-checkbox><el-checkbox v-model="quizForm.showAnswerAfterSubmit">提交后显示客观题解析</el-checkbox></div><el-divider content-position="left">从当前小节题库选题</el-divider><div v-if="questions.length" class="builder-list"><div v-for="question in questions.filter((item) => item.status === 'PUBLISHED')" :key="question.id" class="builder-row"><el-checkbox :model-value="isQuestionSelected(question.id)" @change="(checked) => updateQuestionSelection(question.id, checked)">{{ question.title }}</el-checkbox><el-tag size="small" effect="plain">{{ labelOfType(question.questionType) }}</el-tag><el-input-number v-if="isQuestionSelected(question.id)" v-model="scoreMap[question.id]" :min="1" :max="100" size="small" controls-position="right" /><span v-else class="muted">未选</span></div></div><div v-else class="empty-inline">当前小节没有已发布题目，请先在“题库”创建并通过审核。</div></el-form><template #footer><el-button @click="quizEditorVisible = false">取消</el-button><el-button type="primary" @click="saveQuiz">保存测验草稿</el-button></template></el-dialog>
    <el-dialog v-model="questionEditorVisible" :title="questionEditingId ? '编辑题目' : '新建题目'" width="min(760px, 94vw)"><el-form label-position="top"><div class="question-form-grid"><el-form-item label="题目标题" required><el-input v-model="questionForm.title" /></el-form-item><el-form-item label="题型"><el-select v-model="questionForm.questionType"><el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="难度"><el-rate v-model="questionForm.difficulty" :max="5" /></el-form-item><el-form-item v-if="questionForm.knowledgePoint" label="关联知识点"><el-input v-model="questionForm.knowledgePoint" /></el-form-item></div><el-form-item label="题干" required><el-input v-model="questionForm.stem" type="textarea" :rows="4" /></el-form-item><el-form-item v-if="questionForm.questionType.includes('CHOICE')" label="选项（每行一个）"><el-input v-model="questionForm.options" type="textarea" :rows="5" /></el-form-item><el-form-item label="参考答案" required><el-input v-model="questionForm.referenceAnswer" /></el-form-item><el-form-item label="答案解析"><el-input v-model="questionForm.analysis" type="textarea" :rows="4" /></el-form-item></el-form><template #footer><el-button @click="questionEditorVisible = false">取消</el-button><el-button type="primary" @click="saveQuestion">保存并发布</el-button></template></el-dialog>
    <AiAssistantDrawer v-model="aiVisible" :course-id="props.courseId" :chapter="props.chapter" :context-title="aiContext.title" :context-meta="aiContext.meta" :question-type="aiContext.questionType" :question-id="aiContext.questionId" :context-type="aiContext.type" :context-excerpt="aiContext.excerpt" />
  </section>
</template>

<style scoped>
.question-bank-panel { min-height: 420px; }.quiz-toolbar, .quiz-run-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }.quiz-toolbar strong { color: #263b53; font-size: 18px; }.quiz-toolbar span, .quiz-run-header span { margin-left: 12px; color: #78869a; font-size: 13px; }.quiz-list, .resource-list { display: grid; gap: 10px; }.quiz-card, .question-row { display: flex; justify-content: space-between; gap: 18px; padding: 17px; border: 1px solid #e1e7ee; border-radius: 7px; background: #fff; }.quiz-card-main, .question-row-main { min-width: 0; }.quiz-card h4 { margin: 10px 0 5px; color: #314258; font-size: 16px; }.quiz-card p, .question-row p { margin: 6px 0 0; color: #69798c; font-size: 13px; line-height: 1.6; }.quiz-card-actions, .question-row-actions { display: flex; align-items: center; gap: 4px; flex: 0 0 auto; }.quiz-completed-info { display: flex; align-items: center; color: #267745; font-size: 13px; font-weight: 600; }.question-row strong { display: block; margin-top: 8px; color: #314258; font-size: 15px; }.question-row-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; color: #78869a; font-size: 12px; }.question-bank-actions { display: flex; justify-content: flex-end; align-items: center; gap: 8px; margin-bottom: 12px; }.question-bank-stats { margin-right: auto; color: #78869a; font-size: 12px; }.question-bank-actions :deep(svg), .quiz-toolbar :deep(svg) { width: 16px; height: 16px; }.quiz-question { padding: 18px 20px; border: 1px solid #e1e7ee; border-radius: 7px; background: #fff; scroll-margin-top: 18px; }.quiz-question.answered { border-color: #b8d8c4; }.quiz-sheet { display: grid; gap: 14px; }.quiz-question-header { display: flex; align-items: flex-start; gap: 12px; }.quiz-question-header > div { min-width: 0; flex: 1; }.quiz-number { display: grid; width: 28px; height: 28px; flex: 0 0 28px; place-items: center; border-radius: 50%; background: #e9f1fc; color: #3775c3; font-size: 13px; font-weight: 700; }.quiz-question strong { display: block; margin-top: 8px; color: #314258; font-size: 15px; }.quiz-stem { margin: 14px 0 0; color: #4d5e73; line-height: 1.75; white-space: pre-wrap; }.quiz-answer-area { margin: 16px 0 0 40px; padding-top: 14px; border-top: 1px solid #eef1f5; }.practice-options { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; }.quiz-run-layout { display: grid; grid-template-columns: minmax(0, 1fr) 190px; gap: 18px; align-items: start; }.quiz-nav { position: sticky; top: 18px; padding: 16px; border: 1px solid #e1e7ee; background: #fbfcfe; }.quiz-nav strong { color: #314258; }.quiz-nav-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; margin: 14px 0; }.quiz-nav-grid button { aspect-ratio: 1; border: 1px solid #d9e1ea; border-radius: 4px; background: #fff; color: #69798c; cursor: pointer; }.quiz-nav-grid button.active { border-color: #4aa56d; background: #eaf7ee; color: #267745; }.quiz-nav-legend { display: grid; gap: 7px; color: #78869a; font-size: 12px; }.quiz-nav-legend span { display: flex; align-items: center; gap: 7px; }.dot { width: 8px; height: 8px; border: 1px solid #cbd6e2; border-radius: 50%; }.dot.done { border-color: #4aa56d; background: #4aa56d; }.save-button, .submit-button { width: 100%; margin: 8px 0 0; }.quiz-clock { min-width: 108px; padding: 10px 14px; border: 1px solid #dce6ef; text-align: center; }.quiz-clock span { display: block; margin: 0; font-size: 12px; }.quiz-clock strong { display: block; margin-top: 3px; color: #267745; font-size: 20px; }.quiz-clock.urgent { border-color: #e9b7ad; }.quiz-clock.urgent strong { color: #b34f48; }.answer-result { margin-top: 14px; padding: 11px 12px; background: #fff3f1; color: #a94c45; }.answer-result.correct { background: #effaf4; color: #238353; }.answer-result-head, .answer-detail { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }.answer-detail { margin-top: 8px; color: #526276; font-size: 13px; }.answer-detail span { display: block; }.answer-result p { margin: 8px 0 0; line-height: 1.6; }.answer-icon { width: 18px; height: 18px; }.correct-icon { color: #32a568; }.wrong-icon { color: #d85c53; }.question-bank-empty { display: flex; min-height: 260px; align-items: center; justify-content: center; flex-direction: column; gap: 10px; border: 1px dashed #cbd6e2; color: #728095; }.question-bank-empty :deep(svg) { width: 34px; height: 34px; color: #9ab6d0; }.question-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }.question-form-grid :deep(.el-select), .question-form-grid :deep(.el-date-editor), .question-form-grid :deep(.el-input-number) { width: 100%; }.quiz-settings { display: flex; flex-wrap: wrap; gap: 18px; }.builder-list { display: grid; gap: 8px; max-height: 300px; overflow: auto; }.builder-row { display: grid; grid-template-columns: minmax(0, 1fr) auto 92px; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #edf0f4; }.builder-row :deep(.el-checkbox) { min-width: 0; }.builder-row :deep(.el-checkbox__label) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.muted, .empty-inline { color: #9aa7b6; font-size: 13px; }.favorite-icon { flex: 0 0 auto; }
@media (max-width: 760px) { .quiz-toolbar, .quiz-run-header, .quiz-card, .question-row { align-items: flex-start; flex-direction: column; }.quiz-toolbar span { display: block; margin: 6px 0 0; }.quiz-card-actions, .question-row-actions { width: 100%; }.quiz-run-layout { grid-template-columns: 1fr; }.quiz-nav { position: static; order: -1; }.question-form-grid { grid-template-columns: 1fr; }.quiz-answer-area { margin-left: 0; } }
.student-quiz-panel > .el-tabs { display: none; }
.quiz-run-shell { max-width: 1080px; margin: 0 auto; }
.quiz-run-layout { display: grid; grid-template-columns: minmax(0, 1fr) 190px; gap: 18px; align-items: start; }
.quiz-question-group { margin-bottom: 30px; }
.quiz-group-title { margin: 0 0 16px; padding-bottom: 12px; border-bottom: 2px solid #e6edf5; color: #1f2f43; font-size: 20px; }
.quiz-question { border-color: #edf1f5; border-radius: 2px; box-shadow: 0 2px 10px rgba(35, 67, 105, .04); }
.quiz-question-header { gap: 14px; }
.quiz-question-header strong { font-size: 17px; }
.quiz-total-result { display: flex; align-items: baseline; gap: 5px; color: #4b5e74; }
.quiz-total-result strong { color: #182b42; font-size: 30px; }
.quiz-total-result span { font-size: 14px; }
.teacher-preview-label strong { color: #21406b; font-size: 20px; }
.teacher-preview-answer { margin-top: 12px; padding: 12px 14px; border: 1px dashed #c8d6e7; background: #f7faff; color: #425266; }
.teacher-preview-answer strong { display: block; color: #21406b; font-size: 14px; }
.teacher-preview-answer p { margin: 8px 0 0; line-height: 1.6; }
.quiz-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; padding-top: 16px; border-top: 1px solid #e5ebf2; }
.quiz-actions .save-button, .quiz-actions .submit-button { width: auto; margin: 0; }
</style>
