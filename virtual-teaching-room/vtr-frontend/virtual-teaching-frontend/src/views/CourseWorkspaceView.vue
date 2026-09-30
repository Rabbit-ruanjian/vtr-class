<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, CirclePlus, Refresh } from '@element-plus/icons-vue'
import PageHero from '@/components/PageHero.vue'
import { courseApi } from '@/api'
import CourseAiKnowledgePanel from '@/components/CourseAiKnowledgePanel.vue'

const route = useRoute()
const router = useRouter()
const courseId = Number(route.params.id)
const loading = ref(false)
const overview = ref(null)
const classrooms = ref([])
const members = ref([])
const activeTab = ref('overview')

const stats = computed(() => [
  ['教师团队', overview.value?.teacherCount ?? 0], ['教学班', overview.value?.classroomCount ?? 0],
  ['学生', overview.value?.studentCount ?? 0], ['资源', overview.value?.resourceCount ?? 0],
  ['作业习题', overview.value?.assignmentCount ?? 0]
])

async function load() {
  loading.value = true
  try {
    const [summary, classList, memberList] = await Promise.all([
      courseApi.overview(courseId), courseApi.classrooms(courseId), courseApi.members(courseId)
    ])
    overview.value = summary
    classrooms.value = classList || []
    members.value = memberList || []
  } finally { loading.value = false }
}

async function addMember() {
  const { value } = await ElMessageBox.prompt('请输入教师用户 ID', '添加协作教师', { inputPattern: /^\d+$/, inputErrorMessage: '请输入有效的用户 ID' })
  await courseApi.addMember(courseId, { userId: Number(value), role: 'CO_TEACHER' })
  await load()
  ElMessage.success('协作教师已添加')
}

async function removeMember(member) {
  if (member.role === 'OWNER') return
  await ElMessageBox.confirm(`确认移除 ${member.nickname || member.username}？`, '移除协作成员', { type: 'warning' })
  await courseApi.removeMember(courseId, member.userId)
  await load()
  ElMessage.success('成员已移除')
}

function go(path) { router.push({ path, query: { courseId } }) }
onMounted(load)
</script>

<template>
  <div class="page-stack" v-loading="loading">
    <PageHero :title="overview?.course?.courseName || '课程工作台'" description="围绕一门课程集中管理教学班、教师协作、课程资源与作业。">
      <template #actions>
        <el-button :icon="ArrowLeft" @click="router.push('/courseware')">返回课程</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </template>
    </PageHero>
    <div class="workspace-tabs">
      <el-button :type="activeTab === 'overview' ? 'primary' : 'default'" @click="activeTab = 'overview'">总览</el-button>
      <el-button :type="activeTab === 'classrooms' ? 'primary' : 'default'" @click="activeTab = 'classrooms'">教学班</el-button>
      <el-button :type="activeTab === 'members' ? 'primary' : 'default'" @click="activeTab = 'members'">教师团队</el-button>
      <el-button @click="go('/courseware')">课程资源</el-button>
      <el-button @click="go('/courseware')">课程作业</el-button>
      <el-button @click="go('/research-tasks')">教研任务</el-button>
      <el-button :type="activeTab === 'ai' ? 'primary' : 'default'" @click="activeTab = 'ai'">AI知识库</el-button>
    </div>
    <div v-if="activeTab === 'overview'" class="stats-grid">
      <div v-for="item in stats" :key="item[0]" class="stat-item"><span>{{ item[0] }}</span><strong>{{ item[1] }}</strong></div>
    </div>
    <section v-if="activeTab === 'classrooms'" class="workspace-section">
      <h3>教学班与班级成员</h3>
      <el-table :data="classrooms" empty-text="暂无教学班"><el-table-column prop="className" label="班级" /><el-table-column prop="studentCount" label="学生人数" width="120" /><el-table-column prop="status" label="状态" width="120" /></el-table>
    </section>
    <section v-if="activeTab === 'members'" class="workspace-section">
      <div class="section-heading"><h3>教师协作团队</h3><el-button type="primary" :icon="CirclePlus" @click="addMember">添加教师</el-button></div>
      <el-table :data="members" empty-text="暂无协作成员"><el-table-column label="教师" min-width="180"><template #default="{ row }">{{ row.nickname || row.username }}</template></el-table-column><el-table-column prop="email" label="邮箱" /><el-table-column prop="role" label="角色" width="180" /><el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-if="row.role !== 'OWNER'" link type="danger" @click="removeMember(row)">移除</el-button></template></el-table-column></el-table>
    </section>
    <CourseAiKnowledgePanel v-if="activeTab === 'ai'" :course-id="courseId" />
  </div>
</template>

<style scoped>
.workspace-tabs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
.stats-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.stat-item { padding: 20px; border: 1px solid var(--el-border-color); background: var(--el-bg-color); border-radius: 6px; }
.stat-item span { display: block; color: var(--text-muted); font-size: 13px; }
.stat-item strong { display: block; margin-top: 8px; font-size: 28px; color: var(--brand-deep); }
.workspace-section { padding: 20px; background: var(--el-bg-color); border: 1px solid var(--el-border-color); border-radius: 6px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
h3 { margin: 0 0 14px; }
@media (max-width: 640px) { .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
