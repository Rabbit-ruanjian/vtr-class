<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, FolderOpened, RefreshRight } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { courseApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { resolveAssetUrl } from '@/utils/format'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const courses = ref([])

const destination = computed(() => authStore.isTeacher ? '/courses' : '/courseware')
const heading = computed(() => authStore.isTeacher ? '我的教学课程' : '已加入课程')
const description = computed(() => authStore.isTeacher ? '继续课程建设、发布教学活动或管理教学班' : '查看已加入教学班的课程与学习资源')
const shownCourses = computed(() => courses.value.slice(0, 3))

async function load() {
  if (!authStore.isLoggedIn) return
  loading.value = true
  try {
    courses.value = authStore.isTeacher
      ? await courseApi.manageList({ status: 'ACTIVE' })
      : await courseApi.joined()
    courses.value = courses.value.filter((course) => !course.status || course.status === 'ACTIVE')
  } finally { loading.value = false }
}

function openCourse(course) {
  if (authStore.isTeacher) router.push({ name: 'course-workspace', params: { id: course.id } })
  else router.push({ path: '/courseware', query: { courseId: course.id } })
}

onMounted(load)
</script>

<template>
  <section class="my-course-panel" v-loading="loading">
    <header>
      <div><h3>{{ heading }}</h3><p>{{ description }}</p></div>
      <div class="panel-actions"><el-button text :icon="RefreshRight" aria-label="刷新课程" @click="load" /><el-button link type="primary" :icon="ArrowRight" icon-position="right" @click="router.push(destination)">查看全部</el-button></div>
    </header>
    <div v-if="shownCourses.length" class="course-list">
      <button v-for="course in shownCourses" :key="course.id" class="course-row" type="button" @click="openCourse(course)">
        <img v-if="course.coverImage" :src="resolveAssetUrl(course.coverImage)" :alt="`${course.courseName}封面`" />
        <span v-else class="course-image-placeholder"><FolderOpened :size="20" /></span>
        <span class="course-row-main"><strong>{{ course.courseName }}</strong><small>{{ course.courseCode || '未设置课程代码' }}<template v-if="course.semester"> · {{ course.semester }}</template></small></span>
        <el-tag v-if="authStore.isTeacher" size="small" :type="course.status === 'ACTIVE' ? 'success' : 'info'">{{ course.status === 'ACTIVE' ? '授课中' : '已归档' }}</el-tag>
      </button>
    </div>
    <div v-else class="course-panel-empty"><FolderOpened :size="26" /><span>{{ authStore.isTeacher ? '暂未创建或参与课程' : '暂未加入课程' }}</span><el-button type="primary" size="small" @click="router.push(destination)">{{ authStore.isTeacher ? '新建课程' : '去加入课程' }}</el-button></div>
  </section>
</template>

<style scoped>
.my-course-panel { padding:20px; border:1px solid var(--el-border-color); border-radius:8px; background:var(--el-bg-color); }.my-course-panel header { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; margin-bottom:16px; }.my-course-panel h3 { margin:0; color:var(--text-main); font-size:17px; }.my-course-panel p { margin:6px 0 0; color:var(--text-muted); font-size:13px; }.panel-actions { display:flex; align-items:center; }.course-list { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }.course-row { display:flex; min-width:0; align-items:center; gap:10px; padding:10px; border:1px solid var(--el-border-color); border-radius:6px; background:transparent; cursor:pointer; text-align:left; }.course-row:hover { border-color:var(--brand); background:rgba(48,112,255,.04); }.course-row img,.course-image-placeholder { width:44px; height:44px; border-radius:4px; flex:none; object-fit:cover; }.course-image-placeholder { display:flex; align-items:center; justify-content:center; color:var(--brand-deep); background:#edf4ff; }.course-row-main { display:flex; min-width:0; flex:1; flex-direction:column; gap:5px; }.course-row-main strong,.course-row-main small { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.course-row-main strong { color:var(--text-main); font-size:14px; }.course-row-main small { color:var(--text-muted); font-size:12px; }.course-panel-empty { display:flex; min-height:92px; align-items:center; justify-content:center; gap:10px; color:var(--text-muted); }.course-panel-empty :deep(svg) { width:26px !important; height:26px !important; }.course-panel-empty .el-button { margin-left:6px; } @media (max-width:900px) { .course-list { grid-template-columns:1fr; } } @media (max-width:640px) { .my-course-panel header { align-items:stretch; flex-direction:column; }.panel-actions { justify-content:space-between; } }
</style>
