<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHero from '@/components/PageHero.vue'
import { researchTaskApi } from '@/api'
import AiAssistantButton from '@/components/AiAssistantButton.vue'
import AiAssistantDrawer from '@/components/AiAssistantDrawer.vue'

const route = useRoute()
const courseId = Number(route.query.courseId)
const tasks = ref([])
const loading = ref(false)
const dialog = ref(false)
const aiVisible = ref(false)
const aiContext = ref({ title: '', meta: '', questionType: '', questionId: null, type: 'research', excerpt: '' })
const form = ref({ title: '', description: '', type: 'LESSON_PREP', dueAt: null })

async function load() {
  if (!courseId) return
  loading.value = true
  try { tasks.value = await researchTaskApi.list(courseId) } finally { loading.value = false }
}
async function create() {
  await researchTaskApi.create({ ...form.value, courseId })
  dialog.value = false
  form.value = { title: '', description: '', type: 'LESSON_PREP', dueAt: null }
  await load()
  ElMessage.success('任务已发布')
}
async function claim(id) { await researchTaskApi.claim(id); ElMessage.success('已认领任务') }
function openAiAssistant(task) {
  aiContext.value = {
    title: task.title || '当前教研任务',
    meta: `教研任务 · ${task.type || '未分类'} · ${task.status || '未知状态'}`,
    type: 'research',
    excerpt: task.description || ''
  }
  aiVisible.value = true
}
onMounted(load)
</script>

<template>
  <div class="page-stack" v-loading="loading">
    <PageHero title="教研任务" description="围绕课程组推进集体备课、资源共建和成果评审。">
      <template #actions><el-button type="primary" @click="dialog = true">发布任务</el-button></template>
    </PageHero>
    <el-empty v-if="!tasks.length" description="暂无教研任务" />
    <div v-else class="task-list">
      <article v-for="task in tasks" :key="task.id" class="task-item">
        <div><h3>{{ task.title }}</h3><p>{{ task.description }}</p><small>{{ task.type }} · {{ task.status }}</small></div>
        <div class="task-actions">
          <AiAssistantButton label="AI协助" @click="openAiAssistant(task)" />
          <el-button v-if="task.status === 'PUBLISHED'" @click="claim(task.id)">认领</el-button>
        </div>
      </article>
    </div>
    <el-dialog v-model="dialog" title="发布教研任务" width="560px">
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="任务说明"><el-input v-model="form.description" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="任务类型"><el-select v-model="form.type" style="width: 100%"><el-option label="集体备课" value="LESSON_PREP" /><el-option label="资源共建" value="RESOURCE" /><el-option label="听评课" value="OBSERVATION" /><el-option label="成果整理" value="OUTCOME" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="create">发布</el-button></template>
    </el-dialog>
    <AiAssistantDrawer v-model="aiVisible" :course-id="courseId" :context-title="aiContext.title" :context-meta="aiContext.meta" :question-type="aiContext.questionType" :question-id="aiContext.questionId" :context-type="aiContext.type" :context-excerpt="aiContext.excerpt" agent-task="LESSON_PLAN" />
  </div>
</template>

<style scoped>
.task-list { display: grid; gap: 12px; }
.task-item { display: flex; justify-content: space-between; gap: 16px; padding: 18px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
.task-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; }
h3 { margin: 0 0 8px; }
p { margin: 0 0 8px; color: var(--text-muted); white-space: pre-wrap; }
small { color: var(--text-muted); }
@media (max-width: 640px) { .task-item { flex-direction: column; }.task-actions { width: 100%; flex-wrap: wrap; } }
</style>
