<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen, RefreshRight, VideoPause, UserFilled, FolderOpened, Plus, Search, CopyDocument, DataAnalysis, MoreFilled } from '@element-plus/icons-vue'
import PageHero from '@/components/PageHero.vue'
import { academicClassApi, classroomApi, courseApi, schoolApi, uploadApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { resolveAssetUrl } from '@/utils/format'
import { normalizePage } from '@/utils/page'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const courses = ref([])
const query = reactive({ keyword: '', status: '' })
const activeView = ref('active')
const classroomDialogVisible = ref(false)
const classroomSaving = ref(false)
const selectedCourse = ref(null)
const classrooms = ref([])
const academicClasses = ref([])
const schoolStructure = ref({ departments: [] })
const courseSchoolOptions = ref([])
const academicImportVisible = ref(false)
const academicImportClassroom = ref(null)
const academicImportId = ref(null)
const academicImportLoading = ref(false)
const classroomForm = reactive({ className: '', description: '', grade: '', semester: '', inviteCode: '' })
const membersDialogVisible = ref(false)
const membersLoading = ref(false)
const selectedClassroom = ref(null)
const members = ref([])
const membersTotal = ref(0)
const memberQuery = reactive({ keyword: '', page: 1, size: 20 })
const form = reactive({
  schoolId: null,
  courseName: '',
  courseCode: '',
  description: '',
  coverImage: '',
  semester: '',
  credits: null,
  courseCategory: '',
  teachingDepartment: '',
  assessmentMethod: '',
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
  { label: '公共课（历史分类）', value: '公共课' },
  { label: '专业课（历史分类）', value: '专业课' },
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
const departmentOptions = computed(() => schoolStructure.value.departments || [])
const selectedAcademicClassNames = computed(() => academicClasses.value
  .filter((item) => form.allowedAcademicClassIds.includes(item.id))
  .map((item) => item.name)
  .join('、'))

const canManageCourse = (course) =>
  authStore.isAdmin || course.createdBy === authStore.user?.id

const visibleCourses = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  return courses.value.filter((course) => {
    const matchesKeyword = !keyword || [course.courseName, course.courseCode, course.teachingDepartment]
      .filter(Boolean).some((value) => String(value).toLowerCase().includes(keyword))
    const matchesView = activeView.value === 'all' || (activeView.value === 'active' && course.status === 'ACTIVE') || (activeView.value === 'archived' && course.status !== 'ACTIVE')
    return matchesKeyword && matchesView
  })
})

const courseMetrics = computed(() => ({
  total: courses.value.length,
  active: courses.value.filter((course) => course.status === 'ACTIVE').length,
  archived: courses.value.filter((course) => course.status !== 'ACTIVE').length
}))

function courseLabel(course) {
  return course.status === 'ACTIVE' ? '授课中' : '已归档'
}

function cloneCourse(course) {
  openEdit(course)
  editingId.value = null
  form.courseName = `${course.courseName || ''}（副本）`
  form.courseCode = ''
  ElMessage.info('已复制课程基础信息，请补充新学期和教学班后保存')
}

function resetForm() {
  editingId.value = null
  form.courseName = ''
  form.courseCode = ''
  form.description = ''
  form.coverImage = ''
  form.semester = ''
  form.credits = null
  form.courseCategory = ''
  form.teachingDepartment = ''
  form.assessmentMethod = ''
  form.allowedAcademicClassIds = []
  form.schoolId = authStore.user?.schoolId || null
  schoolStructure.value = { departments: [] }
}

async function loadCourses() {
  loading.value = true
  try {
    courses.value = await courseApi.manageList({
      keyword: query.keyword.trim() || undefined,
      status: query.status || undefined
    })
  } finally {
    loading.value = false
  }
}

async function openClassrooms(course) {
  selectedCourse.value = course
  classroomForm.className = ''
  classroomForm.description = ''
  classroomForm.grade = ''
  classroomForm.semester = course.semester || ''
  classroomForm.inviteCode = ''
  classroomDialogVisible.value = true
  classrooms.value = await courseApi.classrooms(course.id)
  academicClasses.value = normalizePage(await academicClassApi.list({ page: 1, size: 100 })).list
}

function openAcademicImport(classroom) {
  academicImportClassroom.value = classroom
  academicImportId.value = null
  academicImportVisible.value = true
}

async function importAcademicClass() {
  if (!academicImportId.value) { ElMessage.warning('请选择行政班'); return }
  academicImportLoading.value = true
  try {
    const students = await academicClassApi.students(academicImportId.value)
    const studentIds = students.map((student) => student.userId || (student.registrationStatus === '已注册' ? student.id : null)).filter((id) => id != null)
    const unregisteredCount = students.length - studentIds.length
    if (!studentIds.length) {
      ElMessage.warning('该行政班暂无已注册的本校学生账号，暂时无法导入')
      return
    }
    await classroomApi.addStudents({ classroomId: academicImportClassroom.value.id, studentIds })
    classrooms.value = await courseApi.classrooms(selectedCourse.value.id)
    academicImportVisible.value = false
    ElMessage.success(`已将 ${studentIds.length} 名学生加入教学班${unregisteredCount ? `，${unregisteredCount} 名未注册学生已跳过` : ''}`)
  } finally { academicImportLoading.value = false }
}

async function createClassroom() {
  if (!classroomForm.className.trim()) {
    ElMessage.warning('请输入教学班名称')
    return
  }
  classroomSaving.value = true
  try {
    await classroomApi.create({ ...classroomForm, className: classroomForm.className.trim(), courseId: selectedCourse.value.id })
    classrooms.value = await courseApi.classrooms(selectedCourse.value.id)
    classroomForm.className = ''
    classroomForm.description = ''
    classroomForm.grade = ''
    classroomForm.inviteCode = ''
    ElMessage.success('教学班已创建')
  } finally {
    classroomSaving.value = false
  }
}

async function addStudents(classroom) {
  const { value } = await ElMessageBox.prompt('每行输入一个学生学号或用户名。', `添加学生到 ${classroom.className}`, {
    inputType: 'textarea',
    inputPlaceholder: 'student01\nstudent02'
  })
  const studentNumbers = value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean)
  if (!studentNumbers.length) return
  await classroomApi.addStudents({ classroomId: classroom.id, studentNumbers })
  classrooms.value = await courseApi.classrooms(selectedCourse.value.id)
  ElMessage.success('学生已加入教学班')
}

async function loadMembers() {
  if (!selectedClassroom.value) return
  membersLoading.value = true
  try {
    const pageData = await classroomApi.students(selectedClassroom.value.id, memberQuery)
    members.value = pageData?.list || []
    membersTotal.value = pageData?.total || 0
  } finally {
    membersLoading.value = false
  }
}

async function openMembers(classroom) {
  selectedClassroom.value = classroom
  memberQuery.keyword = ''
  memberQuery.page = 1
  membersDialogVisible.value = true
  await loadMembers()
}

async function changeMemberPage(page) {
  memberQuery.page = page
  await loadMembers()
}

async function renameClassroom(classroom) {
  const { value } = await ElMessageBox.prompt('输入新的教学班名称。', '编辑教学班', {
    inputValue: classroom.className,
    inputPattern: /\S/,
    inputErrorMessage: '教学班名称不能为空'
  })
  await classroomApi.update(classroom.id, { className: value.trim() })
  classrooms.value = await courseApi.classrooms(selectedCourse.value.id)
  ElMessage.success('教学班名称已更新')
}

async function changeClassroomStatus(classroom) {
  const archiving = classroom.status === 'ACTIVE'
  await ElMessageBox.confirm(
    archiving ? '归档后学生不能再通过邀请码加入该教学班。' : '恢复后学生可再次通过邀请码加入教学班。',
    archiving ? '归档教学班' : '恢复教学班',
    { type: 'warning' }
  )
  if (archiving) {
    await classroomApi.archive(classroom.id)
  } else {
    await classroomApi.restore(classroom.id)
  }
  classrooms.value = await courseApi.classrooms(selectedCourse.value.id)
  ElMessage.success(archiving ? '教学班已归档' : '教学班已恢复')
}

async function removeMember(member) {
  await ElMessageBox.confirm(`确认将 ${member.nickname || member.username} 移出教学班吗？`, '移除学生', {
    type: 'warning'
  })
  await classroomApi.removeStudent(selectedClassroom.value.id, member.id)
  await Promise.all([
    loadMembers(),
    courseApi.classrooms(selectedCourse.value.id).then((items) => { classrooms.value = items })
  ])
  ElMessage.success('学生已移出教学班')
}

function openCreate() {
  resetForm()
  loadCourseBaseData().catch(() => { academicClasses.value = []; schoolStructure.value = { departments: [] } })
  dialogVisible.value = true
}

async function loadCourseBaseData(course = null) {
  const schoolId = course?.schoolId || authStore.user?.schoolId
  const schoolList = await (authStore.isAdmin ? schoolApi.list() : schoolApi.active())
  courseSchoolOptions.value = schoolList || []
  const resolvedSchoolId = schoolId || schoolList?.[0]?.id
  if (!form.schoolId && resolvedSchoolId) form.schoolId = resolvedSchoolId
  if (resolvedSchoolId) schoolStructure.value = await schoolApi.academicStructure(resolvedSchoolId)
  await loadAcademicClasses()
}

async function changeCourseSchool(schoolId) {
  form.teachingDepartment = ''
  schoolStructure.value = { departments: [] }
  if (schoolId) await loadCourseBaseData({ schoolId })
}

async function loadAcademicClasses() {
  const payload = await academicClassApi.list({ page: 1, size: 100 })
  academicClasses.value = normalizePage(payload).list
}

function openWorkspace(course) {
  router.push({ name: 'course-workspace', params: { id: course.id } })
}

function openEdit(course) {
  editingId.value = course.id
  form.courseName = course.courseName || ''
  form.courseCode = course.courseCode || ''
  form.description = course.description || ''
  form.coverImage = course.coverImage || ''
  form.semester = course.semester || ''
  form.credits = course.credits ?? null
  form.courseCategory = course.courseCategory || ''
  form.teachingDepartment = course.teachingDepartment || ''
  form.assessmentMethod = course.assessmentMethod || ''
  form.allowedAcademicClassIds = Array.isArray(course.allowedAcademicClassIds) ? [...course.allowedAcademicClassIds] : []
  loadCourseBaseData(course).catch(() => { academicClasses.value = []; schoolStructure.value = { departments: [] } })
  dialogVisible.value = true
}

async function uploadCover(event) {
  const file = event.target.files?.[0]
  if (!file) return

  try {
    form.coverImage = await uploadApi.courseCover(file)
    ElMessage.success('课程封面已上传')
  } finally {
    event.target.value = ''
  }
}

async function saveCourse() {
  if (!form.courseName.trim()) {
    ElMessage.warning('请输入课程名称')
    return
  }
  if (!form.semester) return ElMessage.warning('请选择学期')
  if (!form.courseCategory) return ElMessage.warning('请选择课程类别')
  if (!form.teachingDepartment) return ElMessage.warning('请选择开课院系')

  saving.value = true
  try {
    const payload = { ...form, allowedAcademicClassIds: [...form.allowedAcademicClassIds], courseName: form.courseName.trim() }
    ;['semester', 'courseCategory', 'teachingDepartment', 'assessmentMethod'].forEach((key) => {
      payload[key] = payload[key]?.trim() || null
    })
    if (editingId.value) {
      await courseApi.update(editingId.value, payload)
      ElMessage.success('课程已更新')
    } else {
      await courseApi.create(payload)
      ElMessage.success('课程已创建')
    }
    dialogVisible.value = false
    await loadCourses()
  } finally {
    saving.value = false
  }
}

async function changeStatus(course) {
  const archiving = course.status === 'ACTIVE'
  const action = archiving ? '结束授课' : '恢复授课'
  await ElMessageBox.confirm(
    archiving
      ? '结束授课后，课程会从正常课程入口隐藏；已有的教学班、课件和作业都会保留。'
      : '恢复授课后，课程会重新出现在正常课程入口中。',
    `确认${action}`,
    { type: 'warning' }
  )
  if (archiving) {
    await courseApi.archive(course.id)
  } else {
    await courseApi.restore(course.id)
  }
  ElMessage.success(`课程已${action}`)
  await loadCourses()
}

onMounted(loadCourses)
</script>

<template>
  <div class="page-stack">
    <PageHero
      title="课程管理"
      description="以课程作为教学组织的一级单位。教学班仅在课程内用于区分授课群组、成员和班级可见资源。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadCourses">刷新</el-button>
        <el-button type="primary" @click="openCreate">新建课程</el-button>
      </template>
    </PageHero>

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        class="toolbar-grow"
        clearable
        placeholder="搜索课程名称或课程代码"
        @keyup.enter="loadCourses"
      />
      <el-select v-model="query.status" class="status-filter" clearable placeholder="全部状态" @change="loadCourses">
        <el-option label="授课中" value="ACTIVE" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <el-button type="primary" @click="loadCourses">查询</el-button>
    </div>

    <el-table :data="courses" v-loading="loading">
      <el-table-column prop="courseName" label="课程名称" min-width="220" />
      <el-table-column label="封面" width="132">
        <template #default="{ row }">
          <img v-if="row.coverImage" class="course-cover" :src="resolveAssetUrl(row.coverImage)" :alt="`${row.courseName} 封面`" />
          <span v-else class="course-cover-empty">未上传</span>
        </template>
      </el-table-column>
      <el-table-column prop="courseCode" label="课程代码" width="180">
        <template #default="{ row }">{{ row.courseCode || '--' }}</template>
      </el-table-column>
      <el-table-column prop="courseCategory" label="课程类别" width="120">
        <template #default="{ row }">{{ row.courseCategory || '--' }}</template>
      </el-table-column>
      <el-table-column label="教学信息" min-width="260">
        <template #default="{ row }">
          <div class="course-meta">
            <span>{{ row.semester || '未设置学期' }}</span>
            <span>{{ row.credits == null ? '未设置学分' : `${row.credits} 学分` }}</span>
            <span>{{ row.teachingDepartment || '未设置院系' }}</span>
            <span>{{ row.assessmentMethod || '未设置考核方式' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="课程简介" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '--' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="188" fixed="right" align="center">
        <template #default="{ row }">
          <div v-if="canManageCourse(row)" class="course-actions">
            <el-tooltip content="进入课程工作台" placement="top">
              <el-button class="course-action action-workspace" circle :icon="FolderOpened" aria-label="进入课程工作台" @click="openWorkspace(row)" />
            </el-tooltip>
            <el-tooltip content="编辑课程" placement="top">
              <el-button class="course-action action-edit" circle :icon="EditPen" aria-label="编辑课程" @click="openEdit(row)" />
            </el-tooltip>
            <el-tooltip content="教学班与成员" placement="top">
              <el-button class="course-action action-classroom" circle :icon="UserFilled" aria-label="教学班与成员" @click="openClassrooms(row)" />
            </el-tooltip>
            <el-tooltip :content="row.status === 'ACTIVE' ? '结束授课' : '恢复授课'" placement="top">
              <el-button
                class="course-action"
                :class="row.status === 'ACTIVE' ? 'action-end' : 'action-restore'"
                circle
                :icon="row.status === 'ACTIVE' ? VideoPause : RefreshRight"
                :aria-label="row.status === 'ACTIVE' ? '结束授课' : '恢复授课'"
                @click="changeStatus(row)"
              />
            </el-tooltip>
          </div>
          <span v-else class="muted-text">--</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑课程' : '新建课程'" width="760px" class="course-dialog">
      <el-form label-position="top">
  <div class="form-grid">
          <el-form-item v-if="authStore.role === 'SUPER_ADMIN'" label="所属学校" required>
            <el-select v-model="form.schoolId" filterable class="full-width" placeholder="请选择学校" @change="changeCourseSchool">
              <el-option v-for="school in courseSchoolOptions.filter(item => item.status === 'ACTIVE')" :key="school.id" :label="`${school.name}（${school.code}）`" :value="school.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="课程名称" required>
            <el-input v-model="form.courseName" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item label="课程代码（可选）">
            <el-input v-model="form.courseCode" maxlength="50" show-word-limit placeholder="留空自动生成，如 K8M2QX7A" />
            <div class="form-hint">留空时自动生成唯一代码。</div>
          </el-form-item>
          <el-form-item label="课程封面" class="form-grid-wide">
            <div class="cover-editor">
              <img v-if="form.coverImage" class="cover-preview" :src="resolveAssetUrl(form.coverImage)" alt="课程封面预览" />
              <div class="cover-actions">
                <input id="course-cover-upload" type="file" accept="image/jpeg,image/png,image/gif,image/webp" @change="uploadCover" />
                <el-button v-if="form.coverImage" link type="danger" @click="form.coverImage = ''">移除封面</el-button>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="学期" required>
            <el-select v-model="form.semester" clearable filterable placeholder="请选择学期" class="full-width">
              <el-option v-for="semester in semesters" :key="semester" :label="semester" :value="semester" />
              <el-option v-if="form.semester && !semesters.includes(form.semester)" :label="`${form.semester}（历史数据）`" :value="form.semester" />
            </el-select>
          </el-form-item>
          <el-form-item label="学分">
            <el-input-number v-model="form.credits" :min="0.5" :max="999.9" :step="0.5" :precision="1" controls-position="right" class="full-width" />
          </el-form-item>
          <el-form-item label="课程类别" required>
            <el-select v-model="form.courseCategory" clearable placeholder="请选择" class="full-width">
              <el-option v-for="item in courseCategories" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="开课院系" required>
            <el-select v-model="form.teachingDepartment" clearable filterable placeholder="请先在学校管理中配置院系" class="full-width">
              <el-option v-for="department in departmentOptions" :key="department.id" :label="department.name" :value="department.name" />
              <el-option v-if="form.teachingDepartment && !departmentOptions.some((item) => item.name === form.teachingDepartment)" :label="`${form.teachingDepartment}（历史数据）`" :value="form.teachingDepartment" />
            </el-select>
            <div v-if="!departmentOptions.length" class="form-hint">当前学校还没有配置院系，请联系管理员先到“学校管理”中维护。</div>
          </el-form-item>
          <el-form-item label="考核方式">
            <el-select v-model="form.assessmentMethod" clearable placeholder="请选择" class="full-width">
              <el-option label="考试" value="考试" />
              <el-option label="考查" value="考查" />
              <el-option label="课程论文" value="课程论文" />
              <el-option label="项目作业" value="项目作业" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许进入课程的行政班" class="form-grid-wide">
            <el-select v-model="form.allowedAcademicClassIds" multiple filterable clearable collapse-tags collapse-tags-tooltip class="full-width" placeholder="不选择表示不限制，选择后仅这些班级可用邀请码加入">
              <el-option v-for="item in academicClasses" :key="item.id" :label="`${item.name} · ${item.grade || ''} ${item.major || ''}`" :value="item.id" />
            </el-select>
            <div v-if="selectedAcademicClassNames" class="form-hint">已选择：{{ selectedAcademicClassNames }}</div>
            <div class="form-hint">选择后，只有这些行政班的学生可以通过课程代码或邀请码加入。</div>
          </el-form-item>
        </div>
        <el-form-item label="课程简介">
          <el-input v-model="form.description" type="textarea" :rows="5" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCourse">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="classroomDialogVisible" :title="`${selectedCourse?.courseName || ''} - 教学班`" width="760px">
      <el-form label-position="top" class="classroom-form" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="教学班名称" required>
            <el-input v-model="classroomForm.className" maxlength="100" placeholder="例如：软件工程 1 班" />
          </el-form-item>
          <el-form-item label="邀请码（可选）">
            <el-input v-model="classroomForm.inviteCode" maxlength="16" placeholder="留空自动生成" />
          </el-form-item>
          <el-form-item label="年级">
            <el-input v-model="classroomForm.grade" maxlength="50" />
          </el-form-item>
          <el-form-item label="学期">
            <el-input v-model="classroomForm.semester" maxlength="50" />
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input v-model="classroomForm.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-button type="primary" :loading="classroomSaving" @click="createClassroom">创建教学班</el-button>
      </el-form>

      <el-table :data="classrooms" class="classroom-table">
        <el-table-column prop="className" label="教学班" min-width="180" />
        <el-table-column prop="inviteCode" label="邀请码" width="140" />
        <el-table-column prop="studentCount" label="学生数" width="90" />
        <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openMembers(row)">查看成员</el-button>
          <el-button link type="primary" :disabled="row.status !== 'ACTIVE'" @click="addStudents(row)">按学号添加</el-button>
          <el-button link type="primary" :disabled="row.status !== 'ACTIVE'" @click="openAcademicImport(row)">从行政班导入</el-button>
          <el-button link type="primary" :disabled="row.status !== 'ACTIVE'" @click="renameClassroom(row)">编辑</el-button>
          <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="changeClassroomStatus(row)">{{ row.status === 'ACTIVE' ? '归档' : '恢复' }}</el-button>
        </template>
      </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="membersDialogVisible" :title="`${selectedClassroom?.className || ''} - 班级成员`" width="720px">
      <div class="member-toolbar">
        <el-input v-model="memberQuery.keyword" clearable placeholder="搜索姓名、学号或用户名" @keyup.enter="memberQuery.page = 1; loadMembers()" @clear="memberQuery.page = 1; loadMembers()" />
        <el-button type="primary" @click="memberQuery.page = 1; loadMembers()">搜索</el-button>
      </div>
      <el-table :data="members" v-loading="membersLoading" empty-text="当前教学班暂无学生">
        <el-table-column label="姓名" min-width="160">
          <template #default="{ row }">{{ row.nickname || row.username || '--' }}</template>
        </el-table-column>
        <el-table-column prop="identityNumber" label="学号" min-width="140"><template #default="{ row }">{{ row.identityNumber || '--' }}</template></el-table-column>
        <el-table-column prop="academicClassName" label="行政班" min-width="180"><template #default="{ row }">{{ row.academicClassName || '未分配行政班' }}</template></el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="200">
          <template #default="{ row }">{{ row.email || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="membersTotal > memberQuery.size"
        class="member-pagination"
        layout="total, prev, pager, next"
        :current-page="memberQuery.page"
        :page-size="memberQuery.size"
        :total="membersTotal"
        @current-change="changeMemberPage"
      />
    </el-dialog>

    <el-dialog v-model="academicImportVisible" title="从行政班导入学生" width="520px">
      <el-form label-position="top"><el-form-item label="目标教学班"><el-input :model-value="academicImportClassroom?.className || ''" disabled /></el-form-item><el-form-item label="行政班"><el-select v-model="academicImportId" filterable placeholder="选择行政班" style="width:100%"><el-option v-for="item in academicClasses" :key="item.id" :label="`${item.name} · ${item.major}`" :value="item.id" /></el-select></el-form-item></el-form>
      <p class="academic-import-hint">系统会将该行政班当前的学生加入此教学班。学生仍可同时参加其他课程。</p>
      <template #footer><el-button @click="academicImportVisible = false">取消</el-button><el-button type="primary" :loading="academicImportLoading" @click="importAcademicClass">导入学生</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.form-hint {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.full-width {
  width: 100%;
}

.course-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

.course-cover {
  display: block;
  width: 104px;
  height: 52px;
  border-radius: 4px;
  object-fit: cover;
}

.course-cover-empty {
  display: inline-flex;
  width: 104px;
  height: 52px;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  background: var(--el-fill-color-light);
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  font-size: 12px;
}

.form-grid-wide {
  grid-column: 1 / -1;
}

.cover-editor {
  display: flex;
  min-height: 96px;
  align-items: center;
  gap: 14px;
}

.cover-preview {
  width: 160px;
  height: 80px;
  border-radius: 4px;
  object-fit: cover;
}

.cover-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.muted-text {
  color: var(--text-muted);
}

.course-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.course-actions :deep(.course-action.el-button) {
  width: 30px;
  height: 30px;
  min-height: 30px;
  margin: 0;
  padding: 0;
  border-radius: 4px !important;
  box-shadow: none !important;
  transform: none !important;
}

.course-actions :deep(.action-edit.el-button) {
  color: var(--brand-deep) !important;
  background: rgba(48, 112, 255, 0.08) !important;
  border: 1px solid rgba(48, 112, 255, 0.2) !important;
}

.course-actions :deep(.action-workspace.el-button) {
  color: #7a4ca5 !important;
  background: rgba(122, 76, 165, 0.08) !important;
  border: 1px solid rgba(122, 76, 165, 0.2) !important;
}

.course-actions :deep(.action-classroom.el-button) {
  color: #27815f !important;
  background: rgba(39, 129, 95, 0.08) !important;
  border: 1px solid rgba(39, 129, 95, 0.2) !important;
}

.status-filter { width: 140px; }
.classroom-table { margin-top: 20px; }
.academic-import-hint { margin: -2px 0 0; color: var(--text-muted); font-size: 13px; line-height: 1.5; }
.member-toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
.member-pagination { justify-content: flex-end; margin-top: 16px; }

.course-actions :deep(.action-end.el-button) {
  color: #c24a4a !important;
  background: rgba(220, 72, 72, 0.08) !important;
  border: 1px solid rgba(220, 72, 72, 0.2) !important;
}

.course-actions :deep(.action-restore.el-button) {
  color: #27815f !important;
  background: rgba(39, 129, 95, 0.08) !important;
  border: 1px solid rgba(39, 129, 95, 0.2) !important;
}

.course-actions :deep(.course-action.el-button:hover) {
  filter: brightness(0.96);
}

@media (max-width: 640px) {
  .form-grid,
  .course-meta {
    grid-template-columns: 1fr;
  }

  .cover-editor {
    align-items: flex-start;
    flex-direction: column;
  }
}
/* Keep search, status, and action controls aligned as one toolbar. */
.toolbar .toolbar-grow { flex: 1 1 420px; min-width: 240px; width: auto; }
.toolbar .status-filter { flex: 0 0 140px; width: 140px; }
.toolbar > .el-button { flex: 0 0 auto; }
@media (max-width: 640px) {
  .toolbar .toolbar-grow { flex-basis: 100%; }
  .toolbar .status-filter { flex: 1 1 auto; width: auto; }
}
</style>
