<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { chapterQuizApi, examApi } from '@/api'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const exam = ref(null)
const result = ref(null)
const answers = reactive({})
let timer = null
const remainingSeconds = ref(0)
const questionLabels = { SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', JUDGMENT: '判断题', FILL: '填空题', TEXT: '简答题', PROGRAMMING: '编程题' }
const isCompleted = computed(() => Boolean(result.value))
const canReviewPaper = computed(() => result.value?.canReviewPaper !== false)
function questionResult(question) { return result.value?.answers?.find((answer) => String(answer.questionId) === String(question.id)) }

function typeLabel(type) { return questionLabels[type] || '题目' }
function optionsOf(question) {
  const raw = question.questionType === 'JUDGMENT' && !question.options ? '正确\n错误' : String(question.options || '')
  return raw.split(/\n|\r|\|/).map((item) => item.trim()).filter(Boolean).map((item, index) => {
    const match = item.match(/^([A-Za-z])[.、)）:]?\s*(.*)$/)
    return match ? { value: match[1].toUpperCase(), label: item } : { value: String.fromCharCode(65 + index), label: `${String.fromCharCode(65 + index)}. ${item}` }
  })
}
function formatSeconds(value) { return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}` }
function initAnswers() { Object.keys(answers).forEach((key) => delete answers[key]); (exam.value?.questions || []).forEach((question) => { answers[question.id] = question.questionType === 'MULTIPLE_CHOICE' ? [] : question.answer || '' }) }
function startTimer() { if (!exam.value?.attempt?.expiresAt) return; const tick = () => { remainingSeconds.value = Math.max(0, Math.floor((new Date(exam.value.attempt.expiresAt).getTime() - Date.now()) / 1000)); if (!remainingSeconds.value) clearTimer() }; tick(); timer = window.setInterval(tick, 1000) }
function clearTimer() { if (timer) window.clearInterval(timer); timer = null }
async function load() {
  loading.value = true
  try {
    const overview = await examApi.detail(route.params.examId)
    if (overview.completed) {
      const attempts = await chapterQuizApi.myAttempts(overview.id)
      const attempt = (attempts || []).find((item) => ['SUBMITTED', 'REVIEWED', 'PENDING_REVIEW'].includes(item.status))
      exam.value = await chapterQuizApi.attemptDetail(overview.id, attempt.id)
      result.value = exam.value
    } else {
      exam.value = await chapterQuizApi.start(overview.id)
      result.value = null
      startTimer()
    }
    initAnswers()
  } catch (error) { ElMessage.error(error?.message || '考试详情加载失败') } finally { loading.value = false }
}
async function submit() {
  try {
    await ElMessageBox.confirm('提交后将不能修改试卷答案，确认提交吗？', '提交试卷', { type: 'warning' })
    submitting.value = true
    const payload = (exam.value.questions || []).map((question) => ({ questionId: question.id, answer: answers[question.id] })).filter((item) => Array.isArray(item.answer) ? item.answer.length : String(item.answer || '').trim())
    result.value = { ...(await chapterQuizApi.submit(exam.value.attempt.id, { answers: payload })), canReviewPaper: exam.value.allowReviewAfterSubmit !== false }
    exam.value = { ...exam.value, ...result.value }
    clearTimer()
    ElMessage.success(result.value.pendingReview ? '试卷已提交，主观题等待教师批改' : '试卷已提交')
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '提交试卷失败') } finally { submitting.value = false }
}
function back() { router.push({ path: '/courseware', query: { courseId: route.params.courseId || route.query.courseId, tab: 'exams' } }) }
onMounted(load)
onBeforeUnmount(clearTimer)
</script>

<template>
  <main class="exam-detail-page" v-loading="loading">
    <header class="exam-detail-header"><el-button text :icon="ArrowLeft" @click="back">返回考试列表</el-button><div><h1>{{ exam?.title || '课程考试' }}</h1><p v-if="exam">{{ exam.questions?.length || 0 }} 道题 · {{ exam.totalScore }} 分 · {{ exam.durationMinutes }} 分钟</p></div><div v-if="exam?.attempt && !isCompleted" class="exam-countdown">剩余时间<strong>{{ formatSeconds(remainingSeconds) }}</strong></div><div v-else-if="isCompleted" class="exam-result-score">{{ result.score ?? 0 }}<span>/ {{ exam?.totalScore }} 分</span></div></header>
    <template v-if="exam">
      <section class="exam-detail-intro"><div><span class="exam-status">{{ isCompleted ? '已完成' : '进行中' }}</span><span>{{ exam.description || '请完成全部题目后提交试卷。' }}</span></div><p v-if="isCompleted && !canReviewPaper">教师已关闭考后查看试卷权限，仅保留本次成绩。</p><p v-else>答题过程中可以回看和修改答案，提交后系统会自动批改选择题、判断题等客观题。</p></section>
      <section v-if="canReviewPaper" class="exam-detail-questions"><article v-for="(question, index) in exam.questions" :key="question.id" class="exam-detail-question"><div class="exam-question-heading"><b>第 {{ index + 1 }} 题</b><strong>{{ typeLabel(question.questionType) }}</strong><span>{{ question.score }} 分</span></div><h2>{{ question.stem }}</h2><el-checkbox-group v-if="question.questionType === 'MULTIPLE_CHOICE'" v-model="answers[question.id]" :disabled="isCompleted"><el-checkbox v-for="option in optionsOf(question)" :key="option.value" :label="option.value" border>{{ option.label }}</el-checkbox></el-checkbox-group><el-radio-group v-else-if="['SINGLE_CHOICE', 'JUDGMENT'].includes(question.questionType)" v-model="answers[question.id]" :disabled="isCompleted"><el-radio v-for="option in optionsOf(question)" :key="option.value" :label="option.value" border>{{ option.label }}</el-radio></el-radio-group><el-input v-else v-model="answers[question.id]" :disabled="isCompleted" type="textarea" :rows="question.questionType === 'PROGRAMMING' ? 10 : 4" placeholder="请输入答案" /><div v-if="isCompleted" class="exam-answer-feedback"><template v-if="questionResult(question)"><CircleCheckFilled v-if="questionResult(question).correct" class="correct" /><CircleCloseFilled v-else class="wrong" /><span>我的答案：{{ questionResult(question).answer || '未作答' }}</span><span v-if="questionResult(question).referenceAnswer">正确答案：{{ questionResult(question).referenceAnswer }}</span><em v-if="questionResult(question).analysis">{{ questionResult(question).analysis }}</em></template></div></article></section><div v-else class="exam-paper-hidden">教师已设置为考后不展示试卷内容。</div>
      <footer v-if="!isCompleted" class="exam-detail-footer"><span>提交后客观题由系统自动批改，简答题/编程题由教师评阅。</span><el-button type="primary" :loading="submitting" @click="submit">提交试卷</el-button></footer>
    </template>
  </main>
</template>

<style scoped>
.exam-detail-page { width: min(980px, 100%); min-height: 720px; margin: 0 auto; padding: 24px 0 90px; color: #28344a; }.exam-detail-header { display: flex; align-items: flex-start; gap: 24px; padding-bottom: 22px; border-bottom: 1px solid #e6ebf2; }.exam-detail-header > div:nth-child(2) { flex: 1; }.exam-detail-header h1 { margin: 2px 0 8px; color: #243b64; font-size: 26px; }.exam-detail-header p { margin: 0; color: #748198; }.exam-countdown, .exam-result-score { display: flex; align-items: flex-end; flex-direction: column; color: #8390a1; font-size: 12px; }.exam-countdown strong { margin-top: 3px; color: #3b82f6; font-size: 21px; }.exam-result-score { color: #238353; font-size: 30px; font-weight: 700; }.exam-result-score span { margin-left: 4px; color: #6b8f7a; font-size: 13px; }.exam-detail-intro { margin: 22px 0; padding: 18px 20px; border: 1px solid #dce6f5; border-radius: 6px; background: #f7faff; }.exam-detail-intro > div { display: flex; align-items: center; gap: 12px; color: #526276; font-size: 14px; }.exam-detail-intro p { margin: 10px 0 0; color: #6b7b8e; font-size: 13px; line-height: 1.65; }.exam-status { padding: 4px 9px; border-radius: 4px; background: #e8f6ee; color: #278359; font-size: 12px; }.exam-detail-questions { display: grid; gap: 16px; }.exam-detail-question { padding: 22px; border: 1px solid #e0e7ef; border-radius: 6px; background: #fff; }.exam-question-heading { display: flex; align-items: center; gap: 10px; color: #748198; font-size: 13px; }.exam-question-heading b { color: #3b82f6; }.exam-question-heading strong { color: #526276; font-weight: 500; }.exam-question-heading span { margin-left: auto; }.exam-detail-question h2 { margin: 13px 0 18px; color: #253a62; font-size: 18px; line-height: 1.65; white-space: pre-wrap; }.exam-detail-question :deep(.el-radio-group), .exam-detail-question :deep(.el-checkbox-group) { display: grid; gap: 10px; }.exam-answer-feedback { display: flex; flex-wrap: wrap; gap: 10px 18px; margin-top: 16px; padding-top: 13px; border-top: 1px solid #edf0f3; color: #64748a; font-size: 13px; }.exam-answer-feedback svg { width: 18px; height: 18px; }.exam-answer-feedback .correct { color: #31a568; }.exam-answer-feedback .wrong { color: #d85c53; }.exam-answer-feedback em { width: 100%; color: #748198; font-style: normal; line-height: 1.6; }.exam-paper-hidden { padding: 52px 20px; border: 1px dashed #cbd6e2; color: #7d8b9d; text-align: center; }.exam-detail-footer { position: sticky; bottom: 0; display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 22px; padding: 15px 20px; border: 1px solid #dfe7f1; border-radius: 6px; background: rgba(255,255,255,.97); box-shadow: 0 -4px 16px rgba(38,70,112,.07); color: #667085; font-size: 13px; }
@media (max-width: 700px) { .exam-detail-header, .exam-detail-footer { align-items: flex-start; flex-direction: column; }.exam-countdown, .exam-result-score { align-items: flex-start; }.exam-detail-footer .el-button { width: 100%; } }
</style>
