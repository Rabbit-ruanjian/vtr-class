<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, Plus, RefreshRight, UserFilled } from '@element-plus/icons-vue'
import PageHero from '@/components/PageHero.vue'
import { academicClassApi, schoolApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { normalizePage } from '@/utils/page'
import * as XLSX from 'xlsx'

const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const importing = ref(false)
const authStore = useAuthStore()
const schools = ref([])
const schoolStructure = ref({ departments: [] })
const archivingId = ref(null)
const removingStudentId = ref(null)
const clearingStudents = ref(false)
const classes = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, status: 'ACTIVE', keyword: '', schoolId: null, college: null, grade: null, major: null })
const summary = ref([])
const summaryLoading = ref(false)
const activeCollege = ref(null)
const expandedGrades = ref([])
const searchMode = computed(() => Boolean(query.keyword && query.keyword.trim()))
const selected = ref(null)
const drawerVisible = ref(false)
const students = ref([])
const studentsLoading = ref(false)
const dialogVisible = ref(false)
const importVisible = ref(false)
const editingId = ref(null)
const pendingImportClassId = ref(null)
const importText = ref('')
const importFileName = ref('')
const importPreview = ref([])
const form = reactive({ schoolId: null, name: '', college: '', campus: '', major: '', grade: '', classCode: '', headTeacherName: '', counselorName: '' })
const selectableSchools = computed(() => schools.value.filter((school) => school.status === 'ACTIVE'))
const departmentOptions = computed(() => schoolStructure.value.departments || [])
const majorOptions = computed(() => departmentOptions.value.find((item) => item.name === form.college)?.majors || [])
const totalClassCount = computed(() => summary.value.reduce((sum, item) => sum + (item.classCount || 0), 0))
const totalStudentCount = computed(() => summary.value.reduce((sum, item) => sum + (item.studentCount || 0), 0))
const activeCollegeSummary = computed(() => summary.value.find((item) => item.college === activeCollege.value) || null)
const gradeGroups = computed(() => {
  const map = new Map()
  for (const item of classes.value) {
    const grade = item.grade || '未分年级'
    if (!map.has(grade)) map.set(grade, [])
    map.get(grade).push(item)
  }
  return Array.from(map.entries())
    .sort((left, right) => String(right[0]).localeCompare(String(left[0])))
    .map(([grade, items]) => ({ grade, items }))
})

async function loadSummary() {
  summaryLoading.value = true
  try {
    const result = await academicClassApi.summary({ status: query.status, schoolId: query.schoolId })
    const rows = Array.isArray(result) ? result : (result?.list || [])
    const structure = query.schoolId ? await schoolApi.academicStructure(query.schoolId) : { departments: [] }
    const byCollege = new Map(rows.map((item) => [item.college, item]))
    for (const department of (structure?.departments || [])) {
      if (!byCollege.has(department.name)) byCollege.set(department.name, { college: department.name, classCount: 0, studentCount: 0, grades: [] })
    }
    summary.value = Array.from(byCollege.values())
  } catch (error) {
    summary.value = []
  } finally { summaryLoading.value = false }
}

async function selectCollege(college) {
  activeCollege.value = college
  query.college = college
  query.grade = null
  query.major = null
  query.page = 1
  await load()
  const grades = (activeCollegeSummary.value?.grades || []).map((item) => item.grade)
  expandedGrades.value = grades.length ? [grades[0]] : []
}

async function backToColleges() {
  activeCollege.value = null
  query.college = null
  query.grade = null
  query.major = null
  query.page = 1
  classes.value = []
  total.value = 0
  await loadSummary()
}

async function refreshAll() {
  await loadSummary()
  if (searchMode.value || activeCollege.value) await load()
}

async function runSearch() {
  query.page = 1
  if (query.keyword && query.keyword.trim()) {
    query.college = null
    query.grade = null
    query.major = null
    activeCollege.value = null
    await load()
  } else if (activeCollege.value) {
    await load()
  } else {
    classes.value = []
    total.value = 0
  }
}

async function changeStatus() {
  query.page = 1
  await loadSummary()
  if (searchMode.value || activeCollege.value) await load()
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = normalizePage(await academicClassApi.list(query))
    classes.value = result.list
    total.value = result.total
  } catch (error) {
    classes.value = []
    total.value = 0
    loadError.value = error?.message || '行政班列表加载失败'
  } finally { loading.value = false }
}
function reset() { editingId.value = null; pendingImportClassId.value = null; schoolStructure.value = { departments: [] }; Object.assign(form, { schoolId: null, name: '', college: '', campus: '', major: '', grade: '', classCode: '', headTeacherName: '', counselorName: '' }) }
async function loadSchoolStructure(schoolId, preserve = true) {
  schoolStructure.value = schoolId ? await schoolApi.academicStructure(schoolId) : { departments: [] }
  if (!preserve) { form.college = ''; form.major = '' }
}
async function handleSchoolChange(schoolId) {
  try { await loadSchoolStructure(schoolId, false) } catch (error) { schoolStructure.value = { departments: [] } }
}
function handleDepartmentChange() {
  if (!majorOptions.value.some((item) => item.name === form.major)) form.major = ''
}
function generateClassCode() {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
  const bytes = new Uint8Array(8)
  crypto.getRandomValues(bytes)
  return `AC-${Array.from(bytes, (byte) => alphabet[byte % alphabet.length]).join('')}`
}
async function openCreate() {
  reset()
  if (selectableSchools.value.length === 1) form.schoolId = selectableSchools.value[0].id
  form.classCode = generateClassCode()
  if (form.schoolId) await loadSchoolStructure(form.schoolId, false)
  if (form.schoolId && activeCollege.value && activeCollege.value !== '未分类学院'
      && departmentOptions.value.some((item) => item.name === activeCollege.value)) {
    form.college = activeCollege.value
  }
  dialogVisible.value = true
}
async function copyClassCode(row) {
  const code = String(row.classCode || '')
  if (!code) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(code)
    } else {
      const input = document.createElement('textarea')
      input.value = code
      input.style.position = 'fixed'
      input.style.opacity = '0'
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      input.remove()
    }
    ElMessage.success('班级编码已复制')
  } catch (error) {
    ElMessage.error('复制失败，请手动选择编码')
  }
}
function parseStudentRows(value) {
  const rows = String(value || '').split(/\r?\n/)
  const seen = new Set()
  return rows.map((row) => row.split(/[,，\t]/).map((item) => item.trim())).map((cells, index) => ({ studentNumber: cells[0], name: cells[1] || '', index })).filter((item) => {
    const header = (item.index === 0 && !/^[A-Za-z0-9_-]+$/.test(item.studentNumber)) || ['student_number', 'student number'].includes(String(item.studentNumber || '').toLowerCase())
    if (!item.studentNumber || header || seen.has(item.studentNumber)) return false
    seen.add(item.studentNumber); return true
  })
}
function parseStudentFile(bytes, fileName) {
  if (/\.(xlsx|xls)$/i.test(fileName)) {
    const workbook = XLSX.read(bytes, { type: 'array' })
    const sheet = workbook.Sheets[workbook.SheetNames[0]]
    return XLSX.utils.sheet_to_csv(sheet)
  }
  const utf8Text = new TextDecoder('utf-8').decode(bytes)
  return utf8Text.includes('\uFFFD') ? new TextDecoder('gb18030').decode(bytes) : utf8Text
}
function refreshImportPreview() { importPreview.value = parseStudentRows(importText.value) }
async function handleImportFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  importFileName.value = file.name
  const bytes = new Uint8Array(await file.arrayBuffer())
  importText.value = parseStudentFile(bytes, file.name)
  refreshImportPreview()
  event.target.value = ''
}
async function saveWithImport() {
  if (!form.schoolId) return ElMessage.warning('请选择所属学校')
  saving.value = true
  try {
    const creating = !editingId.value
    const shouldImport = creating || pendingImportClassId.value === editingId.value
    const saved = creating ? await academicClassApi.create(form) : await academicClassApi.update(editingId.value, form)
    const classroomId = editingId.value || saved?.id
    if (creating && classroomId) {
      editingId.value = classroomId
      pendingImportClassId.value = importText.value.trim() ? classroomId : null
    }
    const students = shouldImport ? parseStudentRows(importText.value) : []
    if (shouldImport && importText.value.trim() && !students.length) {
      throw new Error('文件中没有识别到有效学生，请确认第一列为学号、第二列为姓名')
    }
    if (classroomId && students.length) {
      const result = await academicClassApi.batchAssign(classroomId, students)
      if ((result.created || 0) + (result.existing || 0) + (result.unregistered || 0) === 0) throw new Error('学生导入失败：后端没有写入任何成员')
      pendingImportClassId.value = null
      ElMessage.success(`行政班已保存：已绑定 ${result.existing} 人，待注册 ${result.unregistered || 0} 人，跳过 ${result.skipped} 人`)
    } else {
      ElMessage.success('行政班已保存')
    }
    dialogVisible.value = false
    await loadSummary()
    if (activeCollege.value || searchMode.value) await load()
    if (students.length) await viewStudents({ ...form, id: classroomId })
  } finally { saving.value = false }
}
async function openEdit(row) { editingId.value = row.id; pendingImportClassId.value = null; Object.assign(form, row); await loadSchoolStructure(form.schoolId, true); dialogVisible.value = true }
async function archive(row) {
  await ElMessageBox.confirm(`确认归档行政班“${row.name}”吗？归档后不能继续导入学生，但历史名单仍会保留。`, '归档行政班', { type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '取消' })
  archivingId.value = row.id
  try {
    await academicClassApi.archive(row.id)
    await loadSummary()
    if (activeCollege.value || searchMode.value) await load()
    ElMessage.success('行政班已归档')
  } finally { archivingId.value = null }
}
async function restore(row) {
  archivingId.value = row.id
  try {
    await academicClassApi.restore(row.id)
    await loadSummary()
    if (activeCollege.value || searchMode.value) await load()
    ElMessage.success('行政班已恢复')
  } finally { archivingId.value = null }
}
async function viewStudents(row) {
  selected.value = row
  students.value = []
  drawerVisible.value = true
  studentsLoading.value = true
  try {
    const result = await academicClassApi.students(row.id)
    students.value = Array.isArray(result) ? result : (result?.list || [])
  } catch (error) {
    ElMessage.error('学生名单加载失败，请确认后端服务已重启')
  } finally {
    studentsLoading.value = false
  }
}
function openImport(row) { selected.value = row; importText.value = ''; importFileName.value = ''; importPreview.value = []; importVisible.value = true }
async function batchImport() {
  const students = parseStudentRows(importText.value)
  if (!students.length) return ElMessage.warning('未识别到学生。请使用第一列学号、第二列姓名的 CSV 文件')
  importing.value = true
  try {
    const result = await academicClassApi.batchAssign(selected.value.id, students)
    if ((result.created || 0) + (result.existing || 0) + (result.unregistered || 0) === 0) return ElMessage.error(`导入失败：没有学生被写入，失败学号 ${result.failedNumbers?.join('、') || '请检查数据'}`)
    importVisible.value = false
    await viewStudents(selected.value)
    const failedText = result.failedNumbers?.length ? `，失败 ${result.failedNumbers.length} 人` : ''
    ElMessage.success(`导入完成：已绑定 ${result.existing} 人，待注册 ${result.unregistered || 0} 人，跳过 ${result.skipped} 人${failedText}`)
  } finally { importing.value = false }
}
async function removeStudent(row) {
  await ElMessageBox.confirm(`确认将“${row.name || row.studentNumber}”移出当前行政班吗？`, '移除学生', { type: 'warning', confirmButtonText: '确认移除', cancelButtonText: '取消' })
  removingStudentId.value = row.id
  try {
    if (row.userId) await academicClassApi.removeStudent(selected.value.id, row.userId)
    else await academicClassApi.removeRoster(selected.value.id, row.id)
    students.value = students.value.filter((student) => student.id !== row.id)
    ElMessage.success('学生已移出行政班')
  } finally { removingStudentId.value = null }
}
async function clearAllStudents() {
  if (!selected.value) return
  if (!students.value.length) return ElMessage.info('该行政班暂无学生')
  await ElMessageBox.confirm(`确认彻底清空行政班“${selected.value.name}”中的全部 ${students.value.length} 名学生吗？名单记录将被删除，已注册学生将与该班解绑。`, '彻底清空行政班', { type: 'warning', confirmButtonText: '确认彻底清空', cancelButtonText: '取消' })
  clearingStudents.value = true
  try {
    const removed = await academicClassApi.clearStudents(selected.value.id)
    const result = await academicClassApi.students(selected.value.id)
    students.value = Array.isArray(result) ? result : (result?.list || [])
    ElMessage.success(`已彻底清空，删除 ${removed || 0} 条名单记录`)
  } finally { clearingStudents.value = false }
}
onMounted(async () => {
  schools.value = await (authStore.isAdmin ? schoolApi.list() : schoolApi.active())
  query.schoolId = authStore.role === 'SUPER_ADMIN'
    ? null
    : (authStore.user?.schoolId || schools.value[0]?.id || null)
  await loadSummary()
})
</script>

<template>
  <div class="page-stack academic-classes-view">
    <PageHero title="行政班管理" description="维护学生所属的专业、年级和行政班。行政班由管理员维护，教师可从行政班导入学生到授课班。">
      <template #actions><el-button :icon="RefreshRight" :loading="loading" @click="load">刷新</el-button><el-button type="primary" :icon="Plus" @click="openCreate">新建行政班</el-button></template>
    </PageHero>
    <div class="toolbar academic-toolbar">
      <el-radio-group v-model="query.status" size="small" @change="changeStatus"><el-radio-button label="ACTIVE">有效行政班</el-radio-button><el-radio-button label="ARCHIVED">已归档</el-radio-button></el-radio-group>
      <el-input v-model="query.keyword" class="toolbar-grow" clearable placeholder="跨学院搜索班级名称、学院、专业、年级或编码" @keyup.enter="runSearch" @clear="runSearch" />
      <el-button type="primary" @click="runSearch">查询</el-button>
    </div>

    <!-- 搜索态：跨学院扁平结果 -->
    <template v-if="searchMode">
      <div class="academic-breadcrumb"><el-button link type="primary" @click="() => { query.keyword = ''; runSearch() }">← 返回学院导航</el-button><span class="academic-breadcrumb-text">搜索“{{ query.keyword }}”的结果</span></div>
      <div v-if="loadError" class="academic-load-error"><strong>行政班列表加载失败</strong><span>{{ loadError }}</span><el-button type="primary" :icon="RefreshRight" @click="load">重新加载</el-button></div>
      <el-table v-else class="academic-class-table" :data="classes" v-loading="loading" empty-text="没有匹配的行政班">
        <el-table-column prop="name" label="行政班" min-width="140" />
        <el-table-column prop="college" label="学院" min-width="140" />
        <el-table-column prop="campus" label="校区" min-width="100" />
        <el-table-column prop="major" label="专业" min-width="140" />
        <el-table-column prop="grade" label="年级" min-width="100" />
        <el-table-column prop="classCode" label="班级编码" min-width="150">
          <template #default="{ row }"><div class="academic-code-cell"><span>{{ row.classCode }}</span><el-tooltip content="复制班级编码" placement="top"><el-button text :icon="CopyDocument" aria-label="复制班级编码" @click="copyClassCode(row)" /></el-tooltip></div></template>
        </el-table-column>
        <el-table-column prop="headTeacherName" label="班主任" min-width="120" />
        <el-table-column prop="counselorName" label="辅导员" min-width="120" />
        <el-table-column label="状态" min-width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '有效' : '已归档' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" min-width="280">
          <template #default="{ row }"><div class="academic-row-actions"><el-button link type="primary" :icon="UserFilled" @click="viewStudents(row)">学生名单</el-button><el-button v-if="row.status === 'ACTIVE'" link type="primary" @click="openImport(row)">批量分配</el-button><el-button v-if="row.status === 'ACTIVE'" link @click="openEdit(row)">编辑</el-button><el-button v-if="row.status === 'ACTIVE'" link type="danger" :loading="archivingId === row.id" @click="archive(row)">归档</el-button><el-button v-else link type="success" :loading="archivingId === row.id" @click="restore(row)">恢复</el-button></div></template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > query.size" v-model:current-page="query.page" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </template>

    <!-- 学院概览：第 1 级 -->
    <template v-else-if="!activeCollege">
      <div class="academic-overview-head" v-loading="summaryLoading">
        <span>共 {{ summary.length }} 个学院 · {{ totalClassCount }} 个行政班 · {{ totalStudentCount }} 名学生</span>
      </div>
      <div v-if="!summaryLoading && !summary.length" class="academic-empty">当前学校暂无{{ query.status === 'ACTIVE' ? '有效' : '已归档' }}行政班，点击右上角“新建行政班”开始维护。</div>
      <div v-else class="academic-college-grid" v-loading="summaryLoading">
        <button v-for="college in summary" :key="college.college" type="button" class="academic-college-card" @click="selectCollege(college.college)">
          <div class="academic-college-name">{{ college.college }}</div>
          <div class="academic-college-stat"><strong>{{ college.classCount }}</strong> 个行政班 · <strong>{{ college.studentCount }}</strong> 名学生</div>
          <div class="academic-college-grades"><el-tag v-for="grade in college.grades" :key="grade.grade" size="small" type="info" effect="plain">{{ grade.grade }} · {{ grade.classCount }} 班</el-tag></div>
        </button>
      </div>
    </template>

    <!-- 学院内年级分组：第 2/3 级 -->
    <template v-else>
      <div class="academic-breadcrumb"><el-button link type="primary" @click="backToColleges">← 全部学院</el-button><span class="academic-breadcrumb-text">{{ activeCollege }}<template v-if="activeCollegeSummary"> · {{ activeCollegeSummary.classCount }} 个行政班 · {{ activeCollegeSummary.studentCount }} 名学生</template></span></div>
      <div v-if="loadError" class="academic-load-error"><strong>行政班列表加载失败</strong><span>{{ loadError }}</span><el-button type="primary" :icon="RefreshRight" @click="load">重新加载</el-button></div>
      <div v-else-if="!loading && !gradeGroups.length" class="academic-empty">该学院暂无{{ query.status === 'ACTIVE' ? '有效' : '已归档' }}行政班，点击右上角“新建行政班”添加。</div>
      <el-collapse v-else v-model="expandedGrades" v-loading="loading" class="academic-grade-collapse">
        <el-collapse-item v-for="group in gradeGroups" :key="group.grade" :name="group.grade">
          <template #title><span class="academic-grade-title">{{ group.grade }} 级 · {{ activeCollege }} · {{ group.items.length }} 个班</span></template>
          <el-table class="academic-class-table" :data="group.items" empty-text="暂无行政班">
            <el-table-column prop="name" label="行政班" min-width="140" />
            <el-table-column prop="campus" label="校区" min-width="100" />
            <el-table-column prop="major" label="专业" min-width="150" />
            <el-table-column prop="classCode" label="班级编码" min-width="150">
              <template #default="{ row }"><div class="academic-code-cell"><span>{{ row.classCode }}</span><el-tooltip content="复制班级编码" placement="top"><el-button text :icon="CopyDocument" aria-label="复制班级编码" @click="copyClassCode(row)" /></el-tooltip></div></template>
            </el-table-column>
            <el-table-column prop="headTeacherName" label="班主任" min-width="110" />
            <el-table-column prop="counselorName" label="辅导员" min-width="110" />
            <el-table-column label="状态" min-width="90"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '有效' : '已归档' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" min-width="280">
              <template #default="{ row }"><div class="academic-row-actions"><el-button link type="primary" :icon="UserFilled" @click="viewStudents(row)">学生名单</el-button><el-button v-if="row.status === 'ACTIVE'" link type="primary" @click="openImport(row)">批量分配</el-button><el-button v-if="row.status === 'ACTIVE'" link @click="openEdit(row)">编辑</el-button><el-button v-if="row.status === 'ACTIVE'" link type="danger" :loading="archivingId === row.id" @click="archive(row)">归档</el-button><el-button v-else link type="success" :loading="archivingId === row.id" @click="restore(row)">恢复</el-button></div></template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
      <el-pagination v-if="total > query.size" v-model:current-page="query.page" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </template>
    <el-dialog v-model="dialogVisible" :title="pendingImportClassId === editingId ? '完成学生导入' : editingId ? '编辑行政班' : '新建行政班'" width="620px" :close-on-click-modal="false">
      <el-form label-position="top"><div class="academic-form-grid"><el-form-item label="所属学校" required><el-select v-model="form.schoolId" filterable clearable style="width: 100%" placeholder="请选择所属学校" :disabled="Boolean(editingId && form.schoolId) || selectableSchools.length <= 1" @change="handleSchoolChange"><el-option v-for="school in selectableSchools" :key="school.id" :label="`${school.name}（${school.code}）`" :value="school.id" /></el-select><small v-if="editingId && form.schoolId" class="school-field-tip">行政班所属学校不可在编辑时修改。</small><small v-else-if="selectableSchools.length > 1" class="school-field-tip">请选择该行政班所属的学校，学生学号将按此学校进行匹配。</small></el-form-item><el-form-item label="班级名称" required><el-input v-model="form.name" /></el-form-item><el-form-item label="班级编码" required><el-input v-model="form.classCode" maxlength="50" placeholder="留空时系统自动生成唯一编码" /></el-form-item><el-form-item label="院系" required><el-select v-model="form.college" filterable clearable style="width: 100%" placeholder="请先在学校管理中配置院系" @change="handleDepartmentChange"><el-option v-for="department in departmentOptions" :key="department.id" :label="department.name" :value="department.name" /></el-select></el-form-item><el-form-item label="校区"><el-input v-model="form.campus" /></el-form-item><el-form-item label="专业" required><el-select v-model="form.major" filterable clearable style="width: 100%" placeholder="请先选择院系"><el-option v-for="major in majorOptions" :key="major.id" :label="major.name" :value="major.name" /></el-select><small v-if="form.college && !majorOptions.length" class="school-field-tip">该院系暂未配置专业，请先到学校管理中添加。</small></el-form-item><el-form-item label="年级" required><el-input v-model="form.grade" placeholder="例如：2024" /></el-form-item><el-form-item label="班主任"><el-input v-model="form.headTeacherName" /></el-form-item><el-form-item label="辅导员"><el-input v-model="form.counselorName" /></el-form-item></div><el-form-item v-if="!editingId || pendingImportClassId === editingId" label="批量导入学生（可选）"><div class="student-import-box"><input type="file" accept=".xlsx,.xls,.csv,.txt,text/csv,text/plain" @change="handleImportFile" /><span v-if="importFileName" class="import-file-name">{{ importFileName }}</span><el-input v-model="importText" type="textarea" :rows="5" placeholder="上传 CSV，或从 Excel 复制学号粘贴到这里；支持一行一个学号、逗号或制表符分隔。第一列应为唯一学号。" /><small>系统会自动去重，并跳过空行；未注册的学号会在结果中提示。</small></div></el-form-item></el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveWithImport">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="importVisible" :title="`导入学生到 ${selected?.name || ''}`" width="620px" :close-on-click-modal="false"><div class="student-import-box"><input type="file" accept=".xlsx,.xls,.csv,.txt,text/csv,text/plain" @change="handleImportFile" /><span v-if="importFileName" class="import-file-name">{{ importFileName }}</span><el-input v-model="importText" type="textarea" :rows="8" placeholder="上传 CSV，第一列学号、第二列姓名；也可从 Excel 复制粘贴" @input="refreshImportPreview" /><div v-if="importPreview.length" class="import-preview">已识别 {{ importPreview.length }} 名学生：{{ importPreview.slice(0, 3).map(item => `${item.studentNumber} ${item.name}`).join('，') }}<span v-if="importPreview.length > 3"> 等</span></div><small>未注册学生只加入名单，注册时输入学号即可自动绑定；重复学号会自动跳过。</small></div><template #footer><el-button :disabled="importing" @click="importVisible = false">取消</el-button><el-button type="primary" :loading="importing" @click="batchImport">确认导入 {{ importPreview.length ? `${importPreview.length} 人` : '' }}</el-button></template></el-dialog>
    <el-drawer v-model="drawerVisible" title="行政班学生名单" size="520px"><template v-if="selected"><div class="academic-drawer-title"><strong>{{ selected.name }}</strong><span>{{ studentsLoading ? '加载中...' : `${students.length} 人` }}</span><el-button v-if="selected.status === 'ACTIVE' && students.length" link type="danger" :loading="clearingStudents" @click="clearAllStudents">清空全班</el-button></div><el-table v-loading="studentsLoading" :data="students" empty-text="暂无学生，请先导入名单"><el-table-column prop="studentNumber" label="学号" /><el-table-column prop="name" label="姓名" /><el-table-column label="注册状态" width="100"><template #default="{ row }">{{ row.registrationStatus || (row.userId ? '已注册' : '未注册') }}</template></el-table-column><el-table-column v-if="selected.status === 'ACTIVE'" label="操作" width="90"><template #default="{ row }"><el-button link type="danger" :loading="removingStudentId === row.id" @click="removeStudent(row)">移除</el-button></template></el-table-column></el-table></template></el-drawer>
  </div>
</template>

<style scoped>
.academic-form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 18px; }
.academic-breadcrumb { display:flex; align-items:center; gap:12px; margin-bottom:14px; }
.academic-breadcrumb-text { color:var(--text-secondary); font-size:13px; }
.academic-overview-head { margin-bottom:14px; color:var(--text-secondary); font-size:13px; }
.academic-empty { min-height:180px; display:flex; align-items:center; justify-content:center; color:var(--text-muted); border:1px dashed var(--el-border-color); border-radius:8px; padding:24px; text-align:center; }
.academic-college-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(240px,1fr)); gap:16px; }
.academic-college-card { display:flex; flex-direction:column; gap:10px; align-items:flex-start; text-align:left; padding:18px; border:1px solid var(--el-border-color); border-radius:12px; background:var(--el-bg-color, #fff); cursor:pointer; transition:box-shadow .2s, border-color .2s, transform .1s; }
.academic-college-card:hover { border-color:var(--el-color-primary); box-shadow:0 6px 18px rgba(31,63,114,.1); transform:translateY(-1px); }
.academic-college-name { font-size:15px; font-weight:600; color:var(--text-main); }
.academic-college-stat { font-size:13px; color:var(--text-secondary); }
.academic-college-stat strong { color:var(--text-main); }
.academic-college-grades { display:flex; flex-wrap:wrap; gap:6px; }
.academic-grade-collapse { border-top:none; }
.academic-grade-title { font-size:14px; font-weight:600; color:var(--text-main); }
.school-field-tip { display:block; margin-top:6px; color:var(--text-muted); font-size:12px; line-height:1.5; }
.academic-toolbar { margin-bottom: 14px; }
.academic-toolbar :deep(.el-radio-group) { flex: 0 0 auto; }
.academic-load-error { display: flex; min-height: 220px; align-items: center; justify-content: center; flex-direction: column; gap: 10px; border: 1px dashed var(--el-border-color); color: var(--text-muted); }
.academic-load-error strong { color: var(--text-main); }
.academic-load-error span { max-width: 100%; overflow-wrap: anywhere; }
.academic-classes-view :deep(.el-table) { width: 100%; }
.academic-classes-view :deep(.academic-class-table .el-table__body-wrapper),
.academic-classes-view :deep(.academic-class-table .el-table__header-wrapper) { overflow-x: auto !important; }
.academic-classes-view :deep(.el-table__inner-wrapper),
.academic-classes-view :deep(.el-table__header-wrapper),
.academic-classes-view :deep(.el-table__body-wrapper) { width: 100%; }
.academic-classes-view :deep(.el-table__header th),
.academic-classes-view :deep(.el-table__body td) { padding: 10px 12px; }
.academic-classes-view :deep(.el-table .cell) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; line-height: 1.45; font-size: 13px; }
.academic-classes-view :deep(.el-table th .cell) { font-size: 13px; white-space: nowrap; }
.academic-classes-view :deep(.el-table__header-wrapper),
.academic-classes-view :deep(.el-table__body-wrapper) { width: 100% !important; }
.academic-row-actions { display: flex; align-items: center; gap: 12px; white-space: nowrap; }
.academic-row-actions :deep(.el-button) { margin: 0; font-size: 13px; }
.academic-code-cell { display: flex; align-items: center; gap: 8px; min-width: 0; }
.academic-code-cell span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.academic-code-cell :deep(.el-button) { flex: 0 0 auto; margin: 0; padding: 4px; }
.academic-classes-view :deep(.el-table__fixed-right) { box-shadow: -6px 0 14px rgba(31, 63, 114, .08); }
.student-import-box { display:flex; flex-direction:column; gap:8px; width:100%; }
.student-import-box input { max-width:260px; }
.student-import-box small { color:var(--text-muted); line-height:1.5; }
.import-file-name { color:var(--text-secondary); font-size:12px; }
.academic-drawer-title { display:flex; justify-content:space-between; margin-bottom:16px; color:var(--text-secondary); }
@media (max-width:1100px) {
  .academic-classes-view :deep(.el-table .cell),
  .academic-classes-view :deep(.el-table th .cell),
  .academic-row-actions :deep(.el-button) { font-size: 12px; }
  .academic-classes-view :deep(.el-table__body td) { padding: 8px 10px; }
}
@media (max-width:640px) {
  .academic-form-grid { grid-template-columns:1fr; }
  .academic-classes-view :deep(.el-table .cell),
  .academic-classes-view :deep(.el-table th .cell),
  .academic-row-actions :deep(.el-button) { font-size: 12px; }
}
</style>
