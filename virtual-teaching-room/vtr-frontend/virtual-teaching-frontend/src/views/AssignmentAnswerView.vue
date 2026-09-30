<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, DocumentChecked } from '@element-plus/icons-vue'
import { assignmentApi, learningResourceApi, submissionApi } from '@/api'
import { formatDateTime, splitLines } from '@/utils/format'
import AiAssistantButton from '@/components/AiAssistantButton.vue'
import AiAssistantDrawer from '@/components/AiAssistantDrawer.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const assignment = ref(null)
const questionBank = ref([])
const answerMap = reactive({})
const submission = ref(null)
const isResubmitting = ref(false)
const navAnchor = ref(null)
const navFixed = ref(false)
const navStyle = ref({})
const activeQuestionId = ref(null)
const pendingQuestionId = ref(null)
const aiVisible = ref(false)
const aiContext = reactive({ title: '', meta: '', questionType: '', questionId: null, type: 'question', excerpt: '' })
const navStickyTop = 110
let pendingScrollTimer = null

const courseId = computed(() => Number(route.params.courseId))
const assignmentId = computed(() => Number(route.params.assignmentId))
const questions = computed(() => (assignment.value?.questionIds || [])
  .map((id) => questionBank.value.find((item) => String(item.id) === String(id)))
  .filter(Boolean))
const canSubmit = computed(() => assignment.value?.status === 'PUBLISHED'
  && !assignment.value?.isExpired
  && assignment.value?.myRemainingSubmits !== 0)
const hasShortAnswer = computed(() => questions.value.some((question) => question.questionType === 'TEXT'))
const isSubmitted = computed(() => Boolean(submission.value))
const canResubmit = computed(() => isSubmitted.value && hasShortAnswer.value && canSubmit.value)
const objectiveQuestions = computed(() => questions.value.filter(isObjectiveQuestion))
const correctObjectiveCount = computed(() => objectiveQuestions.value.filter((question) => questionResult(question) === true).length)

function questionTypeLabel(type) {
  return ({ SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', JUDGMENT: '判断题', FILL: '填空题', TEXT: '简答题', PROGRAMMING: '编程题' })[type] || type
}

function isObjectiveQuestion(question) {
  return !['TEXT', 'PROGRAMMING'].includes(question.questionType)
}

function resetAnswers() {
  Object.keys(answerMap).forEach((key) => delete answerMap[key])
  questions.value.forEach((question) => {
    answerMap[question.id] = question.questionType === 'MULTIPLE_CHOICE' ? [] : ''
  })
}

function normalizeAnswer(value) {
  return String(value == null ? '' : value)
    .trim()
    .replace(/\s+/g, '')
    .replace(/[。．.、,，;；：:]/g, '')
    .toLowerCase()
}

function choiceKey(value) {
  const raw = String(value == null ? '' : value).trim()
  const letter = raw.match(/^([a-z])(?:[.、)）\s]|$)/i)
  return letter ? letter[1].toLowerCase() : normalizeAnswer(raw)
}

function judgmentKey(value) {
  const normalized = normalizeAnswer(value)
  if (['正确', '对', 'yes', 'true', '1'].includes(normalized)) return 'correct'
  if (['错误', '错', 'no', 'false', '0'].includes(normalized)) return 'wrong'
  return normalized
}

function splitAnswer(value) {
  return String(value == null ? '' : value).split(/[、,，;；|\/\n]+/).map((item) => item.trim()).filter(Boolean)
}

function parseSubmissionAnswers(content) {
  const parsed = {}
  const text = String(content || '')
  const matches = [...text.matchAll(/(?:^|\n)第\s*(\d+)\s*题\s*[：:]\s*([\s\S]*?)(?=\n第\s*\d+\s*题\s*[：:]|$)/g)]
  matches.forEach((match) => { parsed[Number(match[1]) - 1] = match[2].trim() })
  return parsed
}

function loadSubmissionAnswers(record) {
  const parsed = parseSubmissionAnswers(record?.code)
  questions.value.forEach((question, index) => {
    const value = parsed[index] || ''
    answerMap[question.id] = question.questionType === 'MULTIPLE_CHOICE' ? splitAnswer(value) : value
  })
}

function questionResult(question) {
  if (!submission.value || isResubmitting.value || question.questionType === 'TEXT' || question.questionType === 'PROGRAMMING') return null
  const answer = answerText(question)
  const reference = question.referenceAnswer
  if (!answer || !reference) return false
  if (question.questionType === 'JUDGMENT') return judgmentKey(answer) === judgmentKey(reference)
  if (question.questionType === 'MULTIPLE_CHOICE') {
    const actual = splitAnswer(answer).map(choiceKey).sort()
    const expected = splitAnswer(reference).map(choiceKey).sort()
    return actual.length === expected.length && actual.every((item, index) => item === expected[index])
  }
  if (question.questionType === 'FILL') {
    const actual = splitAnswer(answer).map(normalizeAnswer)
    const expected = splitAnswer(reference).map(normalizeAnswer)
    return actual.length === expected.length && actual.every((item, index) => item === expected[index])
  }
  return choiceKey(answer) === choiceKey(reference) || normalizeAnswer(answer) === normalizeAnswer(reference)
}

function resultLabel(question) {
  const result = questionResult(question)
  if (result === true) return '回答正确'
  if (result === false) return '回答错误'
  return '等待老师批改'
}

function submissionTime(record) {
  return record?.submittedAt || record?.submitTime || ''
}

function latestSubmission(records) {
  return [...(Array.isArray(records) ? records : [])].sort((a, b) => new Date(submissionTime(b)) - new Date(submissionTime(a)))[0] || null
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

function openAiAssistant(question, index) {
  Object.assign(aiContext, {
    title: `第 ${index + 1} 题${question.title ? ` · ${question.title}` : ''}`,
    meta: `${questionTypeLabel(question.questionType)} · ${assignment.value?.title || '当前作业'}`,
    questionType: question.questionType || '',
    questionId: question.id || null,
    type: 'question',
    excerpt: questionExcerpt(question)
  })
  aiVisible.value = true
}

function questionExcerpt(question) {
  const parts = [question.stem || '']
  if (question.options) parts.push(`选项：\n${question.options}`)
  return parts.filter(Boolean).join('\n\n')
}

function updateNavigation() {
  if (!navAnchor.value) return
  const compactMode = window.innerWidth <= 720
  const anchorRect = navAnchor.value.getBoundingClientRect()
  navFixed.value = !compactMode && anchorRect.top <= navStickyTop
  navStyle.value = navFixed.value
    ? { left: `${anchorRect.left}px`, top: `${navStickyTop}px`, width: `${anchorRect.width}px` }
    : {}

  if (pendingQuestionId.value) {
    activeQuestionId.value = pendingQuestionId.value
    return
  }

  const questionElements = Array.from(document.querySelectorAll('.answer-question'))
  // 以题目顶部穿过导航栏所在水平线作为切换点。这样长题卡下方已经
  // 出现下一题时，不会因为上一题可见面积更大而继续高亮上一题。
  const navigationLine = navStickyTop + 8
  const activeElement = questionElements.find((element) => {
    const rect = element.getBoundingClientRect()
    return rect.bottom > navigationLine && rect.top >= navigationLine
  }) || [...questionElements].reverse().find((element) => {
    return element.getBoundingClientRect().top < navigationLine
  }) || questionElements[0]
  activeQuestionId.value = activeElement?.id.replace('question-', '') || null
}

function finishPendingScroll() {
  if (!pendingQuestionId.value) return
  const question = document.getElementById(`question-${pendingQuestionId.value}`)
  if (question && Math.abs(question.getBoundingClientRect().top - navStickyTop) > 12) {
    pendingScrollTimer = window.setTimeout(finishPendingScroll, 120)
    return
  }
  activeQuestionId.value = pendingQuestionId.value
  pendingQuestionId.value = null
  window.clearTimeout(pendingScrollTimer)
}

function scrollToQuestion(questionId) {
  const question = document.getElementById(`question-${questionId}`)
  if (!question) return
  pendingQuestionId.value = String(questionId)
  activeQuestionId.value = pendingQuestionId.value
  window.clearTimeout(pendingScrollTimer)
  const targetTop = window.scrollY + question.getBoundingClientRect().top - navStickyTop
  window.scrollTo({ top: Math.max(0, targetTop), behavior: 'smooth' })
  pendingScrollTimer = window.setTimeout(finishPendingScroll, 1600)
}

async function load() {
  loading.value = true
  try {
    const detail = await assignmentApi.detail(assignmentId.value)
    assignment.value = detail
    if (detail?.canView === false) {
      questionBank.value = []
      submission.value = null
      return
    }

    const [questionPayload, submissionPayload] = await Promise.all([
      learningResourceApi.questions({ courseId: courseId.value, assignmentId: assignmentId.value }),
      submissionApi.mySubmissions(assignmentId.value)
    ])
    questionBank.value = Array.isArray(questionPayload) ? questionPayload : (questionPayload?.list || [])
    submission.value = latestSubmission(submissionPayload)
    isResubmitting.value = false
    resetAnswers()
    if (submission.value) loadSubmissionAnswers(submission.value)
    await nextTick()
    updateNavigation()
  } finally {
    loading.value = false
  }
}

async function backToAssignments() {
  await router.push({ path: '/courseware', query: { courseId: courseId.value, tab: 'assignments' } })
}

async function submitAssignment() {
  const unanswered = questions.value
    .map((question, index) => hasAnswer(question) ? null : index + 1)
    .filter(Boolean)
  if (unanswered.length) {
    ElMessage.warning(`还有第 ${unanswered.join('、')} 题未作答`)
    return
  }
  submitting.value = true
  try {
    const content = questions.value
      .map((question, index) => `第 ${index + 1} 题：${answerText(question)}`)
      .join('\n')
    await submissionApi.submit(assignmentId.value, { assignmentId: assignmentId.value, language: 'TEXT', code: content, content })
    ElMessage.success(isResubmitting.value ? '作业已重新提交' : '作业已提交')
    const records = await submissionApi.mySubmissions(assignmentId.value)
    submission.value = latestSubmission(records)
    isResubmitting.value = false
    const questionPayload = await learningResourceApi.questions({
      courseId: courseId.value,
      assignmentId: assignmentId.value
    })
    questionBank.value = Array.isArray(questionPayload) ? questionPayload : (questionPayload?.list || [])
    if (submission.value) loadSubmissionAnswers(submission.value)
  } finally {
    submitting.value = false
  }
}

function startResubmit() {
  if (!canResubmit.value) return
  isResubmitting.value = true
}

function cancelResubmit() {
  isResubmitting.value = false
  if (submission.value) loadSubmissionAnswers(submission.value)
}

onMounted(() => {
  window.addEventListener('scroll', updateNavigation, { passive: true })
  window.addEventListener('scrollend', finishPendingScroll, { passive: true })
  window.addEventListener('resize', updateNavigation)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', updateNavigation)
  window.removeEventListener('scrollend', finishPendingScroll)
  window.removeEventListener('resize', updateNavigation)
  window.clearTimeout(pendingScrollTimer)
})

load()
</script>

<template>
  <main v-loading="loading" class="answer-workspace">
    <header class="answer-topbar">
      <el-button text :icon="ArrowLeft" @click="backToAssignments">返回课程作业</el-button>
      <span>作业作答</span>
    </header>

    <section v-if="assignment && assignment.canView === false" class="assignment-access-layout">
      <div class="assignment-access-card">
        <p class="assignment-access-kicker">{{ assignment.courseName || '课程作业' }}</p>
        <h1>{{ assignment.title }}</h1>
        <p>{{ assignment.accessMessage || '当前无法查看该作业' }}</p>
        <el-button type="primary" @click="backToAssignments">返回课程作业</el-button>
      </div>
    </section>

    <section v-else-if="assignment" class="answer-layout">
      <header class="answer-assignment-header">
        <p>课程作业</p>
        <h1>{{ assignment.title }}</h1>
        <div><span>题量：{{ questions.length }}</span><span>满分：{{ assignment.totalScore || 100 }}</span><span>提交截止：{{ formatDateTime(assignment.deadline) }}</span></div>
      </header>

      <section class="answer-content">
        <article v-for="(question, index) in questions" :id="`question-${question.id}`" :key="question.id" class="answer-question" :class="{ 'is-submitted': isSubmitted && !isResubmitting, 'is-correct': questionResult(question) === true, 'is-wrong': questionResult(question) === false }">
          <div class="answer-question-header"><span>第 {{ index + 1 }} 题</span><em>{{ questionTypeLabel(question.questionType) }}</em><strong v-if="isSubmitted && !isResubmitting" class="question-result" :class="{ correct: questionResult(question) === true, wrong: questionResult(question) === false }">{{ resultLabel(question) }}</strong><AiAssistantButton @click="openAiAssistant(question, index)" /></div>
          <h2>{{ question.stem }}</h2>
          <div v-if="isSubmitted && !isResubmitting" class="submitted-answer-area">
            <div class="submitted-answer-label">你的答案</div>
            <div class="submitted-answer-content">{{ answerText(question) || '未作答' }}</div>
            <div v-if="isObjectiveQuestion(question)" class="reference-answer">
              <div>参考答案：{{ question.referenceAnswer || '暂未提供' }}</div>
              <div v-if="question.analysis" class="answer-analysis">解析：{{ question.analysis }}</div>
            </div>
          </div>
          <div v-else class="answer-input-area">
            <el-radio-group v-if="['SINGLE_CHOICE', 'JUDGMENT'].includes(question.questionType)" v-model="answerMap[question.id]" class="answer-options">
              <el-radio v-for="option in (question.questionType === 'JUDGMENT' ? ['正确', '错误'] : splitLines(question.options))" :key="option" :label="option" border>{{ option }}</el-radio>
            </el-radio-group>
            <el-checkbox-group v-else-if="question.questionType === 'MULTIPLE_CHOICE'" v-model="answerMap[question.id]" class="answer-options">
              <el-checkbox v-for="option in splitLines(question.options)" :key="option" :label="option" border>{{ option }}</el-checkbox>
            </el-checkbox-group>
            <el-input v-else v-model="answerMap[question.id]" type="textarea" :rows="question.questionType === 'PROGRAMMING' ? 13 : 5" :placeholder="answerPlaceholder(question)" />
          </div>
        </article>
        <div v-if="!questions.length" class="answer-empty">本作业暂未配置可作答题目。</div>
        <footer v-if="!isSubmitted" class="answer-submit-panel">
          <div><DocumentChecked :size="20" /><span>已完成 {{ questions.filter(hasAnswer).length }}/{{ questions.length }} 题</span></div>
          <el-button type="primary" size="large" :loading="submitting" :disabled="!canSubmit || !questions.length" @click="submitAssignment">提交作业</el-button>
        </footer>
        <section v-else class="submission-result-panel">
          <div class="submission-result-heading">
            <div>
              <span class="result-kicker">提交结果</span>
              <h3>{{ !hasShortAnswer || submission.status === 'GRADED' || submission.status === 'REVIEWED' ? '作业已批改' : '作业已提交' }}</h3>
            </div>
            <strong v-if="submission.score !== null && submission.score !== undefined" class="submission-score">{{ submission.score }}<small>/{{ assignment.totalScore || 100 }} 分</small></strong>
          </div>
          <div class="submission-result-meta">
            <span>提交于 {{ formatDateTime(submissionTime(submission)) }}</span>
            <span v-if="objectiveQuestions.length">客观题：{{ correctObjectiveCount }}/{{ objectiveQuestions.length }} 题正确</span>
            <span v-if="submission.statusDescription">{{ submission.statusDescription }}</span>
          </div>
          <p v-if="submission.manualReview || submission.feedback" class="teacher-feedback">老师评语：{{ submission.manualReview || submission.feedback }}</p>
          <p v-else-if="hasShortAnswer" class="teacher-feedback pending">简答题等待老师批改，批改后可在这里查看评语。</p>
          <div class="submission-result-actions">
            <el-button v-if="canResubmit && !isResubmitting" type="primary" @click="startResubmit">重新提交</el-button>
            <template v-if="isResubmitting">
              <el-button @click="cancelResubmit">取消修改</el-button>
              <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitAssignment">提交修改</el-button>
            </template>
            <span v-if="!canResubmit && hasShortAnswer && assignment.isExpired" class="resubmit-disabled">已超过截止时间，无法重新提交</span>
          </div>
        </section>
      </section>

      <aside ref="navAnchor" class="answer-summary-column">
        <div class="answer-summary" :class="{ 'is-fixed': navFixed }" :style="navStyle">
          <p>题目导航</p>
          <nav class="answer-index" aria-label="题目导航">
            <a v-for="(question, index) in questions" :key="question.id" href="#" :class="{ answered: hasAnswer(question), active: String(activeQuestionId) === String(question.id) }" @click.prevent="scrollToQuestion(question.id)">{{ index + 1 }}</a>
          </nav>
        </div>
      </aside>
    </section>
    <AiAssistantDrawer v-model="aiVisible" :course-id="courseId" :context-title="aiContext.title" :context-meta="aiContext.meta" :question-type="aiContext.questionType" :question-id="aiContext.questionId" :context-type="aiContext.type" :context-excerpt="aiContext.excerpt" />
  </main>
</template>

<style scoped>
.answer-workspace { min-height: calc(100vh - 44px); padding: 4px 0 48px; color: #263650; }
.assignment-access-layout { display: grid; place-items: center; min-height: 430px; padding: 30px 18px; }
.assignment-access-card { width: min(560px, 100%); padding: 42px 36px; border: 1px solid #dfe7f0; border-radius: 8px; background: #fff; text-align: center; box-shadow: 0 12px 32px rgba(45, 72, 112, .08); }
.assignment-access-kicker { margin: 0 0 8px; color: #6e89b5; font-size: 14px; }
.assignment-access-card h1 { margin: 0; color: #263f69; font-size: 24px; font-weight: 600; }
.assignment-access-card p:not(.assignment-access-kicker) { margin: 18px 0 26px; color: #758196; line-height: 1.7; }
.answer-topbar { display: flex; align-items: center; justify-content: space-between; min-height: 54px; padding: 0 6px; border-bottom: 1px solid #e2e8f0; color: #77849a; font-size: 14px; }
.answer-layout { display: grid; grid-template-columns: minmax(0, 1fr) 260px; gap: 24px; width: min(1180px, calc(100% - 40px)); margin: 26px auto 0; align-items: start; }
.answer-content { min-width: 0; display: grid; gap: 18px; }
.answer-assignment-header { grid-column: 1 / -1; padding: 4px 2px 18px; border-bottom: 1px solid #e2e8f0; }
.answer-assignment-header p { margin: 0 0 6px; color: #5c88d8; font-size: 13px; }
.answer-assignment-header h1 { margin: 0; color: #203a66; font-size: 24px; line-height: 1.45; }
.answer-assignment-header > div { display: flex; flex-wrap: wrap; gap: 8px 22px; margin-top: 13px; color: #728097; font-size: 13px; }
.answer-assignment-header > div span { white-space: nowrap; }
.answer-question { scroll-margin-top: 16px; padding: 26px 28px; border: 1px solid #dfe7f0; border-radius: 7px; background: #fff; }
.answer-question.is-correct { border-color: #b7e3c5; }.answer-question.is-wrong { border-color: #f2c2c2; }.answer-question-header { display: flex; align-items: center; gap: 12px; color: #2b70d7; font-size: 14px; }.question-result { margin-left: auto; color: #7a879b; font-size: 13px; font-weight: 500; }.question-result.correct { color: #29965b; }.question-result.wrong { color: #d65353; }
.answer-question-header em { padding: 3px 8px; border-radius: 4px; background: #eef5ff; color: #5a8edb; font-size: 12px; font-style: normal; }
.answer-question h2 { margin: 12px 0 20px; color: #263d66; font-size: 18px; line-height: 1.7; }
.answer-input-area { padding: 15px; border: 1px solid #e1e8f1; border-radius: 6px; background: #fbfcfe; }
.answer-options { display: grid; gap: 10px; }
.answer-options :deep(.el-radio.is-bordered), .answer-options :deep(.el-checkbox.is-bordered) { width: 100%; height: auto; min-height: 42px; margin: 0; padding: 10px 13px; white-space: normal; }
.answer-options :deep(.el-radio__label), .answer-options :deep(.el-checkbox__label) { line-height: 1.55; white-space: normal; }
.submitted-answer-area { padding: 15px 16px; border: 1px solid #e1e8f1; border-radius: 6px; background: #fbfcfe; }.submitted-answer-label { color: #8995a7; font-size: 12px; }.submitted-answer-content { margin-top: 8px; color: #2f405c; line-height: 1.75; white-space: pre-wrap; }.reference-answer { margin-top: 10px; padding-top: 10px; border-top: 1px dashed #e2e8f0; color: #c45353; font-size: 13px; line-height: 1.6; }.answer-analysis { margin-top: 6px; color: #687991; }
.answer-empty { min-height: 260px; display: grid; place-items: center; border: 1px dashed #ccd8e7; color: #8794a8; }
.answer-submit-panel { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 20px 22px; border: 1px solid #dbe5ef; border-radius: 7px; background: #f9fbfe; color: #68778d; font-size: 14px; }
.answer-submit-panel > div { display: flex; align-items: center; gap: 8px; }
.answer-submit-panel :deep(.el-button) { min-width: 128px; }
.submission-result-panel { padding: 20px 22px; border: 1px solid #dbe5ef; border-radius: 7px; background: #f9fbfe; }.submission-result-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; }.result-kicker { color: #6d7e96; font-size: 12px; }.submission-result-heading h3 { margin: 5px 0 0; color: #263f69; font-size: 18px; }.submission-score { color: #286ed2; font-size: 27px; }.submission-score small { margin-left: 3px; color: #8491a4; font-size: 13px; font-weight: 400; }.submission-result-meta { display: flex; flex-wrap: wrap; gap: 8px 20px; margin-top: 14px; color: #748198; font-size: 13px; }.teacher-feedback { margin: 15px 0 0; padding: 11px 13px; border-radius: 5px; background: #fff; color: #50617a; font-size: 13px; line-height: 1.65; }.teacher-feedback.pending { color: #8a6d3b; background: #fffaf0; }.submission-result-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 16px; }.resubmit-disabled { color: #a0aabb; font-size: 13px; }
.answer-summary-column { grid-column: 2; align-self: start; min-width: 0; }
.answer-summary { padding: 18px; border: 1px solid #dce5f0; border-radius: 7px; background: #f9fbfe; }
.answer-summary.is-fixed { position: fixed; z-index: 30; box-shadow: 0 10px 28px rgba(52, 83, 128, .12); }
.answer-summary > p { margin: 0; color: #405a80; font-size: 14px; font-weight: 600; }
.answer-index { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; margin-top: 14px; }
.answer-index a { display: grid; place-items: center; aspect-ratio: 1; border: 1px solid #ced9e8; border-radius: 4px; background: #fff; color: #56657b; font-size: 13px; text-decoration: none; }
.answer-index a:hover { border-color: #5790ea; color: #286cd5; }
.answer-index a.answered { border-color: #4f90ef; background: #eff6ff; color: #2266ca; }
.answer-index a.active { border-color: #286cd5; background: #286cd5; color: #fff; box-shadow: 0 2px 7px rgba(40, 108, 213, .24); }
@media (max-width: 900px) { .answer-layout { grid-template-columns: minmax(0, 1fr) 220px; gap: 18px; width: min(100% - 28px, 760px); }.answer-summary { padding: 15px; } }
@media (max-width: 720px) { .answer-workspace { padding-bottom: 28px; }.answer-layout { grid-template-columns: 1fr; margin-top: 16px; }.answer-assignment-header { grid-column: 1; }.answer-summary-column { grid-column: 1; grid-row: 2; }.answer-summary.is-fixed { position: static; width: auto !important; }.answer-index { grid-template-columns: repeat(8, 1fr); }.answer-question { padding: 20px 16px; }.answer-question h2 { font-size: 16px; }.answer-submit-panel { align-items: stretch; flex-direction: column; padding: 16px; }.answer-submit-panel :deep(.el-button) { width: 100%; }.submission-result-heading { align-items: flex-start; }.submission-result-panel { padding: 16px; }.submission-result-actions { align-items: stretch; flex-direction: column; }.submission-result-actions :deep(.el-button) { width: 100%; }.answer-topbar { padding: 0; } }
</style>
