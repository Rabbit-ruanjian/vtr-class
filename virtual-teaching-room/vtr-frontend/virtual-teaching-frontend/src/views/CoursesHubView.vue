<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, DataAnalysis, Delete, EditPen, FolderOpened, MoreFilled, Plus, RefreshRight, Search, UserFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHero from '@/components/PageHero.vue'
import { courseApi, uploadApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { resolveAssetUrl } from '@/utils/format'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
// A course-management landing page must never hide accessible courses by default.
const activeView = ref('all')
const courses = ref([])
const selectedCourseId = ref(null)
const loadError = ref('')
const query = reactive({ keyword: '', status: '' })
const form = reactive({ courseName: '', courseCode: '', description: '', semester: '', credits: null, courseCategory: '', teachingDepartment: '', assessmentMethod: '', coverImage: '' })

const metrics = computed(() => ({
  total: courses.value.length,
  active: courses.value.filter((course) => course.status === 'ACTIVE').length,
  archived: courses.value.filter((course) => course.status !== 'ACTIVE').length
}))

const visibleCourses = computed(() => {
  const keyword = query.keyword.trim().toLowerCase()
  return courses.value.filter((course) => {
    const matchesKeyword = !keyword || [course.courseName, course.courseCode, course.teachingDepartment].filter(Boolean).some((value) => String(value).toLowerCase().includes(keyword))
    const matchesTab = activeView.value === 'all' || (activeView.value === 'active' && course.status === 'ACTIVE') || (activeView.value === 'archived' && course.status !== 'ACTIVE')
    const matchesStatus = !query.status || course.status === query.status
    return matchesKeyword && matchesTab && matchesStatus
  })
})
const selectedCourse = computed(() => visibleCourses.value.find((course) => String(course.id) === String(selectedCourseId.value)) || visibleCourses.value[0] || null)

const canManage = (course) => authStore.isAdmin
  || String(course.createdBy) === String(authStore.user?.id)

async function loadCourses() {
  loading.value = true
  loadError.value = ''
  try {
    courses.value = await courseApi.manageList() || []
    if (!selectedCourseId.value && courses.value.length) selectedCourseId.value = courses.value[0].id
  } catch (error) {
    courses.value = []
    loadError.value = error.message || '课程列表加载失败'
    ElMessage.error(loadError.value)
  } finally { loading.value = false }
}

function openWorkspace(course) { router.push({ name: 'course-workspace', params: { id: course.id } }) }
function selectCourse(course) { selectedCourseId.value = course.id }
function resetForm() { Object.assign(form, { courseName: '', courseCode: '', description: '', semester: '', credits: null, courseCategory: '', teachingDepartment: '', assessmentMethod: '', coverImage: '' }); editingId.value = null }
function openCreate() { resetForm(); dialogVisible.value = true }
function openEdit(course) { editingId.value = course.id; Object.keys(form).forEach((key) => { form[key] = course[key] ?? (key === 'credits' ? null : '') }); dialogVisible.value = true }
function cloneCourse(course) { openEdit(course); editingId.value = null; form.courseName = `${course.courseName || ''}（副本）`; form.courseCode = ''; ElMessage.info('已复制课程基础信息，请补充新学期后保存') }
async function uploadCover(event) {
  const file = event.target.files?.[0]
  if (!file) return
  try {
    form.coverImage = await uploadApi.courseCover(file)
    ElMessage.success('课程封面已上传')
  } catch (error) {
    ElMessage.error(error?.message || '课程封面上传失败')
  } finally {
    event.target.value = ''
  }
}
async function saveCourse() {
  if (!form.courseName.trim()) { ElMessage.warning('请输入课程名称'); return }
  saving.value = true
  try {
    const payload = { ...form, courseName: form.courseName.trim() }
    if (editingId.value) await courseApi.update(editingId.value, payload)
    else await courseApi.create(payload)
    dialogVisible.value = false
    await loadCourses()
    ElMessage.success(editingId.value ? '课程已更新' : '课程已创建')
  } finally { saving.value = false }
}

async function archiveCourse(course) {
  await ElMessageBox.confirm(
    `确认归档课程“${course.courseName}”？归档后学生将不能继续使用该课程，但课程资料仍会保留。`,
    '归档课程',
    { type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '取消' }
  )
  await courseApi.archive(course.id)
  await loadCourses()
  ElMessage.success('课程已归档')
}

async function restoreCourse(course) {
  await ElMessageBox.confirm(
    `确认恢复课程“${course.courseName}”？恢复后课程将重新进入授课状态。`,
    '恢复课程',
    { type: 'info', confirmButtonText: '确认恢复', cancelButtonText: '取消' }
  )
  await courseApi.restore(course.id)
  await loadCourses()
  ElMessage.success('课程已恢复')
}

onMounted(loadCourses)
</script>

<template>
  <div class="course-hub page-stack">
    <PageHero title="教学中心" description="从这里创建课程，并继续完成章节、教学班和教学资源建设。">
      <template #actions>
        <el-button v-if="authStore.isAdmin" @click="router.push({ name: 'admin', query: { tab: 'classes' } })">班级与学生</el-button>
        <el-button :icon="RefreshRight" :loading="loading" @click="loadCourses">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建课程</el-button>
      </template>
    </PageHero>

    <div v-if="loadError" class="hub-load-error">
      <strong>课程列表未能加载</strong>
      <span>{{ loadError }}</span>
      <el-button type="primary" :icon="RefreshRight" @click="loadCourses">重新加载</el-button>
    </div>

    <div v-else v-loading="loading" class="hub-studio">
      <aside class="studio-shelf">
        <div class="studio-shelf-title"><span>课程书阁</span><small>{{ metrics.total }} 门课程</small></div>
        <div class="studio-filter"><el-input v-model="query.keyword" clearable :prefix-icon="Search" placeholder="查找课程" /></div>
        <nav class="studio-course-list" aria-label="课程列表">
          <button v-for="(course, index) in visibleCourses" :key="course.id" type="button" :class="{ active: String(selectedCourse?.id) === String(course.id) }" @click="selectCourse(course)"><i :class="`tone-${index % 4}`"></i><span>{{ course.courseName }}</span><small>{{ course.courseCode || '课程' }}</small></button>
        </nav>
        <button class="studio-add" type="button" @click="openCreate">＋ 新建课程</button>
        <p class="studio-shelf-foot">课程资源、章节与教学班<br />在同一处持续建设</p>
      </aside>
      <main v-if="selectedCourse" class="studio-paper">
        <div class="studio-kicker">— 课程空间 · {{ selectedCourse.status === 'ACTIVE' ? '本学期授课' : '历史课程' }} —</div>
        <h1>{{ selectedCourse.courseName }}</h1>
        <p class="studio-subtitle">{{ selectedCourse.description || '进入课程工作台，继续建设章节内容与教学资源。' }}</p>
        <section class="studio-content-card"><div class="studio-content-main"><div class="studio-course-mark"><FolderOpened :size="28" /></div><div><span class="studio-label">课程代码</span><strong>{{ selectedCourse.courseCode || '未设置课程代码' }}</strong></div><div><span class="studio-label">开课院系</span><strong>{{ selectedCourse.teachingDepartment || '--' }}</strong></div><div><span class="studio-label">课程类别</span><strong>{{ selectedCourse.courseCategory || '--' }}</strong></div><div><span class="studio-label">学分</span><strong>{{ selectedCourse.credits ?? '--' }}</strong></div></div><div class="studio-actions"><el-button type="primary" @click="openWorkspace(selectedCourse)">进入课程工作台</el-button><el-button @click="openWorkspace(selectedCourse)">教学班与学生</el-button></div></section>
        <div class="studio-resource-grid"><button type="button" @click="openWorkspace(selectedCourse)"><strong>章节与课件</strong><span>组织课程目录，上传课件与视频</span><b>进入 →</b></button><button type="button" @click="openWorkspace(selectedCourse)"><strong>课程测验</strong><span>建设题库，发布作业与考试</span><b>进入 →</b></button><button type="button" @click="openWorkspace(selectedCourse)"><strong>教学数据</strong><span>查看学习进度与参与情况</span><b>查看 →</b></button></div>
      </main>
      <div v-else class="studio-empty"><FolderOpened :size="42" /><strong>还没有可进入的课程</strong><span>创建一门课程，开始建设教学空间</span><el-button type="primary" :icon="Plus" @click="openCreate">新建课程</el-button></div>
      <aside class="studio-guide"><div class="guide-avatar">塾</div><h2>云塾 · 教研助手</h2><p>课程资源 · 教学设计 · 学习支持</p><div class="guide-note">当前正在查看<br /><strong>{{ selectedCourse?.courseName || '课程空间' }}</strong><br />你可以从左侧切换课程，或进入工作台继续编辑。</div><div class="guide-actions"><button type="button" @click="selectedCourse && openWorkspace(selectedCourse)">进入工作台</button><button type="button" @click="openCreate">新建课程</button></div></aside>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑课程' : '新建课程'" width="680px">
      <el-form label-position="top"><div class="hub-form-grid"><el-form-item label="课程名称" required><el-input v-model="form.courseName" maxlength="100" /></el-form-item><el-form-item label="课程代码"><el-input v-model="form.courseCode" maxlength="50" placeholder="可留空自动生成" /></el-form-item><el-form-item label="学期"><el-input v-model="form.semester" placeholder="例如：2026-2027 第一学期" /></el-form-item><el-form-item label="学分"><el-input-number v-model="form.credits" :min="0" :step="0.5" controls-position="right" class="full-width" /></el-form-item><el-form-item label="课程类别"><el-input v-model="form.courseCategory" /></el-form-item><el-form-item label="开课院系"><el-input v-model="form.teachingDepartment" /></el-form-item></div><el-form-item label="课程封面"><div class="cover-editor"><img v-if="form.coverImage" class="cover-preview" :src="resolveAssetUrl(form.coverImage)" alt="课程封面预览" /><span v-else class="cover-empty">暂未上传封面</span><div class="cover-actions"><label class="cover-upload-button">{{ form.coverImage ? '更换封面' : '上传封面' }}<input type="file" accept="image/jpeg,image/png,image/gif,image/webp" @change="uploadCover" /></label><el-button v-if="form.coverImage" link type="danger" @click="form.coverImage = ''">移除封面</el-button></div></div></el-form-item><el-form-item label="课程简介"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveCourse">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hub-summary { display:flex; align-items:center; gap:22px; padding:18px 22px; border:1px solid var(--el-border-color); background:var(--el-bg-color); border-radius:8px; }
.hub-summary div { min-width:86px; }.hub-summary strong { display:block; color:var(--brand-deep); font-size:26px; }.hub-summary span { color:var(--text-muted); font-size:12px; }.hub-summary i { width:1px; height:34px; background:var(--el-border-color); }.hub-summary p { margin:0 0 0 auto; color:var(--text-secondary); font-size:13px; }
.hub-tabs { display:flex; gap:24px; border-bottom:1px solid var(--el-border-color); }.hub-tabs button { padding:13px 2px; border:0; border-bottom:2px solid transparent; background:none; color:var(--text-secondary); cursor:pointer; }.hub-tabs button.active { border-color:var(--brand); color:var(--brand-deep); font-weight:600; }.hub-tabs b { margin-left:5px; font-size:12px; color:var(--text-muted); }
.hub-toolbar { margin-top:2px; }.hub-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px; min-height:220px; }.hub-card { overflow:hidden; border:1px solid var(--el-border-color); border-radius:8px; background:var(--el-bg-color); box-shadow:0 4px 14px rgba(25,65,120,.06); }.hub-cover { position:relative; display:block; width:100%; height:148px; padding:0; border:0; background:linear-gradient(135deg,#dbeafe,#eff6ff); cursor:pointer; }.hub-cover img { width:100%; height:100%; object-fit:cover; }.hub-cover span { display:flex; height:100%; align-items:center; justify-content:center; flex-direction:column; gap:6px; color:var(--text-muted); }.hub-cover em { position:absolute; top:12px; right:12px; padding:4px 8px; border-radius:4px; background:#fff; font-size:12px; font-style:normal; }.hub-cover em.active { color:#27815f; }.hub-cover em.archived { color:#7b8494; }
.hub-card-content { padding:16px; }.hub-card header { display:flex; justify-content:space-between; gap:8px; }.hub-card h2 { overflow:hidden; margin:0; color:var(--text-main); font-size:17px; text-overflow:ellipsis; white-space:nowrap; }.hub-card header p { margin:6px 0 0; color:var(--text-muted); font-size:12px; }.hub-description { display:-webkit-box; min-height:38px; margin:14px 0; overflow:hidden; color:var(--text-secondary); font-size:13px; line-height:1.5; -webkit-box-orient:vertical; -webkit-line-clamp:2; }.hub-facts { display:grid; grid-template-columns:repeat(3,1fr); gap:8px; padding:12px 0; border-top:1px solid var(--el-border-color); border-bottom:1px solid var(--el-border-color); }.hub-facts strong,.hub-facts small { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.hub-facts strong { color:var(--text-main); font-size:13px; }.hub-facts small { margin-top:4px; color:var(--text-muted); font-size:11px; }.hub-card footer { display:flex; align-items:center; gap:4px; padding-top:14px; }.hub-card footer .el-button:last-child { margin-left:auto; }.hub-empty { display:flex; min-height:260px; grid-column:1/-1; align-items:center; justify-content:center; flex-direction:column; gap:9px; color:var(--text-muted); }.hub-empty strong { color:var(--text-secondary); }.hub-form-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:0 18px; }.full-width { width:100%; }
@media (max-width:900px) { .hub-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } .hub-summary p { display:none; } }
@media (max-width:640px) { .hub-grid,.hub-form-grid { grid-template-columns:1fr; }.hub-summary { gap:12px; padding:14px; }.hub-summary i { display:none; }.hub-summary strong { font-size:21px; }.hub-tabs { gap:14px; overflow:auto; }.hub-toolbar { flex-wrap:wrap; }.hub-toolbar .toolbar-grow { flex-basis:100%; }.status-filter { flex:1; } }
/* Keep Element Plus icons at their intended size inside fixed cover areas. */
.hub-cover span :deep(svg) { width:28px !important; height:28px !important; flex:none; }
.hub-empty :deep(svg) { width:42px !important; height:42px !important; flex:none; }
.hub-load-error { display:flex; min-height:220px; align-items:center; justify-content:center; flex-direction:column; gap:9px; border:1px dashed var(--el-border-color); color:var(--text-muted); }.hub-load-error strong { color:var(--text-main); }
.course-hub { min-height: calc(100vh - 130px); padding: 8px 0 30px; background: #eee6d7; }
.course-hub :deep(.page-titlebar) { padding: 10px 18px 18px; border-bottom-color: #d5c9b7; }
.hub-studio { display: grid; grid-template-columns: 250px minmax(0, 1fr) 250px; gap: 24px; min-height: 650px; padding: 22px; border-radius: 20px; background: #e9dfcf; }
.studio-shelf { display: flex; min-width: 0; flex-direction: column; padding: 24px 16px 18px; border-radius: 18px; background: #433328; color: #f6efe3; box-shadow: 0 16px 32px rgba(66,48,35,.18); }
.studio-shelf-title { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; padding: 0 10px 18px; border-bottom: 1px solid rgba(255,255,255,.14); }.studio-shelf-title span { font-family: "Songti SC", serif; font-size: 22px; }.studio-shelf-title small { color: #c9b9a5; font-size: 11px; }
.studio-filter { margin: 16px 0 10px; }.studio-filter :deep(.el-input__wrapper) { border: 1px solid rgba(255,255,255,.18); box-shadow: none; background: rgba(255,255,255,.08); }.studio-filter :deep(input) { color: #f6efe3; }.studio-filter :deep(input::placeholder) { color: #bcae9e; }
.studio-course-list { display: grid; gap: 9px; overflow: auto; }.studio-course-list button { display: grid; grid-template-columns: 7px minmax(0,1fr); gap: 9px; align-items: center; min-width: 0; padding: 13px 10px; border: 0; border-radius: 7px; background: rgba(255,255,255,.08); color: #f6efe3; cursor: pointer; text-align: left; }.studio-course-list button.active { background: #6a5648; box-shadow: inset 3px 0 #e8c875; }.studio-course-list i { width: 7px; height: 32px; border-radius: 5px; background: #73b8ec; }.studio-course-list i.tone-1 { background: #5bdb91; }.studio-course-list i.tone-2 { background: #f2c742; }.studio-course-list i.tone-3 { background: #ec8a8a; }.studio-course-list span { overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }.studio-course-list small { grid-column: 2; overflow: hidden; color: #c9b9a5; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.studio-add { margin-top: auto; padding: 12px; border: 1px solid #bd3a35; border-radius: 6px; background: transparent; color: #db6155; cursor: pointer; font-size: 14px; }.studio-shelf-foot { margin: 18px 10px 0; color: #c9b9a5; font-size: 12px; line-height: 1.7; }
.studio-paper { min-width: 0; padding: 10px 24px 28px; color: #314b5d; text-align: center; }.studio-kicker { color: #8b99a2; font-family: "Songti SC", serif; font-size: 14px; }.studio-paper h1 { margin: 18px 0 8px; color: #243b4d; font-family: "Songti SC", serif; font-size: clamp(26px, 3vw, 38px); font-weight: 500; }.studio-subtitle { max-width: 700px; margin: 0 auto 22px; color: #73818b; line-height: 1.7; }
.studio-content-card { display: grid; gap: 20px; padding: 24px; border: 1px solid #ddd3c1; border-radius: 16px; background: #faf7ee; box-shadow: 0 18px 32px rgba(77,62,44,.12); text-align: left; }.studio-content-main { display: grid; grid-template-columns: 64px repeat(4,minmax(0,1fr)); gap: 16px; align-items: center; }.studio-course-mark { display: grid; width: 58px; height: 58px; place-items: center; border-radius: 12px; background: #e5edf2; color: #315b7d; }.studio-label { display: block; margin-bottom: 5px; color: #a08f77; font-size: 11px; }.studio-content-main strong { color: #324d5e; font-size: 14px; overflow-wrap: anywhere; }.studio-actions { display: flex; gap: 10px; padding-top: 16px; border-top: 1px solid #e2d8c7; }.studio-actions :deep(.el-button--primary) { --el-button-bg-color: #b43b37; --el-button-border-color: #b43b37; --el-button-hover-bg-color: #96312f; }
.studio-resource-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 12px; margin-top: 18px; text-align: left; }.studio-resource-grid button { display: grid; gap: 8px; min-height: 130px; padding: 18px; border: 1px solid #ddd3c1; border-radius: 12px; background: #f6f0e4; color: #315b7d; cursor: pointer; text-align: left; }.studio-resource-grid button:hover { border-color: #caa85a; background: #fbf5e7; }.studio-resource-grid strong { font-family: "Songti SC", serif; font-size: 18px; font-weight: 500; }.studio-resource-grid span { color: #8a887f; font-size: 12px; line-height: 1.6; }.studio-resource-grid b { align-self: end; color: #ae812a; font-size: 12px; font-weight: 500; }
.studio-guide { align-self: start; padding: 28px 18px 20px; border-radius: 18px; background: #faf7ee; color: #4a5962; text-align: center; box-shadow: 0 14px 28px rgba(77,62,44,.1); }.guide-avatar { display: grid; width: 76px; height: 76px; margin: 0 auto 14px; place-items: center; border-radius: 12px; background: #e2d9c8; color: #3b4e5d; font-family: "Songti SC",serif; font-size: 34px; }.studio-guide h2 { margin: 0; color: #3b4e5d; font-family: "Songti SC",serif; font-size: 19px; font-weight: 500; }.studio-guide p { margin: 6px 0 22px; color: #9a9286; font-size: 12px; }.guide-note { min-height: 150px; padding: 18px; border-radius: 12px; background: #f0eadf; color: #777b7c; font-size: 13px; line-height: 1.8; text-align: left; }.guide-note strong { color: #315b7d; font-size: 15px; }.guide-actions { display: grid; gap: 9px; margin-top: 18px; }.guide-actions button { padding: 10px; border: 1px solid #bd3a35; border-radius: 5px; background: transparent; color: #b43b37; cursor: pointer; }.guide-actions button:last-child { background: #b43b37; color: #fff9ee; }
@media (max-width: 1100px) { .hub-studio { grid-template-columns: 210px minmax(0,1fr); }.studio-guide { display: none; } }
@media (max-width: 760px) { .hub-studio { grid-template-columns: 1fr; padding: 14px; }.studio-shelf { min-height: 280px; }.studio-course-list { max-height: 180px; }.studio-content-main { grid-template-columns: repeat(2,minmax(0,1fr)); }.studio-course-mark { grid-column: 1 / -1; }.studio-resource-grid { grid-template-columns: 1fr; } }
.cover-editor { display:flex; align-items:center; gap:14px; }.cover-preview { width:180px; height:96px; object-fit:cover; border:1px solid var(--el-border-color); border-radius:6px; }.cover-empty { color:var(--text-muted); font-size:13px; }.cover-actions { display:flex; align-items:center; gap:10px; }.cover-upload-button { padding:8px 12px; border:1px solid var(--el-border-color); border-radius:5px; color:var(--brand); background:#fff; cursor:pointer; }.cover-upload-button:hover { border-color:var(--brand); background:#f6faff; }.cover-upload-button input { display:none; }
/* Keep the course search controls on one coherent row. */
.hub-toolbar .toolbar-grow { flex: 1 1 420px; min-width: 240px; width: auto; }
.hub-toolbar .status-filter { flex: 0 0 160px; width: 160px; }
.hub-toolbar > .el-button { flex: 0 0 auto; }
@media (max-width: 640px) {
  .hub-toolbar .toolbar-grow { flex-basis: 100%; }
  .hub-toolbar .status-filter { flex: 1 1 auto; width: auto; }
}
</style>
