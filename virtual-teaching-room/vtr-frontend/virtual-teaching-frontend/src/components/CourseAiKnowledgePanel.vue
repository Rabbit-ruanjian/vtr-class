<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { renderAiMarkdown } from '@/utils/aiMarkdown'

const props = defineProps({ courseId: { type: [Number, String], required: true } })
const authStore = useAuthStore()
const resources = ref([])
const loading = ref(false)
const uploading = ref(false)
const file = ref(null)
const form = ref({ title: '', chapter: '', license: 'CC BY', sourceAuthor: '', sourceUrl: '', attribution: '' })
const artifacts = ref([])
const agentLoading = ref(false)
const agentForm = ref({ task: 'LESSON_PLAN', chapter: '', message: '请根据当前课程资料生成一份可供教师审核的章节备课方案。' })
const reindexing = ref({})
const evaluationCases = ref([])
const evaluationResults = ref([])
const evaluationLoading = ref(false)
const evaluationForm = ref({
  title: '',
  question: '',
  chapter: '',
  requiredKeywords: '',
  forbiddenKeywords: '',
  expectedRoute: 'RAG',
  expectedEvidence: true,
  enabled: true
})
const canReview = computed(() => authStore.isTeacher || authStore.isAdmin)
const statusLabel = (status) => ({
  READY: '已发布',
  PENDING_REVIEW: '待审核',
  REJECTED: '已驳回'
}[status] || status || '未知')
const statusType = (status) => ({ READY: 'success', PENDING_REVIEW: 'warning', REJECTED: 'danger' }[status] || 'info')
const confidenceLabel = (confidence) => ({ HIGH: '依据充分', MEDIUM: '通用回答', LOW: '资料未充分覆盖', UNVERIFIED: '需自行核验' }[confidence] || '')

async function load() {
  loading.value = true
  try {
    resources.value = await aiApi.courseResources(Number(props.courseId)) || []
    artifacts.value = await aiApi.agentArtifacts(Number(props.courseId)) || []
    evaluationCases.value = await aiApi.evaluationCases(Number(props.courseId)) || []
  } finally { loading.value = false }
}

function choose(event) { file.value = event.target.files?.[0] || null; event.target.value = '' }

async function upload() {
  if (!file.value) return ElMessage.warning('请选择 PDF、DOCX、PPTX、TXT 或 MD 文件')
  uploading.value = true
  try {
    await aiApi.uploadCourseResource(file.value, { ...form.value, courseId: props.courseId, sourceType: 'OPEN_RESOURCE' })
    file.value = null
    form.value = { title: '', chapter: '', license: 'CC BY', sourceAuthor: '', sourceUrl: '', attribution: '' }
    await load()
    ElMessage.success('资源已入库，等待教师审核后进入 RAG')
  } catch (error) { ElMessage.error(error?.message || '资源入库失败') } finally { uploading.value = false }
}

async function generateArtifact() {
  agentLoading.value = true
  try {
    await aiApi.agent({ courseId: Number(props.courseId), chapter: agentForm.value.chapter, message: agentForm.value.message, agentTask: agentForm.value.task })
    await load()
    ElMessage.success('AI产物已生成，等待教师审核')
  } catch (error) { ElMessage.error(error?.message || 'AI任务执行失败') } finally { agentLoading.value = false }
}

async function reviewArtifact(item, action) {
  await aiApi.reviewAgentArtifact(item.id, action, action === 'APPROVE' ? '教师审核通过' : '教师要求重新整理')
  await load()
  ElMessage.success(action === 'APPROVE' ? '已审核通过' : '已驳回')
}

async function reviewResource(item, action) {
  await aiApi.reviewCourseResource(item.id, action, action === 'APPROVE' ? '教师审核通过' : '教师要求重新整理')
  await load()
  ElMessage.success(action === 'APPROVE' ? '课程资源已发布到 RAG' : '课程资源已驳回')
}

async function reindexResource(item) {
  reindexing.value = { ...reindexing.value, [item.id]: true }
  try {
    const count = await aiApi.reindexCourseResource(item.id)
    ElMessage.success(`已为 ${count || item.chunkCount || 0} 个片段重建向量索引`)
  } catch (error) {
    ElMessage.error(error?.message || '向量索引重建失败')
  } finally {
    const next = { ...reindexing.value }
    delete next[item.id]
    reindexing.value = next
  }
}

async function createEvaluationCase() {
  if (!evaluationForm.value.title.trim() || !evaluationForm.value.question.trim()) {
    return ElMessage.warning('请填写评测标题和问题')
  }
  evaluationLoading.value = true
  try {
    await aiApi.createEvaluationCase(Number(props.courseId), { ...evaluationForm.value })
    evaluationForm.value = { title: '', question: '', chapter: '', requiredKeywords: '', forbiddenKeywords: '', expectedRoute: 'RAG', expectedEvidence: true, enabled: true }
    evaluationCases.value = await aiApi.evaluationCases(Number(props.courseId)) || []
    ElMessage.success('评测样本已保存')
  } catch (error) { ElMessage.error(error?.message || '评测样本保存失败') } finally { evaluationLoading.value = false }
}

async function runEvaluations() {
  if (!evaluationCases.value.length) return ElMessage.warning('请先添加至少一个评测样本')
  evaluationLoading.value = true
  try {
    evaluationResults.value = await aiApi.runEvaluations(Number(props.courseId)) || []
    ElMessage.success('评测完成')
  } catch (error) { ElMessage.error(error?.message || '评测执行失败') } finally { evaluationLoading.value = false }
}

onMounted(load)
</script>

<template>
  <section class="knowledge-panel" v-loading="loading">
    <div class="knowledge-heading"><div><h3>AI课程知识库</h3><p>资料会先保存、解析和切片；只有教师审核发布后，才会作为 RAG 的课程依据。</p></div><el-button @click="load">刷新</el-button></div>
    <div class="knowledge-form">
    <input type="file" accept=".pdf,.docx,.pptx,.txt,.md" @change="choose" />
      <el-input v-model="form.title" placeholder="资源标题（可选）" />
      <el-input v-model="form.chapter" placeholder="章节，例如：第7章 排序" />
      <el-input v-model="form.license" placeholder="许可证，例如：CC BY" />
      <el-input v-model="form.sourceAuthor" placeholder="作者/机构" />
      <el-input v-model="form.sourceUrl" placeholder="原始来源地址（仅做版权记录）" />
      <el-input v-model="form.attribution" placeholder="署名信息" />
      <el-button type="primary" :loading="uploading" @click="upload">上传并建立 RAG 索引</el-button>
    </div>
    <el-table :data="resources" empty-text="暂未导入课程知识库资源">
      <el-table-column prop="documentName" label="资源" min-width="220" />
      <el-table-column prop="chapter" label="章节" min-width="150" />
      <el-table-column prop="license" label="许可证" width="120" />
      <el-table-column prop="chunkCount" label="知识片段" width="100" />
      <el-table-column prop="sourceAuthor" label="作者/机构" min-width="150" />
      <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
      <el-table-column v-if="canReview" label="审核" width="180">
        <template #default="{ row }">
          <div v-if="row.status === 'PENDING_REVIEW'" class="resource-actions">
            <el-button size="small" type="success" @click="reviewResource(row, 'APPROVE')">发布到 RAG</el-button>
            <el-button size="small" type="danger" plain @click="reviewResource(row, 'REJECT')">驳回</el-button>
          </div>
          <div v-else class="resource-actions">
            <span class="resource-review-remark">{{ row.reviewRemark || '—' }}</span>
            <el-button size="small" plain :loading="reindexing[row.id]" @click="reindexResource(row)">重建向量</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="agent-block">
      <div class="knowledge-heading"><div><h3>课程教研 Agent</h3><p>Agent 先检索本地课程资料，再生成可审核的教学产物。</p></div></div>
      <div class="agent-form">
        <el-select v-model="agentForm.task"><el-option label="生成备课方案" value="LESSON_PLAN" /><el-option label="生成题目草稿" value="QUESTION_GENERATION" /><el-option label="分析学情数据" value="LEARNING_ANALYSIS" /></el-select>
        <el-input v-model="agentForm.chapter" placeholder="章节，可选" />
        <el-input v-model="agentForm.message" type="textarea" :rows="3" placeholder="描述你希望 Agent 完成的任务" />
        <el-button type="primary" :loading="agentLoading" :disabled="!canReview" @click="generateArtifact">生成并进入审核</el-button>
      </div>
      <div v-if="artifacts.length" class="artifact-list">
        <article v-for="item in artifacts" :key="item.id" class="artifact-item">
          <div class="artifact-heading"><strong>{{ item.title }}</strong><el-tag size="small" :type="item.status === 'APPROVED' ? 'success' : item.status === 'REJECTED' ? 'danger' : 'warning'">{{ item.status }}</el-tag></div>
          <div class="artifact-content" v-html="renderAiMarkdown(item.content)" />
          <div class="artifact-meta"><span v-if="item.answerMode">{{ item.answerMode === 'TEACHING_AGENT' ? '教研 Agent' : item.answerMode }}</span><span v-if="confidenceLabel(item.confidence)">{{ confidenceLabel(item.confidence) }}</span><span v-if="item.latencyMs !== undefined">{{ item.latencyMs }} ms</span></div>
          <div v-if="item.sources?.length" class="artifact-sources">依据：{{ item.sources.map((source) => source.documentName || `片段${source.chunkIndex}`).join('、') }}</div>
          <div v-if="canReview && item.status === 'PENDING_REVIEW'" class="artifact-actions"><el-button size="small" type="success" @click="reviewArtifact(item, 'APPROVE')">审核通过</el-button><el-button size="small" type="danger" plain @click="reviewArtifact(item, 'REJECT')">驳回</el-button></div>
        </article>
      </div>
    </div>
    <div class="evaluation-block">
      <div class="knowledge-heading"><div><h3>AI 质量评测</h3><p>用真实课程问题检查路由、课程依据和关键答案要点，避免只凭感觉判断 AI 是否准确。</p></div><el-button :loading="evaluationLoading" :disabled="!evaluationCases.length" @click="runEvaluations">运行评测</el-button></div>
      <div class="evaluation-form">
        <el-input v-model="evaluationForm.title" placeholder="样本名称，例如：页面置换基本概念" />
        <el-input v-model="evaluationForm.chapter" placeholder="章节，可选" />
        <el-select v-model="evaluationForm.expectedRoute"><el-option label="课程 RAG" value="RAG" /><el-option label="通用问答" value="DIRECT" /><el-option label="联网核验" value="WEB_SEARCH" /></el-select>
        <el-input v-model="evaluationForm.question" type="textarea" :rows="2" placeholder="标准测试问题" />
        <el-input v-model="evaluationForm.requiredKeywords" placeholder="必须包含的答案要点，用逗号分隔" />
        <el-input v-model="evaluationForm.forbiddenKeywords" placeholder="禁止出现的错误词，用逗号分隔（可选）" />
        <el-button type="primary" :loading="evaluationLoading" @click="createEvaluationCase">保存测试样本</el-button>
      </div>
      <div v-if="evaluationCases.length" class="evaluation-list">
        <div class="evaluation-summary">当前 {{ evaluationCases.length }} 个样本<span v-if="evaluationResults.length"> · 本次通过 {{ evaluationResults.filter((item) => item.passed).length }} 个</span></div>
        <article v-for="item in (evaluationResults.length ? evaluationResults : evaluationCases)" :key="item.caseId || item.id" class="evaluation-item">
          <div><strong>{{ item.title }}</strong><el-tag v-if="item.passed !== undefined" size="small" :type="item.passed ? 'success' : 'danger'">{{ item.passed ? '通过' : '未通过' }}</el-tag></div>
          <p>{{ item.question }}</p>
          <small v-if="item.passed !== undefined">路由：{{ item.actualRoute || '—' }} · 证据：{{ item.evidencePassed ? '通过' : '未通过' }} · {{ item.latencyMs ?? 0 }} ms</small>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.knowledge-panel { padding: 20px; border: 1px solid var(--el-border-color); border-radius: 6px; background: var(--el-bg-color); }
.resource-actions { display:flex; gap:6px; flex-wrap:wrap; }.resource-review-remark { color:var(--text-muted); font-size:12px; }
.knowledge-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; margin-bottom:16px; }
.knowledge-heading h3 { margin:0 0 6px; }.knowledge-heading p { margin:0; color:var(--text-muted); font-size:13px; }
.knowledge-form { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; margin-bottom:18px; }
.knowledge-form input[type=file] { min-width:0; padding:7px; border:1px solid var(--el-border-color); border-radius:4px; }
.knowledge-form .el-button { width:max-content; }
.agent-block { margin-top:22px; padding-top:18px; border-top:1px solid var(--el-border-color); }
.evaluation-block { margin-top:22px; padding-top:18px; border-top:1px solid var(--el-border-color); }
.evaluation-form { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; margin-bottom:14px; }.evaluation-form .el-textarea, .evaluation-form .el-button { grid-column:1 / -1; width:max-content; }.evaluation-list { display:grid; gap:8px; }.evaluation-summary { color:var(--text-muted); font-size:13px; }.evaluation-item { padding:10px 12px; border:1px solid var(--el-border-color); border-radius:6px; background:#fbfcff; }.evaluation-item > div { display:flex; align-items:center; gap:8px; justify-content:space-between; }.evaluation-item p { margin:6px 0; }.evaluation-item small { color:var(--text-muted); }
.agent-form { display:grid; grid-template-columns:180px 1fr; gap:10px; margin-bottom:14px; }.agent-form :deep(.el-textarea), .agent-form .el-button { grid-column:1 / -1; width:max-content; }
.artifact-list { display:grid; gap:10px; }.artifact-item { padding:12px; border:1px solid var(--el-border-color); border-radius:6px; background:#fbfcff; }.artifact-heading { display:flex; justify-content:space-between; gap:10px; }.artifact-content { margin:8px 0; color:var(--text-main); line-height:1.6; max-height:260px; overflow:auto; }.artifact-content :deep(p), .artifact-content :deep(h1), .artifact-content :deep(h2), .artifact-content :deep(h3) { margin:0 0 7px; }.artifact-content :deep(ul), .artifact-content :deep(ol) { margin:4px 0 8px; padding-left:20px; }.artifact-content :deep(pre) { padding:8px; overflow:auto; border-radius:5px; background:#202943; color:#f4f7ff; }.artifact-content :deep(code) { font-family:Consolas,monospace; }.artifact-meta { display:flex; flex-wrap:wrap; gap:5px; margin-bottom:7px; color:var(--text-muted); font-size:12px; }.artifact-meta span { padding:2px 6px; border-radius:999px; background:#eef1f8; }.artifact-sources { color:var(--text-muted); font-size:12px; }.artifact-actions { margin-top:10px; }
@media (max-width:800px) { .knowledge-form, .evaluation-form { grid-template-columns:1fr; } }
</style>
