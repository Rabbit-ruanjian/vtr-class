<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { assignmentApi, coursewareApi, examApi, notificationApi, submissionApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { normalizePage } from '@/utils/page'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const unreadCount = ref(0)
const selectedIds = ref([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const activeNotification = ref(null)
const relatedAssignment = ref(null)
const relatedResource = ref(null)
let notificationPollTimer = null

const query = reactive({ page: 1, size: 20, category: 'all', unreadOnly: false })

const categoryUnreadCounts = computed(() => {
  const counts = { all: 0, assignment: 0, exam: 0, resource: 0, forum: 0, activity: 0, system: 0 }
  list.value.forEach((row) => {
    if (row.isRead) return
    const key = categoryKey(row.type)
    counts[key] += 1
    counts.all += 1
  })
  return counts
})

const categoryOptions = [
  { label: '全部通知', value: 'all' },
  { label: '作业通知', value: 'assignment' },
  { label: '考试通知', value: 'exam' },
  { label: '资源通知', value: 'resource' },
  { label: '论坛通知', value: 'forum' },
  { label: '教研活动', value: 'activity' },
  { label: '系统通知', value: 'system' }
]

const assignmentNotificationTypes = new Set([
  'ASSIGNMENT_PUBLISHED',
  'ASSIGNMENT_DEADLINE_REMINDER',
  'ASSIGNMENT_SUBMITTED',
  'SUBMISSION_GRADED'
])
const examNotificationTypes = new Set(['EXAM_PUBLISHED'])

const resourceNotificationTypes = new Set([
  'RESOURCE_PENDING',
  'RESOURCE_APPROVED',
  'RESOURCE_REJECTED',
  'RESOURCE_PUBLISHED',
  'OUTLINE_ARCHIVED'
])
const forumNotificationTypes = new Set([
  'FORUM_POST_PENDING',
  'FORUM_COMMENT_PENDING',
  'FORUM_REPLY',
  'FORUM_POST_APPROVED',
  'FORUM_POST_REJECTED',
  'FORUM_COMMENT_APPROVED',
  'FORUM_COMMENT_REJECTED'
])
const activityNotificationTypes = new Set([
  'ACTIVITY_PENDING',
  'ACTIVITY_APPROVED',
  'ACTIVITY_REJECTED',
  'ACTIVITY_CANCELLED'
])
const moderationNotificationTypes = new Set(['MODERATION_RISK', 'MODERATION_REPORTED', 'MODERATION_APPEAL'])

function categoryLabel(type) {
  const labels = { assignment: '作业', exam: '考试', resource: '资源', forum: '论坛', activity: '活动', system: '系统' }
  return labels[categoryKey(type)]
}

function categoryKey(type) {
  if (assignmentNotificationTypes.has(type)) return 'assignment'
  if (examNotificationTypes.has(type)) return 'exam'
  if (resourceNotificationTypes.has(type)) return 'resource'
  if (forumNotificationTypes.has(type)) return 'forum'
  if (activityNotificationTypes.has(type)) return 'activity'
  return 'system'
}

function notificationDate(value) {
  const date = dayjs(value)
  if (!date.isValid()) return '--'
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `${date.format('MM-DD')}（${weekdays[date.day()]}） ${date.format('HH:mm')}`
}

function isSelected(id) {
  return selectedIds.value.includes(id)
}

const allCurrentPageSelected = computed(() => list.value.length > 0
  && list.value.every((row) => isSelected(row.id)))
const someCurrentPageSelected = computed(() => list.value.some((row) => isSelected(row.id)))

function toggleAllCurrentPage(value) {
  const currentIds = list.value.map((row) => row.id)
  selectedIds.value = value
    ? [...new Set([...selectedIds.value, ...currentIds])]
    : selectedIds.value.filter((id) => !currentIds.includes(id))
}

function toggleSelected(row) {
  selectedIds.value = isSelected(row.id)
    ? selectedIds.value.filter((id) => id !== row.id)
    : [...selectedIds.value, row.id]
}

async function loadUnreadCount() {
  try {
    unreadCount.value = await notificationApi.unreadCount()
  } catch (error) {
    unreadCount.value = 0
  }
}

async function loadNotifications(silent = false) {
  loading.value = true
  try {
    const pageData = normalizePage(await notificationApi.list(query, { silent }))
    list.value = pageData.list
    total.value = pageData.total
    selectedIds.value = selectedIds.value.filter((id) => list.value.some((row) => row.id === id))
    if (!list.value.length && total.value > 0 && query.page > 1) {
      query.page -= 1
      await loadNotifications(silent)
    }
  } finally {
    loading.value = false
  }
}

async function refreshAll(silent = false) {
  await Promise.all([loadNotifications(silent), loadUnreadCount()])
}

function applyFilters() {
  query.page = 1
  selectedIds.value = []
  loadNotifications()
}

async function markRead(id, showMessage = false) {
  await notificationApi.markRead(id)
  window.dispatchEvent(new Event('notifications-updated'))
  if (showMessage) ElMessage.success('已标记为已读')
}

async function markAllRead() {
  await notificationApi.markAllRead()
  window.dispatchEvent(new Event('notifications-updated'))
  selectedIds.value = []
  ElMessage.success('全部消息已标记为已读')
  await Promise.all([loadNotifications(), loadUnreadCount()])
}

async function openNotification(row) {
  if (!row.isRead) {
    await markRead(row.id)
    row.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }

  activeNotification.value = row
  relatedAssignment.value = null
  relatedResource.value = null
  detailLoading.value = false
  detailVisible.value = true

  if (assignmentNotificationTypes.has(row.type) && row.relatedId) {
    detailLoading.value = true
    try {
      if (row.type === 'SUBMISSION_GRADED') {
        const submission = await submissionApi.detail(row.relatedId, { silent: true })
        relatedAssignment.value = await assignmentApi.detail(submission.assignmentId, { silent: true })
      } else {
        relatedAssignment.value = await assignmentApi.detail(row.relatedId, { silent: true })
      }
    } catch (error) {
      relatedAssignment.value = null
    } finally {
      detailLoading.value = false
    }
  }

  if (examNotificationTypes.has(row.type) && row.relatedId) {
    detailLoading.value = true
    try { relatedAssignment.value = await examApi.detail(row.relatedId, { silent: true }) } catch (error) { relatedAssignment.value = null } finally { detailLoading.value = false }
  }

  if (resourceNotificationTypes.has(row.type) && row.relatedId && row.type !== 'RESOURCE_PENDING') {
    detailLoading.value = true
    try {
      relatedResource.value = await coursewareApi.detail(row.relatedId, { silent: true })
    } catch (error) {
      relatedResource.value = null
    } finally {
      detailLoading.value = false
    }
  }
}

async function openRelatedAssignment() {
  if (!relatedAssignment.value || (authStore.isStudent && relatedAssignment.value.canView === false)) return
  if (authStore.isStudent) {
    await router.push({
      name: 'assignment-answer',
      params: {
        courseId: relatedAssignment.value.courseId,
        assignmentId: relatedAssignment.value.id
      }
    })
  } else {
    await router.push({
      name: 'assignments',
      query: {
        courseId: relatedAssignment.value.courseId,
        assignmentId: relatedAssignment.value.id
      }
    })
  }
  detailVisible.value = false
}

async function openRelatedContent() {
  const row = activeNotification.value
  if (!row) return

  if (assignmentNotificationTypes.has(row.type)) {
    await openRelatedAssignment()
    return
  }

  if (examNotificationTypes.has(row.type) && relatedAssignment.value) {
    await router.push({ name: 'exam-detail', params: { courseId: relatedAssignment.value.courseId, examId: relatedAssignment.value.id }, query: { courseId: relatedAssignment.value.courseId } })
    detailVisible.value = false
    return
  }

  if (row.type === 'RESOURCE_PENDING') {
    await router.push({ name: 'admin', query: { tab: 'resource-review' } })
  } else if (moderationNotificationTypes.has(row.type) && authStore.isAdmin) {
    await router.push({ name: 'admin', query: { tab: 'risk' } })
  } else if (resourceNotificationTypes.has(row.type) && relatedResource.value) {
    const resourceType = relatedResource.value.resourceType || 'teaching-courseware'
    await router.push({
      path: `/courseware/${resourceType}`,
      query: {
        courseId: relatedResource.value.courseId,
        chapter: relatedResource.value.chapter,
        sectionId: relatedResource.value.sectionId,
        resourceId: relatedResource.value.id
      }
    })
  } else if (forumNotificationTypes.has(row.type)) {
    if (['FORUM_POST_PENDING', 'FORUM_COMMENT_PENDING'].includes(row.type) && authStore.isAdmin) {
      await router.push({ name: 'admin', query: { tab: 'risk' } })
    } else {
      const postId = row.type === 'FORUM_COMMENT_PENDING' ? undefined : row.relatedId
      await router.push({ name: 'forum', query: postId ? { postId } : undefined })
    }
  } else if (activityNotificationTypes.has(row.type)) {
    if (row.type === 'ACTIVITY_PENDING' && authStore.isAdmin) {
      await router.push({ name: 'admin-activity-review' })
    } else if (row.relatedId) {
      await router.push({ name: 'activity-detail', params: { id: row.relatedId } })
    } else {
      await router.push({ name: 'activities' })
    }
  } else if (row.type.startsWith('COURSE_MEMBER_') && row.relatedId) {
    if (authStore.isStudent) {
      await router.push({ name: 'courseware', query: { courseId: row.relatedId } })
    } else {
      await router.push({ name: 'course-workspace', params: { id: row.relatedId } })
    }
  } else {
    return
  }
  detailVisible.value = false
}

function canOpenRelated(row) {
  if (!row) return false
  if (assignmentNotificationTypes.has(row.type)) return Boolean(relatedAssignment.value)
  if (examNotificationTypes.has(row.type)) return Boolean(relatedAssignment.value)
  if (row.type === 'RESOURCE_PENDING') return authStore.isAdmin
  if (moderationNotificationTypes.has(row.type)) return authStore.isAdmin
  if (resourceNotificationTypes.has(row.type)) return Boolean(relatedResource.value)
  return forumNotificationTypes.has(row.type) || activityNotificationTypes.has(row.type)
    || row.type.startsWith('COURSE_MEMBER_')
}

async function markSelectedRead() {
  if (!selectedIds.value.length) return
  await Promise.all(selectedIds.value.map((id) => notificationApi.markRead(id)))
  window.dispatchEvent(new Event('notifications-updated'))
  selectedIds.value = []
  ElMessage.success('已标记所选消息')
  await Promise.all([loadNotifications(), loadUnreadCount()])
}

async function deleteSelected() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${selectedIds.value.length} 条通知吗？删除后无法恢复。`,
      '批量删除通知',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    await notificationApi.removeBatch(selectedIds.value)
    selectedIds.value = []
    ElMessage.success('所选通知已删除')
    await Promise.all([loadNotifications(), loadUnreadCount()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除通知失败')
  }
}

async function deleteOne(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除通知“${row.title}”吗？删除后无法恢复。`,
      '删除通知',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
    await notificationApi.remove(row.id)
    selectedIds.value = selectedIds.value.filter((id) => id !== row.id)
    ElMessage.success('通知已删除')
    await Promise.all([loadNotifications(), loadUnreadCount()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除通知失败')
  }
}

onMounted(() => {
  refreshAll()
  notificationPollTimer = window.setInterval(() => refreshAll(true), 30000)
})

onUnmounted(() => {
  if (notificationPollTimer) window.clearInterval(notificationPollTimer)
})
</script>

<template>
  <div class="page-stack notifications-page notifications-paper">
    <aside class="notification-categories">
      <div class="notification-categories-heading"><span>消息分类</span><small>MESSAGE INBOX</small></div>
      <nav class="notification-category-nav" aria-label="消息分类">
        <button
          v-for="item in categoryOptions"
          :key="item.value"
          type="button"
          :class="{ active: query.category === item.value }"
          @click="query.category = item.value; applyFilters()"
        >
          <span>{{ item.label }}</span>
          <b v-if="categoryUnreadCounts[item.value]">{{ categoryUnreadCounts[item.value] }}</b>
        </button>
      </nav>
      <div class="notification-ai-note">
        <strong>诸葛 AI 提醒</strong>
        <p v-if="unreadCount">当前有 {{ unreadCount }} 条未读消息，建议优先处理需要审核和课程安排类通知。</p>
        <p v-else>目前没有待处理消息，新的教学安排会在这里提醒你。</p>
      </div>
    </aside>

    <main class="notification-inbox" v-loading="loading">
      <header class="notification-inbox-heading">
        <div><span class="notification-eyebrow">CLOUD ACADEMY</span><h1>消息通知</h1><p>共 {{ total }} 条通知 · {{ unreadCount }} 条待处理</p></div>
        <span class="notification-assistant-pill">云塾先生</span>
      </header>
      <div class="notification-toolbar">
        <el-select v-model="query.category" class="notification-category-filter" @change="applyFilters">
          <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-checkbox v-model="query.unreadOnly" @change="applyFilters">只看未读</el-checkbox>
        <span class="notification-toolbar-spacer" />
        <el-button class="notification-refresh" @click="refreshAll()" :loading="loading">刷新</el-button>
        <el-button class="notification-read-all" type="primary" @click="markAllRead">全部已读</el-button>
        <el-button v-if="selectedIds.length" class="notification-selected-read" plain @click="markSelectedRead">所选已读</el-button>
        <el-button v-if="selectedIds.length" class="notification-selected-delete" type="danger" plain @click="deleteSelected">删除所选</el-button>
      </div>

      <div v-if="list.length" class="notification-list">
        <div class="notification-list-toolbar">
          <el-checkbox :model-value="allCurrentPageSelected" :indeterminate="someCurrentPageSelected && !allCurrentPageSelected" @change="toggleAllCurrentPage">全选当前页</el-checkbox>
          <span v-if="selectedIds.length">已选择 {{ selectedIds.length }} 条</span>
        </div>
        <article v-for="row in list" :key="row.id" class="notification-row" :class="{ 'is-unread': !row.isRead }" @click="openNotification(row)">
          <el-checkbox class="notification-checkbox" :model-value="isSelected(row.id)" :aria-label="`选择${row.title}`" @click.stop @change="toggleSelected(row)" />
          <div class="notification-icon" :class="`is-${categoryKey(row.type)}`" aria-hidden="true"><span>{{ categoryLabel(row.type).slice(0, 1) }}</span></div>
          <div class="notification-copy"><div class="notification-title-line"><h3>{{ row.title }}</h3><span v-if="!row.isRead" class="notification-unread-mark">待处理</span></div><p class="notification-sender">发件人：{{ categoryLabel(row.type) }}通知</p><p class="notification-content">{{ row.content }}</p></div>
          <time class="notification-time">{{ notificationDate(row.createdAt) }}</time>
          <button class="notification-delete" type="button" @click.stop="deleteOne(row)">删除</button>
        </article>
      </div>
      <el-empty v-else description="暂无消息" />
      <div v-if="total > query.size" class="notification-pagination"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" layout="total, prev, pager, next" :total="total" @current-change="loadNotifications" @size-change="query.page = 1; loadNotifications()" /></div>
    </main>

    <el-dialog
      v-model="detailVisible"
      width="min(720px, calc(100vw - 32px))"
      class="notification-detail-dialog"
      destroy-on-close
    >
      <template #header>
        <h2 class="notification-detail-title">{{ activeNotification?.title }}</h2>
      </template>

      <div v-if="activeNotification" class="notification-detail">
        <div class="notification-detail-meta">
          <span>{{ categoryLabel(activeNotification.type) }}通知</span>
          <time>{{ notificationDate(activeNotification.createdAt) }}</time>
        </div>
        <p class="notification-detail-content">{{ activeNotification.content }}</p>

        <div v-if="assignmentNotificationTypes.has(activeNotification.type)" class="related-assignment-area">
          <div v-if="detailLoading" class="related-assignment-loading">正在加载作业信息…</div>
          <button
            v-else-if="relatedAssignment && (!authStore.isStudent || relatedAssignment.canView !== false)"
            type="button"
            class="related-assignment"
            @click="openRelatedAssignment"
          >
            <span class="related-assignment-mark">作业</span>
            <span class="related-assignment-title">{{ relatedAssignment.title }}</span>
            <span class="related-assignment-arrow">{{ authStore.isStudent ? '进入作业' : '查看提交' }}&nbsp;›</span>
          </button>
          <div v-else-if="relatedAssignment && authStore.isStudent && relatedAssignment.accessMessage" class="related-assignment-missing">
            {{ relatedAssignment.accessMessage }}
          </div>
          <div v-else class="related-assignment-missing">该作业暂时无法打开</div>
        </div>

        <div v-if="resourceNotificationTypes.has(activeNotification.type)" class="related-assignment-area">
          <div v-if="detailLoading" class="related-assignment-loading">正在加载资源信息…</div>
          <button
            v-else-if="activeNotification.type === 'RESOURCE_PENDING' || relatedResource"
            type="button"
            class="related-assignment"
            @click="openRelatedContent"
          >
            <span class="related-assignment-mark">资源</span>
            <span class="related-assignment-title">{{ relatedResource?.title || '待审核教学资源' }}</span>
            <span class="related-assignment-arrow">打开相关内容&nbsp;›</span>
          </button>
          <div v-else class="related-assignment-missing">该资源暂时无法打开</div>
        </div>

        <div v-if="!assignmentNotificationTypes.has(activeNotification.type)
          && !resourceNotificationTypes.has(activeNotification.type)
          && canOpenRelated(activeNotification)" class="related-assignment-area">
          <button type="button" class="related-assignment" @click="openRelatedContent">
            <span class="related-assignment-mark">{{ categoryLabel(activeNotification.type) }}</span>
            <span class="related-assignment-title">查看相关{{ categoryLabel(activeNotification.type) }}内容</span>
            <span class="related-assignment-arrow">打开相关内容&nbsp;›</span>
          </button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.notification-inbox-card :deep(.el-card__body) {
  padding: 0;
}

.notification-filter-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 62px;
  padding: 0 26px;
  border-bottom: 1px solid #edf0f5;
  background: #fff;
}

.notification-category-filter {
  width: 150px;
}

.notification-list {
  min-height: 180px;
}

.notification-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  padding: 0 26px;
  border-bottom: 1px solid #edf0f5;
  color: #98a0af;
  font-size: 13px;
}

.notification-row {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 100px;
  padding: 18px 26px;
  border-bottom: 1px solid #edf0f5;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.notification-row:hover,
.notification-row.is-unread {
  background: #f8fbff;
}

.notification-row:hover {
  background: #f1f7ff;
}

.notification-checkbox {
  flex: 0 0 auto;
}

.notification-icon {
  display: flex;
  flex: 0 0 56px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  width: 56px;
  height: 56px;
  border-radius: 4px;
  color: #fff;
  background: #09a8ec;
  font-size: 13px;
}

.notification-icon.is-assignment { background: #409eff; }
.notification-icon.is-exam { background: #6c7eea; }
.notification-icon.is-resource { background: #8b6bd8; }
.notification-icon.is-forum { background: #e6a23c; }
.notification-icon.is-activity { background: #36a269; }
.notification-icon.is-system { background: #909399; }

.notification-copy {
  min-width: 0;
  flex: 1;
}

.notification-title-line {
  display: flex;
  align-items: center;
  gap: 9px;
}

.notification-title-line h3 {
  overflow: hidden;
  margin: 0;
  color: #303744;
  font-size: 17px;
  font-weight: 500;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-row.is-unread .notification-title-line h3 {
  color: #1f2d3d;
  font-weight: 650;
}

.notification-sender,
.notification-content {
  overflow: hidden;
  margin: 3px 0 0;
  color: #9aa2b1;
  font-size: 13px;
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-content {
  color: #667085;
}

.notification-time {
  flex: 0 0 158px;
  align-self: flex-start;
  padding-top: 3px;
  color: #98a0af;
  font-size: 13px;
  text-align: right;
  white-space: nowrap;
}

.notification-delete {
  flex: 0 0 auto;
}

.notification-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 18px 26px;
}

.notification-detail-title {
  margin: 0;
  color: #26364f;
  font-size: 22px;
  font-weight: 500;
}

.notification-detail {
  padding: 4px 18px 12px;
}

.notification-detail-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
  color: #758196;
  font-size: 14px;
}

.notification-detail-meta time {
  color: #9aa3b3;
}

.notification-detail-content {
  margin: 0;
  color: #3d4654;
  font-size: 16px;
  line-height: 1.9;
  white-space: pre-wrap;
}

.related-assignment-area {
  margin-top: 32px;
}

.related-assignment,
.related-assignment-loading,
.related-assignment-missing {
  width: 100%;
  min-height: 80px;
  border-radius: 5px;
}

.related-assignment {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 20px;
  border: 0;
  color: #354052;
  background: #f4f6f8;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.2s ease;
}

.related-assignment:hover {
  background: #eaf3ff;
}

.related-assignment-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 56px;
  height: 56px;
  border-radius: 5px;
  color: #fff;
  background: #36a9ee;
  font-size: 15px;
}

.related-assignment-title {
  overflow: hidden;
  flex: 1;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.related-assignment-arrow {
  flex: 0 0 auto;
  color: #4a8fe8;
  font-size: 13px;
}

.related-assignment-loading,
.related-assignment-missing {
  display: flex;
  align-items: center;
  padding: 0 20px;
  color: #98a0af;
  background: #f4f6f8;
  font-size: 14px;
}

:deep(.notification-detail-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 24px 28px 18px;
  border-bottom: 1px solid #edf0f5;
}

:deep(.notification-detail-dialog .el-dialog__body) {
  padding: 24px 28px 28px;
}

@media (max-width: 720px) {
  .notification-row {
    align-items: flex-start;
    gap: 10px;
    padding: 15px 14px;
  }

  .notification-list-toolbar {
    padding: 0 14px;
  }

  .notification-filter-bar {
    flex-wrap: wrap;
    gap: 10px;
    min-height: 58px;
    padding: 10px 14px;
  }

  .notification-icon {
    flex-basis: 48px;
    width: 48px;
    height: 48px;
    font-size: 12px;
  }

  .notification-time {
    flex-basis: auto;
    font-size: 12px;
  }

  .notification-content {
    display: none;
  }

  .notification-detail {
    padding: 0;
  }

  :deep(.notification-detail-dialog .el-dialog__header),
  :deep(.notification-detail-dialog .el-dialog__body) {
    padding-right: 18px;
    padding-left: 18px;
  }

  .notification-detail-title {
    font-size: 19px;
  }

  .related-assignment {
    gap: 11px;
    padding: 10px 12px;
  }

  .related-assignment-mark {
    flex-basis: 48px;
    height: 48px;
  }

  .related-assignment-arrow {
    font-size: 12px;
  }
}

/* 仿书房通知中心：左侧分类、右侧消息，不改变通知接口和交互。 */
.notifications-paper { display:grid; grid-template-columns:minmax(235px, 34%) minmax(0, 1fr); gap:0; min-height:calc(100vh - 145px); margin:0; overflow:hidden; background:linear-gradient(135deg,#f3ead5,#e7d7b9); color:#514337; }
.notification-categories { min-width:0; padding:42px clamp(24px,4vw,54px); border-right:1px solid rgba(156,126,78,.2); background:linear-gradient(180deg,rgba(248,240,222,.9),rgba(235,221,191,.78)); }
.notification-categories-heading { display:flex; align-items:baseline; justify-content:space-between; gap:14px; padding-bottom:18px; border-bottom:1px solid #d9c5a0; color:#3f3026; font-family:"Songti SC",serif; font-size:26px; }
.notification-categories-heading small { color:#aa9677; font-family:inherit; font-size:10px; letter-spacing:1px; }
.notification-category-nav { display:grid; gap:4px; margin-top:22px; }
.notification-category-nav button { display:flex; align-items:center; justify-content:space-between; min-height:58px; padding:0 18px; border:0; border-left:4px solid transparent; border-radius:0 12px 12px 0; background:transparent; color:#5d4b3a; cursor:pointer; font:inherit; font-size:18px; text-align:left; }
.notification-category-nav button:hover { background:rgba(255,255,255,.28); }
.notification-category-nav button.active { border-left-color:#9b3a3f; background:rgba(157,119,73,.16); color:#432f27; }
.notification-category-nav button b { display:inline-flex; min-width:32px; height:32px; align-items:center; justify-content:center; border-radius:50%; background:#963b40; color:#fff8e9; font-family:Georgia,serif; font-size:15px; font-weight:500; }
.notification-ai-note { margin-top:38px; padding:20px 22px; border:1px solid #d7c098; border-radius:15px; background:rgba(255,250,237,.42); }
.notification-ai-note strong { color:#a54042; font-family:"Songti SC",serif; font-size:17px; font-weight:500; }
.notification-ai-note p { margin:13px 0 0; color:#7d6c57; font-size:14px; line-height:1.8; }
.notification-inbox { min-width:0; padding:42px clamp(28px,4vw,58px) 36px; background:linear-gradient(90deg,rgba(243,232,208,.72),rgba(250,243,226,.42)); }
.notification-inbox-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:22px; padding-bottom:20px; border-bottom:1px solid #d6bf96; }
.notification-eyebrow { color:#aa936e; font-size:10px; letter-spacing:2px; }
.notification-inbox-heading h1 { margin:7px 0 5px; color:#3f3026; font-family:"Songti SC",serif; font-size:30px; font-weight:600; }
.notification-inbox-heading p { margin:0; color:#a38c6c; font-family:"Songti SC",serif; font-size:14px; }
.notification-assistant-pill { padding:10px 23px; border-radius:999px; background:#2e6547; color:#f8f0df; font-family:"Songti SC",serif; font-size:16px; box-shadow:0 5px 12px rgba(46,101,71,.16); }
.notification-toolbar { display:flex; align-items:center; flex-wrap:wrap; gap:13px; padding:23px 0 24px; }
.notification-toolbar-spacer { flex:1; }
.notifications-paper .notification-category-filter { width:146px; }
.notifications-paper .notification-category-filter :deep(.el-select__wrapper) { min-height:43px; border:1px solid #d7c29b; border-radius:10px; background:rgba(255,250,239,.55); box-shadow:none; }
.notifications-paper .notification-toolbar .el-checkbox { color:#6c5b49; }
.notifications-paper .notification-toolbar .el-button { min-height:43px; border-radius:10px; font-family:"Songti SC",serif; font-size:15px; }
.notification-refresh { border-color:#d4bd94; background:rgba(255,250,239,.55); color:#5d4b3a; }
.notification-read-all { --el-button-bg-color:#963b40; --el-button-border-color:#963b40; --el-button-hover-bg-color:#a8484b; --el-button-hover-border-color:#a8484b; }
.notification-list { min-height:180px; }
.notifications-paper .notification-list-toolbar { min-height:38px; padding:0 5px 10px; border-bottom:1px solid rgba(177,147,98,.24); color:#927c61; }
.notifications-paper .notification-row { display:flex; align-items:flex-start; gap:18px; min-height:130px; margin-bottom:17px; padding:24px 25px; border:1px solid #d8c29c; border-radius:14px; background:rgba(255,252,244,.62); box-shadow:0 5px 15px rgba(111,83,42,.05); cursor:pointer; transition:transform .18s ease,box-shadow .18s ease,background .18s ease; }
.notifications-paper .notification-row:hover { background:rgba(255,253,247,.86); box-shadow:0 9px 22px rgba(111,83,42,.1); transform:translateY(-1px); }
.notifications-paper .notification-row.is-unread { background:rgba(255,251,239,.84); }
.notifications-paper .notification-checkbox { flex:0 0 auto; margin-top:14px; }
.notifications-paper .notification-icon { display:flex; width:66px; height:66px; flex:0 0 66px; align-items:center; justify-content:center; border-radius:13px; color:#fff9ec; font-family:"Songti SC",serif; font-size:19px; }
.notifications-paper .notification-icon.is-assignment { background:#b66d5a; }.notifications-paper .notification-icon.is-exam { background:#627b98; }.notifications-paper .notification-icon.is-resource { background:#9987c8; }.notifications-paper .notification-icon.is-forum { background:#bf8b4d; }.notifications-paper .notification-icon.is-activity { background:#2e6547; }.notifications-paper .notification-icon.is-system { background:#8d8d8d; }
.notifications-paper .notification-copy { min-width:0; flex:1; }
.notifications-paper .notification-title-line { display:flex; align-items:center; gap:10px; }
.notifications-paper .notification-title-line h3 { margin:0; overflow:hidden; color:#3f3026; font-family:"Songti SC",serif; font-size:20px; font-weight:600; line-height:1.4; text-overflow:ellipsis; white-space:nowrap; }
.notification-unread-mark { padding:3px 8px; border-radius:999px; background:#f0ddc0; color:#a1433f; font-family:"Songti SC",serif; font-size:11px; white-space:nowrap; }
.notifications-paper .notification-sender,.notifications-paper .notification-content { margin:7px 0 0; overflow:hidden; color:#a08a6f; font-family:"Songti SC",serif; font-size:14px; line-height:1.55; text-overflow:ellipsis; white-space:nowrap; }
.notifications-paper .notification-content { color:#6e5c4c; font-size:15px; }
.notifications-paper .notification-time { flex:0 0 165px; padding-top:4px; color:#9b8467; font-family:"Songti SC",serif; font-size:14px; text-align:right; white-space:nowrap; }
.notifications-paper .notification-delete { flex:0 0 auto; margin-top:55px; padding:0; border:0; background:transparent; color:#a1433f; cursor:pointer; font:inherit; font-family:"Songti SC",serif; font-size:14px; }
.notifications-paper .notification-delete:hover { color:#7d252d; text-decoration:underline; }
.notifications-paper .notification-pagination { padding:10px 0; }
.notifications-paper .notification-pagination :deep(.el-pagination) { --el-pagination-bg-color:transparent; --el-pagination-button-bg-color:transparent; --el-pagination-text-color:#806d56; }
@media (max-width:900px) { .notifications-paper { grid-template-columns:1fr; overflow:visible; }.notification-categories { padding:24px 20px; border-right:0; border-bottom:1px solid rgba(156,126,78,.2); }.notification-category-nav { display:flex; flex-wrap:wrap; gap:5px; }.notification-category-nav button { min-height:42px; padding:0 12px; border-left:0; border-bottom:3px solid transparent; border-radius:8px; font-size:14px; }.notification-category-nav button.active { border-bottom-color:#9b3a3f; }.notification-ai-note { display:none; }.notification-inbox { padding:26px 20px; } }
@media (max-width:640px) { .notification-inbox-heading { flex-direction:column; }.notification-assistant-pill { align-self:flex-start; }.notification-toolbar-spacer { display:none; }.notifications-paper .notification-row { gap:11px; padding:16px 13px; }.notifications-paper .notification-icon { width:48px; height:48px; flex-basis:48px; font-size:15px; }.notifications-paper .notification-title-line h3 { font-size:16px; }.notifications-paper .notification-time { flex:0 0 auto; font-size:11px; }.notifications-paper .notification-content { display:none; }.notifications-paper .notification-delete { margin-top:34px; font-size:12px; } }
</style>
