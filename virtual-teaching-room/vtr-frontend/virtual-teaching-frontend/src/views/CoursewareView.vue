<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection,
  EditPen,
  VideoPlay,
  Notebook,
  Files,
  ArrowDown,
  Calendar,
  Delete,
  RefreshRight,
  Plus,
  MoreFilled,
  List,
  DataAnalysis,
  DocumentDelete,
  UploadFilled,
  UserFilled,
  MagicStick
} from '@element-plus/icons-vue'
import { academicClassApi, aiApi, chapterQuizApi, classroomApi, courseApi, coursewareApi, examApi, learningResourceApi, moderationApi, schoolApi, uploadApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { coursewareVisibilityOptions, targetAudienceOptions } from '@/config/options'
import { formatDateTime, formatFileSize, optionLabel, resolveAssetUrl } from '@/utils/format'
import aiHanfuAssistant from '@/assets/ai-hanfu-assistant.png'
import answerCloud from '@/assets/answer-cloud.png'
import teachingHomeBackground from '@/assets/teaching-home-background.png'
import { normalizePage } from '@/utils/page'
import KnowledgeBubbleGraph from '@/components/KnowledgeBubbleGraph.vue'
import TeachingVideoPanel from '@/components/TeachingVideoPanel.vue'
import QuestionBankPanel from '@/components/QuestionBankPanel.vue'
import CourseQuestionLibraryPanel from '@/components/CourseQuestionLibraryPanel.vue'
import CourseExamsPanel from '@/components/CourseExamsPanel.vue'
import ImperialExamPanel from '@/components/ImperialExamPanel.vue'
import AssignmentsView from '@/views/AssignmentsView.vue'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitLoading = ref(false)
const uploadLoading = ref(false)
const joining = ref(false)
const inviteCode = ref('')
const homeShelfOpen = ref(false)
const homeQuestion = ref('')
const homeAnswer = ref('')
const homeQuestionLoading = ref(false)
const homeAnswerVisible = ref(false)
const homeAnswerBubble = ref(null)
const homeAnswerBubblePosition = reactive({ left: null, top: null })
const homeAnswerBubbleDragging = ref(false)
const homeAnswerBubbleDragState = reactive({ pointerId: null, offsetX: 0, offsetY: 0 })
const homeAnswerBubblePositionKey = 'vtr-home-answer-bubble-position'
const homeQuickQuestions = ['总结本课程重点', '讲解当前课程的核心概念', '帮我制定学习计划', '推荐适合复习的内容']
const detailVisible = ref(false)
const detail = ref(null)
const editorVisible = ref(false)
const editingId = ref(null)
const previewVisible = ref(false)
const preview = ref(null)
const outlineError = ref('')
const courseCreateVisible = ref(false)
const courseCreateLoading = ref(false)
const knowledgeMapGenerating = ref(false)
const outlineGenerating = ref(false)
const courseEditingId = ref(null)
const chapterDialogVisible = ref(false)
const chapterSaving = ref(false)
const chapterEditingId = ref(null)
const chapterDialogTitle = ref('新增章节')
const sectionDialogVisible = ref(false)
const sectionSaving = ref(false)
const sectionEditingId = ref(null)
const sectionChapterId = ref(null)
const pendingSectionChapterTitle = ref('')
const courseSection = ref('chapters')
const imperialExamOpen = ref(false)
const questionBankChapterId = ref('')
const questionBankSectionId = ref('')
const courseResources = ref([])
const courseClassrooms = ref([])
const courseStudents = ref([])
const courseStudentsTotal = ref(0)
const studentsLoading = ref(false)
const selectedStudentClassroomId = ref(null)
const studentKeyword = ref('')
const studentPage = ref(1)
const studentPageSize = ref(20)
const academicClasses = ref([])
const courseSchoolOptions = ref([])
const courseSchoolStructure = ref({ departments: [] })
const academicImportVisible = ref(false)
const academicImportId = ref(null)
const academicImportLoading = ref(false)
const mistakeItems = ref([])
const mistakesLoading = ref(false)
const studyAttempts = ref([])
const completedQuizSections = ref(new Set())
const videoProgressRecords = ref({})
const completedExams = ref([])
const studyLoading = ref(false)

const list = ref([])
const total = ref(0)
const classroomOptions = ref([])
const courses = ref([])
const chapterOptions = ref([])
const sectionOptions = ref([])
const contributorIds = ref(new Set())
const knowledgeMapDraftResources = ref([])
const outlineEditorOpen = ref(false)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  visibility: '',
  targetAudience: '',
  fileType: '',
  resourceType: '',
  courseId: null,
  chapter: '',
  classroomId: null,
  sectionId: null
})

const editor = reactive({
  title: '',
  description: '',
  fileUrl: '',
  fileName: '',
  fileType: '',
  resourceType: '',
  fileSize: null,
  visibility: 'COURSE',
  targetAudience: 'ALL',
  classroomId: null
})

const courseForm = reactive({
  schoolId: null,
  courseName: '',
  courseCode: '',
  description: '',
  semester: '',
  credits: null,
  courseCategory: '',
  teachingDepartment: '',
  coverImage: '',
  allowedAcademicClassIds: []
})

const courseCategories = [
  { label: '必修课', value: '必修课' },
  { label: '选修课', value: '选修课' },
  { label: '公共基础课', value: '公共基础课' },
  { label: '专业基础课', value: '专业基础课' },
  { label: '专业核心课', value: '专业核心课' },
  { label: '专业选修课', value: '专业选修课' },
  { label: '专业拓展课', value: '专业拓展课' },
  { label: '实践课', value: '实践课' },
  { label: '通识教育课', value: '通识教育课' },
  { label: '其他', value: '其他' }
]
const semesters = computed(() => {
  const now = new Date()
  const startYear = now.getMonth() >= 7 ? now.getFullYear() : now.getFullYear() - 1
  return [
    `${startYear}-${startYear + 1} 学年第一学期`,
    `${startYear}-${startYear + 1} 学年第二学期`,
    `${startYear - 1}-${startYear} 学年第一学期`,
    `${startYear - 1}-${startYear} 学年第二学期`
  ]
})
const courseDepartmentOptions = computed(() => courseSchoolStructure.value.departments || [])

const chapterForm = reactive({
  title: '',
  sortOrder: null
})

const sectionForm = reactive({
  title: '',
  subtitle: '',
  description: '',
  sortOrder: null
})

const canManage = computed(() => authStore.isTeacher)
const canManageCourse = (course) => authStore.isAdmin
  || String(course?.createdBy) === String(authStore.user?.id)
const canManageChapters = computed(() => authStore.isAdmin || (authStore.isTeacher && Boolean(activeCourse.value)
  && (String(activeCourse.value.createdBy) === String(authStore.user?.id)
    || contributorIds.value.has(Number(authStore.user?.id)))))
const canEditResource = (row) => !authStore.isAdmin && (String(row?.teacherId) === String(authStore.user?.id)
  || contributorIds.value.has(Number(authStore.user?.id)))
const canDeleteResource = (row) => (authStore.isAdmin && row?.resourceType === 'knowledge-map')
  || (!authStore.isAdmin && String(row?.teacherId) === String(authStore.user?.id))

const resourceTypes = [
  { id: 'assignments', title: '小节测验', icon: EditPen, tone: 'gold' },
  { id: 'teaching-video', title: '教学视频', icon: VideoPlay, tone: 'blue' },
  { id: 'teaching-courseware', title: '教学课件', icon: Files, tone: 'gold' }
]

const activeCourse = computed(() => courses.value.find((item) => String(item.id) === String(route.query.courseId)) || null)
const homeCourseId = ref(null)
const homeCourse = computed(() => courses.value.find((item) => String(item.id) === String(homeCourseId.value)) || courses.value[0] || null)
const homeChapters = ref([])
const homeChapter = computed(() => homeChapters.value[0] || null)
const homeSectionCount = computed(() => homeChapters.value.reduce((total, chapter) => total + (chapter.sections?.length || 0), 0))
const homeSelectedChapterId = ref(null)
const homeKnowledgePoints = ref([])
const homeKnowledgeLoading = ref(false)
const homeKnowledgeLoaded = ref(false)
const homeSelectedSectionKey = ref(null)
const homeSelectedPoint = ref('')
const homeSelectedKnowledgeKey = ref('')
const selectedHomeChapter = computed(() => {
  const chapter = homeChapters.value.find((item) => String(item.chapterId || item.id) === String(homeSelectedChapterId.value)) || homeChapters.value[0] || null
  if (!chapter) return null
  const section = (chapter.sections || []).find((item, index) => String(item.id || `${chapter.chapterId || chapter.id}-${index}`) === String(homeSelectedSectionKey.value))
  return section ? { ...chapter, title: section.title || chapter.title, name: section.title || chapter.name } : chapter
})
const selectedHomeSection = computed(() => {
  const chapter = selectedHomeChapter.value
  if (!chapter) return null
  const chapterKey = String(chapter.chapterId || chapter.id)
  return (chapter.sections || []).find((section, index) => String(section.id || `${chapterKey}-${index}`) === String(homeSelectedSectionKey.value)) || chapter.sections?.[0] || null
})
const selectedHomeKnowledgePoints = computed(() => {
  const chapter = selectedHomeChapter.value
  const section = selectedHomeSection.value
  if (!chapter || !section) return []
  const chapterKey = String(chapter.chapterId || chapter.id)
  const sectionIndex = (chapter.sections || []).indexOf(section)
  const sectionKey = String(section.id || `${chapterKey}-${sectionIndex}`)
  const points = homeKnowledgePoints.value
    .filter((item) => String(item.chapterId || '') === chapterKey && String(item.sectionId || '') === sectionKey)
    .flatMap((item) => item.points || [])
  const uniquePoints = [...new Set(points.map((point) => String(point).trim()).filter(Boolean))]
  return uniquePoints.length ? uniquePoints : [section.title || '当前小节核心知识点']
})
const selectedHomeKnowledge = computed(() => {
  const point = homeSelectedPoint.value || homeSelectedKnowledgeKey.value
  return point ? { section: selectedHomeSection.value, point } : null
})
const selectedHomeKnowledgeGroups = computed(() => selectedHomeKnowledgePoints.value.map((point, index) => ({
  section: selectedHomeSection.value,
  index,
  sectionKey: point,
  points: [point]
})))
const isCourseHome = computed(() => !route.params.resourceType && !activeCourse.value)
const isCourseOverview = computed(() => !route.params.resourceType && Boolean(activeCourse.value) && !route.query.chapter)
const isChapterOverview = computed(() => !route.params.resourceType && Boolean(activeCourse.value) && Boolean(route.query.chapter))
const chapters = computed(() => chapterOptions.value)
const courseResourceTypes = computed(() => [...new Set(courseResources.value.map((item) => item.resourceType).filter(Boolean))])
const knowledgeMapResources = computed(() => courseResources.value.filter((item) => item.resourceType === 'knowledge-map'))
const chapterSummaries = computed(() => chapters.value.map((chapter) => {
  const resources = courseResources.value.filter((item) => item.resourceType !== 'knowledge-map' && String(item.chapter || '').trim() === chapter.id)
  const sections = sectionOptions.value.filter((section) => String(section.chapterId) === String(chapter.chapterId))
  const labels = [...new Set(resources.map((item) => {
    const labelMap = {
      'teaching-outline': '教学大纲',
      'teaching-video': '教学视频',
      'teaching-courseware': '教学课件'
    }
    return labelMap[item.resourceType] || (item.resourceType === 'assignments' ? '小节测验' : '课程资源')
  }))]
  const attempted = studyAttempts.value.filter((item) => item.chapter === chapter.id)
  if (chapter.questionCount) labels.push(`题库 ${chapter.questionCount} 题`)
  const practicedQuestions = new Set(attempted.map((item) => item.questionId).filter(Boolean)).size
  const quizCount = sections.filter((section) => completedQuizSections.value.has(Number(section.id))).length
  const videoCount = resources.filter((item) => item.resourceType === 'teaching-video' && videoProgressRecords.value[item.id]?.completed).length
  return {
    ...chapter,
    sections,
    resourceCount: resources.length,
    resourceLabels: [...new Set(labels)],
    attemptCount: attempted.length,
    quizCount,
    videoCount,
    studyCount: attempted.length + quizCount + videoCount,
    practiceRate: chapter.questionCount ? Math.min(100, Math.round((practicedQuestions / chapter.questionCount) * 100)) : 0
  }
}))
const studyMetrics = computed(() => {
  const total = studyAttempts.value.length
  const correct = studyAttempts.value.filter((item) => item.correct).length
  const videoProgress = Object.values(videoProgressRecords.value)
  const learnedChapterNames = new Set(studyAttempts.value.map((item) => item.chapter).filter(Boolean))
  completedQuizSections.value.forEach((sectionId) => {
    const section = sectionOptions.value.find((item) => Number(item.id) === Number(sectionId))
    const chapter = chapters.value.find((item) => String(item.chapterId || item.id) === String(section?.chapterId))
    if (chapter?.id) learnedChapterNames.add(chapter.id)
  })
  courseResources.value
    .filter((item) => item.resourceType === 'teaching-video' && videoProgressRecords.value[item.id]?.completed)
    .map((item) => item.chapter)
    .filter(Boolean)
    .forEach((chapter) => learnedChapterNames.add(chapter))
  return {
    attempts: total,
    accuracy: total ? Math.round((correct / total) * 100) : 0,
    learnedChapters: learnedChapterNames.size,
    mistakes: studyAttempts.value.filter((item) => !item.correct).length,
    quizCount: completedQuizSections.value.size,
    videoCount: videoProgress.filter((item) => item?.completed).length,
    examCount: completedExams.value.length,
    onlineSessions: total + completedQuizSections.value.size + completedExams.value.length + videoProgress.filter((item) => Number(item?.watchedSeconds) > 0).length
  }
})
const maxChapterStudyCount = computed(() => Math.max(1, ...chapterSummaries.value.map((chapter) => chapter.studyCount || 0)))
const selectedChapter = computed(() => chapters.value.find((item) => item.id === route.query.chapter) || null)
const selectedSection = computed(() => sectionOptions.value.find((item) => String(item.id) === String(route.query.sectionId)) || null)
const selectedQuestionBankChapter = computed(() => chapterSummaries.value.find((item) => String(item.id) === String(questionBankChapterId.value)) || null)
const selectedQuestionBankSection = computed(() => {
  const chapter = selectedQuestionBankChapter.value || chapterSummaries.value[0] || null
  if (!chapter) return null
  return chapter.sections.find((item) => String(item.id) === String(questionBankSectionId.value)) || chapter.sections[0] || null
})
const sectionCompleted = (section) => authStore.isStudent && (studyAttempts.value.some((item) => String(item.sectionId) === String(section.id)) || completedQuizSections.value.has(Number(section.id)))
const selectedResource = computed(() =>
  resourceTypes.find((item) => item.id === route.params.resourceType) || null
)
const isTeachingOutline = computed(() => route.params.resourceType === 'teaching-outline')
const isKnowledgeMap = computed(() => route.params.resourceType === 'knowledge-map')
const isTeachingVideo = computed(() => route.params.resourceType === 'teaching-video')
const isQuestionBank = computed(() => route.params.resourceType === 'assignments')
const isTeachingCourseware = computed(() => route.params.resourceType === 'teaching-courseware')
const outlineResource = computed(() => list.value[0] || null)
const knowledgeMapResource = computed(() => list.value[0] || null)
const outlinePreviewResource = computed(() => outlineResource.value)
const knowledgeMapEditorResources = computed(() => knowledgeMapDraftResources.value)
const canReviewKnowledgeMap = computed(() => authStore.isAdmin && isKnowledgeMap.value && knowledgeMapResource.value?.visibility === 'PUBLIC')
const canReviewOutline = computed(() => authStore.isAdmin && isTeachingOutline.value && outlineResource.value?.visibility === 'PUBLIC')
const canReviewOutlineMindMap = computed(() => false)
const deletableKnowledgeMaps = computed(() => list.value.filter((resource) => canDeleteResource(resource)))
const canBuildKnowledgeMap = computed(() => authStore.role === 'TEACHER')
// 空大纲时允许教师上传，同时放开管理员：管理员也能上传/AI 生成教学大纲。
const canCreateOutline = computed(() => canManage.value || authStore.isAdmin)
const canGenerateOutline = computed(() => (canBuildKnowledgeMap.value || authStore.isAdmin) && canManageChapters.value)
// 教学大纲和知识图谱是两类独立资源：教学大纲没有内容时只显示上传/空状态，
// 结构化节点编辑器只属于知识图谱，避免从侧边栏点击教学大纲时误进入图谱编辑页。
const showKnowledgeMapEditor = computed(() => canBuildKnowledgeMap.value
  && isKnowledgeMap.value
  && (outlineEditorOpen.value || !knowledgeMapResource.value))

function chapterNumber(chapter, index = 0) {
  const value = Number(chapter?.sortOrder)
  return Number.isInteger(value) && value > 0 && value < 2147483647 ? value : index + 1
}

function chapterLabel(chapter, index = 0) {
  const number = chapterNumber(chapter, index)
  const digits = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']
  let chinese = String(number)
  if (number < 10) chinese = digits[number]
  else if (number < 20) chinese = `十${number === 10 ? '' : digits[number - 10]}`
  else if (number < 100) chinese = `${digits[Math.floor(number / 10)]}十${number % 10 ? digits[number % 10] : ''}`
  return `第${chinese}章`
}

function splitOutlineContent(value) {
  return String(value || '')
    .split(/[。；;！!？?\n]+/)
    .flatMap((sentence) => sentence.split(/[，,、]+/))
    .map((item) => item.trim())
    .filter((item) => item.length >= 2)
    .slice(0, 8)
}

const outlineMindMap = computed(() => {
  const row = outlinePreviewResource.value
  if (!row) return []
  try {
    const config = JSON.parse(row.description || '')
    if (Array.isArray(config?.nodes)) {
      const nodes = config.nodes.map((node) => ({ ...node, id: String(node.id), parentId: node.parentId == null ? null : String(node.parentId) }))
      const children = (parentId) => nodes.filter((node) => node.parentId === parentId)
      const flattenChildren = (parentId, depth = 0) => children(parentId).flatMap((node) => [
        `${'　'.repeat(depth)}${depth ? '↳ ' : ''}${node.name}`,
        ...flattenChildren(node.id, depth + 1)
      ])
      const root = nodes.find((node) => node.parentId == null) || nodes[0]
      return children(root?.id).map((branch) => ({
        label: branch.name,
        items: flattenChildren(branch.id)
      }))
    }
  } catch {
    // Existing text-only outlines continue to use the legacy presentation.
  }
  const content = splitOutlineContent(row.description)
  return [
    { label: '课程定位', items: [activeCourse.value?.courseName || '课程资源', '课程教学大纲'] },
    { label: '教学内容', items: content.length ? content : [row.title || '课程教学大纲'] },
    { label: '课程资料', items: [row.title || '课程教学大纲', '课程教学目标与学习重点'] }
  ]
})

function openCourse(course) {
  router.push({ path: '/courseware', query: { courseId: course.id } })
}

const homeAnswerBubbleStyle = computed(() => {
  if (homeAnswerBubblePosition.left == null || homeAnswerBubblePosition.top == null) return {}
  return {
    position: 'fixed',
    left: `${homeAnswerBubblePosition.left}px`,
    top: `${homeAnswerBubblePosition.top}px`,
    right: 'auto',
    bottom: 'auto'
  }
})

function clampHomeAnswerBubblePosition(left, top) {
  const bubble = homeAnswerBubble.value
  if (!bubble || typeof window === 'undefined') return
  const rect = bubble.getBoundingClientRect()
  const maxLeft = Math.max(0, window.innerWidth - rect.width)
  const maxTop = Math.max(0, window.innerHeight - rect.height)
  homeAnswerBubblePosition.left = Math.min(Math.max(0, left), maxLeft)
  homeAnswerBubblePosition.top = Math.min(Math.max(0, top), maxTop)
}

function persistHomeAnswerBubblePosition() {
  if (typeof window === 'undefined' || homeAnswerBubblePosition.left == null || homeAnswerBubblePosition.top == null) return
  window.localStorage.setItem(homeAnswerBubblePositionKey, JSON.stringify({
    left: homeAnswerBubblePosition.left,
    top: homeAnswerBubblePosition.top
  }))
}

function loadHomeAnswerBubblePosition() {
  if (typeof window === 'undefined') return
  try {
    const saved = JSON.parse(window.localStorage.getItem(homeAnswerBubblePositionKey) || 'null')
    if (Number.isFinite(saved?.left) && Number.isFinite(saved?.top)) {
      homeAnswerBubblePosition.left = saved.left
      homeAnswerBubblePosition.top = saved.top
    }
  } catch {
    window.localStorage.removeItem(homeAnswerBubblePositionKey)
  }
}

function stopHomeAnswerBubbleDrag() {
  if (typeof window !== 'undefined') {
    window.removeEventListener('pointermove', moveHomeAnswerBubble)
    window.removeEventListener('pointerup', stopHomeAnswerBubbleDrag)
    window.removeEventListener('pointercancel', stopHomeAnswerBubbleDrag)
  }
  const bubble = homeAnswerBubble.value
  if (bubble && homeAnswerBubbleDragState.pointerId != null) {
    try { bubble.releasePointerCapture?.(homeAnswerBubbleDragState.pointerId) } catch { /* pointer already released */ }
  }
  if (homeAnswerBubbleDragging.value) persistHomeAnswerBubblePosition()
  homeAnswerBubbleDragging.value = false
  homeAnswerBubbleDragState.pointerId = null
}

function moveHomeAnswerBubble(event) {
  if (!homeAnswerBubbleDragging.value || event.pointerId !== homeAnswerBubbleDragState.pointerId) return
  const bubble = homeAnswerBubble.value
  if (!bubble) return
  const nextLeft = event.clientX - homeAnswerBubbleDragState.offsetX
  const nextTop = event.clientY - homeAnswerBubbleDragState.offsetY
  clampHomeAnswerBubblePosition(nextLeft, nextTop)
  event.preventDefault()
}

function startHomeBubbleDrag(event) {
  if (!homeAnswerBubble.value || homeAnswerBubbleDragging.value) return
  if (event.button !== undefined && event.button !== 0) return
  if (event.target?.closest?.('.studio-home-answer-bubble-content')) return
  const rect = homeAnswerBubble.value.getBoundingClientRect()
  homeAnswerBubblePosition.left = rect.left
  homeAnswerBubblePosition.top = rect.top
  homeAnswerBubbleDragState.pointerId = event.pointerId
  homeAnswerBubbleDragState.offsetX = event.clientX - rect.left
  homeAnswerBubbleDragState.offsetY = event.clientY - rect.top
  homeAnswerBubbleDragging.value = true
  homeAnswerBubble.value.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', moveHomeAnswerBubble)
  window.addEventListener('pointerup', stopHomeAnswerBubbleDrag)
  window.addEventListener('pointercancel', stopHomeAnswerBubbleDrag)
  event.preventDefault()
}

async function selectHomeCourse(course) {
  if (!course?.id) return
  homeCourseId.value = course.id
  homeShelfOpen.value = false
  homeAnswer.value = ''
  homeAnswerVisible.value = false
  await loadHomeChapters(course.id)
}

function selectHomeChapter(chapter) {
  homeSelectedChapterId.value = chapter?.chapterId || chapter?.id || null
  const firstSection = chapter?.sections?.[0]
  homeSelectedSectionKey.value = firstSection?.id || (chapter ? `${chapter.chapterId || chapter.id}-0` : null)
  homeSelectedPoint.value = ''
  homeSelectedKnowledgeKey.value = ''
  homeAnswer.value = ''
  homeAnswerVisible.value = false
}

function selectHomeSection(chapter, section) {
  homeSelectedChapterId.value = chapter?.chapterId || chapter?.id || null
  const index = chapter?.sections?.indexOf(section) ?? 0
  homeSelectedSectionKey.value = section?.id || `${chapter?.chapterId || chapter?.id}-${index}`
  homeSelectedPoint.value = ''
  homeSelectedKnowledgeKey.value = ''
  homeAnswer.value = ''
  homeAnswerVisible.value = false
}

function compactHomeKnowledgePoint(value) {
  const text = String(value || '').replace(/[。；;：:，,].*$/, '').trim()
  return text.length > 12 ? `${text.slice(0, 12)}…` : text
}

function parseHomeKnowledgeReply(reply, chapters) {
  const source = String(reply || '').replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/i, '').trim()
  let parsed = null
  try { parsed = JSON.parse(source) } catch {
    const start = source.indexOf('{')
    const end = source.lastIndexOf('}')
    if (start >= 0 && end > start) {
      try { parsed = JSON.parse(source.slice(start, end + 1)) } catch { parsed = null }
    }
  }
  const rows = Array.isArray(parsed?.sections) ? parsed.sections : []
  const result = []
  rows.forEach((row) => {
    const chapter = chapters.find((item) => String(item.chapterId || item.id) === String(row?.chapterId || row?.id))
      || chapters.find((item) => String(row?.chapterTitle || row?.name || '').includes(String(item.title || item.name || '')))
    const section = chapter?.sections?.find((item) => String(item.id) === String(row.sectionId))
      || chapter?.sections?.find((item) => String(row.sectionTitle || row.title || '').includes(String(item.title || '')))
    const rawPoints = row.knowledgePoints || row.points || row.nodes || row.knowledgePoint || row.summary || row.corePoint || []
    const points = (Array.isArray(rawPoints) ? rawPoints : [rawPoints])
      .map((point) => typeof point === 'string' ? point : point?.name)
      .map((point) => compactHomeKnowledgePoint(point)).filter(Boolean).slice(0, 6)
    if (chapter && points.length) result.push({ chapterId: chapter.chapterId || chapter.id, sectionId: section?.id || row.sectionId, points })
  })
  return result
}

async function loadHomeKnowledgePoints(courseId, chapters) {
  homeKnowledgePoints.value = []
  homeKnowledgeLoaded.value = false
  if (!courseId || !chapters?.length) return
  homeKnowledgeLoading.value = true
  try {
    const directory = chapters.flatMap((chapter, chapterIndex) => (chapter.sections || []).map((section, sectionIndex) => ({
      chapterId: chapter.chapterId || chapter.id,
      chapterTitle: chapter.title || chapter.name || `第${chapterIndex + 1}章`,
      sectionId: section.id || `${chapter.chapterId || chapter.id}-${sectionIndex}`,
      sectionTitle: section.title || `第${sectionIndex + 1}节`
    })))
    const result = await aiApi.chat({
      courseId,
      chapter: chapters.map((chapter) => chapter.title || chapter.name).filter(Boolean).join('、'),
      message: `请根据本课程资料和下面给出的章节、小节名称，为每个小节提炼 3-6 个最重要、彼此独立的核心知识点。每个知识点必须是简洁短语，建议 2-8 个字，最多不超过 12 个字，不要写完整解释句。注意：每个小节对应一个独立的知识点集合，不能把不同小节的知识点混在一起。只返回一个合法 JSON 对象，不要 Markdown，不要解释文字。格式必须是：{"sections":[{"chapterId":"原值","sectionId":"原值","chapterTitle":"原值","sectionTitle":"原值","knowledgePoints":["知识点1","知识点2","知识点3"]}]}。必须覆盖目录中的每个小节；没有足够课程资料时，只能根据小节名称概括基础概念，不要编造具体数据。\n\n课程目录：\n${JSON.stringify(directory, null, 2)}`
    })
    homeKnowledgePoints.value = parseHomeKnowledgeReply(result?.reply || result?.content, chapters)
  } catch {
    homeKnowledgePoints.value = []
  } finally {
    homeKnowledgeLoaded.value = true
    homeKnowledgeLoading.value = false
  }
}

async function openHomeSection(section) {
  if (!homeCourse.value) return
  await router.push({ path: '/courseware', query: { courseId: homeCourse.value.id } })
  courseSection.value = section
  if (section === 'students') loadCourseStudents()
}

async function askHomeQuestion(prompt = homeQuestion.value) {
  const message = String(prompt || '').trim()
  if (!message || homeQuestionLoading.value) return
  if (!homeCourse.value) return ElMessage.warning('请先选择一门课程')
  homeQuestion.value = message
  homeAnswer.value = ''
  homeAnswerVisible.value = true
  homeQuestionLoading.value = true
  try {
    const result = await aiApi.chat({
      courseId: homeCourse.value.id,
      contextTitle: homeCourse.value.courseName || '当前课程',
      contextMeta: '教学中心首页快速问答',
      message
    })
    homeAnswer.value = String(
      result?.reply
      || result?.content
      || result?.answer
      || result?.data?.reply
      || result?.data?.content
      || '暂时没有得到回答，请稍后再试。'
    ).trim()
  } catch (error) {
    homeAnswer.value = error?.userMessage || error?.message || '暂时无法连接诸葛助手，请稍后再试。'
  } finally {
    homeQuestionLoading.value = false
  }
}

function closeHomeAnswerBubble() {
  homeAnswerVisible.value = false
}

async function runHomeQuickAction(action) {
  if (!homeCourse.value) return ElMessage.warning('请先选择一门课程')
  const chapterName = selectedHomeChapter.value?.title || selectedHomeChapter.value?.name || '当前章节'
  const sectionName = selectedHomeKnowledge.value?.section?.title || '当前小节'
  const knowledgePoint = selectedHomeKnowledge.value?.point || sectionName
  const actionMap = {
    explain: `请围绕“${chapterName}”中的“${sectionName}”这一小节，以及它对应的核心知识点“${knowledgePoint}”，展开讲解，给出清晰的教学说明。`,
    quiz: `请只根据“${chapterName}”中的“${sectionName}”小节和核心知识点“${knowledgePoint}”，生成一道课堂测验题，并给出答案和解析。`
  }
  if (actionMap[action]) {
    homeQuestion.value = actionMap[action]
    await askHomeQuestion()
    return
  }
  if (action === 'lesson-plan' || action === 'outline') {
    await router.push({ path: '/courseware/teaching-outline', query: { courseId: homeCourse.value.id } })
    return
  }
  if (action === 'note') {
    ElMessage.info('教研笔记入口已保留，进入课程工作台后可继续整理和保存。')
    return
  }
  await openCourse(homeCourse.value)
}

async function joinCourse(code) {
  const normalizedCode = String(code || '').trim()
  if (!normalizedCode) {
    ElMessage.warning('请输入课程代码或教学班邀请码')
    return
  }
  joining.value = true
  try {
    await classroomApi.join(normalizedCode)
    inviteCode.value = ''
    await loadCourses()
    ElMessage.success('已加入教学班，课程已添加到课程与资源')
  } catch (error) {
    ElMessage.error(error?.message || '加入课程失败')
  } finally {
    joining.value = false
  }
}

function resetCourseForm() {
  Object.assign(courseForm, {
    schoolId: authStore.user?.schoolId || null,
    courseName: '',
    courseCode: '',
    description: '',
    semester: '',
    credits: null,
    courseCategory: '',
    teachingDepartment: '',
    coverImage: '',
    allowedAcademicClassIds: []
  })
  courseSchoolStructure.value = { departments: [] }
  courseEditingId.value = null
}

function openCourseCreate() {
  resetCourseForm()
  loadCourseBaseData().catch(() => { academicClasses.value = []; courseSchoolStructure.value = { departments: [] } })
  courseCreateVisible.value = true
}

async function loadCourseBaseData(course = null) {
  const schoolId = course?.schoolId || authStore.user?.schoolId
  const schoolsForCourse = await (authStore.isAdmin ? schoolApi.list() : schoolApi.active())
  courseSchoolOptions.value = schoolsForCourse || []
  const resolvedSchoolId = schoolId || schoolsForCourse?.[0]?.id
  if (!courseForm.schoolId && resolvedSchoolId) courseForm.schoolId = resolvedSchoolId
  if (resolvedSchoolId) courseSchoolStructure.value = await schoolApi.academicStructure(resolvedSchoolId)
  const payload = await academicClassApi.list({ page: 1, size: 100, ...(resolvedSchoolId ? { schoolId: resolvedSchoolId } : {}) })
  academicClasses.value = normalizePage(payload).list
}

async function changeCourseSchool(schoolId) {
  courseForm.teachingDepartment = ''
  courseSchoolStructure.value = { departments: [] }
  if (schoolId) await loadCourseBaseData({ schoolId })
}

async function openCourseEdit(course) {
  courseEditingId.value = course.id
  Object.keys(courseForm).forEach((key) => {
    courseForm[key] = course[key] ?? (key === 'credits' ? null : '')
  })
  courseForm.allowedAcademicClassIds = Array.isArray(course.allowedAcademicClassIds) ? [...course.allowedAcademicClassIds] : []
  loadCourseBaseData(course).catch(() => { academicClasses.value = []; courseSchoolStructure.value = { departments: [] } })
  courseCreateVisible.value = true
}

async function uploadCourseCover(event) {
  const file = event.target.files?.[0]
  if (!file) return
  try {
    courseForm.coverImage = await uploadApi.courseCover(file)
    ElMessage.success('课程封面已上传')
  } catch (error) {
    ElMessage.error(error?.message || '课程封面上传失败')
  } finally {
    event.target.value = ''
  }
}

function resetChapterForm() {
  Object.assign(chapterForm, { title: '', sortOrder: null })
  chapterEditingId.value = null
  chapterDialogTitle.value = '新增章节'
}

function openChapterCreate() {
  resetChapterForm()
  chapterDialogVisible.value = true
}

function openChapterEdit(chapter) {
  chapterEditingId.value = chapter.chapterId
  chapterDialogTitle.value = chapter.chapterId ? '编辑章节' : '完善章节信息'
  Object.assign(chapterForm, {
    title: chapter.title || chapter.id || '',
    sortOrder: Number(chapter.sortOrder) >= 2147483647 ? null : chapter.sortOrder
  })
  chapterDialogVisible.value = true
}

function resetSectionForm(chapterId) {
  sectionChapterId.value = chapterId
  sectionEditingId.value = null
  Object.assign(sectionForm, { title: '', subtitle: '', description: '', sortOrder: null })
}

function openSectionCreate(chapter) {
  if (!chapter.chapterId) {
    pendingSectionChapterTitle.value = chapter.title || chapter.id || ''
    openChapterEdit(chapter)
    ElMessage.info('请先保存章节信息，保存后将继续新增小节')
    return
  }
  resetSectionForm(chapter.chapterId)
  sectionDialogVisible.value = true
}

function openSectionEdit(section) {
  sectionChapterId.value = section.chapterId
  sectionEditingId.value = section.id
  Object.assign(sectionForm, {
    title: section.title || '',
    subtitle: section.subtitle || '',
    description: section.description || '',
    sortOrder: section.sortOrder ?? null
  })
  sectionDialogVisible.value = true
}

async function saveChapter() {
  if (!String(chapterForm.title || '').trim()) {
    ElMessage.warning('请输入章节名称')
    return
  }
  chapterSaving.value = true
  try {
    const courseId = activeCourse.value?.id || query.courseId
    const updating = Boolean(chapterEditingId.value)
    const pendingTitle = pendingSectionChapterTitle.value
    const payload = { title: chapterForm.title.trim(), sortOrder: chapterForm.sortOrder }
    if (chapterEditingId.value) await courseApi.updateChapter(courseId, chapterEditingId.value, payload)
    else await courseApi.createChapter(courseId, payload)
    chapterDialogVisible.value = false
    await loadChapters(courseId)
    pendingSectionChapterTitle.value = ''
    if (!updating && pendingTitle) {
      const createdChapter = chapters.value.find((chapter) => chapter.title === pendingTitle && chapter.chapterId)
      if (createdChapter) {
        await nextTick()
        openSectionCreate(createdChapter)
      }
    }
    ElMessage.success(updating ? '章节已更新' : '章节信息已保存')
  } catch (error) {
    ElMessage.error(error?.message || '章节保存失败')
  } finally {
    chapterSaving.value = false
  }
}

async function saveSection() {
  if (!String(sectionForm.title || '').trim()) {
    ElMessage.warning('请输入小节名称')
    return
  }
  sectionSaving.value = true
  try {
    const courseId = activeCourse.value?.id || query.courseId
    const payload = { ...sectionForm, title: sectionForm.title.trim() }
    if (sectionEditingId.value) {
      await courseApi.updateSection(courseId, sectionChapterId.value, sectionEditingId.value, payload)
    } else {
      await courseApi.createSection(courseId, sectionChapterId.value, payload)
    }
    sectionDialogVisible.value = false
    await loadChapters(courseId)
    ElMessage.success(sectionEditingId.value ? '小节已更新' : '小节已新增')
  } catch (error) {
    ElMessage.error(error?.message || '小节保存失败')
  } finally {
    sectionSaving.value = false
  }
}

async function archiveChapter(chapter) {
  try {
    await ElMessageBox.confirm(`确认删除章节“${chapter.title || chapter.id}”？该章节下的小节、课件、练习题和小节测验将一并从课程中移除。`, '删除章节', {
      type: 'warning', confirmButtonText: '删除章节及内容', cancelButtonText: '取消'
    })
    await courseApi.archiveChapter(activeCourse.value.id, chapter.chapterId)
    await loadChapters(activeCourse.value.id)
    ElMessage.success('章节已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '章节删除失败')
  }
}

async function archiveSection(section) {
  try {
    await ElMessageBox.confirm(`确认归档小节“${section.title}”？`, '归档小节', {
      type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '取消'
    })
    await courseApi.archiveSection(activeCourse.value.id, section.chapterId, section.id)
    await loadChapters(activeCourse.value.id)
    ElMessage.success('小节已归档')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '小节归档失败')
  }
}

async function saveCourse() {
  if (!String(courseForm.courseName || '').trim()) {
    ElMessage.warning('请输入课程名称')
    return
  }
  if (!courseForm.semester) return ElMessage.warning('请选择学期')
  if (!courseForm.courseCategory) return ElMessage.warning('请选择课程类别')
  if (!courseForm.teachingDepartment) return ElMessage.warning('请选择开课院系')
  if (!courseForm.semester) return ElMessage.warning('请选择学期')
  if (!courseForm.courseCategory) return ElMessage.warning('请选择课程类别')
  if (!courseForm.teachingDepartment) return ElMessage.warning('请选择开课院系')
  courseCreateLoading.value = true
  try {
    const payload = { ...courseForm, allowedAcademicClassIds: [...courseForm.allowedAcademicClassIds], courseName: courseForm.courseName.trim() }
    if (courseEditingId.value) await courseApi.update(courseEditingId.value, payload)
    else await courseApi.create(payload)
    courseCreateVisible.value = false
    await loadCourses()
    ElMessage.success(courseEditingId.value ? '课程信息已更新' : '课程已创建')
  } catch (error) {
    ElMessage.error(error?.message || '课程创建失败')
  } finally {
    courseCreateLoading.value = false
  }
}

async function changeCourseStatus(course) {
  const archived = course.status === 'ACTIVE'
  await ElMessageBox.confirm(
    archived
      ? `确认归档课程“${course.courseName}”？归档后学生将无法继续使用，但课程资料会保留。`
      : `确认恢复课程“${course.courseName}”？恢复后课程将重新开放。`,
    archived ? '归档课程' : '恢复课程',
    { type: 'warning', confirmButtonText: archived ? '确认归档' : '确认恢复', cancelButtonText: '取消' }
  )
  if (archived) await courseApi.archive(course.id)
  else await courseApi.restore(course.id)
  await loadCourses()
  ElMessage.success(archived ? '课程已归档' : '课程已恢复')
}

async function submitKnowledgeMap(config, options = {}) {
  const aiGenerated = Boolean(options.aiGenerated)
  const row = isKnowledgeMap.value ? knowledgeMapResource.value : outlineResource.value
  try {
    if (row) {
      await coursewareApi.update(row.id, { description: JSON.stringify(config), aiGenerated })
    } else if (isKnowledgeMap.value) {
      await coursewareApi.create({
        title: `${activeCourse.value?.courseName || '课程'} · 知识图谱`,
        description: JSON.stringify(config),
        fileUrl: 'structured://knowledge-map',
        fileName: 'knowledge-map.json',
        fileType: 'json',
        fileSize: JSON.stringify(config).length,
        resourceType: 'knowledge-map',
        courseId: activeCourse.value?.id || query.courseId,
        chapter: null,
        sectionId: null,
        visibility: 'COURSE',
        targetAudience: 'ALL'
      })
    } else if (isTeachingOutline.value) {
      await coursewareApi.createStructuredOutline({
        title: `${activeCourse.value?.courseName || '课程'} · 教学大纲`,
        description: JSON.stringify(config),
        courseId: activeCourse.value?.id || query.courseId,
        chapter: null,
        sectionId: null,
        visibility: 'COURSE',
        targetAudience: 'ALL',
        aiGenerated
      })
    } else {
      ElMessage.warning('当前页面不是知识图谱')
      return
    }
    ElMessage.success(isKnowledgeMap.value ? '知识图谱已保存并直接发布；高风险内容会进入复核' : '教学大纲已保存并直接发布；高风险内容会进入复核')
    outlineEditorOpen.value = false
    await loadList()
    await loadKnowledgeMapDraft(query.courseId, query.chapter, route.query.sectionId)
  } catch (error) {
    ElMessage.error(error?.message || '提交思维导图失败')
  }
}

function parseKnowledgeMapReply(reply) {
  const text = String(reply || '').replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/i, '').trim()
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start < 0 || end <= start) throw new Error('AI 未返回可识别的知识图谱 JSON')
  const config = JSON.parse(text.slice(start, end + 1))
  if (!Array.isArray(config.nodes) || !config.nodes.length) throw new Error('AI 返回的知识图谱没有节点')
  if (!config.nodes.some((node) => node.parentId == null)) throw new Error('AI 返回的知识图谱缺少课程核心节点')
  return config
}

function chapterOutlineContext() {
  return chapters.value.map((chapter, chapterIndex) => {
    const chapterName = chapter.title || chapter.id || `第${chapterIndex + 1}章`
    const sections = sectionOptions.value
      .filter((section) => String(section.chapterId) === String(chapter.chapterId))
      .map((section, sectionIndex) => `    - ${chapterIndex + 1}.${sectionIndex + 1} ${section.title || '未命名小节'}${section.subtitle ? `（${section.subtitle}）` : ''}${section.description ? `；说明：${section.description}` : ''}`)
      .join('\n')
    return `- 第${chapterIndex + 1}章：${chapterName}${sections ? `\n${sections}` : '\n    - 暂未设置小节'}`
  }).join('\n')
}

function parseTeachingOutlineReply(reply) {
  const config = parseKnowledgeMapReply(reply)
  if (!config.nodes.some((node) => node.type === 'chapter')) {
    throw new Error('AI 返回的大纲缺少章节结构')
  }
  const nodeNames = config.nodes.map((node) => String(node.name || '').replace(/\s/g, ''))
  const missingChapter = chapters.value.find((chapter) => {
    const name = String(chapter.title || chapter.id || '').replace(/\s/g, '')
    return name && !nodeNames.some((nodeName) => nodeName.includes(name))
  })
  if (missingChapter) {
    throw new Error(`AI 返回的大纲遗漏了章节“${missingChapter.title || missingChapter.id}”`)
  }
  // 大纲必须覆盖每一个已配置的小节，避免 AI 静默丢失章节下的小节目录。
  const missingSection = sectionOptions.value.find((section) => {
    const name = String(section.title || '').replace(/\s/g, '')
    return name && !nodeNames.some((nodeName) => nodeName.includes(name))
  })
  if (missingSection) {
    throw new Error(`AI 返回的大纲遗漏了小节“${missingSection.title}”，请重新生成`)
  }
  return config
}

async function generateTeachingOutline() {
  const courseId = activeCourse.value?.id || query.courseId
  if (!courseId) return ElMessage.warning('请先进入一门课程')
  if (!chapters.value.length) return ElMessage.warning('请至少先创建一个课程章节，再使用 AI 生成教学大纲')
  const replacing = Boolean(outlineResource.value)
  try {
    await ElMessageBox.confirm(
      replacing
        ? 'AI 将根据当前章节和小节重新生成教学大纲，并替换现有大纲内容；生成后直接发布，无需管理员审核。'
        : 'AI 将根据当前章节和小节生成结构化教学大纲，生成后直接发布，无需管理员审核。',
      replacing ? '重新生成教学大纲' : 'AI 助手生成教学大纲',
      { type: 'warning', confirmButtonText: '开始生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  outlineGenerating.value = true
  try {
    const directory = chapterOutlineContext().slice(0, 7000)
    const result = await aiApi.agent({
      courseId,
      agentTask: 'TEACHING_OUTLINE',
      message: `请以以下已配置的课程目录为唯一的章节与小节依据，生成可供教师审核的教学大纲。课程名称：${activeCourse.value?.courseName || '当前课程'}。\n\n课程目录：\n${directory}\n\n请为每个小节给出精炼的教学目标、教学重点和建议学时；没有课程资料支持的内容请标记为“待教师补充”，不要编造课程事实。`
    })
    const config = parseTeachingOutlineReply(result?.content)
    await submitKnowledgeMap(config, { aiGenerated: true })
    ElMessage.success(replacing ? 'AI 已更新教学大纲并直接发布' : 'AI 已生成教学大纲并直接发布')
  } catch (error) {
    ElMessage.error(error?.message || 'AI 生成教学大纲失败，请检查课程资料或 AI 服务配置')
  } finally {
    outlineGenerating.value = false
  }
}

async function generateKnowledgeMap() {
  const courseId = activeCourse.value?.id || query.courseId
  if (!courseId) return ElMessage.warning('请先进入一门课程')
  if (!chapters.value.length) return ElMessage.warning('请先在课程中配置章节与小节')
  knowledgeMapGenerating.value = true
  try {
    const config = buildDirectoryKnowledgeMap()
    await submitKnowledgeMap(config)
    ElMessage.success('已根据课程章节目录生成知识图谱并直接发布')
  } catch (error) {
    ElMessage.error(error?.message || '知识图谱生成失败')
  } finally {
    knowledgeMapGenerating.value = false
  }
}

function mermaidText(value) {
  return String(value || '').replace(/[\r\n]+/g, ' ').replace(/["<>()[\]{}|]/g, '').trim().slice(0, 80) || '未命名节点'
}

function buildDirectoryKnowledgeMap() {
  const nodes = [{ id: 'course-root', parentId: null, name: activeCourse.value?.courseName || '课程知识图谱', type: 'core' }]
  const edges = []
  const lines = ['graph TD']
  const nodeIds = new Map([['course-root', 'courseRoot']])
  lines.push(`    courseRoot[${mermaidText(activeCourse.value?.courseName || '课程知识图谱')}]`)
  const orderedChapters = [...chapters.value].sort((a, b) => chapterNumber(a) - chapterNumber(b))
  orderedChapters.forEach((chapter, chapterIndex) => {
    const chapterId = `chapter-${chapter.chapterId || chapter.id || chapterIndex + 1}`
    const mermaidId = `chapter${chapterIndex + 1}`
    const chapterName = chapter.title || chapter.id || `第${chapterIndex + 1}章`
    nodeIds.set(chapterId, mermaidId)
    nodes.push({ id: chapterId, parentId: 'course-root', name: chapterName, type: 'chapter' })
    edges.push({ from: 'course-root', to: chapterId, label: '包含', type: 'contains' })
    lines.push(`    ${mermaidId}[${mermaidText(chapterName)}]`)
    lines.push(`    courseRoot -->|包含| ${mermaidId}`)
    if (chapterIndex > 0) {
      const previousId = `chapter-${orderedChapters[chapterIndex - 1].chapterId || orderedChapters[chapterIndex - 1].id || chapterIndex}`
      edges.push({ from: previousId, to: chapterId, label: '前置依赖', type: 'prerequisite' })
      lines.push(`    chapter${chapterIndex} -. 前置依赖 .-> ${mermaidId}`)
    }
    const sections = sectionOptions.value.filter((section) => String(section.chapterId) === String(chapter.chapterId || chapter.id))
    sections.forEach((section, sectionIndex) => {
      const sectionId = `section-${chapterIndex + 1}-${section.id || sectionIndex + 1}`
      const sectionMermaidId = `section${chapterIndex + 1}_${sectionIndex + 1}`
      const sectionName = section.title || `第${sectionIndex + 1}节`
      nodeIds.set(sectionId, sectionMermaidId)
      nodes.push({ id: sectionId, parentId: chapterId, name: sectionName, type: 'section' })
      edges.push({ from: chapterId, to: sectionId, label: '包含', type: 'contains' })
      lines.push(`    ${sectionMermaidId}(${mermaidText(sectionName)})`)
      lines.push(`    ${mermaidId} -->|包含| ${sectionMermaidId}`)
      if (String(section.subtitle || '').trim()) {
        const subsectionId = `${sectionId}-sub`
        const subsectionMermaidId = `${sectionMermaidId}_sub`
        const subsectionName = String(section.subtitle).split(/[；;、，,|/]/)[0].trim()
        nodes.push({ id: subsectionId, parentId: sectionId, name: subsectionName, type: 'subsection' })
        edges.push({ from: sectionId, to: subsectionId, label: '包含', type: 'contains' })
        lines.push(`    ${subsectionMermaidId}(${mermaidText(subsectionName)})`)
        lines.push(`    ${sectionMermaidId} -->|包含| ${subsectionMermaidId}`)
      }
    })
  })
  return { version: 3, nodes, edges, mermaid: lines.join('\n') }
}

async function loadKnowledgeMapDraft(courseId, chapter, sectionId) {
  knowledgeMapDraftResources.value = []
  if (!courseId || !isKnowledgeMap.value) return
  try {
    const payload = await coursewareApi.list({
      courseId,
      resourceType: 'knowledge-map',
      page: 1,
      size: 20
    })
    knowledgeMapDraftResources.value = normalizePage(payload).list
  } catch {
    knowledgeMapDraftResources.value = []
  }
}

async function reviewKnowledgeMap(row, action) {
  try {
    const { value } = await ElMessageBox.prompt(
      action === 'APPROVE' ? '审核通过后将发布这份知识图谱。' : '请填写驳回原因。',
      action === 'APPROVE' ? '审核知识图谱' : '驳回知识图谱',
      { inputType: 'textarea', inputValidator: (text) => action === 'REJECT' && !String(text || '').trim() ? '驳回原因不能为空' : true }
    )
    await coursewareApi.reviewKnowledgeMap(row.id, { action, remark: value || '' })
    ElMessage.success(action === 'APPROVE' ? '知识图谱已发布' : '知识图谱已驳回')
    await loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '审核失败')
  }
}

async function reviewOutline(row, action) {
  try {
    const { value } = await ElMessageBox.prompt(
      action === 'APPROVE' ? '可填写审核备注。' : '请填写驳回原因。',
      action === 'APPROVE' ? '审核教学大纲' : '驳回教学大纲',
      { inputType: 'textarea', inputValidator: (text) => action === 'REJECT' && !String(text || '').trim() ? '驳回原因不能为空' : true }
    )
    await coursewareApi.reviewOutline(row.id, { action, remark: value || '' })
    ElMessage.success(action === 'APPROVE' ? '教学大纲已发布' : '教学大纲已驳回')
    await loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '审核失败')
  }
}

async function archiveOutline(row) {
  try {
    await ElMessageBox.confirm('归档后学生将无法继续查看，但原有记录会保留。', '归档教学大纲', {
      type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '取消'
    })
    await coursewareApi.archiveOutline(row.id)
    ElMessage.success('教学大纲已归档')
    await loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '归档失败')
  }
}

function openResourceCategory(resource) {
  const sectionId = resolveResourceSectionId()
  if (!sectionId && resource.id !== 'knowledge-map') {
    ElMessage.warning('请先选择一个小节')
    return
  }
  router.push({
    path: `/courseware/${resource.id}`,
    query: { courseId: route.query.courseId, chapter: route.query.chapter, ...(sectionId ? { sectionId } : {}) }
  })
}

function resolveResourceSectionId() {
  if (route.query.sectionId) return route.query.sectionId
  const chapter = chapterSummaries.value.find((item) => String(item.id) === String(route.query.chapter))
  const firstSection = chapter?.sections?.[0]
    || sectionOptions.value.find((item) => String(item.chapterId) === String(selectedChapter.value?.chapterId))
  return firstSection?.id || null
}

function returnToResourceTypes() {
  const sectionId = resolveResourceSectionId()
  router.push({
    path: '/courseware',
    query: { courseId: route.query.courseId, chapter: route.query.chapter, ...(sectionId ? { sectionId } : {}) }
  })
}

function openSection(chapter, section) {
  router.push({ path: '/courseware', query: { courseId: route.query.courseId, chapter: chapter.id, sectionId: section.id } })
}

function selectQuestionBankSection(chapter, section = null) {
  questionBankChapterId.value = chapter?.id || ''
  const resolvedSection = section || chapter?.sections?.[0] || null
  questionBankSectionId.value = resolvedSection?.id ? String(resolvedSection.id) : ''
}

function ensureQuestionBankSelection() {
  if (questionBankChapterId.value && selectedQuestionBankChapter.value) {
    const hasSection = selectedQuestionBankChapter.value.sections.some((item) => String(item.id) === String(questionBankSectionId.value))
    if (hasSection) return
  }
  const firstChapter = chapterSummaries.value[0] || null
  const firstSection = firstChapter?.sections?.[0] || null
  questionBankChapterId.value = firstChapter?.id || ''
  questionBankSectionId.value = firstSection?.id ? String(firstSection.id) : ''
}

function selectCourseSection(section) {
  courseSection.value = section
  if (section === 'question-bank') ensureQuestionBankSelection()
  if (section === 'analysis' || section === 'mistakes') loadStudyAttempts()
  if (section === 'students') {
    studentPage.value = 1
    loadCourseStudents()
  }
}

const selectedStudentClassroom = computed(() => courseClassrooms.value.find((item) => String(item.id) === String(selectedStudentClassroomId.value)) || null)

function resetStudentPage() {
  studentPage.value = 1
  loadCourseStudents()
}

function studentStatusLabel(status) {
  const labels = { ACTIVE: '正常', INACTIVE: '已停用', DISABLED: '已停用', LOCKED: '已锁定' }
  return labels[status] || '状态未知'
}

function studentStatusTagType(status) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'INACTIVE' || status === 'DISABLED') return 'info'
  return 'warning'
}

async function loadCourseStudents() {
  if (!activeCourse.value?.id || authStore.isStudent) return
  studentsLoading.value = true
  try {
    const classroomPayload = await courseApi.classrooms(activeCourse.value.id)
    courseClassrooms.value = Array.isArray(classroomPayload) ? classroomPayload : (classroomPayload?.list || classroomPayload?.content || [])
    if (!courseClassrooms.value.some((item) => String(item.id) === String(selectedStudentClassroomId.value))) {
      selectedStudentClassroomId.value = courseClassrooms.value[0]?.id || null
    }
    if (!selectedStudentClassroomId.value) {
      courseStudents.value = []
      courseStudentsTotal.value = 0
      return
    }
    const payload = await classroomApi.students(selectedStudentClassroomId.value, {
      page: studentPage.value,
      size: studentPageSize.value,
      keyword: studentKeyword.value.trim() || undefined
    })
    const page = normalizePage(payload)
    courseStudents.value = page.list
    courseStudentsTotal.value = page.total
  } catch (error) {
    courseStudents.value = []
    courseStudentsTotal.value = 0
    ElMessage.error(error?.message || '课程学生加载失败')
  } finally {
    studentsLoading.value = false
  }
}

async function removeCourseStudent(student) {
  if (!selectedStudentClassroomId.value || !student?.id) return
  try {
    await ElMessageBox.confirm(
      `确认将“${student.nickname || student.username || '该学生'}”移出教学班“${selectedStudentClassroom.value?.className || ''}”？学生的历史学习记录会保留。`,
      '移出教学班',
      { type: 'warning', confirmButtonText: '确认移出', cancelButtonText: '取消' }
    )
    await classroomApi.removeStudent(selectedStudentClassroomId.value, student.id)
    if (courseStudents.value.length === 1 && studentPage.value > 1) studentPage.value -= 1
    await loadCourseStudents()
    ElMessage.success('学生已移出教学班')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '移出学生失败')
  }
}

async function openAcademicStudentImport() {
  if (!courseClassrooms.value.length) {
    await loadCourseStudents()
  }
  if (!selectedStudentClassroomId.value) {
    ElMessage.warning('请先为课程创建教学班')
    return
  }
  const payload = await academicClassApi.list({ page: 1, size: 100, ...(activeCourse.value?.schoolId ? { schoolId: activeCourse.value.schoolId } : {}) })
  academicClasses.value = normalizePage(payload).list
  academicImportId.value = null
  academicImportVisible.value = true
}

async function importAcademicStudents() {
  if (!academicImportId.value || !selectedStudentClassroomId.value) {
    ElMessage.warning('请选择行政班和课程教学班')
    return
  }
  academicImportLoading.value = true
  try {
    const students = await academicClassApi.students(academicImportId.value)
    const studentIds = students.map((student) => student.userId || (student.registrationStatus === '已注册' ? student.id : null)).filter((id) => id != null)
    const skipped = students.length - studentIds.length
    if (!studentIds.length) {
      ElMessage.warning('该行政班暂无已注册的本校学生账号，未注册学生已自动跳过')
      return
    }
    await classroomApi.addStudents({ classroomId: selectedStudentClassroomId.value, studentIds })
    academicImportVisible.value = false
    await loadCourseStudents()
    ElMessage.success(`已导入 ${studentIds.length} 名学生${skipped ? `，跳过 ${skipped} 名未注册学生` : ''}`)
  } catch (error) {
    ElMessage.error(error?.message || '行政班导入失败')
  } finally {
    academicImportLoading.value = false
  }
}

function sectionNameById(sectionId) {
  return sectionOptions.value.find((item) => String(item.id) === String(sectionId))?.title || ''
}

function openMistake(item) {
  router.push({
    path: '/courseware/assignments',
    query: {
      courseId: route.query.courseId,
      ...(item.chapter ? { chapter: item.chapter } : {}),
      ...(item.sectionId ? { sectionId: item.sectionId, questionId: item.questionId || item.id } : {})
    }
  })
}

function openChapterAssignments(chapter, section = null) {
  const resolvedSection = section || chapter?.sections?.[0] || sectionOptions.value.find((item) => String(item.chapterId) === String(chapter?.chapterId))
  if (!resolvedSection?.id) {
    ElMessage.warning('本章还没有可查看的小节')
    return
  }
  router.push({
    path: '/courseware/assignments',
    query: { courseId: route.query.courseId, chapter: chapter.id, sectionId: resolvedSection.id, ...(chapter.questionId ? { questionId: chapter.questionId } : {}) }
  })
}

function openCourseAssignments() {
  if (!activeCourse.value?.id) {
    ElMessage.warning('请先选择一门课程')
    return
  }
  selectCourseSection('assignments')
}

async function loadStudyAttempts() {
  if (!authStore.isStudent || !route.query.courseId) {
    studyAttempts.value = []
    mistakeItems.value = []
    completedQuizSections.value = new Set()
    videoProgressRecords.value = {}
    completedExams.value = []
    return
  }
  studyLoading.value = true
  try {
    const videoResources = courseResources.value.filter((item) => item.resourceType === 'teaching-video' && item.status === 'ACTIVE')
    const [practiceAttempts, quizSections, mistakes, videoProgress, exams] = await Promise.all([
      learningResourceApi.myAttempts({ courseId: route.query.courseId }),
      chapterQuizApi.completedSections({ courseId: route.query.courseId }),
      learningResourceApi.myMistakes({ courseId: route.query.courseId }),
      Promise.all(videoResources.map(async (video) => {
        try { return [video.id, await coursewareApi.myVideoProgress(video.id)] } catch { return [video.id, null] }
      })),
      examApi.list({ courseId: route.query.courseId })
    ])
    studyAttempts.value = practiceAttempts || []
    completedQuizSections.value = new Set((quizSections || []).map((id) => Number(id)))
    videoProgressRecords.value = Object.fromEntries(videoProgress.filter(([, value]) => value).map(([id, value]) => [id, value]))
    completedExams.value = (exams || []).filter((item) => item.completed)
    mistakeItems.value = mistakes || studyAttempts.value.filter((item) => !item.correct)
  } catch (error) {
    ElMessage.error(error?.message || '错题集加载失败')
    studyAttempts.value = []
    mistakeItems.value = []
    completedQuizSections.value = new Set()
    videoProgressRecords.value = {}
    completedExams.value = []
  } finally {
    studyLoading.value = false
    mistakesLoading.value = false
  }
}

function returnToResourceHub() {
  router.push('/courseware')
}

function returnToCourseOverview() {
  router.push({ path: '/courseware', query: { courseId: route.query.courseId } })
}

async function loadCourses() {
  const payload = authStore.isStudent
    ? await courseApi.joined()
    : await courseApi.manageList({ status: 'ACTIVE' })
  const loadedCourses = Array.isArray(payload) ? payload : (payload?.list || payload?.content || [])
  courses.value = loadedCourses.filter((course) => !course.status || course.status === 'ACTIVE')
  if (!courses.value.some((course) => String(course.id) === String(homeCourseId.value))) {
    homeCourseId.value = courses.value[0]?.id || null
  }
  await loadHomeChapters(homeCourse.value?.id)
}

async function loadHomeChapters(courseId) {
  homeChapters.value = []
  if (!courseId) return
  try {
    const [chapterPayload, sectionPayload] = await Promise.all([
      courseApi.chapters(courseId),
      courseApi.sections(courseId)
    ])
    const chapterList = Array.isArray(chapterPayload)
      ? chapterPayload
      : (chapterPayload?.list || chapterPayload?.content || [])
    const sectionList = Array.isArray(sectionPayload)
      ? sectionPayload
      : (sectionPayload?.list || sectionPayload?.content || [])
    const activeSections = sectionList.filter((item) => item && item.status !== 'ARCHIVED')
    homeChapters.value = chapterList
      .filter((item) => item && item.status !== 'ARCHIVED')
      .map((chapter) => ({
        ...chapter,
        chapterId: chapter.chapterId || chapter.id,
        sections: activeSections.filter((section) => String(section.chapterId) === String(chapter.chapterId || chapter.id))
    }))
    homeSelectedChapterId.value = homeChapters.value[0]?.chapterId || homeChapters.value[0]?.id || null
    homeSelectedSectionKey.value = homeChapters.value[0]?.sections?.[0]?.id || (homeChapters.value[0] ? `${homeChapters.value[0].chapterId || homeChapters.value[0].id}-0` : null)
    homeSelectedPoint.value = ''
    homeSelectedKnowledgeKey.value = ''
    // 首页右页按当前章节展示 AI 提取的知识点，异步加载不阻塞目录首屏。
    loadHomeKnowledgePoints(courseId, homeChapters.value)
  } catch {
    homeChapters.value = []
    homeSelectedChapterId.value = null
    homeKnowledgePoints.value = []
  }
}

async function loadContributors(courseId) {
  contributorIds.value = new Set()
  if (!courseId || authStore.isStudent || authStore.isAdmin) return
  try {
    const members = await courseApi.members(courseId)
    const list = Array.isArray(members) ? members : (members?.list || members?.content || [])
    contributorIds.value = new Set(list
      .filter((member) => ['OWNER', 'CO_TEACHER', 'TEACHING_ASSISTANT'].includes(member.role))
      .map((member) => Number(member.userId)))
  } catch {
    contributorIds.value = new Set()
  }
}

async function loadChapters(courseId) {
  chapterOptions.value = []
  sectionOptions.value = []
  courseResources.value = []
  if (!courseId) return
  try {
    const [chapterPayload, sectionPayload, resourcePayload] = await Promise.all([
      courseApi.chapters(courseId),
      courseApi.sections(courseId),
      coursewareApi.list({ courseId, page: 1, size: 1000 })
    ])
    // 归档章节只保留在后台，课程页面不得再次展示。
    const chapterData = (Array.isArray(chapterPayload) ? chapterPayload : (chapterPayload?.list || []))
      .filter((item) => item && item.status !== 'ARCHIVED')
    if (!authStore.isStudent && canManageChapters.value && chapterData.some((item) => !item.chapterId)) {
      const created = await courseApi.syncLegacyChapters(courseId)
      if (Number(created) > 0) {
        await loadChapters(courseId)
        return
      }
    }
    sectionOptions.value = Array.isArray(sectionPayload) ? sectionPayload : (sectionPayload?.list || [])
    const pageData = normalizePage(resourcePayload)
    courseResources.value = pageData.list
    chapterOptions.value = chapterData.map((item) => ({
      ...item,
      chapterId: item.chapterId || item.id,
      id: item.title,
      description: item.description || item.subtitle || '已汇聚该章节的教学资源，可继续学习。'
    }))
    if (authStore.isStudent && ['analysis', 'mistakes'].includes(courseSection.value)) await loadStudyAttempts()
  } catch {
    chapterOptions.value = []
    courseResources.value = []
  }
}

function getFileExtension(fileName, fileType) {
  const source = String(fileName || fileType || '')
  const match = source.match(/\.([a-z0-9]+)$/i)
  return (match?.[1] || source).toLowerCase()
}

function previewActionLabel(row) {
  return ['ppt', 'pptx'].includes(getFileExtension(row?.fileName, row?.fileType))
    ? '用本地软件打开'
    : '在线查看'
}

const previewKind = computed(() => {
  const extension = getFileExtension(preview.value?.fileName, preview.value?.fileType)

  if (['mp4', 'webm', 'ogg', 'mov'].includes(extension)) return 'video'
  if (['mp3', 'wav', 'oga', 'm4a'].includes(extension)) return 'audio'
  if (extension === 'pdf') return 'pdf'
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(extension)) return 'image'
  return 'unsupported'
})

function resetEditor() {
  editingId.value = null
  editor.title = ''
  editor.description = ''
  editor.fileUrl = ''
  editor.fileName = ''
  editor.fileType = ''
  editor.resourceType = query.resourceType || ''
  editor.fileSize = null
  editor.visibility = 'COURSE'
  editor.targetAudience = 'ALL'
  editor.classroomId = null

  // 教学大纲始终属于当前课程，不继承资源列表中的班级筛选状态。
  if (isTeachingOutline.value) {
    editor.resourceType = 'teaching-outline'
    editor.visibility = 'COURSE'
    editor.targetAudience = 'ALL'
  }
}

async function loadClassrooms() {
  if (!canManage.value) {
    classroomOptions.value = []
    return
  }

  let payload

  if (authStore.isAdmin) {
    payload = await classroomApi.adminList({ page: 1, size: 100 })
  } else {
    payload = await classroomApi.mine({ page: 1, size: 100 })
  }

  classroomOptions.value = normalizePage(payload).list
}

async function loadList() {
  loading.value = true

  try {
    const params = Object.fromEntries(
      Object.entries(query).filter(([, value]) => value !== '' && value !== null && value !== undefined)
    )
    if (isKnowledgeMap.value || isTeachingOutline.value) {
      delete params.chapter
      delete params.sectionId
    }
    if (isTeachingOutline.value) {
      delete params.classroomId
    }
    const pageData = normalizePage(await coursewareApi.list(params))
    list.value = pageData.list
    total.value = pageData.total
    const resourceId = Number(route.query.resourceId)
    const relatedResource = resourceId ? list.value.find((item) => Number(item.id) === resourceId) : null
    if (relatedResource) await loadDetail(relatedResource)
  } catch (error) {
    list.value = []
    total.value = 0
    outlineError.value = error?.message || '教学大纲加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function loadDetail(row) {
  try {
    detailVisible.value = true
    detail.value = await coursewareApi.detail(row.id)
  } catch (error) {
    detailVisible.value = false
    ElMessage.error(error?.message || 'Unable to load resource details')
  }
}

async function reportResource(row = detail.value) {
  if (!authStore.isLoggedIn) return ElMessage.warning('登录后才能举报')
  if (!row?.id) return
  if (authStore.user?.id === row.teacherId) return ElMessage.warning('不能举报自己发布的资源')
  try {
    const { value } = await ElMessageBox.prompt(
      '请说明举报原因，管理员只处理异常或高风险内容。',
      '举报教学资源',
      { inputType: 'textarea', inputValidator: value => String(value || '').trim() ? true : '请填写举报原因' }
    )
    await moderationApi.report({ targetType: 'COURSEWARE', targetId: row.id, reason: value.trim() })
    ElMessage.success('举报已提交，管理员会进行复核')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '举报失败')
  }
}

async function appealResource(row = detail.value) {
  if (!row?.id) return
  try {
    const { value } = await ElMessageBox.prompt(
      '请说明你认为处置有误的原因，管理员会重新复核。',
      '教学资源申诉',
      { inputType: 'textarea', inputValidator: value => String(value || '').trim() ? true : '请填写申诉理由' }
    )
    await moderationApi.appeal({ targetType: 'COURSEWARE', targetId: row.id, reason: value.trim() })
    ElMessage.success('申诉已提交')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '申诉失败')
  }
}

function openCreate() {
  resetEditor()
  if (isTeachingOutline.value) {
    editor.resourceType = 'teaching-outline'
    editor.classroomId = null
    editor.visibility = 'COURSE'
    editor.targetAudience = 'ALL'
  }
  editorVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  editor.title = row.title
  editor.description = row.description || ''
  editor.fileUrl = row.fileUrl
  editor.fileName = row.fileName
  editor.fileType = row.fileType
  editor.resourceType = row.resourceType || query.resourceType || 'teaching-courseware'
  editor.fileSize = row.fileSize
  editor.visibility = row.visibility || 'COURSE'
  editor.targetAudience = row.targetAudience || 'ALL'
  editor.classroomId = isTeachingOutline.value ? null : (row.classroomId || null)
  if (isTeachingOutline.value) {
    editor.resourceType = 'teaching-outline'
    editor.visibility = 'COURSE'
    editor.targetAudience = 'ALL'
  }
  editorVisible.value = true
}

function statusInfo(value) {
  return {
    PENDING: ['待审核', 'warning'],
    ACTIVE: ['已发布', 'success'],
    REJECTED: ['已驳回', 'danger'],
    ARCHIVED: ['已归档', 'info']
  }[value] || [value || '未知', 'info']
}

async function uploadFile(event) {
  const file = event.target.files?.[0]

  if (!file) {
    return
  }

  if (isTeachingCourseware.value && !['ppt', 'pptx', 'pdf'].includes(getFileExtension(file.name, file.type))) {
    ElMessage.warning('教学课件仅支持 PPT、PPTX、PDF 格式')
    event.target.value = ''
    return
  }

  uploadLoading.value = true

  try {
    const fileUrl = await uploadApi.courseware(file)
    editor.fileUrl = fileUrl
    editor.fileName = file.name
    editor.fileType = file.name.includes('.') ? file.name.split('.').pop() : file.type || 'unknown'
    editor.fileSize = file.size
    if (isKnowledgeMap.value && getFileExtension(file.name, file.type) === 'json') {
      const text = await file.text()
      try {
        const config = JSON.parse(text)
        if (Array.isArray(config.nodes)) editor.description = text
      } catch {
        ElMessage.warning('JSON 图谱文件格式无法识别，可继续作为普通图谱文件保存')
      }
    }
    ElMessage.success('资源文件已上传')
  } catch (error) {
    ElMessage.error(error?.message || 'Resource upload failed')
  } finally {
    uploadLoading.value = false
    event.target.value = ''
  }
}

async function saveCourseware() {
  submitLoading.value = true

  const payload = {
    title: editor.title,
    description: editor.description,
    courseId: activeCourse.value?.id || query.courseId || null,
    visibility: isTeachingOutline.value ? 'COURSE' : editor.visibility,
    targetAudience: isTeachingOutline.value ? 'ALL' : editor.targetAudience,
    classroomId: isTeachingOutline.value ? null : editor.classroomId,
    resourceType: editor.resourceType || query.resourceType,
    chapter: isTeachingOutline.value || isKnowledgeMap.value ? null : (route.query.chapter || null),
    sectionId: isTeachingOutline.value || isKnowledgeMap.value
      ? null
      : (route.query.sectionId ? Number(route.query.sectionId) : null)
  }

  try {
    if (!payload.courseId) {
      ElMessage.warning('请先进入具体课程后再上传资源')
      return
    }
    if (!String(payload.title || '').trim()) {
      ElMessage.warning('Please enter a resource title')
      return
    }
    if (!editingId.value && !editor.fileUrl) {
      ElMessage.warning('Please upload a resource file first')
      return
    }
    if (editingId.value) {
      await coursewareApi.update(editingId.value, {
        ...payload,
        ...(editor.fileUrl ? {
          fileUrl: editor.fileUrl,
          fileName: editor.fileName,
          fileType: editor.fileType,
          fileSize: editor.fileSize
        } : {})
      })
      ElMessage.success(payload.visibility === 'PUBLIC' ? '全校公开更新已提交发布审核' : '资源已更新并直接发布；高风险内容会进入复核')
    } else {
      await coursewareApi.create({
        ...payload,
        fileUrl: editor.fileUrl,
        fileName: editor.fileName,
        fileType: editor.fileType,
        fileSize: editor.fileSize
      })
      ElMessage.success(payload.visibility === 'PUBLIC' ? '全校公开申请已提交发布审核' : '资源已直接发布；高风险内容会进入复核')
    }

    editorVisible.value = false
    resetEditor()
    await loadList()
  } catch (error) {
    ElMessage.error(error?.message || 'Unable to save resource')
  } finally {
    submitLoading.value = false
  }
}

async function removeCourseware(id) {
  const resource = list.value.find((item) => Number(item.id) === Number(id))
  const isMap = resource?.resourceType === 'knowledge-map'
  try {
    await ElMessageBox.confirm(
      isMap ? `确认删除课程“${activeCourse.value?.courseName || ''}”的知识图谱吗？删除后可重新创建。` : '该资源将从课程中删除。',
      isMap ? '删除知识图谱' : '删除资源',
      { type: 'warning', confirmButtonText: isMap ? '直接删除' : '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await coursewareApi.remove(id)
  ElMessage.success('资源已删除')
  await loadList()
}

async function reviewCourseware(row, action) {
  try {
    const { value } = await ElMessageBox.prompt(
      action === 'APPROVE' ? '可填写审核备注。' : '请填写驳回原因。',
      action === 'APPROVE' ? '审核通过' : '驳回课件',
      { inputType: 'textarea', inputValidator: (text) => action === 'REJECT' && !String(text || '').trim() ? '驳回原因不能为空' : true }
    )
    await coursewareApi.reviewCourseware(row.id, { action, remark: value || '' })
    ElMessage.success('审核结果已提交')
    await loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '审核失败')
  }
}

async function archiveCourseware(row) {
  await coursewareApi.archiveCourseware(row.id)
  ElMessage.success('课件已归档')
  await loadList()
}

async function downloadCourseware(row) {
  const blob = await coursewareApi.download(row.id)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = row.fileName || 'courseware-resource'
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  ElMessage.success('已记录下载并打开资源')
  await loadList()
}

async function openCoursewareLocally(row) {
  try {
    await coursewareApi.openLocal(row.id)
    ElMessage.success('已调用本地 PowerPoint/WPS 打开课件')
  } catch (error) {
    ElMessage.warning(error?.message || '无法调用本地软件，请先下载后打开')
  }
}

async function openPreview(row) {
  const extension = getFileExtension(row.fileName, row.fileType)
  if (['ppt', 'pptx'].includes(extension)) {
    await openCoursewareLocally(row)
    return
  }
  const supported = ['mp4', 'webm', 'ogg', 'mov', 'mp3', 'wav', 'oga', 'm4a', 'pdf', 'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp']

  if (!supported.includes(extension)) {
    ElMessage.warning('该文件类型暂不支持在线预览，请下载后查看。')
    return
  }

  const blob = await coursewareApi.preview(row.id)
  preview.value = { ...row, fileUrl: URL.createObjectURL(blob) }
  previewVisible.value = true
  await loadList()
}

function clearPreview() {
  if (preview.value?.fileUrl?.startsWith('blob:')) URL.revokeObjectURL(preview.value.fileUrl)
  preview.value = null
}

watch(
  () => [route.params.resourceType, route.query.courseId, route.query.chapter, route.query.sectionId, route.query.questionId, route.query.tab, route.query.resourceId],
  ([resourceType, courseId, chapter, , , tab]) => {
    query.resourceType = resourceType || ''
    query.courseId = courseId ? Number(courseId) : null
    query.chapter = chapter || ''
    query.sectionId = route.query.sectionId ? Number(route.query.sectionId) : null
    outlineEditorOpen.value = false
    query.page = 1
    if (!chapter && !resourceType) {
      courseSection.value = tab === 'assignments' ? 'assignments' : 'chapters'
      if (authStore.isStudent) loadStudyAttempts()
      else {
        studyAttempts.value = []
        mistakeItems.value = []
        completedQuizSections.value = new Set()
      }
    }

    loadChapters(query.courseId)
    loadContributors(query.courseId)
    loadKnowledgeMapDraft(query.courseId, chapter, route.query.sectionId)
    if (courseSection.value === 'question-bank') ensureQuestionBankSelection()

    outlineError.value = ''
    if (resourceType) {
      loadList()
    }
  },
  { immediate: true }
)

onMounted(async () => {
  loadHomeAnswerBubblePosition()
  loadClassrooms()
  await loadCourses()
  if (query.courseId) {
    await loadContributors(query.courseId)
    await loadChapters(query.courseId)
    if (courseSection.value === 'question-bank') ensureQuestionBankSelection()
  }
})

onBeforeUnmount(() => {
  stopHomeAnswerBubbleDrag()
})

</script>

<template>
  <div class="resource-page">
    <section v-if="isCourseHome && !imperialExamOpen" class="resource-hub studio-resource-hub studio-home-landing" :style="{ '--teaching-home-background': `url(${teachingHomeBackground})` }" aria-labelledby="resource-hub-title">
      <button class="studio-shelf-tab" type="button" :aria-expanded="homeShelfOpen" @click="homeShelfOpen = !homeShelfOpen"><span>课程书阁</span><small>{{ courses.length }}</small></button>
      <aside v-if="homeShelfOpen" class="studio-course-shelf studio-course-shelf-drawer"><div class="studio-shelf-title"><span>课程书阁</span><small>{{ courses.length }} 门课程</small><button class="studio-shelf-close" type="button" @click="homeShelfOpen = false">收起</button></div><div v-if="authStore.isStudent" class="studio-join"><el-input v-model="inviteCode" clearable placeholder="课程邀请码" @keyup.enter="joinCourse(inviteCode)" /><el-button type="primary" :loading="joining" @click="joinCourse(inviteCode)">加入</el-button></div><nav><button v-for="(course,index) in courses" :key="course.id" type="button" :class="{ active: String(homeCourse?.id) === String(course.id) }" @click="selectHomeCourse(course)"><i :class="`tone-${index % 4}`"></i><span>{{ course.courseName }}</span><small>{{ course.courseCode || '课程资源' }}</small></button></nav><button v-if="authStore.isTeacher || authStore.isAdmin" class="studio-shelf-add" type="button" @click="openCourseCreate">＋ 新建课程</button></aside>
      <main v-if="homeCourse" class="studio-home-stage">
        <div class="studio-home-topline"><div><span class="studio-home-eyebrow">TEACHING CENTER</span><h1>云塾教学中心</h1><p>当前课程：<strong>{{ homeCourse.courseName }}</strong></p></div><button class="studio-home-workspace-button" type="button" @click="openCourse(homeCourse)">进入课程工作台</button></div>
        <div class="studio-home-book"><section class="studio-home-page studio-home-directory"><div class="studio-book-heading"><span>{{ homeCourse.courseName }}</span><small>目　录</small></div><div class="studio-chapter-list"><article v-for="(chapter,index) in homeChapters" :key="chapter.id || chapter.chapterId" class="studio-chapter-item" :class="{ active: String(selectedHomeChapter?.chapterId || selectedHomeChapter?.id) === String(chapter.chapterId || chapter.id) }"><button class="studio-chapter-row" type="button" @click="selectHomeChapter(chapter)"><span class="studio-chapter-number">{{ String(index + 1).padStart(2, '0') }}</span><span class="studio-chapter-copy"><strong>{{ chapter.title || chapter.name || `第 ${index + 1} 章` }}</strong><em>{{ chapter.description || '课程章节' }}</em></span></button><div v-if="chapter.sections?.length" class="studio-section-list"><button v-for="(section, sectionIndex) in chapter.sections" :key="section.id || `${chapter.chapterId}-${sectionIndex}`" class="studio-section-row" :class="{ 'is-selected': String(homeSelectedSectionKey) === String(section.id || `${chapter.chapterId || chapter.id}-${sectionIndex}`) }" type="button" @click="selectHomeSection(chapter, section)"><span>{{ index + 1 }}.{{ sectionIndex + 1 }}</span><strong>{{ section.title || `第 ${sectionIndex + 1} 节` }}</strong></button></div><div v-else class="studio-section-empty">暂无小节</div></article><div v-if="!homeChapters.length" class="studio-no-chapters">暂无章节目录</div></div><footer>共 {{ homeChapters.length }} 章 · {{ homeSectionCount }} 节</footer></section><section class="studio-home-page studio-home-knowledge"><div class="studio-book-heading"><span>{{ selectedHomeChapter?.title || selectedHomeChapter?.name || '本章知识点' }}</span><small>AI 知识点</small></div><p class="studio-knowledge-intro">每个小节对应一个 AI 核心知识点，点击后再选择下方操作。</p><div v-if="homeKnowledgeLoading" class="studio-knowledge-state">正在查找本章知识点…</div><div v-else-if="selectedHomeKnowledgeGroups.length" class="studio-knowledge-groups"><button v-for="group in selectedHomeKnowledgeGroups" :key="group.sectionKey" type="button" class="studio-knowledge-point" :class="{ active: String(homeSelectedKnowledgeKey) === String(group.sectionKey) }" @click="homeSelectedKnowledgeKey = group.sectionKey; homeSelectedPoint = group.sectionKey"><span class="studio-knowledge-point-section">{{ group.section ? group.section.title : `第 ${group.index + 1} 节` }}</span><strong>{{ group.points[0] }}</strong></button></div><div v-else class="studio-knowledge-state">暂无知识点，请先配置课程小节。</div><div class="studio-home-action-grid"><button type="button" :disabled="!selectedHomeKnowledge" @click="runHomeQuickAction('explain')"><span>📖</span>展开讲解</button><button type="button" :disabled="!selectedHomeKnowledge" @click="runHomeQuickAction('quiz')"><span>🖊</span>出一道课堂测验</button><button type="button" @click="runHomeQuickAction('lesson-plan')"><span>📝</span>一键生成教案</button><button type="button" @click="runHomeQuickAction('outline')"><span>📊</span>导出课件大纲</button><button type="button" @click="runHomeQuickAction('note')"><span>💾</span>存入教研笔记</button><button type="button" class="imperial-entry-button" @click="imperialExamOpen = true"><span>🏮</span>科举闯关</button></div></section></div>
        <aside v-if="homeAnswerVisible && (homeAnswer || homeQuestionLoading)" ref="homeAnswerBubble" class="studio-home-answer-bubble" :style="homeAnswerBubbleStyle" aria-live="polite">
          <img class="studio-home-answer-cloud" :src="answerCloud" alt="" aria-hidden="true" />
          <button class="studio-home-answer-close" type="button" aria-label="关闭回答气泡" title="关闭" @click="closeHomeAnswerBubble">×</button>
          <div class="studio-home-answer-bubble-content">
            <strong>云塾女先生</strong>
            <p v-if="homeQuestionLoading">正在整理这个知识点，请稍等……</p>
            <p v-else>{{ homeAnswer }}</p>
          </div>
        </aside>
      </main>
      <div v-else class="empty-course studio-course-empty">{{ authStore.isStudent ? '暂未加入课程，请输入课程代码或教学班邀请码加入。' : '暂无可进入的课程，请先创建课程。' }}<el-button v-if="authStore.isTeacher || authStore.isAdmin" type="primary" @click="openCourseCreate">新建课程</el-button></div>
    </section>

    <section v-else-if="isCourseHome && imperialExamOpen" class="resource-hub imperial-exam-resource-hub">
      <ImperialExamPanel :course-id="homeCourse.id" :course-name="homeCourse.courseName" :sections="homeChapters.flatMap((chapter) => chapter.sections || [])" @back="imperialExamOpen = false" />
    </section>

    <section v-else-if="isCourseOverview && authStore.isStudent" class="course-study-shell">
      <aside class="course-study-sidebar" aria-label="课程学习导航">
        <div class="course-study-brand">
          <div class="course-study-avatar"><Files :size="20" /></div>
          <div><strong>{{ activeCourse.courseName }}</strong><span>{{ activeCourse.teacherName || '课程教师' }}</span></div>
        </div>
        <nav class="course-study-nav">
          <button type="button" :class="{ active: courseSection === 'chapters' }" @click="selectCourseSection('chapters')"><List :size="18" />章节</button>
          <button type="button" :class="{ active: courseSection === 'knowledge-map' }" @click="selectCourseSection('knowledge-map')"><Collection :size="18" />知识图谱</button>
          <button type="button" :class="{ active: courseSection === 'assignments' }" @click="selectCourseSection('assignments')"><EditPen :size="18" />作业</button>
          <button type="button" :class="{ active: courseSection === 'analysis' }" @click="selectCourseSection('analysis')"><DataAnalysis :size="18" />学习记录</button>
          <button type="button" :class="{ active: courseSection === 'exams' }" @click="selectCourseSection('exams')"><Calendar :size="18" />考试</button>
          <button type="button" :class="{ active: courseSection === 'mistakes' }" @click="selectCourseSection('mistakes')"><DocumentDelete :size="18" />错题集</button>
        </nav>
      </aside>

      <main class="course-study-main">
        <div class="course-study-header">
          <div>
            <button class="resource-back" type="button" @click="returnToResourceHub">返回课程列表</button>
            <h3>{{ activeCourse.courseName }}</h3>
            <p>{{ activeCourse.courseCode || '课程学习空间' }} · {{ chapters.length }} 个章节</p>
          </div>
        </div>

        <section v-if="courseSection === 'knowledge-map'" class="course-study-section course-map-section">
           <div v-if="authStore.isTeacher || authStore.isAdmin" class="course-map-toolbar"><span>知识图谱</span><div><el-button type="primary" @click="router.push({ path: '/courseware/knowledge-map', query: { courseId: activeCourse?.id } })">查看 / 按目录生成</el-button></div></div>
           <KnowledgeBubbleGraph :course-name="activeCourse?.courseName" :chapters="chapters" :sections="sectionOptions" :resources="knowledgeMapResources" />
        </section>

        <section v-else-if="courseSection === 'chapters'" class="course-study-section">
          <div class="study-section-heading"><div><h4>课程目录</h4><p>章节用于组织目录，请点击小节进入对应教学资源。</p></div><span>{{ chapters.length }} 个章节</span></div>
          <div class="chapter-study-list">
            <article v-for="(chapter, index) in chapterSummaries" :key="chapter.id" class="chapter-outline-item">
              <div class="chapter-outline-heading">
                <div class="chapter-index">{{ index + 1 }}</div>
                <strong class="chapter-study-title">{{ chapterLabel(chapter, index) }} {{ chapter.title || chapter.id }}</strong>
              </div>
              <div class="chapter-section-list">
<button v-for="(section, sectionIndex) in chapter.sections" :key="section.id" type="button" class="chapter-section-item" :class="{ 'is-complete': sectionCompleted(section) }" @click="openSection(chapter, section)">
                  <span class="section-index" :class="{ 'is-complete': sectionCompleted(section) }"><span v-if="sectionCompleted(section)">✓</span><template v-else>{{ index + 1 }}.{{ sectionIndex + 1 }}</template></span>
                  <span>{{ section.title }}</span>
                </button>
                <span v-if="!chapter.sections.length" class="chapter-section-empty">本章暂未配置小节</span>
              </div>
            </article>
            <div v-if="!chapters.length" class="study-empty">当前课程还没有章节目录。</div>
          </div>
        </section>

        <section v-else-if="courseSection === 'analysis'" class="course-study-section">
          <div class="study-section-heading"><div><h4>学习记录</h4><p>查看当前课程的学习进度、练习次数和答题情况。</p></div></div>
          <div class="study-metric-grid" v-loading="studyLoading"><div><strong>{{ studyMetrics.learnedChapters }}/{{ chapters.length }}</strong><span>已学习章节</span></div><div><strong>{{ studyMetrics.onlineSessions }}</strong><span>在线学习次数</span></div><div><strong>{{ studyMetrics.quizCount }}</strong><span>章节测验</span></div><div><strong>{{ studyMetrics.examCount }}</strong><span>考试</span></div><div><strong>{{ studyMetrics.videoCount }}</strong><span>视频任务点</span></div><div><strong>{{ studyMetrics.accuracy }}%</strong><span>答题正确率</span></div></div>
          <div class="chapter-study-chart" aria-label="章节学习次数"><div v-for="(chapter, index) in chapterSummaries" :key="chapter.id" class="chapter-study-chart-item"><span>{{ chapterLabel(chapter, index) }}</span><div class="chapter-study-chart-bar"><i :style="{ height: `${Math.max(8, Math.round((chapter.studyCount / maxChapterStudyCount) * 100))}%` }"></i></div><strong>{{ chapter.studyCount }} 次</strong><small>{{ chapter.quizCount + chapter.videoCount }} 个任务点</small></div><p v-if="!chapterSummaries.length" class="study-empty">暂无学习记录。</p></div>
          <div class="analysis-list"><div v-for="(chapter, index) in chapterSummaries" :key="chapter.id"><span>{{ chapterLabel(chapter, index) }} {{ chapter.title || chapter.id }}</span><div class="analysis-track"><i :style="{ width: `${chapter.practiceRate}%` }"></i></div><em>{{ chapter.practiceRate }}% 题目已练习</em></div></div>
        </section>

        <section v-else-if="courseSection === 'assignments'" class="course-study-section assignment-course-section">
          <AssignmentsView />
        </section>

        <section v-else-if="courseSection === 'exams'" class="course-study-section">
          <CourseExamsPanel :course-id="activeCourse?.id || query.courseId" />
        </section>

        <section v-else class="course-study-section" v-loading="studyLoading">
          <div class="mistake-list"><article v-for="item in mistakeItems" :key="item.id" class="mistake-question-card" role="button" tabindex="0" @click="openMistake(item)" @keydown.enter="openMistake(item)"><div class="mistake-question-meta"><span>{{ item.source || '错题' }}</span><span>{{ item.chapter || '未分章' }}</span><span v-if="sectionNameById(item.sectionId)">{{ sectionNameById(item.sectionId) }}</span></div><h5>{{ item.title }}</h5><p class="mistake-stem">{{ item.stem }}</p><p class="mistake-answer wrong-answer">我的答案：{{ item.answer || '未作答' }}</p><p class="mistake-answer right-answer">正确答案：{{ item.referenceAnswer || '等待教师批改' }}</p><p v-if="item.analysis" class="mistake-analysis">解析：{{ item.analysis }}</p></article><div v-if="!mistakeItems.length && !mistakesLoading" class="study-empty">暂时没有错题，继续保持。</div></div>
        </section>
      </main>
    </section>

    <section v-else-if="isCourseOverview" class="course-study-shell teacher-course-shell">
      <aside class="course-study-sidebar" aria-label="课程管理导航">
        <div class="course-study-brand">
          <div class="course-study-avatar"><Files :size="20" /></div>
          <div><strong>{{ activeCourse.courseName }}</strong><span>{{ activeCourse.teacherName || '课程教师' }}</span></div>
        </div>
        <nav class="course-study-nav">
          <button type="button" :class="{ active: courseSection === 'chapters' }" @click="selectCourseSection('chapters')"><List :size="18" />章节</button>
          <button type="button" :class="{ active: courseSection === 'knowledge-map' }" @click="selectCourseSection('knowledge-map')"><Collection :size="18" />知识图谱</button>
          <button type="button" :class="{ active: courseSection === 'question-bank' }" @click="selectCourseSection('question-bank')"><EditPen :size="18" />题目库</button>
          <button type="button" :class="{ active: courseSection === 'students' }" @click="selectCourseSection('students')"><UserFilled :size="18" />学生管理</button>
          <button type="button" :class="{ active: courseSection === 'assignments' }" @click="selectCourseSection('assignments')"><EditPen :size="18" />作业</button>
          <button type="button" :class="{ active: courseSection === 'exams' }" @click="selectCourseSection('exams')"><Calendar :size="18" />考试</button>
        </nav>
      </aside>

      <main class="course-study-main">
        <div class="course-study-header">
          <div>
          <button class="resource-back" type="button" @click="returnToResourceHub">返回课程列表</button>
            <h3>{{ activeCourse.courseName }}</h3>
            <p>{{ activeCourse.courseCode || '课程资源总览' }}</p>
          </div>
          <div class="teacher-course-header-actions">
          </div>
        </div>

        <section v-if="courseSection === 'knowledge-map'" class="course-study-section course-map-section">
           <div class="course-map-toolbar"><span>知识图谱</span><div><el-button type="primary" @click="router.push({ path: '/courseware/knowledge-map', query: { courseId: activeCourse?.id } })">查看 / 按目录生成</el-button></div></div>
           <KnowledgeBubbleGraph :course-name="activeCourse?.courseName" :chapters="chapters" :sections="sectionOptions" :resources="knowledgeMapResources" />
        </section>
        <section v-else-if="courseSection === 'exams'" class="course-study-section">
          <CourseExamsPanel :course-id="activeCourse?.id || query.courseId" />
        </section>
        <section v-else-if="courseSection === 'question-bank'" class="course-study-section">
          <div class="question-bank-workbench">
            <aside class="question-bank-outline" aria-label="题库小节选择">
              <strong>选择小节</strong>
              <div class="question-bank-chapter-list">
                <section v-for="(chapter, index) in chapterSummaries" :key="chapter.id">
                  <p>{{ chapterLabel(chapter, index) }} {{ chapter.title || chapter.id }}</p>
                  <button
                    v-for="(section, sectionIndex) in chapter.sections"
                    :key="section.id"
                    type="button"
                    :class="{ active: String(selectedQuestionBankSection?.id) === String(section.id) }"
                    @click="selectQuestionBankSection(chapter, section)"
                  >
                    <span>{{ chapterNumber(chapter, index) }}.{{ sectionIndex + 1 }}</span>
                    <span>{{ section.title }}</span>
                  </button>
                  <em v-if="!chapter.sections.length">本章暂无小节</em>
                </section>
              </div>
            </aside>
            <div v-if="selectedQuestionBankSection && selectedQuestionBankChapter" class="question-library-shell">
              <CourseQuestionLibraryPanel
                :course-id="activeCourse?.id || query.courseId"
                :chapter="selectedQuestionBankChapter?.id"
                :section-id="selectedQuestionBankSection?.id"
                :section-title="selectedQuestionBankSection?.title"
              />
            </div>
            <div v-else class="study-empty">请先创建课程小节，再在这里维护题目。</div>
          </div>
        </section>
        <section v-else-if="courseSection === 'students'" class="course-study-section student-management-section">
          <div class="study-section-heading student-management-heading">
            <div><h4>课程学生</h4><p>按教学班管理学生；可搜索、批量导入和移出学生。</p></div>
            <div class="student-management-actions">
              <el-button plain :loading="studentsLoading" @click="loadCourseStudents">刷新学生</el-button>
              <el-button type="primary" :icon="UploadFilled" @click="openAcademicStudentImport">从本校行政班导入</el-button>
            </div>
          </div>
          <div class="student-management-toolbar">
            <el-select v-model="selectedStudentClassroomId" placeholder="选择教学班" @change="resetStudentPage">
              <el-option v-for="classroom in courseClassrooms" :key="classroom.id" :label="`${classroom.className}（${classroom.studentCount || 0}人）`" :value="classroom.id" />
            </el-select>
            <el-input v-model="studentKeyword" clearable placeholder="搜索姓名、学号、用户名或邮箱" @keyup.enter="resetStudentPage" @clear="resetStudentPage" />
            <el-button type="primary" @click="resetStudentPage">查询</el-button>
          </div>
          <el-table :data="courseStudents" v-loading="studentsLoading" class="student-management-table">
            <el-table-column prop="nickname" label="姓名" min-width="150"><template #default="{ row }">{{ row.nickname || row.username || '未命名学生' }}</template></el-table-column>
            <el-table-column label="学号" min-width="140"><template #default="{ row }">{{ row.identityNumber || '--' }}</template></el-table-column>
            <el-table-column prop="academicClassName" label="行政班" min-width="180"><template #default="{ row }">{{ row.academicClassName || '未分配行政班' }}</template></el-table-column>
            <el-table-column prop="email" label="邮箱" min-width="180"><template #default="{ row }">{{ row.email || '--' }}</template></el-table-column>
            <el-table-column label="账号状态" width="110"><template #default="{ row }"><el-tag size="small" :type="studentStatusTagType(row.status)">{{ studentStatusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="加入时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.joinedAt) }}</template></el-table-column>
            <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="danger" @click="removeCourseStudent(row)">移出班级</el-button></template></el-table-column>
          </el-table>
          <div v-if="!studentsLoading && !courseStudents.length" class="study-empty">{{ selectedStudentClassroom ? '该教学班暂时没有学生。' : '请先创建并选择一个教学班。' }}</div>
          <div class="student-management-footer">
            <p class="student-count-hint">当前教学班共 {{ courseStudentsTotal }} 名学生。未注册学生不会被导入。</p>
            <el-pagination v-if="courseStudentsTotal > studentPageSize" v-model:current-page="studentPage" v-model:page-size="studentPageSize" :total="courseStudentsTotal" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" @size-change="resetStudentPage" @current-change="loadCourseStudents" />
          </div>
        </section>
        <section v-else-if="courseSection === 'assignments'" class="course-study-section assignment-course-section">
          <AssignmentsView />
        </section>
        <section v-else class="course-study-section">
          <div class="chapter-directory-actions">
            <el-button v-if="canManageChapters" type="primary" :icon="Plus" @click="openChapterCreate">新增章节</el-button>
          </div>
          <div class="chapter-study-list">
            <article v-for="(chapter, index) in chapterSummaries" :key="chapter.id" class="chapter-outline-item">
              <div class="chapter-outline-heading">
                <div class="chapter-index">{{ index + 1 }}</div>
                <strong class="chapter-study-title">{{ chapterLabel(chapter, index) }} {{ chapter.title || chapter.id }}</strong>
                <div v-if="canManageChapters" class="chapter-management-actions">
                  <el-button link type="primary" :icon="Plus" @click="openSectionCreate(chapter)">新增小节</el-button>
                  <el-button link type="primary" :icon="EditPen" @click="openChapterEdit(chapter)">{{ chapter.chapterId ? '编辑章节' : '完善信息' }}</el-button>
                  <el-button v-if="chapter.chapterId" link type="danger" :icon="Delete" @click="archiveChapter(chapter)">删除章节</el-button>
                </div>
              </div>
              <div class="chapter-section-list">
                <div v-for="(section, sectionIndex) in chapter.sections" :key="section.id" class="chapter-section-row">
                  <button type="button" class="chapter-section-item" :class="{ 'is-complete': sectionCompleted(section) }" @click="openSection(chapter, section)">
                    <span class="section-index" :class="{ 'is-complete': sectionCompleted(section) }"><span v-if="sectionCompleted(section)">✓</span><template v-else>{{ chapterNumber(chapter, index) }}.{{ sectionIndex + 1 }}</template></span>
                    <span>{{ section.title }}</span>
                  </button>
                  <span v-if="canManageChapters" class="section-actions" @click.stop>
                    <el-button link type="primary" @click="openSectionEdit(section)">编辑</el-button>
                    <el-button link type="warning" @click="archiveSection(section)">归档</el-button>
                  </span>
                </div>
                <span v-if="!chapter.sections.length" class="chapter-section-empty">本章暂未配置小节</span>
              </div>
            </article>
            <div v-if="!chapters.length" class="study-empty">该课程暂未配置章节目录。</div>
          </div>
        </section>
      </main>
    </section>

    <section v-else-if="isChapterOverview" class="resource-hub">
      <div class="resource-hub-heading">
        <div>
          <button class="resource-back" type="button" @click="returnToCourseOverview">返回章节目录</button>
          <h3>{{ activeCourse.courseName }} · {{ selectedChapter?.id || route.query.chapter }}<span v-if="selectedSection"> · {{ selectedSection.title }}</span></h3>
          <p v-if="selectedSection">{{ selectedChapter?.description || '选择资源类型进入' }}</p>
          <p v-else>章节仅用于组织小节，请从课程目录选择一个小节。</p>
        </div>
      </div>
      <div v-if="selectedSection" class="resource-category-grid">
        <button v-for="resource in resourceTypes" :key="resource.id" type="button" class="resource-category" :class="[`tone-${resource.tone}`]" @click="openResourceCategory(resource)">
          <span class="resource-category-icon"><component :is="resource.icon" /></span><span>{{ resource.title }}</span>
        </button>
      </div>
       <div v-else class="study-empty">请选择一个小节后查看资源。</div>
    </section>

    <section v-else-if="isKnowledgeMap" class="knowledge-map-reader" aria-labelledby="knowledge-map-title">
      <div class="resource-list-heading knowledge-map-heading">
        <div>
          <button class="resource-back" type="button" @click="returnToResourceTypes">返回资源类型</button>
          <h3 id="knowledge-map-title">{{ activeCourse?.courseName || '课程资源' }} · 知识图谱</h3>
        </div>
        <div class="resource-list-actions">
          <el-button type="primary" plain :loading="loading" @click="loadList">刷新图谱</el-button>
          <el-button v-if="canBuildKnowledgeMap" type="primary" :loading="knowledgeMapGenerating" @click="generateKnowledgeMap">按章节目录生成</el-button>
          <el-button v-if="canBuildKnowledgeMap" plain @click="openCreate">上传图谱文件</el-button>
          <el-button v-if="canReviewKnowledgeMap && ['PENDING', 'REJECTED'].includes(knowledgeMapResource.status)" type="success" plain @click="reviewKnowledgeMap(knowledgeMapResource, 'APPROVE')">审核通过</el-button>
          <el-button v-if="canReviewKnowledgeMap && knowledgeMapResource.status === 'PENDING'" type="danger" plain @click="reviewKnowledgeMap(knowledgeMapResource, 'REJECT')">驳回</el-button>
          <el-button v-if="knowledgeMapResource && canDeleteResource(knowledgeMapResource)" type="danger" plain @click="removeCourseware(knowledgeMapResource.id)">删除知识图谱</el-button>
        </div>
      </div>
      <KnowledgeBubbleGraph
        v-if="!showKnowledgeMapEditor"
        :course-name="activeCourse?.courseName"
        :chapter="''"
        :chapters="knowledgeMapResource ? chapters : []"
        :sections="sectionOptions"
        :resources="list"
      />
      <KnowledgeBubbleGraph
        v-else
        :course-name="activeCourse?.courseName"
        :chapter="''"
        :chapters="chapters"
        :sections="sectionOptions"
        :resources="list"
        editable
        editor-open
        submission-hint="知识图谱独立维护，不会修改教学大纲；保存后在课程内直接发布，高风险内容会进入复核。"
        @invalid="ElMessage.warning('请先填写所有知识图谱节点，再保存。')"
        @submit="submitKnowledgeMap"
      />
      <div v-if="knowledgeMapResource" class="knowledge-map-status-row">
        <span>当前状态</span>
               <el-tag :type="statusInfo(knowledgeMapResource.status)[1]">{{ statusInfo(knowledgeMapResource.status)[0] }}</el-tag>
        <span v-if="knowledgeMapResource.auditRemark" class="knowledge-map-audit-remark">{{ knowledgeMapResource.auditRemark }}</span>
      </div>
    </section>

    <section v-else-if="isTeachingVideo" class="teaching-video-reader" aria-labelledby="teaching-video-title">
      <div class="resource-list-heading teaching-video-heading">
        <div>
          <button class="resource-back" type="button" @click="returnToResourceTypes">返回资源类型</button>
          <h3 id="teaching-video-title">{{ activeCourse?.courseName || '课程资源' }} · {{ selectedChapter?.id || route.query.chapter }} · 教学视频</h3>
        </div>
      </div>
      <TeachingVideoPanel
        :course-id="activeCourse?.id || query.courseId"
        :course-name="activeCourse?.courseName"
        :chapter="selectedChapter?.id || route.query.chapter"
        :section-id="selectedSection?.id || route.query.sectionId"
      />
    </section>

    <section v-else-if="isQuestionBank" class="question-bank-reader" aria-labelledby="question-bank-title">
      <div class="resource-list-heading question-bank-heading">
        <div>
          <button class="resource-back" type="button" @click="returnToResourceTypes">返回资源类型</button>
          <h3 id="question-bank-title">{{ activeCourse?.courseName || '课程资源' }} · {{ selectedChapter?.id || route.query.chapter }}<span v-if="selectedSection"> · {{ selectedSection.title }}</span></h3>
        </div>
      </div>
          <QuestionBankPanel :course-id="activeCourse?.id || query.courseId" :chapter="selectedChapter?.id || route.query.chapter" :section-id="selectedSection?.id || route.query.sectionId" :question-id="route.query.questionId" />
    </section>

        <section v-else-if="isTeachingOutline" class="outline-reader" aria-labelledby="outline-reader-title" style="display:none">
      <div class="resource-list-heading outline-reader-heading">
        <div>
          <button class="resource-back" type="button" @click="returnToResourceTypes">返回资源类型</button>
          <h3 id="outline-reader-title">{{ activeCourse?.courseName || '课程资源' }} · 教学大纲</h3>
        </div>
        <div v-if="canManage || authStore.isAdmin" class="resource-list-actions">
          <el-button v-if="canGenerateOutline" type="primary" :icon="MagicStick" :loading="outlineGenerating" @click="generateTeachingOutline">AI 助手生成大纲</el-button>
          <el-button v-if="outlineResource && canDeleteResource(outlineResource)" type="danger" plain @click="removeCourseware(outlineResource.id)">删除大纲</el-button>
          <el-button v-if="canReviewOutline && ['PENDING', 'REJECTED'].includes(outlineResource.status)" type="success" plain @click="reviewOutline(outlineResource, 'APPROVE')">审核通过</el-button>
          <el-button v-if="canReviewOutline && outlineResource.status === 'PENDING'" type="danger" plain @click="reviewOutline(outlineResource, 'REJECT')">驳回</el-button>
          <el-button v-if="canReviewOutlineMindMap && ['PENDING', 'REJECTED'].includes(outlinePreviewResource.status)" type="success" plain @click="reviewKnowledgeMap(outlinePreviewResource, 'APPROVE')">审核通过思维导图</el-button>
          <el-button v-if="canReviewOutlineMindMap && outlinePreviewResource.status === 'PENDING'" type="danger" plain @click="reviewKnowledgeMap(outlinePreviewResource, 'REJECT')">驳回思维导图</el-button>
          <el-button v-if="canReviewOutline && outlineResource.status === 'ACTIVE'" type="warning" plain @click="archiveOutline(outlineResource)">归档大纲</el-button>
          <el-button v-if="canBuildKnowledgeMap && outlineResource" type="primary" plain @click="openEdit(outlineResource)">编辑教学大纲</el-button>
          <el-button v-if="!outlineResource && canCreateOutline" type="primary" @click="openCreate">上传教学大纲</el-button>
        </div>
      </div>

      <div v-if="outlineError" class="outline-reader-state outline-reader-error">
        <strong>教学大纲加载失败</strong>
        <span>{{ outlineError }}</span>
        <el-button type="primary" @click="loadList">重新加载</el-button>
      </div>
      <div v-else-if="outlinePreviewResource" class="outline-mindmap-shell">
        <div class="outline-mindmap-meta">
          <div>
            <strong>{{ outlinePreviewResource.title }}</strong>
            <span>{{ outlinePreviewResource.fileName || '教师提交的思维导图' }}<template v-if="outlinePreviewResource.resourceType === 'knowledge-map'"> · 待审核预览</template></span>
          </div>
          <el-tag :type="outlinePreviewResource.resourceType === 'knowledge-map' ? 'warning' : 'success'">思维导图{{ outlinePreviewResource.resourceType === 'knowledge-map' ? '预览' : '' }}</el-tag>
        </div>
        <div class="outline-mindmap" aria-label="教学大纲思维导图">
          <div class="mindmap-root">
            <span>{{ activeCourse?.courseName || '课程' }}</span>
            <strong>课程章节与小节教学结构</strong>
          </div>
          <div class="mindmap-connector" aria-hidden="true"></div>
          <div class="mindmap-branches">
            <article v-for="branch in outlineMindMap" :key="branch.label" class="mindmap-branch">
              <h4>{{ branch.label }}</h4>
              <div class="mindmap-branch-line" aria-hidden="true"></div>
              <div class="mindmap-items">
                <div v-for="item in branch.items" :key="item" class="mindmap-item">{{ item }}</div>
              </div>
            </article>
          </div>
        </div>
      </div>
      <div v-else class="outline-reader-state">
        <strong>本课程暂无教学大纲</strong>
        <div v-if="canCreateOutline || canGenerateOutline" class="outline-empty-actions">
          <el-button v-if="canGenerateOutline" type="primary" :icon="MagicStick" :loading="outlineGenerating" @click="generateTeachingOutline">AI 助手生成大纲</el-button>
          <el-button v-if="canCreateOutline" type="primary" plain @click="openCreate">上传教学大纲</el-button>
        </div>
      </div>
    </section>

    <template v-else>
      <div class="resource-list-heading">
      <div>
          <button class="resource-back" type="button" @click="returnToResourceTypes">返回资源类型</button>
          <h3>{{ activeCourse?.courseName || '课程资源' }} · {{ selectedChapter?.id || route.query.chapter }}<span v-if="selectedSection"> · {{ selectedSection.title }}</span> · {{ selectedResource?.title }}</h3>
        </div>
        <div class="resource-list-actions">
          <el-button type="primary" @click="loadList" :loading="loading">刷新列表</el-button>
          <el-button v-if="canManage" plain @click="openCreate">上传资源</el-button>
        </div>
      </div>

      <div v-if="isTeachingCourseware" class="courseware-content-list" v-loading="loading">
        <template v-if="list.length">
          <article v-for="row in list" :key="row.id" class="courseware-content-item">
            <div class="courseware-file-icon" aria-hidden="true"><Files /></div>
            <div class="courseware-content-main">
              <strong>{{ row.fileName || row.title }}</strong>
              <span>资料</span>
            </div>
            <el-tag size="small" :type="row.completed ? 'success' : 'info'">{{ row.completed ? '已完成' : '待学习' }}</el-tag>
            <div class="courseware-item-actions">
              <el-button link @click="loadDetail(row)">详情</el-button>
              <el-button link type="primary" @click="openPreview(row)">{{ previewActionLabel(row) }}</el-button>
              <el-button link type="primary" @click="downloadCourseware(row)">下载</el-button>
              <template v-if="authStore.isAdmin">
                <el-button v-if="row.visibility === 'PUBLIC' && (row.status === 'PENDING' || row.status === 'REJECTED')" link type="success" @click="reviewCourseware(row, 'APPROVE')">通过</el-button>
                <el-button v-if="row.visibility === 'PUBLIC' && row.status === 'PENDING'" link type="danger" @click="reviewCourseware(row, 'REJECT')">驳回</el-button>
                <el-button v-if="row.visibility === 'PUBLIC' && row.status === 'ACTIVE'" link type="warning" @click="archiveCourseware(row)">归档</el-button>
              </template>
              <el-button v-if="canEditResource(row)" link @click="openEdit(row)">编辑</el-button>
              <el-button v-if="canDeleteResource(row)" link type="danger" @click="removeCourseware(row.id)">删除</el-button>
            </div>
          </article>
        </template>
        <div v-else-if="!loading" class="courseware-empty">
          <Files />
          <strong>本章节暂无教学课件</strong>
        </div>
      </div>

      <div v-if="!isTeachingCourseware" class="toolbar">
      <el-input v-model="query.keyword" class="toolbar-grow" clearable placeholder="搜索标题或描述" />
      <el-select v-model="query.visibility" clearable placeholder="可见性" style="width: 140px;">
        <el-option
          v-for="item in coursewareVisibilityOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-select v-model="query.targetAudience" clearable placeholder="面向对象" style="width: 140px;">
        <el-option
          v-for="item in targetAudienceOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-select
        v-model="query.classroomId"
        clearable
        placeholder="班级"
        style="width: 180px;"
      >
        <el-option
          v-for="item in classroomOptions"
          :key="item.id"
          :label="item.className"
          :value="item.id"
        />
      </el-select>
      <el-button type="primary" @click="query.page = 1; loadList()">查询</el-button>
      </div>

      <el-table v-if="!isTeachingCourseware" :data="list" v-loading="loading">
      <el-table-column prop="title" label="标题" min-width="240" />
      <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
      <el-table-column label="可见性" width="120">
        <template #default="{ row }">
          {{ optionLabel(coursewareVisibilityOptions, row.visibility) }}
        </template>
      </el-table-column>
      <el-table-column label="面向对象" width="120">
        <template #default="{ row }">
          {{ optionLabel(targetAudienceOptions, row.targetAudience) }}
        </template>
      </el-table-column>
      <el-table-column label="大小" width="120">
        <template #default="{ row }">
          {{ formatFileSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column label="下载量" width="100">
        <template #default="{ row }">
          {{ row.downloadCount || 0 }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="260">
        <template #default="{ row }">
          <el-button link @click="loadDetail(row)">详情</el-button>
          <el-button link type="primary" @click="openPreview(row)">{{ previewActionLabel(row) }}</el-button>
          <el-button link type="primary" @click="downloadCourseware(row)">下载</el-button>
          <el-tag v-if="['teaching-courseware', 'teaching-outline', 'knowledge-map'].includes(row.resourceType)" size="small" :type="statusInfo(row.status)[1]">{{ statusInfo(row.status)[0] }}</el-tag>
          <template v-if="authStore.isAdmin && row.resourceType === 'teaching-courseware'">
            <el-button v-if="row.visibility === 'PUBLIC' && (row.status === 'PENDING' || row.status === 'REJECTED')" link type="success" @click="reviewCourseware(row, 'APPROVE')">通过</el-button>
            <el-button v-if="row.visibility === 'PUBLIC' && row.status === 'PENDING'" link type="danger" @click="reviewCourseware(row, 'REJECT')">驳回</el-button>
            <el-button v-if="row.visibility === 'PUBLIC' && row.status === 'ACTIVE'" link type="warning" @click="archiveCourseware(row)">归档</el-button>
          </template>
          <template v-if="authStore.isAdmin && row.resourceType === 'teaching-outline'">
            <el-button v-if="row.visibility === 'PUBLIC' && (row.status === 'PENDING' || row.status === 'REJECTED')" link type="success" @click="reviewOutline(row, 'APPROVE')">通过</el-button>
            <el-button v-if="row.visibility === 'PUBLIC' && row.status === 'PENDING'" link type="danger" @click="reviewOutline(row, 'REJECT')">驳回</el-button>
            <el-button v-if="row.visibility === 'PUBLIC' && row.status === 'ACTIVE'" link type="warning" @click="archiveOutline(row)">归档</el-button>
          </template>
          <el-button v-if="canEditResource(row)" link @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canDeleteResource(row)" link type="danger" @click="removeCourseware(row.id)">
            删除
          </el-button>
        </template>
      </el-table-column>
      </el-table>

      <div v-if="!isTeachingCourseware" class="resource-pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="loadList"
      />
      </div>
    </template>

    <el-dialog
      v-model="courseCreateVisible"
        :title="courseEditingId ? '编辑课程' : '新建课程'"
      width="640px"
    >
      <el-form label-position="top">
        <div class="course-create-grid">
          <el-form-item v-if="authStore.role === 'SUPER_ADMIN'" label="所属学校" required><el-select v-model="courseForm.schoolId" filterable style="width: 100%;" placeholder="请选择学校" @change="changeCourseSchool"><el-option v-for="school in courseSchoolOptions.filter(item => item.status === 'ACTIVE')" :key="school.id" :label="`${school.name}（${school.code}）`" :value="school.id" /></el-select></el-form-item>
          <el-form-item label="课程名称" required><el-input v-model="courseForm.courseName" /></el-form-item>
          <el-form-item label="课程代码"><el-input v-model="courseForm.courseCode" placeholder="可留空自动生成" /></el-form-item>
          <el-form-item label="课程封面" class="course-create-wide">
            <div class="course-cover-editor">
              <img v-if="courseForm.coverImage" :src="resolveAssetUrl(courseForm.coverImage)" alt="课程封面预览" />
              <span v-else>暂未上传课程封面</span>
              <label class="course-cover-upload"><UploadFilled />{{ courseForm.coverImage ? '更换封面' : '上传封面' }}<input type="file" accept="image/jpeg,image/png,image/gif,image/webp" @change="uploadCourseCover" /></label>
              <el-button v-if="courseForm.coverImage" link type="danger" @click="courseForm.coverImage = ''">移除</el-button>
            </div>
          </el-form-item>
          <el-form-item label="学期" required><el-select v-model="courseForm.semester" clearable filterable placeholder="请选择学期" style="width: 100%;"><el-option v-for="semester in semesters" :key="semester" :label="semester" :value="semester" /></el-select></el-form-item>
          <el-form-item label="学分"><el-input-number v-model="courseForm.credits" :min="0" :step="0.5" controls-position="right" style="width: 100%;" /></el-form-item>
          <el-form-item label="课程类别" required><el-select v-model="courseForm.courseCategory" clearable filterable placeholder="请选择课程类别" style="width: 100%;"><el-option v-for="item in courseCategories" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="开课院系" required><el-select v-model="courseForm.teachingDepartment" clearable filterable placeholder="请先在学校管理中配置院系" style="width: 100%;"><el-option v-for="department in courseDepartmentOptions" :key="department.id" :label="department.name" :value="department.name" /><el-option v-if="courseForm.teachingDepartment && !courseDepartmentOptions.some(item => item.name === courseForm.teachingDepartment)" :label="`${courseForm.teachingDepartment}（历史数据）`" :value="courseForm.teachingDepartment" /></el-select><div v-if="!courseDepartmentOptions.length" class="form-hint">当前学校尚未配置院系，请联系管理员维护。</div></el-form-item>
          <el-form-item label="允许进入课程的行政班" class="course-create-wide">
            <el-select v-model="courseForm.allowedAcademicClassIds" multiple filterable clearable collapse-tags style="width: 100%" placeholder="不选择表示不限制">
              <el-option v-for="item in academicClasses" :key="item.id" :label="`${item.name} · ${item.grade || ''} ${item.major || ''}`" :value="item.id" />
            </el-select>
            <div class="form-hint">选择后仅这些行政班的学生可以通过课程代码或邀请码加入。</div>
          </el-form-item>
        </div>
        <el-form-item label="课程简介"><el-input v-model="courseForm.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="courseCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="courseCreateLoading" @click="saveCourse">{{ courseEditingId ? '保存修改' : '创建课程' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="chapterDialogVisible"
      :title="chapterDialogTitle"
      width="560px"
    >
      <el-form label-position="top">
        <el-form-item label="章节名称" required><el-input v-model="chapterForm.title" maxlength="100" /></el-form-item>
        <el-form-item label="排序号" required><el-input-number v-model="chapterForm.sortOrder" :min="1" :step="1" controls-position="right" /></el-form-item>
        <el-alert title="请输入章节位置：1 显示为“第一章”，2 显示为“第二章”。已有章节会自动顺延。" type="info" :closable="false" show-icon />
      </el-form>
      <template #footer>
        <el-button @click="pendingSectionChapterTitle = ''; chapterDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="chapterSaving" @click="saveChapter">保存章节</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="sectionDialogVisible"
      :title="sectionEditingId ? '编辑小节' : '新增小节'"
      width="560px"
    >
      <el-form label-position="top">
        <el-form-item label="小节名称" required><el-input v-model="sectionForm.title" maxlength="100" placeholder="例如：1.1 基本概念" /></el-form-item>
        <el-form-item label="小节副标题"><el-input v-model="sectionForm.subtitle" maxlength="200" /></el-form-item>
        <el-form-item label="小节说明"><el-input v-model="sectionForm.description" type="textarea" :rows="3" maxlength="500" /></el-form-item>
        <el-form-item label="排序号"><el-input-number v-model="sectionForm.sortOrder" :min="0" :step="1" controls-position="right" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sectionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sectionSaving" @click="saveSection">保存小节</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editorVisible"
      :title="editingId ? '编辑资源' : '上传资源'"
      width="760px"
    >
      <el-form label-position="top">
        <div class="two-column">
          <div>
            <el-form-item label="资源类别">
              <el-input :model-value="isTeachingOutline ? '教学大纲' : (selectedResource?.title || '教学资源')" disabled />
            </el-form-item>
            <el-form-item v-if="isTeachingOutline" label="所属课程">
              <el-input :model-value="activeCourse?.courseName || `课程 ${query.courseId || ''}`" disabled />
              <div class="footer-note">当前页面已确定课程，教学大纲自动保存到这门课程，无需再次选择课程。</div>
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="editor.title" />
            </el-form-item>
            <el-form-item v-if="!isTeachingOutline" label="可见性">
              <el-select v-model="editor.visibility" style="width: 100%;">
                <el-option
                  v-for="item in coursewareVisibilityOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="!isTeachingOutline" label="面向对象">
              <el-select v-model="editor.targetAudience" style="width: 100%;">
                <el-option
                  v-for="item in targetAudienceOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <div v-else class="outline-scope-note">
              <span>适用范围</span>
              <strong>本课程全部学习者</strong>
              <small>教学大纲为课程级资料，不区分行政班。</small>
            </div>
          </div>
          <div>
            <el-form-item v-if="!isTeachingOutline" label="所属班级">
              <el-select v-model="editor.classroomId" clearable style="width: 100%;">
                <el-option
                  v-for="item in classroomOptions"
                  :key="item.id"
                  :label="item.className"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="资源文件">
               <input type="file" :accept="isTeachingOutline ? 'application/pdf,.pdf' : isKnowledgeMap ? '.json,.mm,.xmind,.pdf,.png,.jpg,.jpeg' : isTeachingCourseware ? '.ppt,.pptx,.pdf' : '.pdf,.ppt,.pptx,.doc,.docx,.jpg,.jpeg,.png,.gif,.mp4,.zip,.rar,.txt'" @change="uploadFile" />
              <div class="footer-note" style="margin-top: 8px;">
                 {{ uploadLoading ? '正在上传资源...' : isTeachingOutline ? '支持上传 PDF 源文件，页面会按导图内容说明生成思维导图' : isKnowledgeMap ? '支持上传 JSON、XMind、MindMup、PDF 或图片；课程内直接发布' : isTeachingCourseware ? '仅支持上传 PPT、PPTX、PDF；课程内直接发布，全校公开需审核' : '支持上传 pdf、ppt、doc、图片、视频、压缩包等后端允许的类型' }}
              </div>
            </el-form-item>
            <el-form-item label="已上传文件" v-if="editor.fileName">
              <div class="muted">
                {{ editor.fileName }} · {{ formatFileSize(editor.fileSize) }}
              </div>
            </el-form-item>
          </div>
        </div>
        <el-form-item :label="isTeachingOutline ? '导图内容说明' : '描述'">
          <el-input v-model="editor.description" type="textarea" :rows="8" :placeholder="isTeachingOutline ? '输入教学大纲内容，使用句号、分号、逗号或换行分隔导图节点。' : ''" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="saveCourseware">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewVisible"
      :title="preview?.title || '在线查看资源'"
      width="min(1080px, 94vw)"
      top="5vh"
      destroy-on-close
      @closed="clearPreview"
    >
      <div v-if="preview" class="courseware-preview">
        <video
          v-if="previewKind === 'video'"
          :src="resolveAssetUrl(preview.fileUrl)"
          controls
          controlslist="nodownload"
          class="courseware-preview-media"
        />
        <audio
          v-else-if="previewKind === 'audio'"
          :src="resolveAssetUrl(preview.fileUrl)"
          controls
          class="courseware-preview-audio"
        />
        <iframe
          v-else-if="previewKind === 'pdf'"
          :src="resolveAssetUrl(preview.fileUrl)"
          :title="preview.fileName"
          class="courseware-preview-pdf"
        />
        <img
          v-else-if="previewKind === 'image'"
          :src="resolveAssetUrl(preview.fileUrl)"
          :alt="preview.fileName"
          class="courseware-preview-image"
        />
      </div>

      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="downloadCourseware(preview)">下载资源</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="academicImportVisible" title="从本校行政班导入学生" width="520px">
      <p class="student-import-hint">导入到：{{ selectedStudentClassroom?.className || '当前教学班' }}。系统会自动跳过未注册学生和已在班学生。</p>
      <el-select v-model="academicImportId" placeholder="选择本校行政班" style="width: 100%;">
        <el-option v-for="item in academicClasses" :key="item.id" :label="item.className || item.name" :value="item.id" />
      </el-select>
      <template #footer><el-button @click="academicImportVisible = false">取消</el-button><el-button type="primary" :loading="academicImportLoading" @click="importAcademicStudents">开始导入</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="资源详情" size="55%">
      <template v-if="detail">
        <div class="page-stack">
          <div class="card-head">
            <div>
              <h3>{{ detail.title }}</h3>
              <p>
                {{ detail.fileName }} · {{ detail.teacherName || `教师 ${detail.teacherId}` }} ·
                {{ formatDateTime(detail.createdAt) }}
              </p>
            </div>
            <el-tag>{{ detail.status }}</el-tag>
          </div>
          <p>{{ detail.description || '暂无描述' }}</p>
          <div class="chips">
            <el-tag>{{ optionLabel(coursewareVisibilityOptions, detail.visibility) }}</el-tag>
            <el-tag>{{ optionLabel(targetAudienceOptions, detail.targetAudience) }}</el-tag>
            <el-tag>{{ formatFileSize(detail.fileSize) }}</el-tag>
            <el-tag v-if="detail.className">{{ detail.className }}</el-tag>
          </div>
          <div style="display: flex; gap: 10px;">
            <el-button type="primary" @click="openPreview(detail)">{{ previewActionLabel(detail) }}</el-button>
            <el-button @click="downloadCourseware(detail)">下载资源</el-button>
            <el-button
              v-if="authStore.isLoggedIn && authStore.user?.id !== detail.teacherId"
              type="danger"
              plain
              @click="reportResource(detail)"
            >举报资源</el-button>
            <el-button
              v-if="authStore.user?.id === detail.teacherId && detail.status === 'REJECTED' && detail.visibility !== 'PUBLIC'"
              type="warning"
              plain
              @click="appealResource(detail)"
            >申诉处置</el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.knowledge-map-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  color: #7b8798;
  font-size: 12px;
}

.knowledge-map-audit-remark {
  color: #b04c4c;
}

.courseware-preview {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f172a;
}

.courseware-preview-media,
.courseware-preview-pdf {
  width: 100%;
  max-height: 72vh;
  aspect-ratio: 16 / 9;
  border: 0;
}

.courseware-preview-audio {
  width: min(620px, 90%);
}

.courseware-preview-image {
  max-width: 100%;
  max-height: 72vh;
  object-fit: contain;
}

.outline-reader {
  display: flex;
  min-height: calc(100vh - 132px);
  flex-direction: column;
  gap: 18px;
}

.outline-scope-note {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 8px 0 18px;
  padding: 10px 12px;
  border: 1px solid #dfe8f5;
  border-radius: 6px;
  background: #f7faff;
  color: var(--text-muted);
  font-size: 12px;
}

.outline-scope-note strong {
  color: var(--text-main);
  font-size: 14px;
}

.outline-reader-heading {
  margin-top: 8px;
}

.outline-reader-state {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  border: 1px dashed var(--el-border-color);
  color: var(--text-muted);
  text-align: center;
}

.outline-reader-state strong {
  color: var(--text-main);
}

.outline-reader-state span {
  max-width: min(560px, 90%);
  line-height: 1.6;
}

.outline-empty-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.outline-reader-error {
  color: var(--el-color-danger);
}

.outline-mindmap-shell {
  display: flex;
  min-height: 520px;
  flex: 1;
  flex-direction: column;
  border: 1px solid var(--el-border-color-light);
  background: #f7f9fc;
}

.outline-mindmap-meta {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.outline-mindmap-meta > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.outline-mindmap-meta strong,
.outline-mindmap-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-mindmap-meta span {
  color: var(--text-muted);
  font-size: 12px;
}

.outline-mindmap {
  display: flex;
  min-height: 460px;
  align-items: center;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  overflow: auto;
  padding: 44px 32px 52px;
}

.mindmap-root {
  display: flex;
  min-width: 220px;
  max-width: min(420px, 90vw);
  align-items: center;
  flex-direction: column;
  gap: 6px;
  padding: 16px 30px;
  border: 2px solid #3b82f6;
  border-radius: 12px;
  background: #eaf2ff;
  color: #1e3a8a;
  text-align: center;
  box-shadow: 0 8px 20px rgba(59, 130, 246, 0.12);
}

.mindmap-root span {
  font-size: 13px;
}

.mindmap-root strong {
  font-size: 20px;
}

.mindmap-connector {
  width: 2px;
  height: 34px;
  background: #9bbcf4;
}

.mindmap-branches {
  position: relative;
  display: grid;
  width: min(1080px, 100%);
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

.mindmap-branches::before {
  position: absolute;
  top: 0;
  right: 16%;
  left: 16%;
  height: 2px;
  background: #9bbcf4;
  content: '';
}

.mindmap-branch {
  position: relative;
  display: flex;
  min-width: 0;
  align-items: center;
  flex-direction: column;
  padding-top: 20px;
}

.mindmap-branch::before {
  position: absolute;
  top: 0;
  width: 2px;
  height: 20px;
  background: #9bbcf4;
  content: '';
}

.mindmap-branch h4 {
  width: min(230px, 100%);
  margin: 0;
  padding: 11px 14px;
  border: 1px solid #c7d9f7;
  border-radius: 8px;
  background: #fff;
  color: #1d4ed8;
  font-size: 16px;
  text-align: center;
  box-shadow: 0 4px 12px rgba(31, 78, 121, 0.08);
}

.mindmap-branch-line {
  width: 2px;
  height: 16px;
  background: #c5d7f3;
}

.mindmap-items {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 8px;
}

.mindmap-item {
  position: relative;
  padding: 10px 12px 10px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.55;
  box-shadow: 0 2px 8px rgba(30, 64, 175, 0.05);
}

.mindmap-item::before {
  position: absolute;
  top: 17px;
  left: 8px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #60a5fa;
  content: '';
}

.resource-page {
  display: flex;
  min-height: calc(100vh - 120px);
  flex-direction: column;
  gap: 24px;
  padding-bottom: 0;
}

.resource-hub {
  padding: 24px 28px 28px;
  border: 1px solid rgba(84, 126, 255, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-soft);
}

.resource-hub-heading,
.resource-list-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.resource-hub-heading h3,
.resource-list-heading h3 {
  margin: 0;
  color: var(--text-main);
  font-size: 20px;
}

.resource-hub-heading p,
.resource-list-heading p {
  margin: 6px 0 0;
  color: var(--text-muted);
}

.resource-hub-count {
  color: var(--text-soft);
  font-size: 14px;
  white-space: nowrap;
}

.resource-hub-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.join-course-input {
  width: min(280px, 28vw);
}

.course-create-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.course-create-wide { grid-column: 1 / -1; }
.course-cover-editor { display: flex; align-items: center; gap: 12px; min-height: 72px; color: var(--text-muted); font-size: 13px; }
.course-cover-editor img { width: 136px; height: 72px; object-fit: cover; border: 1px solid var(--line); border-radius: 6px; }
.course-cover-upload { display: inline-flex; align-items: center; gap: 5px; padding: 8px 12px; border: 1px solid var(--line); border-radius: 5px; color: var(--brand); background: #fff; cursor: pointer; }
.course-cover-upload:hover { border-color: var(--brand); background: #f6faff; }
.course-cover-upload input { display: none; }

.course-resource-heading {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 24px 0 12px;
}

.course-resource-heading h4 {
  margin: 0;
  color: var(--text-main);
  font-size: 16px;
}

.course-resource-heading span {
  color: var(--text-muted);
  font-size: 12px;
}

.course-resource-grid {
  margin-bottom: 22px;
}

.chapter-heading {
  margin-top: 26px;
}

.resource-category-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px 28px;
  margin-top: 24px;
}

.chapter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 24px;
}

.chapter-card {
  min-height: 116px;
  padding: 18px;
  border: 1px solid var(--line);
  border-left: 4px solid var(--brand);
  border-radius: 6px;
  background: #fff;
  color: var(--text-main);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  font: inherit;
  text-align: left;
}

.chapter-card:hover {
  border-color: var(--brand);
  background: var(--bg-soft);
}

.chapter-card strong {
  font-size: 16px;
}

.chapter-card-sections {
  display: grid;
  width: 100%;
  gap: 4px;
  padding-left: 10px;
  border-left: 2px solid #f0ad56;
}

.chapter-card-sections span {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chapter-card-section-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chapter-card-section-row > span:first-child {
  flex: 1;
}

.section-actions {
  display: inline-flex;
  flex: 0 0 auto;
}

.chapter-card span {
  color: var(--text-muted);
  font-size: 14px;
  line-height: 1.5;
}

.chapter-card small {
  color: var(--text-soft);
  font-size: 12px;
}

.chapter-card-actions {
  display: flex;
  gap: 4px;
  margin-top: auto;
}

.course-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; margin-top: 24px; }
.course-card { overflow: hidden; border: 1px solid var(--line); border-radius: 8px; background: #fff; color: var(--text-main); box-shadow: 0 4px 14px rgba(25, 65, 120, .05); }
.course-card-cover { position: relative; display: block; width: 100%; height: 148px; padding: 0; border: 0; background: linear-gradient(135deg, #dbeafe, #eff6ff); cursor: pointer; }
.course-card-cover img { width: 100%; height: 100%; object-fit: cover; }
.course-card-placeholder { display: flex; height: 100%; align-items: center; justify-content: center; flex-direction: column; gap: 8px; color: var(--brand-deep); font-size: 13px; }
.course-card-cover em { position: absolute; top: 12px; right: 12px; padding: 4px 8px; border-radius: 4px; background: #fff; font-size: 12px; font-style: normal; }
.course-card-cover em.active { color: #27815f; }.course-card-cover em.archived { color: #7b8494; }
.course-card-body { padding: 15px 16px 14px; }.course-card-title { display: flex; width: 100%; padding: 0; border: 0; background: transparent; color: var(--text-main); flex-direction: column; align-items: flex-start; gap: 6px; cursor: pointer; font: inherit; text-align: left; }.course-card-title strong { overflow: hidden; width: 100%; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }.course-card-title span { color: var(--text-muted); font-size: 12px; }
.course-card-teacher { margin: 12px 0 0; color: var(--text-secondary); font-size: 13px; }.course-card-teacher::before { content: '授课教师：'; color: var(--text-muted); }.course-card-description { display: -webkit-box; min-height: 38px; margin: 10px 0 14px; overflow: hidden; color: var(--text-secondary); font-size: 13px; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.course-card-actions { display: flex; align-items: center; gap: 4px; padding-top: 12px; border-top: 1px solid var(--line); }.course-card-actions .el-dropdown { margin-left: auto; }.empty-course { grid-column: 1 / -1; padding: 28px 0; color: var(--text-muted); }

.course-study-shell { display: grid; min-height: 680px; grid-template-columns: 188px minmax(0, 1fr); gap: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 8px; background: #fff; }
.teacher-course-header-actions { display: flex; align-items: center; gap: 16px; }
.course-study-sidebar { border-right: 1px solid var(--line); background: #f8fafc; }
.course-study-brand { display: flex; align-items: center; gap: 9px; padding: 16px 13px; border-bottom: 1px solid var(--line); }.course-study-brand strong,.course-study-brand span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.course-study-brand strong { max-width: 126px; color: var(--text-main); font-size: 13px; }.course-study-brand span { max-width: 126px; margin-top: 4px; color: var(--text-muted); font-size: 11px; }.course-study-avatar { display: flex; width: 32px; height: 32px; align-items: center; justify-content: center; flex: 0 0 auto; border-radius: 6px; background: #e8f1ff; color: var(--brand); }.course-study-avatar :deep(svg) { width: 17px; height: 17px; }
.course-study-nav { display: grid; gap: 3px; padding: 11px 8px; }.course-study-nav button { display: flex; min-height: 40px; align-items: center; gap: 9px; padding: 9px 10px; border: 0; border-radius: 6px; background: transparent; color: var(--text-secondary); cursor: pointer; font-size: 13px; text-align: left; }.course-study-nav button :deep(svg) { width: 16px; height: 16px; flex: 0 0 16px; }.course-study-nav button:hover { background: #eef4fc; color: var(--brand-deep); }.course-study-nav button.active { background: #e8f1ff; color: var(--brand); font-weight: 600; }
.course-study-main { min-width: 0; padding: 28px 32px 36px; background: #fff; }.course-study-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding-bottom: 22px; border-bottom: 1px solid var(--line); }.course-study-header h3 { margin: 0; color: var(--text-main); font-size: 24px; }.course-study-header p { margin: 8px 0 0; color: var(--text-muted); }.course-study-progress { display: flex; min-width: 86px; align-items: center; flex-direction: column; padding: 8px 14px; border-left: 1px solid var(--line); }.course-study-progress strong { color: var(--brand-deep); font-size: 24px; }.course-study-progress span { margin-top: 4px; color: var(--text-muted); font-size: 12px; }
.course-study-section { padding-top: 24px; }.study-section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 16px; }.study-section-heading h4 { margin: 0; color: var(--text-main); font-size: 18px; }.study-section-heading p { margin: 6px 0 0; color: var(--text-muted); font-size: 13px; }.study-section-heading > span { color: var(--text-muted); font-size: 13px; white-space: nowrap; }.course-map-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; color: var(--text-main); font-size: 16px; font-weight: 700; }.course-map-toolbar > div { display: flex; flex-wrap: wrap; gap: 8px; }
.chapter-directory-actions { display: flex; min-height: 34px; align-items: center; justify-content: flex-end; margin-bottom: 16px; }
.question-bank-workbench { display: grid; grid-template-columns: minmax(210px, 260px) minmax(0, 1fr); gap: 20px; align-items: start; }.question-bank-outline { position: sticky; top: 16px; overflow: hidden; border: 1px solid var(--line); border-radius: 6px; background: #fbfcfe; }.question-bank-outline > strong { display: block; padding: 14px 16px; border-bottom: 1px solid var(--line); color: var(--text-main); font-size: 14px; }.question-bank-chapter-list { display: grid; gap: 12px; max-height: 620px; padding: 12px; overflow-y: auto; }.question-bank-chapter-list section { display: grid; gap: 3px; }.question-bank-chapter-list p { margin: 0 0 3px; color: #536578; font-size: 12px; font-weight: 700; }.question-bank-chapter-list button { display: grid; grid-template-columns: 38px minmax(0, 1fr); align-items: center; gap: 7px; width: 100%; min-height: 38px; padding: 8px; border: 1px solid transparent; border-radius: 5px; background: transparent; color: var(--text-secondary); cursor: pointer; font: inherit; font-size: 13px; text-align: left; }.question-bank-chapter-list button span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.question-bank-chapter-list button span:first-child { color: var(--text-muted); font-size: 12px; }.question-bank-chapter-list button:hover { background: #f0f5fd; color: var(--brand); }.question-bank-chapter-list button.active { border-color: #c7dbf8; background: #e8f1ff; color: var(--brand-deep); font-weight: 600; }.question-bank-chapter-list button.active span:first-child { color: var(--brand); }.question-bank-chapter-list em { padding: 6px 8px; color: var(--text-muted); font-size: 12px; font-style: normal; }.question-library-shell { min-width: 0; }
.chapter-study-list { display: grid; gap: 18px; }.chapter-outline-item { overflow: hidden; border: 1px solid var(--line); border-radius: 6px; background: #fff; }.chapter-outline-heading { display: flex; min-height: 68px; align-items: center; gap: 14px; padding: 14px 18px; background: #f4f7fa; }.chapter-outline-heading .chapter-study-title { flex: 1; }.chapter-index { display: flex; width: 34px; height: 34px; align-items: center; justify-content: center; flex: 0 0 auto; border-radius: 50%; background: #edf4ff; color: var(--brand); font-size: 13px; font-weight: 600; }.chapter-study-title { display: block; padding: 0; border: 0; background: transparent; color: var(--text-main); cursor: default; font: inherit; font-size: 16px; font-weight: 600; text-align: left; }.chapter-section-list { position: relative; display: grid; gap: 0; padding: 8px 24px 12px 72px; }.chapter-section-list::before { position: absolute; top: 0; bottom: 0; left: 45px; border-left: 1px dashed #cbd7e5; content: ''; }.chapter-section-row { display: flex; min-width: 0; align-items: center; gap: 8px; }.chapter-section-row .chapter-section-item { flex: 1; min-width: 0; }.chapter-section-item { position: relative; display: flex; align-items: center; gap: 14px; min-height: 52px; padding: 8px 10px; border: 0; background: transparent; color: var(--text-main); cursor: pointer; font: inherit; text-align: left; }.chapter-section-item::before { position: absolute; top: 50%; left: -32px; width: 8px; height: 8px; border-radius: 50%; background: #f3a442; content: ''; transform: translateY(-50%); }.chapter-section-item:hover { color: var(--brand); }.section-index { min-width: 38px; color: #e89b3b; font-size: 13px; }.chapter-section-empty { padding: 12px 10px; color: var(--text-muted); font-size: 13px; }
.chapter-study-list > .study-empty { border: 0; }
.course-study-shell { --el-color-primary: #5d96f7; --el-color-primary-light-3: #8bb5fa; --el-color-primary-light-5: #aecbfc; --el-color-primary-light-7: #d2e2fd; --el-color-primary-light-8: #e1ecfe; --el-color-primary-light-9: #f0f6ff; }
.study-metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }.study-metric-grid > div { padding: 20px; border: 1px solid var(--line); border-radius: 6px; background: #fbfdff; }.study-metric-grid strong,.study-metric-grid span { display: block; }.study-metric-grid strong { color: var(--brand-deep); font-size: 26px; }.study-metric-grid span { margin-top: 7px; color: var(--text-muted); font-size: 12px; }.chapter-study-chart { display: flex; min-height: 220px; align-items: flex-end; gap: 18px; margin-top: 24px; padding: 22px 18px 12px; border: 1px solid var(--line); border-radius: 6px; background: linear-gradient(180deg, #fbfdff, #fff); }.chapter-study-chart-item { display: flex; min-width: 58px; height: 180px; align-items: center; flex: 1; flex-direction: column; justify-content: flex-end; gap: 6px; }.chapter-study-chart-item > span { max-width: 96px; overflow: hidden; color: var(--text-secondary); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.chapter-study-chart-bar { display: flex; width: min(48px, 70%); height: 125px; align-items: flex-end; border-radius: 8px 8px 3px 3px; background: #edf3fb; }.chapter-study-chart-bar i { display: block; width: 100%; min-height: 8px; border-radius: 8px 8px 3px 3px; background: linear-gradient(180deg, #83b8fa, #4f8de0); }.chapter-study-chart-item strong { color: var(--brand-deep); font-size: 12px; }.chapter-study-chart-item small { color: var(--text-muted); font-size: 11px; }.analysis-list { display: grid; gap: 15px; margin-top: 24px; }.analysis-list > div { display: grid; grid-template-columns: 150px minmax(0, 1fr) 90px; align-items: center; gap: 14px; color: var(--text-secondary); font-size: 13px; }.analysis-list em { color: var(--text-muted); font-style: normal; text-align: right; }.analysis-track { height: 8px; overflow: hidden; border-radius: 999px; background: #e9eef5; }.analysis-track i { display: block; height: 100%; border-radius: inherit; background: #75aaf5; }.assignment-chapter-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }.assignment-chapter-list button { display: flex; align-items: center; gap: 10px; padding: 17px; border: 1px solid var(--line); border-radius: 6px; background: #fff; color: var(--text-main); cursor: pointer; font: inherit; text-align: left; }.assignment-chapter-list button:hover { border-color: #b9d2f5; background: #fbfdff; }.assignment-chapter-list button small { margin-left: auto; color: var(--brand); }.mistake-list { display: grid; gap: 9px; }.mistake-list button { display: flex; align-items: center; justify-content: space-between; gap: 15px; padding: 15px 16px; border: 1px solid var(--line); border-radius: 6px; background: #fff; color: var(--text-main); cursor: pointer; font: inherit; text-align: left; }.mistake-list button:hover { border-color: #b9d2f5; background: #fbfdff; }.mistake-list strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.mistake-list span { flex: 0 0 auto; color: var(--text-muted); font-size: 12px; }.study-empty { padding: 42px 20px; border: 1px dashed var(--line); color: var(--text-muted); text-align: center; }.exam-empty { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 8px; }.exam-empty strong { color: var(--text-main); }.exam-empty span { color: var(--text-muted); font-size: 13px; }

.chapter-section-item.is-complete::before { width: 16px; height: 16px; background: #35a56f; content: '✓'; color: #fff; font-size: 11px; line-height: 16px; text-align: center; }
.section-index.is-complete { color: #299261; font-weight: 700; }.section-index.is-complete span { display: inline-grid; width: 20px; height: 20px; place-items: center; border-radius: 50%; background: #e9f7ef; }

.resource-category {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 11px;
  padding: 10px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--text-main);
  background: transparent;
  font: inherit;
  font-size: 15px;
  text-align: left;
  cursor: pointer;
  transition: background 140ms ease, border-color 140ms ease, transform 140ms ease;
}

.resource-category:hover,
.resource-category.active {
  border-color: var(--line);
  background: var(--bg-soft);
  transform: translateY(-1px);
}

.resource-category-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 7px;
  color: #fff;
  box-shadow: 0 4px 10px rgba(53, 104, 216, 0.18);
}

.resource-category-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.tone-blue .resource-category-icon {
  background: #3183ee;
}

.tone-purple .resource-category-icon {
  background: #9b62e9;
}

.tone-teal .resource-category-icon {
  background: #1db9b5;
}

.tone-gold .resource-category-icon {
  background: #e9a92d;
}

.resource-list-heading {
  margin-top: 8px;
}

.resource-list-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.courseware-content-list {
  display: grid;
  gap: 10px;
}

.courseware-content-item {
  display: flex;
  min-height: 72px;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--line);
  background: #fff;
}

.courseware-file-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  place-items: center;
  color: #d78b25;
  background: #fff6e4;
}

.courseware-file-icon :deep(svg) {
  width: 21px;
  height: 21px;
}

.courseware-content-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 5px;
}

.courseware-content-main strong {
  overflow: hidden;
  color: var(--text-main);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.courseware-content-main span {
  color: var(--text-muted);
  font-size: 12px;
}

.courseware-item-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 2px;
}

.courseware-empty {
  display: flex;
  min-height: 360px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  border: 1px dashed #cbd6e2;
  color: #718096;
  text-align: center;
}

.courseware-empty :deep(svg) {
  width: 38px;
  height: 38px;
  color: #91b7db;
}

.courseware-empty strong {
  color: #3d5067;
}

.resource-back {
  margin: 0 0 8px;
  padding: 0;
  border: 0;
  color: var(--brand);
  background: transparent;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}

.resource-back:hover {
  color: var(--brand-deep);
}

.resource-pagination {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .chapter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .resource-category-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .course-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .course-study-shell { grid-template-columns: 184px minmax(0, 1fr); }
  .course-study-main { padding: 24px; }
}

@media (max-width: 640px) {
  .resource-hub {
    padding: 20px;
  }

  .resource-hub-heading {
    align-items: flex-start;
  }

  .resource-hub-actions {
    align-items: flex-end;
    flex-direction: column;
    gap: 8px;
  }

  .join-course-input {
    width: min(360px, 100%);
  }

  .course-create-grid {
    grid-template-columns: 1fr;
  }

  .course-grid {
    grid-template-columns: 1fr;
  }

  .course-study-shell { display: block; min-height: 0; }
  .course-study-sidebar { border-right: 0; border-bottom: 1px solid var(--line); }
  .course-study-brand { padding: 14px 16px; }
  .course-study-nav { grid-template-columns: repeat(3, minmax(0, 1fr)); padding: 8px; }
  .course-study-nav button { min-height: 46px; justify-content: center; flex-direction: column; gap: 3px; padding: 7px 4px; font-size: 11px; text-align: center; }
  .course-study-nav button :deep(svg) { width: 16px; height: 16px; }
  .course-study-main { padding: 20px 16px 28px; }
  .course-study-header { gap: 10px; }
  .courseware-content-item { align-items: flex-start; flex-wrap: wrap; }
  .courseware-item-actions { width: 100%; justify-content: flex-start; padding-left: 54px; }
  .course-study-header h3 { font-size: 20px; }
  .course-study-progress { min-width: 68px; padding: 4px 8px; }
  .question-bank-workbench { grid-template-columns: 1fr; }
  .question-bank-outline { position: static; }
  .question-bank-chapter-list { max-height: 250px; }
  .chapter-outline-heading { align-items: flex-start; flex-wrap: wrap; }
  .chapter-outline-heading > .el-button { margin-left: 48px; }
  .chapter-section-list { padding-left: 58px; }
  .chapter-section-list::before { left: 31px; }
  .chapter-section-item::before { left: -24px; }
  .study-metric-grid { grid-template-columns: 1fr; }
  .analysis-list > div { grid-template-columns: 100px minmax(0, 1fr) 70px; gap: 8px; }
  .assignment-chapter-list { grid-template-columns: 1fr; }
  .mistake-list button { align-items: flex-start; flex-direction: column; gap: 7px; }

  .course-resource-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .resource-list-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .outline-mindmap {
    align-items: stretch;
    padding: 32px 16px 40px;
  }

  .mindmap-branches {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .mindmap-branches::before,
  .mindmap-branch::before {
    display: none;
  }

  .mindmap-branch {
    align-items: stretch;
    padding-top: 0;
  }

  .resource-category-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .chapter-grid {
    grid-template-columns: 1fr;
  }

  .resource-category {
    padding: 8px 4px;
    font-size: 14px;
  }
}
.mistake-question-card { padding: 18px 20px; border: 1px solid #e1e7ee; border-radius: 7px; background: #fff; cursor: pointer; transition: border-color 160ms ease, box-shadow 160ms ease; }
.mistake-question-card:hover, .mistake-question-card:focus-visible { border-color: #b9d2f5; box-shadow: 0 6px 18px rgba(53, 98, 151, .1); outline: none; }
.mistake-list { gap: 12px; }
.mistake-question-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; color: var(--text-muted); font-size: 12px; }
.mistake-question-meta span { padding: 3px 8px; border-radius: 999px; background: #f1f5fb; }
.mistake-question-card h5 { margin: 11px 0 8px; color: var(--text-main); font-size: 16px; line-height: 1.5; }
.mistake-stem { margin: 0; color: var(--text-secondary); line-height: 1.7; white-space: pre-wrap; }
.mistake-answer { margin: 12px 0 0; padding: 9px 11px; border-radius: 4px; font-size: 13px; line-height: 1.6; white-space: pre-wrap; }
.wrong-answer { background: #fff3f1; color: #b34f48; }
.right-answer { background: #effaf4; color: #277b54; }
.mistake-analysis { margin: 12px 0 0; padding-top: 12px; border-top: 1px solid #edf0f3; color: var(--text-muted); font-size: 13px; line-height: 1.7; white-space: pre-wrap; }
.outline-reader-state,
.study-empty {
  border-style: solid;
}

.chapter-section-list::before {
  border-left-style: solid;
}
.student-management-heading { align-items: flex-start; }.student-management-actions { display: flex; max-width: 100%; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }.student-management-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin: 18px 0; }.student-management-toolbar .el-select { width: min(260px, 100%); }.student-management-toolbar .el-input { width: min(320px, 100%); }.student-management-table { min-height: 160px; }.student-management-footer { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 12px; }.student-count-hint, .student-import-hint { margin: 0; color: #78869a; font-size: 13px; }.student-import-hint { margin: 0 0 14px; line-height: 1.7; }
@media (max-width: 760px) { .student-management-heading, .student-management-footer { align-items: flex-start; flex-direction: column; }.student-management-actions { justify-content: flex-start; }.student-management-toolbar .el-select, .student-management-toolbar .el-input { width: 100%; max-width: none; }.student-management-footer :deep(.el-pagination) { max-width: 100%; overflow-x: auto; } }
.studio-resource-hub { display: grid; grid-template-columns: 230px minmax(0,1fr) 230px; gap: 22px; min-height: calc(100vh - 145px); flex: 1; padding: 22px; border-radius: 18px; background: #e9dfcf; }
.studio-course-shelf { display: flex; min-width: 0; flex-direction: column; padding: 22px 14px; border-radius: 16px; background: #433328; color: #f6efe3; }.studio-shelf-title { display:flex; align-items:baseline; justify-content:space-between; padding:0 9px 16px; border-bottom:1px solid rgba(255,255,255,.15); }.studio-shelf-title span { font-family:"Songti SC",serif; font-size:21px; }.studio-shelf-title small { color:#c9b9a5; font-size:11px; }.studio-course-shelf nav { display:grid; gap:9px; margin-top:16px; overflow:auto; }.studio-course-shelf nav button { display:grid; grid-template-columns:7px minmax(0,1fr); gap:9px; align-items:center; min-width:0; padding:12px 9px; border:0; border-radius:7px; background:rgba(255,255,255,.08); color:#f6efe3; cursor:pointer; text-align:left; }.studio-course-shelf nav button i { width:7px; height:30px; border-radius:5px; background:#73b8ec; }.studio-course-shelf nav button i.tone-1 { background:#5bdb91; }.studio-course-shelf nav button i.tone-2 { background:#f2c742; }.studio-course-shelf nav button i.tone-3 { background:#ec8a8a; }.studio-course-shelf nav button.active { background:#6a5648; box-shadow:inset 3px 0 #e8c875; }.studio-course-shelf nav span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:14px; }.studio-course-shelf nav small { grid-column:2; overflow:hidden; color:#c9b9a5; text-overflow:ellipsis; white-space:nowrap; }.studio-join { display:flex; gap:5px; margin-top:14px; }.studio-join :deep(.el-input__wrapper) { background:rgba(255,255,255,.08); box-shadow:none; }.studio-join :deep(input) { color:#fff; }.studio-shelf-add { margin-top:auto; padding:11px; border:1px solid #bd3a35; border-radius:5px; background:transparent; color:#db6155; cursor:pointer; }
.studio-course-main { min-width:0; width:100%; align-self:stretch; padding:0; color:#314b5d; text-align:center; }.studio-kicker { color:#8b99a2; font-family:"Songti SC",serif; font-size:14px; }.studio-course-main h3 { margin:17px 0 8px; color:#243b4d; font-family:"Songti SC",serif; font-size:clamp(25px,3vw,36px); font-weight:500; }.studio-course-subtitle { max-width:680px; margin:0 auto 22px; color:#73818b; line-height:1.7; }.studio-course-card { display:grid; grid-template-columns:58px repeat(4,minmax(0,1fr)); gap:14px; align-items:center; padding:22px; border:1px solid #ddd3c1; border-radius:15px; background:#faf7ee; box-shadow:0 15px 30px rgba(77,62,44,.12); text-align:left; }.studio-course-symbol { display:grid; width:54px; height:54px; place-items:center; border-radius:11px; background:#e5edf2; color:#315b7d; }.studio-course-card span { display:block; margin-bottom:5px; color:#a08f77; font-size:11px; }.studio-course-card strong { color:#324d5e; font-size:13px; overflow-wrap:anywhere; }.studio-course-actions { grid-column:1/-1; display:flex; gap:9px; padding-top:14px; border-top:1px solid #e2d8c7; }.studio-course-actions :deep(.el-button--primary) { --el-button-bg-color:#b43b37; --el-button-border-color:#b43b37; }.studio-resource-links { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:11px; margin-top:17px; text-align:left; }.studio-resource-links button { display:grid; gap:7px; min-height:120px; padding:16px; border:1px solid #ddd3c1; border-radius:11px; background:#f6f0e4; color:#315b7d; cursor:pointer; text-align:left; }.studio-resource-links button:hover { border-color:#caa85a; }.studio-resource-links strong { font-family:"Songti SC",serif; font-size:17px; font-weight:500; }.studio-resource-links span { color:#8a887f; font-size:12px; line-height:1.6; }.studio-resource-links b { align-self:end; color:#ae812a; font-size:12px; font-weight:500; }
.studio-course-guide { align-self:start; padding:26px 16px 18px; border-radius:16px; background:#faf7ee; color:#4a5962; text-align:center; box-shadow:0 13px 26px rgba(77,62,44,.1); }.studio-course-guide .guide-avatar { display:grid; width:70px; height:70px; margin:0 auto 12px; place-items:center; border-radius:12px; background:#e2d9c8; color:#3b4e5d; font-family:"Songti SC",serif; font-size:31px; }.studio-course-guide h2 { margin:0; color:#3b4e5d; font-family:"Songti SC",serif; font-size:18px; font-weight:500; }.studio-course-guide p { margin:6px 0 20px; color:#9a9286; font-size:12px; }.studio-course-guide .guide-note { min-height:140px; padding:16px; border-radius:11px; background:#f0eadf; color:#777b7c; font-size:12px; line-height:1.8; text-align:left; }.studio-course-guide .guide-note strong { color:#315b7d; font-size:14px; }.studio-course-guide > button { width:100%; margin-top:16px; padding:10px; border:1px solid #bd3a35; border-radius:5px; background:transparent; color:#b43b37; cursor:pointer; }.studio-course-empty { grid-column:2/-1; }.studio-resource-hub + * { margin-top:0; }
.studio-book-wrap { display:grid; grid-template-columns:1fr 1fr; width:100%; min-height:calc(100vh - 189px); height:100%; overflow:hidden; border-radius:18px; background:#f4ead5; box-shadow:0 18px 32px rgba(77,62,44,.18); }.studio-book-page { min-width:0; padding:28px 28px 22px; background:linear-gradient(90deg,#f8f0de,#efe3ca); text-align:left; }.studio-book-left { border-right:1px solid #ddcdb2; }.studio-book-right { background:linear-gradient(90deg,#eee1c8,#f8f0de); }.studio-book-heading { display:flex; align-items:baseline; justify-content:space-between; gap:12px; padding-bottom:16px; border-bottom:1px solid #d9c7a8; color:#3f5260; font-family:"Songti SC",serif; font-size:20px; }.studio-book-heading small { color:#9e8d73; font-size:12px; }.studio-chapter-list { position:relative; display:grid; gap:8px; margin-top:28px; }.studio-chapter-list::before { position:absolute; top:0; bottom:0; left:22px; border-left:1px solid #d6c29f; content:''; }.studio-chapter-item { position:relative; min-width:0; }.studio-chapter-row { position:relative; display:grid; grid-template-columns:40px minmax(0,1fr); gap:5px; width:100%; padding:13px 10px 8px; border:0; border-radius:5px; background:transparent; color:#514d46; cursor:pointer; text-align:left; }.studio-chapter-item.active .studio-chapter-row { background:#ead9bd; color:#9b3e38; }.studio-chapter-number { z-index:1; color:#9b8b72; font-family:Georgia,serif; }.studio-chapter-copy { display:grid; min-width:0; gap:4px; }.studio-chapter-copy strong { font-family:"Songti SC",serif; font-size:16px; font-weight:500; }.studio-chapter-copy em { overflow:hidden; color:#a49684; font-size:11px; font-style:normal; text-overflow:ellipsis; white-space:nowrap; }.studio-section-list { display:grid; gap:3px; margin:0 10px 8px 50px; }.studio-section-row { display:grid; grid-template-columns:42px minmax(0,1fr); gap:4px; width:100%; padding:7px 9px; border:0; border-left:1px solid #d6c29f; background:transparent; color:#7d7569; cursor:pointer; text-align:left; }.studio-section-row:hover { background:#f1e5d0; color:#9b3e38; }.studio-section-row span { color:#a99677; font-family:Georgia,serif; font-size:11px; }.studio-section-row strong { overflow:hidden; font-family:"Songti SC",serif; font-size:13px; font-weight:400; text-overflow:ellipsis; white-space:nowrap; }.studio-section-empty { margin:0 10px 8px 50px; color:#b2a48e; font-size:11px; }.studio-book-page > footer { margin-top:36px; padding-top:14px; border-top:1px solid #d9c7a8; color:#9e8d73; font-size:12px; }.studio-no-chapters { padding:22px 0; color:#9e8d73; font-size:13px; }.studio-book-intro { margin:24px 0; color:#7c766b; line-height:1.8; }.studio-book-tools { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; }.studio-book-tools button { min-height:90px; padding:15px; border:1px solid #dbc6a0; border-radius:11px; background:#f8f1e3; color:#3f5260; cursor:pointer; text-align:left; }.studio-book-tools button:hover { border-color:#b43b37; background:#fbf6eb; }.studio-book-tools strong { display:block; color:#a33e39; font-family:"Songti SC",serif; font-size:17px; font-weight:500; }.studio-book-tools span { display:block; margin-top:8px; color:#918674; font-size:12px; line-height:1.5; }.studio-book-footer { margin-top:20px; text-align:right; }.studio-book-footer :deep(.el-button--primary) { --el-button-bg-color:#b43b37; --el-button-border-color:#b43b37; }
.studio-knowledge-groups { display:grid; gap:18px; margin-top:26px; }.studio-knowledge-group { min-width:0; }.studio-knowledge-group h4 { margin:0 0 10px; color:#7e6b57; font-family:"Songti SC",serif; font-size:15px; font-weight:500; }.studio-knowledge-chips { display:flex; flex-wrap:wrap; gap:10px; }.studio-knowledge-chip { display:inline-flex; align-items:center; min-height:38px; padding:7px 16px; border:1px solid #55958c; border-radius:999px; background:rgba(255,255,255,.24); color:#4f7f79; font-family:"Songti SC",serif; font-size:14px; line-height:1.35; }.studio-knowledge-state { margin-top:30px; padding:20px 0; color:#9e8d73; font-size:14px; line-height:1.7; }
.studio-home-directory { overflow:auto; }.studio-home-knowledge { background:linear-gradient(90deg,#eee1c8,#fbf4e4); }.studio-knowledge-intro { margin:20px 0 0; color:#8d795f; font-family:"Songti SC",serif; font-size:14px; }.studio-home-knowledge .studio-knowledge-groups { max-height:330px; margin-top:18px; overflow:auto; }.studio-home-knowledge .studio-knowledge-group { padding-bottom:10px; }.studio-home-action-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; margin-top:22px; }.studio-home-action-grid button { display:flex; min-height:58px; align-items:center; justify-content:center; gap:7px; padding:8px 10px; border:1px solid #c9a575; border-radius:10px; background:rgba(255,249,235,.52); color:#8d443e; cursor:pointer; font-family:"Songti SC",serif; font-size:14px; line-height:1.35; }.studio-home-action-grid button:hover { border-color:#2e6547; background:#f7f0df; color:#2e6547; }.studio-home-action-grid button span { font-size:16px; }.studio-home-action-grid button:nth-child(3),.studio-home-action-grid button:nth-child(4),.studio-home-action-grid button:nth-child(5) { color:#2e6547; }.studio-home-action-grid button:nth-child(5) { grid-column:2; }
.studio-home-knowledge .studio-knowledge-groups { display:grid; gap:10px; }.studio-knowledge-point { display:grid; gap:5px; width:100%; padding:12px 15px; border:1px solid #78aaa1; border-radius:12px; background:rgba(255,255,255,.2); color:#547f79; cursor:pointer; text-align:left; }.studio-knowledge-point:hover,.studio-knowledge-point.active { border-color:#9b5148; background:#f4e5cf; color:#9b5148; box-shadow:0 4px 12px rgba(143,86,58,.1); }.studio-knowledge-point-section { color:#9c876b; font-family:"Songti SC",serif; font-size:12px; }.studio-knowledge-point strong { font-family:"Songti SC",serif; font-size:15px; font-weight:500; line-height:1.5; }.studio-home-action-grid button:disabled { cursor:not-allowed; opacity:.45; }
@media (max-width:760px) { .studio-home-action-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.studio-home-action-grid button:nth-child(5) { grid-column:auto; } }
@media (max-width:1100px) { .studio-resource-hub { grid-template-columns:205px minmax(0,1fr); }.studio-course-guide { display:none; } }.studio-course-main { min-width:0; }
@media (max-width:760px) { .resource-page { min-height: auto; }.studio-resource-hub { grid-template-columns:1fr; min-height: auto; padding:14px; }.studio-course-shelf { min-height:260px; }.studio-course-shelf nav { max-height:170px; }.studio-course-card { grid-template-columns:repeat(2,minmax(0,1fr)); }.studio-course-symbol { grid-column:1/-1; }.studio-resource-links { grid-template-columns:1fr; } }
.studio-home-landing { position:relative; display:block; min-height:calc(100vh - 145px); padding:30px clamp(18px,6vw,96px) 34px; overflow:hidden; background:radial-gradient(circle at 50% 14%,rgba(255,255,255,.82),transparent 42%),linear-gradient(135deg,#e9dfcf 0%,#d8c8b1 100%); }.studio-home-landing::before { position:absolute; inset:0; pointer-events:none; background:linear-gradient(90deg,rgba(70,48,33,.11),transparent 14%,transparent 86%,rgba(70,48,33,.12)); content:''; }.studio-shelf-tab { position:absolute; z-index:5; top:30px; left:20px; display:flex; min-width:44px; flex-direction:column; align-items:center; gap:7px; padding:13px 9px; border:1px solid rgba(119,86,54,.35); border-radius:9px; background:#4b362a; color:#f8eddb; cursor:pointer; box-shadow:0 10px 20px rgba(62,44,29,.2); }.studio-shelf-tab span { writing-mode:vertical-rl; font-family:"Songti SC",serif; font-size:15px; letter-spacing:2px; }.studio-shelf-tab small { color:#e1c58e; font-size:11px; }.studio-course-shelf-drawer { position:absolute; z-index:10; top:20px; left:20px; width:min(250px,calc(100% - 40px)); max-height:calc(100% - 40px); overflow:auto; box-shadow:16px 20px 38px rgba(62,44,29,.28); }.studio-course-shelf-drawer .studio-shelf-title { position:relative; padding-right:0; }.studio-shelf-close { padding:3px 0; border:0; background:transparent; color:#e3c991; cursor:pointer; font-size:11px; }.studio-home-stage { position:relative; z-index:1; width:min(1280px,100%); margin:0 auto; }.studio-home-topline { display:flex; align-items:flex-end; justify-content:space-between; gap:24px; margin-bottom:18px; }.studio-home-eyebrow { color:#9c8054; font-size:11px; letter-spacing:3px; }.studio-home-topline h1 { margin:6px 0 5px; color:#40352e; font-family:"Songti SC",serif; font-size:clamp(27px,3vw,42px); font-weight:500; }.studio-home-topline p { margin:0; color:#7e7062; font-size:13px; }.studio-home-topline p strong { color:#a34338; font-family:"Songti SC",serif; font-weight:500; }.studio-home-workspace-button,.studio-home-ask,.studio-home-course-switch { border:1px solid #af443a; border-radius:999px; background:#af443a; color:#fff8ef; cursor:pointer; font:inherit; }.studio-home-workspace-button { padding:11px 20px; box-shadow:0 8px 16px rgba(123,53,45,.18); }.studio-home-book { display:grid; grid-template-columns:1fr 1fr; min-height:clamp(540px,calc(100vh - 250px),720px); overflow:hidden; border:1px solid rgba(157,127,83,.4); border-radius:20px; background:#f5ecd9; box-shadow:0 24px 50px rgba(71,51,34,.25); }.studio-home-page { min-width:0; padding:34px clamp(24px,4vw,52px); background:linear-gradient(90deg,#fbf5e7,#eee0c5); }.studio-home-ai { background:linear-gradient(90deg,#eee1c8,#fbf4e4); }.studio-home-welcome { border-right:1px solid #d8c6a8; }.studio-home-welcome-body { display:flex; min-height:410px; flex-direction:column; align-items:flex-start; justify-content:center; max-width:420px; margin:0 auto; }.studio-home-seal { display:grid; width:58px; height:58px; place-items:center; margin-bottom:20px; border:1px solid #c8a36e; border-radius:50%; background:#f0dfbd; color:#a24338; font-family:"Songti SC",serif; font-size:28px; }.studio-home-welcome-body h2 { margin:0 0 12px; color:#4a4038; font-family:"Songti SC",serif; font-size:28px; font-weight:500; }.studio-home-welcome-body p { margin:0; color:#8b7b69; line-height:1.9; }.studio-home-course-summary { display:grid; gap:5px; width:100%; margin-top:28px; padding:16px 18px; border:1px solid #dbc7a5; border-radius:10px; background:rgba(255,255,255,.32); }.studio-home-course-summary span { color:#a08b70; font-size:12px; }.studio-home-course-summary strong { overflow:hidden; color:#4b5861; font-family:"Songti SC",serif; font-size:18px; font-weight:500; text-overflow:ellipsis; white-space:nowrap; }.studio-home-course-summary small { color:#a08e77; }.studio-home-course-switch { margin-top:15px; padding:8px 16px; border-color:#9d815c; background:transparent; color:#8c6842; }.studio-ai-profile { display:flex; align-items:center; gap:17px; margin:28px 0 20px; }.studio-ai-profile img { width:88px; height:104px; flex:0 0 auto; object-fit:contain; object-position:center bottom; }.studio-ai-profile h2 { margin:0 0 6px; color:#3e5d5c; font-family:"Songti SC",serif; font-size:24px; font-weight:500; }.studio-ai-profile p { margin:0; color:#7b847d; font-size:13px; line-height:1.7; }.studio-home-answer { max-height:180px; margin-bottom:14px; padding:14px 16px; overflow:auto; border:1px solid #d7c29f; border-radius:10px; background:rgba(255,255,255,.45); }.studio-home-answer strong { color:#a34338; font-family:"Songti SC",serif; font-size:14px; }.studio-home-answer p { margin:8px 0 0; color:#6e665c; white-space:pre-wrap; line-height:1.75; }.studio-home-question { width:100%; }.studio-home-question :deep(.el-textarea__inner) { border-color:#d5bf99; border-radius:10px; background:rgba(255,255,255,.62); box-shadow:none; color:#51483e; line-height:1.7; }.studio-home-quick { display:flex; flex-wrap:wrap; gap:8px; margin-top:15px; }.studio-home-quick > span { width:100%; color:#97846c; font-size:12px; }.studio-home-quick button { padding:7px 11px; border:1px solid #c4aa82; border-radius:999px; background:rgba(255,255,255,.3); color:#7f6d57; cursor:pointer; font-size:12px; }.studio-home-quick button:hover { border-color:#9a6252; color:#9a4037; }.studio-home-ask { margin-top:18px; padding:9px 20px; }.studio-home-ask:disabled { cursor:not-allowed; opacity:.55; }
@media (max-width:760px) { .studio-home-landing { min-height:auto; padding:18px 14px 24px; }.studio-shelf-tab { top:16px; left:14px; }.studio-home-topline { align-items:flex-start; flex-direction:column; padding-left:58px; }.studio-home-workspace-button { align-self:stretch; }.studio-home-book { grid-template-columns:1fr; }.studio-home-page { padding:26px 22px; }.studio-home-welcome { border-right:0; border-bottom:1px solid #d8c6a8; }.studio-home-welcome-body { min-height:320px; }.studio-course-shelf-drawer { top:14px; left:14px; max-height:calc(100% - 28px); } }
.studio-chapter-row { pointer-events:none; cursor:default; }
.studio-chapter-item.active .studio-chapter-row { background:transparent; color:#514d46; }
.studio-section-row.is-selected { border-radius:8px; background:#ead9bd; color:#9b3e38; font-weight:500; }
.studio-section-row.is-selected span { color:#9b3e38; }
.studio-knowledge-intro { font-size:0; }
.studio-knowledge-intro::after { content:'选择一个小节后，右侧展示该小节的全部 AI 知识点；选中知识点后再进行下方操作。'; font-size:14px; }
.studio-home-knowledge .studio-knowledge-groups { display:flex; flex-wrap:wrap; gap:10px; max-height:330px; overflow:auto; }
.studio-knowledge-point { display:inline-flex; width:auto; min-height:38px; align-items:center; padding:7px 16px; border:1px solid #55958c; border-radius:999px; background:rgba(255,255,255,.24); color:#4f7f79; cursor:pointer; text-align:left; }
.studio-knowledge-point:hover,
.studio-knowledge-point.active { border-color:#9b5148; background:#f4e5cf; color:#9b5148; box-shadow:none; }
.studio-knowledge-point-section { display:none; }
.studio-knowledge-point strong { font-family:"Songti SC",serif; font-size:14px; font-weight:400; line-height:1.35; }
.studio-home-landing {
  background-color:#241a15;
  background-image:var(--teaching-home-background);
  background-position:center center;
  background-repeat:no-repeat;
  background-size:cover;
}
.studio-home-landing::before { background:linear-gradient(90deg,rgba(21,13,9,.12),transparent 20%,transparent 78%,rgba(21,13,9,.08)); }
.studio-home-stage { width:min(1180px,calc(100% - 360px)); }
.studio-home-book { position:relative; z-index:2; margin:0 auto; }
@media (max-width:1500px) {
  .studio-home-stage { width:min(1120px,calc(100% - 300px)); }
}
@media (max-width:1100px) {
  .studio-home-stage { width:100%; }
}
.studio-home-book {
  grid-template-columns:minmax(0,1fr) minmax(0,1fr);
  min-height:clamp(600px,calc(100vh - 230px),790px);
  padding:14px 16px 17px;
  overflow:hidden;
  border:1px solid #6e4b36;
  border-radius:13px 13px 18px 18px;
  background:linear-gradient(180deg,#8b6246 0%,#67452f 100%);
  box-shadow:0 28px 42px rgba(30,18,11,.42), inset 0 1px rgba(255,235,199,.35), inset 0 -5px rgba(37,20,12,.38);
}
.studio-home-book::before {
  position:absolute;
  z-index:4;
  top:15px;
  bottom:17px;
  left:50%;
  width:15px;
  border-right:1px solid rgba(86,52,35,.42);
  border-left:1px solid rgba(255,250,228,.65);
  background:linear-gradient(90deg,rgba(111,70,46,.18),rgba(255,255,255,.72) 48%,rgba(93,55,36,.24));
  box-shadow:0 0 9px rgba(77,44,28,.28);
  content:'';
  pointer-events:none;
  transform:translateX(-50%);
}
.studio-home-page {
  position:relative;
  z-index:1;
  padding:42px clamp(28px,3.2vw,54px) 28px;
  background-color:#f8efd9;
  background-image:linear-gradient(90deg,rgba(255,255,255,.44),transparent 13%,transparent 87%,rgba(136,92,54,.1)),repeating-linear-gradient(0deg,rgba(145,101,59,.025) 0,rgba(145,101,59,.025) 1px,transparent 1px,transparent 5px);
  box-shadow:inset 0 0 25px rgba(130,87,47,.13);
}
.studio-home-directory { border-right:0; border-radius:7px 0 0 7px; box-shadow:inset -18px 0 25px rgba(123,78,42,.12), inset 0 0 25px rgba(130,87,47,.13); }
.studio-home-knowledge { border-radius:0 7px 7px 0; background-color:#f8efd9; background-image:linear-gradient(90deg,rgba(123,78,42,.13),rgba(255,255,255,.36) 14%,transparent 82%,rgba(255,255,255,.25)),repeating-linear-gradient(0deg,rgba(145,101,59,.025) 0,rgba(145,101,59,.025) 1px,transparent 1px,transparent 5px); box-shadow:inset 18px 0 25px rgba(123,78,42,.12), inset 0 0 25px rgba(130,87,47,.13); }
.studio-home-knowledge::after { position:absolute; top:39px; right:42px; padding:7px 18px; border:1px solid #4b7569; border-radius:999px; background:#3c7067; box-shadow:0 3px 7px rgba(57,79,63,.2); color:#fff9e9; content:'云塾女先生'; font-family:"Songti SC",serif; font-size:13px; letter-spacing:1px; }
.studio-book-heading { padding-bottom:18px; border-bottom-color:#d6bd92; color:#47504b; font-size:22px; letter-spacing:1px; }
.studio-book-heading small { color:#9a8264; font-size:12px; letter-spacing:2px; }
.studio-chapter-list { margin-top:30px; }
.studio-chapter-list::before { left:22px; border-left-color:#c9ad80; }
.studio-chapter-copy strong { color:#504c43; font-size:17px; }
.studio-chapter-copy em { color:#a18d70; }
.studio-section-list { margin-left:48px; }
.studio-section-row { border-left-color:#ccb188; color:#7c715f; }
.studio-section-row.is-selected { border-left:3px solid #b6473f; background:linear-gradient(90deg,#f0d6c9,rgba(240,214,201,.3)); color:#a23b36; box-shadow:0 3px 9px rgba(166,74,57,.09); }
.studio-section-row.is-selected strong { font-weight:600; }
.studio-knowledge-intro { color:#89755a; }
.studio-home-knowledge .studio-knowledge-groups { max-height:300px; margin-top:20px; }
.studio-knowledge-point { border-color:#8cb0a4; background:rgba(255,255,255,.3); color:#4e786d; box-shadow:0 2px 4px rgba(76,112,97,.06); }
.studio-knowledge-point:hover,.studio-knowledge-point.active { border-color:#a5443d; background:#f1ddd0; color:#9e3935; }
.studio-home-action-grid { margin-top:28px; }
.studio-home-action-grid button { min-height:55px; border-color:#bb9a6c; border-radius:9px; background:rgba(255,249,235,.72); box-shadow:0 3px 5px rgba(99,64,35,.08); }
.studio-home-action-grid button:nth-child(1),.studio-home-action-grid button:nth-child(2) { border-color:#b64a42; background:linear-gradient(180deg,#c95950,#a43a35); color:#fff8ef; }
.studio-home-action-grid button:nth-child(1):hover,.studio-home-action-grid button:nth-child(2):hover { border-color:#8f2e2a; background:#96332e; color:#fff8ef; }
.studio-home-action-grid button:nth-child(3),.studio-home-action-grid button:nth-child(4),.studio-home-action-grid button:nth-child(5) { border-color:#477b6b; background:linear-gradient(180deg,#568f7b,#356b5d); color:#fff8ef; }
.studio-home-action-grid button:nth-child(3):hover,.studio-home-action-grid button:nth-child(4):hover,.studio-home-action-grid button:nth-child(5):hover { border-color:#2f5d51; background:#2f6355; color:#fff8ef; }
.studio-home-action-grid .imperial-entry-button { border-color:#a83a2c; background:linear-gradient(180deg,#c65b4c,#9f352f); color:#fff8ef; }
.studio-home-action-grid .imperial-entry-button:hover { border-color:#7f2825; background:#8e2f2b; color:#fff8ef; }
.studio-home-answer { border-color:#d6bd92; background:rgba(255,252,239,.66); }
.studio-home-answer-bubble {
  position:fixed;
  z-index:4;
  left:auto;
  right:clamp(8px,2vw,24px);
  bottom:clamp(130px,20vh,220px);
  display:block;
  width:min(700px,54vw);
  height:clamp(230px,30vh,310px);
  min-height:0;
  padding:15% 16% 12%;
  overflow:hidden;
  border:0;
  background:transparent;
  color:#695541;
  transform:rotate(-1.2deg);
  pointer-events:auto;
  cursor:default;
  user-select:text;
  touch-action:auto;
}
.studio-home-book .studio-home-answer { display:none; }
.studio-home-answer-cloud { position:absolute; inset:0; z-index:0; width:100%; height:100%; object-fit:fill; pointer-events:none; filter:drop-shadow(0 10px 10px rgba(49,31,19,.16)); }
.studio-home-answer-close { position:absolute; z-index:2; top:10%; right:11%; display:grid; width:28px; height:28px; place-items:center; padding:0; border:1px solid rgba(111,82,56,.34); border-radius:50%; background:rgba(255,249,232,.72); color:#76563e; cursor:pointer; font-family:Arial,sans-serif; font-size:22px; line-height:1; }
.studio-home-answer-close:hover { border-color:#a34338; background:rgba(255,246,224,.96); color:#a34338; }
.studio-home-answer-bubble-content { position:absolute; z-index:1; top:20%; right:18%; bottom:18%; left:18%; overflow:auto; pointer-events:auto; cursor:text; user-select:text; touch-action:pan-y; scrollbar-width:none; -ms-overflow-style:none; }
.studio-home-answer-bubble-content::-webkit-scrollbar { display:none; width:0; height:0; }
.studio-home-answer-bubble-content strong { display:block; margin-bottom:8px; color:#3f766c; font-family:"Songti SC",serif; font-size:15px; font-weight:600; letter-spacing:1px; }
.studio-home-answer-bubble-content p { position:relative; z-index:1; margin:0; color:#665344; font-family:"Songti SC",serif; font-size:14px; line-height:1.8; white-space:pre-wrap; }
@media (max-width:1500px) {
  .studio-home-stage { width:min(1180px,calc(100% - 280px)); }
}
@media (max-width:760px) {
  .studio-home-book { padding:10px 10px 12px; }
  .studio-home-book::before { display:none; }
  .studio-home-page { padding:28px 22px 24px; }
  .studio-home-directory,.studio-home-knowledge { border-radius:7px; }
  .studio-home-knowledge::after { top:22px; right:22px; padding:5px 11px; font-size:11px; }
  .studio-home-answer-bubble { position:relative; left:auto; right:auto; bottom:auto; width:100%; height:240px; margin:12px 0 0; padding:16% 17% 13%; transform:none; }
}
:global(.app-shell:has(.studio-home-landing) .app-main) { padding:0; }
:global(.app-shell:has(.studio-home-landing) .page-main) { margin:0; }
:global(.app-shell:has(.studio-home-landing) .resource-page) { min-height:100vh; gap:0; }
:global(.app-shell:has(.studio-home-landing) .app-sidebar) { background:rgba(250,248,243,.94); border-bottom-color:rgba(112,78,49,.16); box-shadow:0 2px 10px rgba(55,35,23,.08); }
.studio-home-landing { min-height:calc(100vh - 76px); padding:14px clamp(18px,6vw,96px) 24px; }
.studio-home-topline { margin-bottom:10px; padding-top:4px; align-items:center; }
.studio-home-topline h1 { margin:4px 0; font-size:clamp(26px,2.7vw,38px); }
.studio-home-topline p { font-size:12px; }
.studio-home-workspace-button { padding:10px 18px; }
@media (max-width:760px) {
  .studio-home-landing { min-height:auto; padding:14px 14px 22px; }
  .studio-home-topline { align-items:flex-start; padding-top:0; }
}
</style>
