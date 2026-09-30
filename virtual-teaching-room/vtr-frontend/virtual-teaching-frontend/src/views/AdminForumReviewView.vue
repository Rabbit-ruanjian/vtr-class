<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { forumApi } from '@/api'
import { forumAuditOptions } from '@/config/options'
import { formatDateTime, optionLabel } from '@/utils/format'
import { normalizePage } from '@/utils/page'

const loading = ref(false)
const router = useRouter()
const reviewType = ref('posts')
const posts = ref([])
const comments = ref([])
const postsTotal = ref(0)
const commentsTotal = ref(0)
const query = reactive({ page: 1, size: 10 })
const auditStatus = ref('PENDING')
const auditVisible = ref(false)
const auditForm = reactive({ id: null, type: 'post', status: 'APPROVED', remark: '' })
const statusLabels = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }

const postTypeLabels = {
  QUESTION: '课程问答',
  NOTE: '学习笔记',
  RESOURCE: '资源分享',
  TEACHING: '课堂复盘',
  CASE: '教学案例',
  RESULT: '学习成果'
}

function postTypeLabel(type) {
  return postTypeLabels[type] || '交流讨论'
}

async function load() {
  loading.value = true
  try {
    const loader = reviewType.value === 'posts'
      ? (auditStatus.value === 'PENDING' ? forumApi.pendingPosts(query) : auditStatus.value === 'APPROVED' ? forumApi.approvedPosts(query) : forumApi.rejectedPosts(query))
      : (auditStatus.value === 'PENDING' ? forumApi.pendingComments(query) : auditStatus.value === 'APPROVED' ? forumApi.approvedComments(query) : forumApi.rejectedComments(query))
    const pageData = normalizePage(await loader)
    if (reviewType.value === 'posts') {
      posts.value = pageData.list
      postsTotal.value = pageData.total
    } else {
      comments.value = pageData.list
      commentsTotal.value = pageData.total
    }
  } finally {
    loading.value = false
  }
}

function resetPage() {
  query.page = 1
  load()
}

function switchQueue(nextStatus) {
  auditStatus.value = nextStatus
  query.page = 1
  load()
}

async function removePost(id) {
  try { await ElMessageBox.confirm('删除后帖子及其评论将一并移除，确认继续吗？', '删除帖子', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }) } catch { return }
  await forumApi.removePost(id)
  ElMessage.success('帖子已删除')
  await load()
}

async function removeComment(id) {
  try { await ElMessageBox.confirm('删除后这条评论将无法恢复，确认继续吗？', '删除评论', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }) } catch { return }
  await forumApi.removeComment(id)
  ElMessage.success('评论已删除')
  await load()
}

function openPost(id) { router.push({ name: 'forum-post-detail', params: { id } }) }

function openAudit(row, type) {
  auditForm.id = row.id
  auditForm.type = type
  auditForm.status = 'APPROVED'
  auditForm.remark = ''
  auditVisible.value = true
}

async function saveAudit() {
  if (auditForm.status === 'REJECTED' && !auditForm.remark.trim()) {
    ElMessage.warning('驳回时请填写原因')
    return
  }
  try {
    const payload = { status: auditForm.status, remark: auditForm.remark.trim() }
    if (auditForm.type === 'post') await forumApi.auditPost(auditForm.id, payload)
    else await forumApi.auditComment(auditForm.id, payload)
    ElMessage.success(auditForm.status === 'APPROVED' ? '内容已通过审核' : '内容已驳回')
    auditVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error?.message || '审核失败')
  }
}

watch(reviewType, resetPage)
onMounted(load)
</script>

<template>
  <div class="forum-review-view">
    <div class="toolbar forum-review-toolbar">
      <el-radio-group v-model="reviewType" size="small">
        <el-radio-button label="posts">帖子</el-radio-button>
        <el-radio-button label="comments">评论</el-radio-button>
      </el-radio-group>
      <span class="review-hint">新内容先进入这里，审核通过后才会在教研社区公开展示。</span>
      <el-button type="primary" @click="load" :loading="loading">刷新内容</el-button>
    </div>
    <div class="queue-tabs">
      <el-button v-for="(label, key) in statusLabels" :key="key" :type="auditStatus === key ? 'primary' : 'default'" @click="switchQueue(key)">{{ label }}</el-button>
    </div>

    <el-table v-if="reviewType === 'posts'" :data="posts" v-loading="loading" empty-text="暂无帖子">
      <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
      <el-table-column prop="authorName" label="作者" width="140" />
      <el-table-column label="类型" width="130"><template #default="{ row }">{{ postTypeLabel(row.postType) }}</template></el-table-column>
      <el-table-column label="提交时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ optionLabel(forumAuditOptions, row.auditStatus) }}</template></el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openPost(row.id)">查看</el-button>
          <el-button v-if="auditStatus === 'PENDING'" link type="success" @click="openAudit(row, 'post')">审核</el-button>
          <el-button link type="danger" @click="removePost(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table v-else :data="comments" v-loading="loading" empty-text="暂无评论">
      <el-table-column prop="content" label="评论内容" min-width="420" show-overflow-tooltip />
      <el-table-column prop="authorName" label="作者" width="140" />
      <el-table-column label="提交时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createTime) }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ optionLabel(forumAuditOptions, row.auditStatus) }}</template></el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openPost(row.postId)">查看帖子</el-button>
          <el-button v-if="auditStatus === 'PENDING'" link type="success" @click="openAudit(row, 'comment')">审核</el-button>
          <el-button link type="danger" @click="removeComment(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="forum-review-pagination">
      <el-pagination
        v-model:current-page="query.page"
        layout="total, prev, pager, next"
        :page-size="query.size"
        :total="reviewType === 'posts' ? postsTotal : commentsTotal"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="auditVisible" title="审核社区内容" width="520px">
      <el-form label-position="top">
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.status">
            <el-radio label="APPROVED">通过</el-radio>
            <el-radio label="REJECTED">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditForm.remark" type="textarea" :rows="4" maxlength="500" show-word-limit :placeholder="auditForm.status === 'REJECTED' ? '请填写驳回原因' : '可填写审核备注'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAudit">提交审核</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
.forum-review-toolbar { align-items: center; }
.queue-tabs { display: flex; gap: 8px; margin-bottom: 14px; }
.review-hint { color: var(--text-muted); font-size: 13px; }
.forum-review-pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
.form-hint { margin-left: 6px; color: var(--text-muted); }
</style>
