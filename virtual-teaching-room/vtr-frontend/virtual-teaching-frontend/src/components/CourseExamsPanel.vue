<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar, DocumentChecked, Plus } from '@element-plus/icons-vue'
import { courseApi, examApi, learningResourceApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({ courseId: { type: [Number, String], required: true } })
const authStore = useAuthStore()
const router = useRouter()
const isTeacher = computed(() => authStore.role === 'TEACHER')
const loading = ref(false)
const exams = ref([])
const questions = ref([])
const editorVisible = ref(false)
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewExam = ref(null)
const editorLoading = ref(false)
const selectedQuestionIds = ref([])
const scoreMap = reactive({})
const questionChapter = ref('')
const questionSectionId = ref('')
const courseChapters = ref([])
const courseSections = ref([])

const form = reactive({ title: '', description: '', totalScore: 100, durationMinutes: 60, attemptLimit: 1, startAt: '', endAt: '', shuffleQuestions: false, shuffleOptions: false, showAnswerAfterSubmit: false, allowReviewAfterSubmit: false, editingId: null, status: '' })
function questionChapterName(question) {
  const section = courseSections.value.find((item) => String(item.id) === String(question?.sectionId))
  return question?.chapter || (section ? sectionChapterName(section) : '')
}
const filteredQuestions = computed(() => questions.value.filter((item) => !questionChapter.value || questionChapterName(item) === questionChapter.value))
const filteredQuestionList = computed(() => filteredQuestions.value.filter((item) => !questionSectionId.value || String(item.sectionId) === String(questionSectionId.value)))
const chapterOptions = computed(() => [...new Set([
  ...courseChapters.value.map((item) => chapterTitle(item)),
  ...questions.value.map((item) => item.chapter)
].filter(Boolean))])
const sectionOptions = computed(() => courseSections.value.filter((item) => !questionChapter.value || sectionBelongsToChapter(item, questionChapter.value)))
const selectedCount = computed(() => selectedQuestionIds.value.length)
const selectedQuestionIdSet = computed(() => new Set(selectedQuestionIds.value.map((id) => String(id))))
const questionTypeLabels = { SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', JUDGMENT: '判断题', FILL: '填空题', TEXT: '简答题', PROGRAMMING: '编程题' }

function statusInfo(value) { return { DRAFT: ['草稿', 'info'], PUBLISHED: ['已发布', 'success'], ARCHIVED: ['已归档', 'warning'] }[value] || ['未知', 'info'] }
function typeLabel(value) { return questionTypeLabels[value] || '题目' }
function formatDate(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '未设置' }
function chapterTitle(chapter) { return chapter?.title || chapter?.name || chapter?.chapterTitle || '' }
function sectionChapterName(section) {
  const linkedChapter = courseChapters.value.find((chapter) => String(chapter.chapterId || chapter.id) === String(section?.chapterId))
  return section?.chapterTitle || section?.chapterName || section?.chapter || chapterTitle(linkedChapter) || String(section?.chapterId || '')
}
function sectionBelongsToChapter(section, chapterValue) {
  const linkedChapter = courseChapters.value.find((chapter) => String(chapter.chapterId || chapter.id) === String(section?.chapterId))
  return sectionChapterName(section) === chapterValue
    || String(section?.chapterId || '') === String(chapterValue)
    || chapterTitle(linkedChapter) === chapterValue
}
function sectionLabel(section) { return [sectionChapterName(section), section.title || section.name || '未命名小节'].filter(Boolean).join(' · ') }
function resetForm() { Object.assign(form, { title: '', description: '', totalScore: 100, durationMinutes: 60, attemptLimit: 1, startAt: '', endAt: '', shuffleQuestions: false, shuffleOptions: false, showAnswerAfterSubmit: false, allowReviewAfterSubmit: false, editingId: null, status: '' }); selectedQuestionIds.value = []; Object.keys(scoreMap).forEach((key) => delete scoreMap[key]); questionChapter.value = ''; questionSectionId.value = '' }
function isQuestionSelected(id) { return selectedQuestionIdSet.value.has(String(id)) }
function updateQuestionSelection(id, checked) {
  const current = selectedQuestionIds.value.filter((item) => String(item) !== String(id))
  if (checked) {
    current.push(id)
    if (scoreMap[id] === undefined) scoreMap[id] = 10
  }
  selectedQuestionIds.value = current
}

async function load() {
  if (!props.courseId) return
  loading.value = true
  try {
    const [examList, chapterList, sectionList] = await Promise.all([examApi.list({ courseId: Number(props.courseId) }), courseApi.chapters(props.courseId), courseApi.sections(props.courseId)])
    exams.value = examList || []
    courseChapters.value = Array.isArray(chapterList) ? chapterList : (chapterList?.list || [])
    courseSections.value = Array.isArray(sectionList) ? sectionList : (sectionList?.list || [])
    if (isTeacher.value) questions.value = (await learningResourceApi.questions({ courseId: Number(props.courseId) }) || []).filter((item) => item.status === 'PUBLISHED')
  } catch (error) { ElMessage.error(error?.message || '考试加载失败') } finally { loading.value = false }
}

async function openEditor(item = null) {
  resetForm()
  if (item) {
    editorLoading.value = true
    try {
      const detail = await examApi.detail(item.id)
      Object.assign(form, { ...detail, editingId: detail.id, startAt: detail.startAt ? String(detail.startAt).slice(0, 19) : '', endAt: detail.endAt ? String(detail.endAt).slice(0, 19) : '' })
      selectedQuestionIds.value = (detail.questions || []).map((question) => question.id)
      ;(detail.questions || []).forEach((question) => { scoreMap[question.id] = question.score || 10 })
    } catch (error) { ElMessage.error(error?.message || '考试详情加载失败'); return } finally { editorLoading.value = false }
  }
  editorVisible.value = true
}

async function openPreview(item) {
  previewLoading.value = true
  try {
    previewExam.value = await examApi.detail(item.id)
    previewVisible.value = true
  } catch (error) {
    ElMessage.error(error?.message || '试卷详情加载失败')
  } finally {
    previewLoading.value = false
  }
}

async function save() {
  if (!form.title.trim()) return ElMessage.warning('请填写考试名称')
  if (!selectedCount.value) return ElMessage.warning('请至少从题库选择一道题目')
  const payload = { courseId: Number(props.courseId), title: form.title, description: form.description, totalScore: Number(form.totalScore), durationMinutes: Number(form.durationMinutes), attemptLimit: Number(form.attemptLimit), startAt: form.startAt || null, endAt: form.endAt || null, shuffleQuestions: form.shuffleQuestions, shuffleOptions: form.shuffleOptions, showAnswerAfterSubmit: form.showAnswerAfterSubmit, allowReviewAfterSubmit: form.allowReviewAfterSubmit, questions: selectedQuestionIds.value.map((id) => ({ questionId: id, score: Number(scoreMap[id] || 10) })) }
  try { if (form.editingId) await examApi.update(form.editingId, payload); else await examApi.create(payload); ElMessage.success(form.editingId ? '考试草稿已保存' : '考试草稿已创建'); editorVisible.value = false; await load() } catch (error) { ElMessage.error(error?.message || '考试保存失败') }
}
async function publish(item) { try { await ElMessageBox.confirm('发布后学生可以在开放时间内参加考试。', '确认发布考试', { type: 'warning' }); await examApi.publish(item.id); ElMessage.success('考试已发布'); await load() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '考试发布失败') } }
async function archive(item) { try { await ElMessageBox.confirm('归档后学生端将不再展示该考试。', '确认归档考试', { type: 'warning' }); await examApi.archive(item.id); ElMessage.success('考试已归档'); await load() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '考试归档失败') } }
async function remove(item) { try { await ElMessageBox.confirm('删除后无法恢复该考试草稿，确认删除吗？', '删除考试草稿', { type: 'warning' }); await examApi.remove(item.id); ElMessage.success('考试草稿已删除'); await load() } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '考试删除失败') } }

async function start(item) {
  await router.push({ name: 'exam-detail', params: { courseId: props.courseId, examId: item.id }, query: { courseId: props.courseId } })
}
watch(() => props.courseId, load, { immediate: true })
watch(questionChapter, (chapter) => {
  if (questionSectionId.value && !courseSections.value.some((section) => String(section.id) === String(questionSectionId.value) && sectionBelongsToChapter(section, chapter))) {
    questionSectionId.value = ''
  }
})
</script>

<template>
  <section class="course-exams-panel" v-loading="loading">
    <div v-if="isTeacher" class="exam-toolbar">
      <el-button type="primary" :icon="Plus" @click="openEditor()">发布考试</el-button>
    </div>

    <div v-if="exams.length" class="exam-card-list">
      <article v-for="item in exams" :key="item.id" class="exam-card">
        <div class="exam-card-icon"><Calendar :size="22" /></div>
        <div class="exam-card-main"><div class="exam-card-meta"><el-tag size="small" :type="statusInfo(item.status)[1]">{{ statusInfo(item.status)[0] }}</el-tag><span>{{ item.questionCount }} 道题</span><span>{{ item.totalScore }} 分</span><span>{{ item.durationMinutes }} 分钟</span><span v-if="item.startAt">{{ formatDate(item.startAt) }} 开始</span></div><h4>{{ item.title }}</h4><p>{{ item.description || '暂无考试说明' }}</p></div>
        <div class="exam-card-actions"><el-button v-if="!isTeacher" type="primary" @click="start(item)">{{ item.completed ? '查看详情' : '进入考试' }}</el-button><span v-if="!isTeacher && item.completed" class="exam-score">{{ item.lastScore ?? 0 }} / {{ item.totalScore }} 分</span><template v-if="isTeacher"><el-button link type="primary" @click="item.status === 'DRAFT' ? openEditor(item) : openPreview(item)">{{ item.status === 'DRAFT' ? '查看/编辑' : '查看试卷' }}</el-button><el-button v-if="item.status === 'DRAFT'" link type="success" @click="publish(item)">发布</el-button><el-button v-if="item.status === 'DRAFT'" link type="danger" @click="remove(item)">删除</el-button><el-button v-if="item.status === 'PUBLISHED'" link type="warning" @click="archive(item)">归档</el-button></template></div>
      </article>
    </div>
    <div v-else class="exam-empty"><div class="exam-empty-icon"><DocumentChecked :size="24" /></div><strong>{{ isTeacher ? '还没有课程考试' : '当前课程暂无考试' }}</strong><span>{{ isTeacher ? '考试可以从课程题库选题，设置时间后再发布给学生。' : '教师发布考试后，考试内容会显示在这里。' }}</span><el-button v-if="isTeacher" type="primary" plain @click="openEditor()">创建第一场考试</el-button></div>

    <el-dialog v-model="editorVisible" :title="form.editingId ? (form.status === 'DRAFT' ? '编辑考试草稿' : '查看考试') : '发布课程考试'" width="min(960px, 94vw)">
      <div v-loading="editorLoading">
        <el-form label-position="top"><div class="exam-form-grid"><el-form-item label="考试名称" required><el-input v-model="form.title" placeholder="例如：数据结构期中考试" /></el-form-item><el-form-item label="总分"><el-input-number v-model="form.totalScore" :min="1" :max="1000" /></el-form-item><el-form-item label="答题时长（分钟）"><el-input-number v-model="form.durationMinutes" :min="1" :max="600" /></el-form-item><el-form-item label="最多提交次数"><el-input-number v-model="form.attemptLimit" :min="1" :max="20" /></el-form-item><el-form-item label="开始时间"><el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="立即开放" /></el-form-item><el-form-item label="结束时间"><el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="不设置结束时间" /></el-form-item></div><el-form-item label="考试说明"><el-input v-model="form.description" type="textarea" :rows="2" placeholder="告诉学生考试范围、注意事项等" /></el-form-item><div class="exam-settings"><el-checkbox v-model="form.shuffleQuestions">题目乱序</el-checkbox><el-checkbox v-model="form.shuffleOptions">选项乱序</el-checkbox><el-checkbox v-model="form.showAnswerAfterSubmit">提交后显示客观题答案</el-checkbox><el-checkbox v-model="form.allowReviewAfterSubmit">允许学生考后查看试卷</el-checkbox></div></el-form>
        <div class="exam-picker-heading"><div><strong>从题库选题</strong><span>已选 {{ selectedCount }} 题 · 仅显示本课程已发布题目</span></div><div class="exam-picker-filters"><el-select v-model="questionChapter" clearable placeholder="按章节筛选" style="width: 180px"><el-option v-for="chapter in chapterOptions" :key="chapter" :label="chapter" :value="chapter" /></el-select><el-select v-model="questionSectionId" clearable placeholder="按小节筛选" style="width: 210px"><el-option v-for="section in sectionOptions" :key="section.id" :label="sectionLabel(section)" :value="section.id" /></el-select></div></div>
        <div v-if="filteredQuestionList.length" class="exam-question-picker"><div v-for="question in filteredQuestionList" :key="question.id" class="exam-question-row"><el-checkbox :model-value="isQuestionSelected(question.id)" @change="(checked) => updateQuestionSelection(question.id, checked)">{{ question.title }}</el-checkbox><div class="exam-question-row-meta"><el-tag size="small" effect="plain">{{ typeLabel(question.questionType) }}</el-tag><span>{{ sectionLabel(courseSections.find((section) => String(section.id) === String(question.sectionId)) || { chapter: question.chapter }) }}</span><el-input-number v-if="isQuestionSelected(question.id)" v-model="scoreMap[question.id]" :min="1" :max="100" size="small" controls-position="right" /><span v-else class="muted">未选</span></div></div></div><div v-else class="exam-picker-empty">当前筛选条件下没有已发布题目，请调整章节/小节或先到“题目库”创建题目。</div>
      </div>
      <template #footer><el-button @click="editorVisible = false">关闭</el-button><el-button v-if="!form.editingId || form.status === 'DRAFT'" type="primary" @click="save">保存考试草稿</el-button></template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="查看试卷" width="min(860px, 94vw)" destroy-on-close>
      <div v-loading="previewLoading" class="exam-paper-preview">
        <template v-if="previewExam">
          <section class="exam-paper-meta"><h3>{{ previewExam.title }}</h3><p>{{ previewExam.questions?.length || 0 }} 道题 · {{ previewExam.totalScore }} 分 · {{ previewExam.durationMinutes }} 分钟</p><p v-if="previewExam.description">{{ previewExam.description }}</p></section>
          <article v-for="(question, index) in previewExam.questions || []" :key="question.id" class="exam-paper-question"><div><b>第 {{ index + 1 }} 题</b><el-tag size="small" effect="plain">{{ typeLabel(question.questionType) }}</el-tag><span>{{ question.score }} 分</span></div><h4>{{ question.stem }}</h4><ul v-if="question.options"><li v-for="option in String(question.options).split(/\r?\n/).filter(Boolean)" :key="option">{{ option }}</li></ul><div class="exam-paper-answer"><strong>参考答案：</strong>{{ question.referenceAnswer || '暂无' }}</div><p v-if="question.analysis"><strong>解析：</strong>{{ question.analysis }}</p></article>
        </template>
      </div>
      <template #footer><el-button @click="previewVisible = false">关闭</el-button></template>
    </el-dialog>

  </section>
</template>

<style scoped>
.course-exams-panel { min-height: 380px; }.exam-toolbar { display: flex; align-items: center; justify-content: flex-end; gap: 16px; margin-bottom: 18px; }.exam-toolbar-title { display: flex; align-items: center; gap: 8px; color: #263b53; font-size: 18px; font-weight: 700; }.exam-toolbar-title :deep(svg) { width: 18px; height: 18px; color: #5d96f7; }.exam-toolbar p { margin: 6px 0 0; color: #78869a; font-size: 13px; }.exam-card-list { display: grid; gap: 12px; }.exam-card { display: flex; align-items: flex-start; gap: 14px; padding: 18px; border: 1px solid #e1e7ee; border-radius: 8px; background: #fff; }.exam-card-icon { display: grid; width: 42px; height: 42px; place-items: center; flex: 0 0 42px; border-radius: 8px; background: #edf4ff; color: #5d96f7; }.exam-card-icon :deep(svg) { width: 22px; height: 22px; }.exam-card-main { min-width: 0; flex: 1; }.exam-card-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; color: #78869a; font-size: 12px; }.exam-card h4 { margin: 9px 0 5px; color: #2d4057; font-size: 16px; }.exam-card p { margin: 0; color: #69798c; font-size: 13px; line-height: 1.6; }.exam-card-actions { display: flex; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 4px; flex: 0 0 auto; }.exam-score { color: #238353; font-size: 13px; font-weight: 700; }.exam-empty { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 9px; min-height: 280px; padding: 32px 20px; border: 1px solid #dbe3ec; color: #728095; text-align: center; }.exam-empty strong { color: #34485e; }.exam-empty span { max-width: 430px; font-size: 13px; line-height: 1.6; }.exam-empty-icon { display: grid; width: 48px; height: 48px; place-items: center; border-radius: 12px; background: #eff5ff; color: #78a5e8; }.exam-empty-icon :deep(svg) { width: 24px; height: 24px; }.exam-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }.exam-form-grid :deep(.el-input-number), .exam-form-grid :deep(.el-date-editor) { width: 100%; }.exam-settings { display: flex; flex-wrap: wrap; gap: 18px; margin-bottom: 20px; }.exam-picker-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 6px 0 12px; padding-top: 16px; border-top: 1px solid #edf0f4; }.exam-picker-heading strong, .exam-picker-heading span { display: block; }.exam-picker-heading strong { color: #2d4057; }.exam-picker-heading span { margin-top: 4px; color: #8a98a9; font-size: 12px; }.exam-question-picker { display: grid; gap: 8px; max-height: 330px; overflow: auto; padding-right: 4px; }.exam-question-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 12px; border: 1px solid #edf0f4; }.exam-question-row :deep(.el-checkbox) { min-width: 0; }.exam-question-row :deep(.el-checkbox__label) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.exam-question-row-meta { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; color: #8a98a9; font-size: 12px; }.exam-question-row-meta :deep(.el-input-number) { width: 94px; }.muted, .exam-picker-empty { color: #9aa7b6; font-size: 13px; }.exam-picker-empty { padding: 28px; border: 1px solid #dbe2ea; text-align: center; }.exam-run-summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; background: #f7faff; color: #64748a; font-size: 13px; }.exam-run-summary strong { color: #3c5570; white-space: nowrap; }.exam-run-question { margin-top: 12px; padding: 15px; border: 1px solid #e6ebf1; border-radius: 6px; }.exam-run-question > div { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }.exam-run-question > div b { color: #5d96f7; }.exam-run-question > div strong { color: #314258; }.exam-run-question p { margin: 12px 0; color: #4d5e73; line-height: 1.7; white-space: pre-wrap; }.exam-run-question :deep(.el-checkbox-group), .exam-run-question :deep(.el-radio-group) { display: grid; gap: 9px; }.exam-result { display: flex; align-items: center; gap: 8px; margin-top: 16px; padding: 14px; background: #effaf4; color: #238353; }.exam-result span { color: #5b8d70; font-size: 13px; }
@media (max-width: 700px) { .exam-toolbar, .exam-card, .exam-picker-heading, .exam-run-summary { align-items: flex-start; flex-direction: column; }.exam-card-actions { width: 100%; justify-content: flex-start; }.exam-form-grid { grid-template-columns: 1fr; }.exam-question-row { align-items: flex-start; flex-direction: column; }.exam-question-row-meta { width: 100%; }.exam-question-row-meta span { margin-left: auto; } }
.exam-empty { min-height: 210px; }
.exam-empty-icon { width: 36px; height: 36px; border-radius: 9px; }
.exam-empty-icon :deep(svg) { width: 18px; height: 18px; }
.exam-empty,
.exam-picker-empty {
  border-style: solid;
}
.exam-paper-meta { padding-bottom: 14px; border-bottom: 1px solid #e6ebf1; }.exam-paper-meta h3 { margin: 0 0 8px; color: #283b55; }.exam-paper-meta p { margin: 4px 0; color: #738198; font-size: 13px; line-height: 1.6; }.exam-paper-question { margin-top: 14px; padding: 16px; border: 1px solid #e2e8f0; border-radius: 7px; }.exam-paper-question > div:first-child { display: flex; align-items: center; gap: 9px; color: #8090a3; font-size: 13px; }.exam-paper-question > div:first-child b { color: #4e86df; }.exam-paper-question h4 { margin: 11px 0; color: #34455b; line-height: 1.65; }.exam-paper-question ul { margin: 0 0 12px; padding-left: 20px; color: #526176; line-height: 1.8; }.exam-paper-answer { padding: 10px 12px; border-radius: 5px; background: #f3f8ff; color: #355d98; line-height: 1.65; white-space: pre-wrap; }.exam-paper-question p { margin: 10px 0 0; color: #617087; line-height: 1.7; white-space: pre-wrap; }
</style>
