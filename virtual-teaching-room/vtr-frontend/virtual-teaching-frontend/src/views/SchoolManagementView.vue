<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, RefreshRight, Delete, Plus as PlusIcon } from '@element-plus/icons-vue'
import PageHero from '@/components/PageHero.vue'
import { schoolApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import * as XLSX from 'xlsx'

const loading = ref(false)
const saving = ref(false)
const schools = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', code: '', region: '' })
const academicStructure = reactive({ departments: [] })
const batchDeptVisible = ref(false)
const batchDeptFileName = ref('')
const batchDeptRows = ref([])
const teacherRosterVisible = ref(false)
const teacherRosterLoading = ref(false)
const teacherRosterSaving = ref(false)
const teacherRosterSchool = ref(null)
const teacherRosterStructure = ref({ departments: [] })
const teacherRosters = ref([])
const teacherBatchFileName = ref('')
const teacherBatchRows = ref([])
const teacherRosterForm = reactive({ id: null, employeeNumber: '', name: '', department: '' })
const authStore = useAuthStore()

async function load() {
  loading.value = true
  try { schools.value = await schoolApi.list() } finally { loading.value = false }
}
function reset() { editingId.value = null; Object.assign(form, { name: '', code: '', region: '' }); academicStructure.departments = [] }
function addDepartment() { academicStructure.departments.push({ name: '', majors: [{ name: '' }] }) }
function removeDepartment(index) { academicStructure.departments.splice(index, 1) }
function openBatchDepartments() { batchDeptFileName.value = ''; batchDeptRows.value = []; batchDeptVisible.value = true }
function parseBatchFile(bytes, fileName) {
  // Excel/CSV/文本统一转成 CSV 文本，再按行解析。
  if (/\.(xlsx|xls)$/i.test(fileName)) {
    const workbook = XLSX.read(bytes, { type: 'array' })
    const sheet = workbook.Sheets[workbook.SheetNames[0]]
    return XLSX.utils.sheet_to_csv(sheet)
  }
  const utf8Text = new TextDecoder('utf-8').decode(bytes)
  return utf8Text.charCodeAt(0) === 0xfeff ? utf8Text.slice(1) : utf8Text
}
function parseBatchDepartments(value) {
  const rows = String(value || '').split(/\r?\n/)
  return rows
    .map((line) => line.trim())
    .filter((line) => line)
    .map((line) => {
      // 每行第一格为院系名，其余单元格为专业；也兼容“院系：专业1，专业2”写法。
      const cells = line.split(/[,，\t]/).map((cell) => cell.trim())
      let namePart = cells.shift() || ''
      let inlineMajors = []
      if (/[:：]/.test(namePart)) {
        const [name, major] = namePart.split(/[:：]/, 2)
        namePart = (name || '').trim()
        if (major && major.trim()) inlineMajors.push(major.trim())
      }
      const name = namePart.trim()
      const majors = inlineMajors
        .concat(cells)
        .map((major) => major.trim())
        .filter((major) => major)
        .map((major) => ({ name: major }))
      return { name, majors: majors.length ? majors : [{ name: '' }] }
    })
    .filter((department) => department.name && !/^(院系|院系名称|学院|department)$/i.test(department.name))
}
async function handleBatchDeptFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  batchDeptFileName.value = file.name
  const bytes = new Uint8Array(await file.arrayBuffer())
  batchDeptRows.value = parseBatchDepartments(parseBatchFile(bytes, file.name))
  event.target.value = ''
  if (!batchDeptRows.value.length) ElMessage.warning('未从文件中识别到院系，请检查文件内容')
}
function applyBatchDepartments() {
  const parsed = batchDeptRows.value
  if (!parsed.length) return ElMessage.warning('请先上传包含院系的文件')
  const existing = new Map(academicStructure.departments.map((department) => [department.name?.trim(), department]))
  // 去掉当前尚未填写名称的空院系占位，避免批量导入后残留空行。
  academicStructure.departments = academicStructure.departments.filter((department) => department.name?.trim())
  let added = 0
  let merged = 0
  for (const department of parsed) {
    const current = existing.get(department.name)
    if (current) {
      const majorNames = new Set((current.majors || []).map((major) => major.name?.trim()).filter((major) => major))
      const newMajors = department.majors.filter((major) => major.name && !majorNames.has(major.name))
      if (newMajors.length) { current.majors = (current.majors || []).filter((major) => major.name?.trim()).concat(newMajors); merged += 1 }
    } else {
      academicStructure.departments.push(department)
      existing.set(department.name, department)
      added += 1
    }
  }
  if (!academicStructure.departments.length) academicStructure.departments.push({ name: '', majors: [{ name: '' }] })
  batchDeptVisible.value = false
  ElMessage.success(`批量完成：新增 ${added} 个院系${merged ? `，合并 ${merged} 个已有院系的专业` : ''}`)
}
function addMajor(department) { department.majors.push({ name: '' }) }
function removeMajor(department, index) { department.majors.splice(index, 1) }
function structurePayload() {
  return {
    departments: academicStructure.departments
      .map((department) => ({
        name: department.name?.trim() || '',
        majors: (department.majors || []).map((major) => ({ name: major.name?.trim() || '' })).filter((major) => major.name)
      }))
      .filter((department) => department.name)
  }
}
function openCreate() { reset(); dialogVisible.value = true }
async function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, row)
  const structure = await schoolApi.academicStructure(row.id)
  academicStructure.departments = (structure?.departments || []).map((department) => ({
    name: department.name,
    majors: (department.majors || []).map((major) => ({ name: major.name }))
  }))
  dialogVisible.value = true
}
function resetTeacherRosterForm() { Object.assign(teacherRosterForm, { id: null, employeeNumber: '', name: '', department: '' }) }
function parseTeacherRows(text) {
  return String(text || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean).map(line => {
    const cells = line.split(/[,，\t]/).map(item => item.trim())
    return { employeeNumber: cells[0] || '', name: cells[1] || '', department: cells[2] || '' }
  }).filter(row => row.employeeNumber && !/^(工号|employee.?number)$/i.test(row.employeeNumber))
}
async function handleTeacherBatchFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  teacherBatchFileName.value = file.name
  const bytes = new Uint8Array(await file.arrayBuffer())
  let text = ''
  if (/\.(xlsx|xls)$/i.test(file.name)) {
    const workbook = XLSX.read(bytes, { type: 'array' })
    text = XLSX.utils.sheet_to_csv(workbook.Sheets[workbook.SheetNames[0]])
  } else text = new TextDecoder('utf-8').decode(bytes)
  teacherBatchRows.value = parseTeacherRows(text)
  event.target.value = ''
}
async function importTeachers() {
  if (!teacherBatchRows.value.length || !teacherRosterSchool.value) return ElMessage.warning('请先上传教师名单')
  teacherRosterSaving.value = true
  try {
    for (const row of teacherBatchRows.value) await schoolApi.addTeacherRoster(teacherRosterSchool.value.id, row)
    ElMessage.success(`已导入 ${teacherBatchRows.value.length} 名教师`)
    teacherBatchRows.value = []; teacherBatchFileName.value = ''
    await loadTeacherRoster()
  } finally { teacherRosterSaving.value = false }
}
async function openTeacherRoster(row) {
  teacherRosterSchool.value = row
  resetTeacherRosterForm()
  teacherRosterVisible.value = true
  await Promise.all([loadTeacherRoster(), loadTeacherRosterStructure()])
}
async function loadTeacherRosterStructure() {
  teacherRosterStructure.value = await schoolApi.academicStructure(teacherRosterSchool.value.id)
}
async function loadTeacherRoster() {
  if (!teacherRosterSchool.value) return
  teacherRosterLoading.value = true
  try { teacherRosters.value = await schoolApi.teacherRoster(teacherRosterSchool.value.id) } finally { teacherRosterLoading.value = false }
}
function editTeacherRoster(row) { Object.assign(teacherRosterForm, { id: row.id, employeeNumber: row.employeeNumber, name: row.name || '', department: row.department || '' }) }
async function saveTeacherRoster() {
  if (!teacherRosterForm.employeeNumber.trim()) return ElMessage.warning('请填写教师工号')
  teacherRosterSaving.value = true
  try {
    const payload = { employeeNumber: teacherRosterForm.employeeNumber.trim(), name: teacherRosterForm.name.trim(), department: teacherRosterForm.department.trim() }
    if (teacherRosterForm.id) await schoolApi.updateTeacherRoster(teacherRosterSchool.value.id, teacherRosterForm.id, payload)
    else await schoolApi.addTeacherRoster(teacherRosterSchool.value.id, payload)
    ElMessage.success(teacherRosterForm.id ? '教师名单已更新' : '教师工号已加入名单')
    resetTeacherRosterForm()
    await loadTeacherRoster()
  } finally { teacherRosterSaving.value = false }
}
async function toggleTeacherRoster(row) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  await schoolApi.updateTeacherRosterStatus(teacherRosterSchool.value.id, row.id, next)
  ElMessage.success(next === 'ACTIVE' ? '工号已启用' : '工号已停用')
  await loadTeacherRoster()
}
async function save() {
  if (!form.name?.trim()) return ElMessage.warning('请填写学校名称')
  saving.value = true
  try {
    const payload = { name: form.name.trim(), code: form.code?.trim() || null, region: form.region?.trim() || null }
    const saved = editingId.value ? await schoolApi.update(editingId.value, payload) : await schoolApi.create(payload)
    const schoolId = editingId.value || saved?.id
    if (schoolId) await schoolApi.updateAcademicStructure(schoolId, structurePayload())
    dialogVisible.value = false
    ElMessage.success(editingId.value ? '学校信息已更新' : '学校已创建')
    await load()
  } finally { saving.value = false }
}
async function toggleStatus(row) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  await ElMessageBox.confirm(`确认${next === 'ACTIVE' ? '启用' : '停用'}“${row.name}”吗？`, '学校状态', { type: 'warning' })
  await schoolApi.updateStatus(row.id, next)
  ElMessage.success('学校状态已更新')
  await load()
}
onMounted(load)
</script>

<template>
  <div class="page-stack school-management-view">
    <PageHero title="学校管理" description="维护学校信息，学校编码用于区分不同学校的学号和行政班。">
      <template #actions><el-button :icon="RefreshRight" :loading="loading" @click="load">刷新</el-button><el-button v-if="authStore.role === 'SUPER_ADMIN'" type="primary" :icon="Plus" @click="openCreate">新增学校</el-button></template>
    </PageHero>
    <el-table :data="schools" v-loading="loading" empty-text="暂无学校">
      <el-table-column prop="name" label="学校名称" min-width="220" />
      <el-table-column prop="code" label="学校编码" width="180" />
      <el-table-column prop="region" label="所属地区" min-width="160" />
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '正常' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="280" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="primary" @click="openTeacherRoster(row)">教师名单</el-button><el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleStatus(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑学校' : '新增学校'" width="680px" class="school-edit-dialog" append-to-body>
      <el-form label-position="top"><el-form-item label="学校名称" required><el-input v-model="form.name" maxlength="100" /></el-form-item><el-form-item label="学校编码"><el-input v-model="form.code" maxlength="30" :disabled="Boolean(editingId)" placeholder="可不填，系统将自动生成唯一编码" /><div class="school-code-tip">学校创建后编码用于区分不同学校的学号和行政班。</div></el-form-item><el-form-item label="所属地区"><el-input v-model="form.region" maxlength="100" /></el-form-item>
        <el-form-item label="院系与专业"><div class="structure-editor"><div v-for="(department, departmentIndex) in academicStructure.departments" :key="departmentIndex" class="department-editor"><div class="department-head"><el-input v-model="department.name" maxlength="100" placeholder="例如：计算机学院" /><el-button link type="danger" :icon="Delete" @click="removeDepartment(departmentIndex)">删除院系</el-button></div><div class="major-editor"><div v-for="(major, majorIndex) in department.majors" :key="majorIndex" class="major-row"><el-input v-model="major.name" maxlength="100" placeholder="专业名称，例如：软件工程" /><el-button link type="danger" :icon="Delete" aria-label="删除专业" @click="removeMajor(department, majorIndex)" /></div><el-button link type="primary" :icon="PlusIcon" @click="addMajor(department)">添加专业</el-button></div></div><div class="structure-actions"><el-button plain type="primary" :icon="PlusIcon" @click="addDepartment">添加院系</el-button><el-button plain :icon="PlusIcon" @click="openBatchDepartments">批量添加院系</el-button></div><div class="structure-tip">先配置学校的院系和专业，后续新建课程、行政班和用户资料会从这里选择。</div></div></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="batchDeptVisible" title="批量添加院系" width="560px" append-to-body>
      <div class="batch-dept-box">
        <input type="file" accept=".xlsx,.xls,.csv,.txt,text/csv,text/plain" @change="handleBatchDeptFile" />
        <span v-if="batchDeptFileName" class="batch-dept-file">{{ batchDeptFileName }}</span>
        <div v-if="batchDeptRows.length" class="batch-dept-preview">已识别 {{ batchDeptRows.length }} 个院系：{{ batchDeptRows.slice(0, 3).map(item => item.name).join('，') }}<span v-if="batchDeptRows.length > 3"> 等</span></div>
        <small>上传 Excel/CSV/文本文件。每行第一列为院系名称，其后各列为该院系专业；也支持“院系：专业1，专业2”写法。已存在的院系会自动合并新专业。</small>
      </div>
      <template #footer><el-button @click="batchDeptVisible = false">取消</el-button><el-button type="primary" :disabled="!batchDeptRows.length" @click="applyBatchDepartments">添加到列表 {{ batchDeptRows.length ? `（${batchDeptRows.length} 个院系）` : '' }}</el-button></template>
    </el-dialog>
    <el-dialog v-model="teacherRosterVisible" :title="`${teacherRosterSchool?.name || ''} · 教师工号名单`" width="760px">
      <div class="teacher-roster-form">
        <el-input v-model="teacherRosterForm.employeeNumber" placeholder="工号（必填）" />
        <el-input v-model="teacherRosterForm.name" placeholder="教师姓名" />
        <el-select v-model="teacherRosterForm.department" filterable clearable placeholder="请选择学院/部门"><el-option v-for="department in teacherRosterStructure.departments" :key="department.id" :label="department.name" :value="department.name" /></el-select>
        <el-button type="primary" :loading="teacherRosterSaving" @click="saveTeacherRoster">{{ teacherRosterForm.id ? '保存修改' : '加入名单' }}</el-button>
        <el-button v-if="teacherRosterForm.id" @click="resetTeacherRosterForm">取消编辑</el-button>
      </div>
      <div class="teacher-batch-import">
        <input type="file" accept=".xlsx,.xls,.csv,.txt,text/csv,text/plain" @change="handleTeacherBatchFile" />
        <span v-if="teacherBatchFileName">{{ teacherBatchFileName }} · 已识别 {{ teacherBatchRows.length }} 人</span>
        <el-button type="primary" plain :loading="teacherRosterSaving" :disabled="!teacherBatchRows.length" @click="importTeachers">批量导入教师</el-button>
        <small>文件列顺序：工号、姓名、学院；教师只归属学院，不绑定行政班。</small>
      </div>
      <el-table :data="teacherRosters" v-loading="teacherRosterLoading" empty-text="暂无教师工号，请先录入学校名单">
        <el-table-column prop="employeeNumber" label="工号" width="180" />
        <el-table-column prop="name" label="姓名" width="150" />
        <el-table-column prop="department" label="学院/部门" min-width="180" />
        <el-table-column label="绑定状态" width="110"><template #default="{ row }">{{ row.userId ? '已注册' : '待注册' }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }">{{ row.status === 'ACTIVE' ? '有效' : '停用' }}</template></el-table-column>
        <el-table-column label="操作" width="150"><template #default="{ row }"><el-button link type="primary" @click="editTeacherRoster(row)">编辑</el-button><el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleTeacherRoster(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button></template></el-table-column>
      </el-table>
      <div class="teacher-roster-tip">只有名单中的有效工号，才能注册为该校教师并发布教学中心、教研活动等内容。</div>
    </el-dialog>
  </div>
</template>

<style scoped>
.school-code-tip { margin-top: 6px; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.teacher-roster-form { display: grid; grid-template-columns: 1fr 1fr 1fr auto auto; gap: 10px; margin-bottom: 16px; }
.teacher-roster-tip { margin-top: 12px; color: var(--text-muted); font-size: 12px; }
.structure-editor { width: 100%; display: flex; flex-direction: column; gap: 12px; }
.department-editor { padding: 12px; border: 1px solid var(--border-color, #e6eaf2); border-radius: 8px; background: var(--surface-soft, #f8fafc); }
.department-head, .major-row { display: flex; align-items: center; gap: 8px; }
.department-head .el-input, .major-row .el-input { flex: 1; }
.major-editor { margin: 10px 0 0 24px; display: flex; flex-direction: column; align-items: flex-start; gap: 8px; }
.major-row { width: 100%; }
.structure-tip { color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.structure-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.batch-dept-box { display: flex; flex-direction: column; gap: 8px; }
.batch-dept-box small { color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.batch-dept-file { color: var(--text-secondary); font-size: 12px; }
.batch-dept-preview { color: var(--text-secondary); font-size: 13px; }
.school-edit-dialog :deep(.el-dialog) { display: flex; flex-direction: column; max-height: 84vh; margin-top: 8vh; }
.school-edit-dialog :deep(.el-dialog__body) { flex: 1; overflow-y: auto; }
.school-edit-dialog :deep(.el-dialog__footer) { flex-shrink: 0; border-top: 1px solid var(--border-color, #e6eaf2); padding-top: 14px; }
@media (max-width: 800px) { .teacher-roster-form { grid-template-columns: 1fr 1fr; } }
</style>
