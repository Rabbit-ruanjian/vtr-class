<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { activityApi, moderationApi, uploadApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { activityStatusOptions, activityTypeOptions } from '@/config/options'
import { formatDateTime, optionLabel, resolveAssetUrl } from '@/utils/format'
import { normalizePage } from '@/utils/page'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const detailLoading = ref(false)
const submitLoading = ref(false)
const editorVisible = ref(false)
const detailVisible = ref(false)
const reviewVisible = ref(false)
const feedbackVisible = ref(false)
const mode = ref('all')
const activityView = ref('card')
const editingId = ref(null)

const list = ref([])
const total = ref(0)
const detail = ref(null)
const discussions = ref([])
const checkinStats = ref(null)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  type: '',
  myParticipation: false,
  myCreated: false
  , courseId: Number(route.query.courseId) || undefined
})

const editor = reactive({
  title: '',
  coverUrl: '',
  organizerUnit: '',
  sponsor: '',
  content: '',
  type: 'LECTURE',
  activityTime: '',
  endTime: '',
  location: '',
  duration: 120,
  maxParticipants: 50
})

const reviewForm = reactive({
  id: null,
  action: 'approve',
  rejectReason: ''
})

const feedbackForm = reactive({
  feedback: '',
  rating: 5
})

const discussionText = ref('')

const canOperate = computed(() => authStore.isTeacher)
const canParticipate = computed(() => authStore.isLoggedIn)
const canReview = computed(() => authStore.isAdmin)
function hasEnded(row) { return row.status === 'ENDED' || (activityEndTime(row) && dayjs(activityEndTime(row)).isBefore(dayjs())) }

function canDeleteActivity(row) {
  return canReview.value || (canOperate.value && row.isOrganizer)
}

function activityTypeClass(type) {
  const map = {
    LECTURE: 'lecture',
    SEMINAR: 'seminar',
    LESSON_PREP: 'lesson-prep',
    OPEN_LESSON: 'open-lesson',
    LESSON_OBSERVATION: 'observation',
    TRAINING: 'training',
    EXPERIENCE_SHARE: 'experience',
    OTHER: 'other'
  }
  return map[type] || 'other'
}

function participantCount(row) {
  return row.participantCount || row.currentParticipants || 0
}

function participantPercent(row) {
  const max = Number(row.maxParticipants || 0)
  if (!max) return 0
  return Math.min(100, Math.round((participantCount(row) / max) * 100))
}

function activityEndTime(activity) {
  if (activity.endTime) {
    return activity.endTime
  }

  if (!activity.activityTime) {
    return null
  }

  return dayjs(activity.activityTime)
    .add(activity.duration ?? 120, 'minute')
    .format('YYYY-MM-DD HH:mm:ss')
}

function resetEditor() {
  editingId.value = null
  editor.title = ''
  editor.coverUrl = ''
  editor.organizerUnit = ''
  editor.sponsor = ''
  editor.content = ''
  editor.type = 'LECTURE'
  editor.activityTime = ''
  editor.endTime = ''
  editor.location = ''
  editor.duration = 120
  editor.maxParticipants = 50
}

function applyMode() {
  query.myCreated = mode.value === 'created'
  query.myParticipation = mode.value === 'joined'
}

async function loadList() {
  loading.value = true

  try {
    applyMode()
    const pageData = normalizePage(await activityApi.list(query))
    list.value = pageData.list
    total.value = pageData.total
  } finally {
    loading.value = false
  }
}

async function loadDetail(id) {
  detailVisible.value = true
  detailLoading.value = true

  try {
    const [activity, discussionPayload, statsPayload] = await Promise.all([
      activityApi.detail(id),
      activityApi.discussions(id, { page: 1, size: 20 }),
      activityApi.checkinStats(id)
    ])

    detail.value = activity
    discussions.value = normalizePage(discussionPayload).list
    checkinStats.value = statsPayload
  } finally {
    detailLoading.value = false
  }
}

function openDetail(id) {
  router.push({ name: 'activity-detail', params: { id } })
}

function openCreate() {
  resetEditor()
  editorVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  editor.title = row.title
  editor.coverUrl = row.coverUrl || ''
  editor.organizerUnit = row.organizerUnit || ''
  editor.sponsor = row.sponsor || ''
  editor.content = row.content
  editor.type = row.type
  editor.activityTime = row.activityTime
  editor.endTime = row.endTime || activityEndTime(row) || ''
  editor.location = row.location || ''
  editor.duration = row.duration || 120
  editor.maxParticipants = row.maxParticipants || 50
  editorVisible.value = true
}

async function saveActivity() {
  submitLoading.value = true

  const payload = {
    title: editor.title,
    coverUrl: editor.coverUrl,
    organizerUnit: editor.organizerUnit,
    sponsor: editor.sponsor,
    content: editor.content,
    type: editor.type,
    activityTime: editor.activityTime,
    endTime: editor.endTime,
    location: editor.location,
    duration: editor.duration,
    maxParticipants: editor.maxParticipants
    , courseId: Number(route.query.courseId) || undefined
  }

  try {
    if (editingId.value) {
      await activityApi.update(editingId.value, payload)
      ElMessage.success(route.query.courseId ? '课程活动已更新并直接发布；高风险内容会进入复核' : '校级活动更新已提交发布审核')
    } else {
      await activityApi.create(payload)
      ElMessage.success(route.query.courseId ? '课程活动已直接发布；高风险内容会进入复核' : '校级活动已提交发布审核')
      mode.value = 'created'
    }

    editorVisible.value = false
    resetEditor()
    await loadList()
  } finally {
    submitLoading.value = false
  }
}

async function uploadCover(event) {
  const file = event.target.files?.[0]
  if (!file) return
  try {
    editor.coverUrl = await uploadApi.image(file)
    ElMessage.success('封面已上传')
  } finally {
    event.target.value = ''
  }
}

async function togglePinned(row) {
  await activityApi.updatePinned(row.id, !row.isPinned)
  ElMessage.success(row.isPinned ? '已取消置顶' : '已置顶')
  await loadList()
}

async function removeActivity(id) {
  await activityApi.remove(id)
  ElMessage.success('活动已删除')
  await loadList()
}

async function cancelActivity(id) {
  try {
    const { value } = await ElMessageBox.prompt('请填写取消原因，已报名教师将收到通知。', '取消活动', {
      confirmButtonText: '确认取消',
      cancelButtonText: '暂不取消',
      inputPlaceholder: '例如：时间安排调整',
      inputValidator: (reason) => reason?.trim() ? true : '请填写取消原因'
    })
    await activityApi.cancel(id, value.trim())
    ElMessage.success('活动已取消，已通知报名教师')
    await loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error
    }
  }
}

async function appealActivity(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请说明你认为处置有误的原因，管理员会重新复核。',
      '活动内容申诉',
      { inputType: 'textarea', inputValidator: value => String(value || '').trim() ? true : '请填写申诉理由' }
    )
    await moderationApi.appeal({ targetType: 'ACTIVITY', targetId: row.id, reason: value.trim() })
    ElMessage.success('申诉已提交')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '申诉失败')
  }
}

async function joinActivity(id) {
  await activityApi.join(id)
  ElMessage.success('已参与活动')
  await loadList()
}

async function cancelJoin(id) {
  await activityApi.cancelJoin(id)
  ElMessage.success('已取消参与')
  await loadList()
}

async function checkin(id) {
  await activityApi.checkin(id)
  ElMessage.success('签到成功')
  await loadDetail(id)
}

function openReview(row) {
  reviewForm.id = row.id
  reviewForm.action = 'approve'
  reviewForm.rejectReason = ''
  reviewVisible.value = true
}

async function submitReview() {
  await activityApi.review(reviewForm.id, {
    action: reviewForm.action,
    rejectReason: reviewForm.rejectReason
  })
  ElMessage.success('活动审核已提交')
  reviewVisible.value = false
  await loadList()
}

async function submitFeedback() {
  await activityApi.feedback(detail.value.id, feedbackForm.feedback, feedbackForm.rating)
  feedbackVisible.value = false
  feedbackForm.feedback = ''
  feedbackForm.rating = 5
  ElMessage.success('反馈已提交')
}

async function addDiscussion() {
  await activityApi.createDiscussion({
    activityId: detail.value.id,
    content: discussionText.value,
    parentId: null
  })
  discussionText.value = ''
  ElMessage.success('讨论已发表')
  await loadDetail(detail.value.id)
}

async function removeDiscussion(id) {
  await activityApi.removeDiscussion(id)
  ElMessage.success('讨论已删除')
  await loadDetail(detail.value.id)
}

async function likeDiscussion(id) {
  await activityApi.likeDiscussion(id)
  ElMessage.success('已点赞讨论')
  await loadDetail(detail.value.id)
}

watch(mode, () => {
  query.page = 1
  loadList()
})

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="research-activity-page">
    <div class="research-activity-room" aria-hidden="true"></div>
    <div class="research-activity-desk" aria-hidden="true"></div>
    <section class="research-activity-sheet">
      <span class="research-sheet-tag">讲会簿</span>
      <header class="research-sheet-head">
        <div>
          <h1><i></i>教研活动</h1>
          <p>课程和班级活动直接发布，校级公开活动提交审核；活动内容仍来自云塾平台。</p>
        </div>
        <div class="research-sheet-actions">
          <button class="research-btn" type="button" :disabled="loading" @click="loadList"><span>↻</span>刷新活动</button>
          <button v-if="canOperate" class="research-btn research-btn-primary" type="button" @click="openCreate"><span>＋</span>创建活动</button>
        </div>
      </header>

      <div class="research-filter-bar">
        <el-input v-model="query.keyword" class="research-search" clearable placeholder="搜索活动标题或内容" />
        <el-select v-model="query.type" clearable placeholder="活动类型" class="research-select">
          <el-option v-for="item in activityTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="活动状态" class="research-select">
          <el-option v-for="item in activityStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <div class="research-tabs">
          <button type="button" :class="{ active: mode === 'all' }" @click="mode = 'all'">全部</button>
          <button v-if="canOperate" type="button" :class="{ active: mode === 'created' }" @click="mode = 'created'">我创建的</button>
          <button v-if="canOperate" type="button" :class="{ active: mode === 'joined' }" @click="mode = 'joined'">我参与的</button>
        </div>
        <div class="research-view-toggle">
          <button type="button" :class="{ active: activityView === 'card' }" @click="activityView = 'card'">卡片</button>
          <button type="button" :class="{ active: activityView === 'list' }" @click="activityView = 'list'">列表</button>
        </div>
        <button class="research-btn research-btn-query" type="button" @click="query.page = 1; loadList()">查询</button>
      </div>

      <section v-if="activityView === 'card'" class="research-activity-grid" v-loading="loading" aria-label="教研活动卡片">
        <article v-for="row in list" :key="row.id" class="research-activity-card">
          <div class="research-activity-cover">
            <img v-if="row.coverUrl" :src="resolveAssetUrl(row.coverUrl)" :alt="row.title" />
            <div v-else class="research-cover-placeholder"><span>塾</span><small>云塾教研</small></div>
            <span class="research-status-seal" :class="`status-${String(row.status || '').toLowerCase()}`">{{ optionLabel(activityStatusOptions, row.status) }}</span>
            <span class="research-category-seal" :class="`category-${activityTypeClass(row.type)}`">{{ optionLabel(activityTypeOptions, row.type) }}</span>
          </div>
          <div class="research-card-body">
            <div class="research-card-title"><el-tag v-if="row.isPinned" size="small" type="danger">置顶</el-tag><h2>{{ row.title }}</h2></div>
            <p class="research-card-org">{{ row.organizer?.nickname || row.organizer?.username || '云塾教研组' }}<span v-if="row.organizerUnit || row.sponsor"> · {{ [row.organizerUnit, row.sponsor].filter(Boolean).join(' · ') }}</span></p>
            <p class="research-card-time">{{ formatDateTime(row.activityTime) }} <span>—</span> {{ formatDateTime(activityEndTime(row), 'HH:mm') }} · {{ row.location || '线上活动' }}</p>
            <div class="research-sign-row"><span>报名进度</span><div class="research-progress"><i :style="{ width: `${participantPercent(row)}%` }"></i></div><b>{{ participantCount(row) }}/{{ row.maxParticipants || 0 }}</b></div>
            <span v-if="canParticipate && row.isJoined" class="research-joined-chip">已报名</span>
            <div class="research-card-actions">
              <button type="button" @click="openDetail(row.id)">详情</button>
              <button v-if="canReview || (canOperate && row.isOrganizer)" type="button" @click="openEdit(row)">编辑</button>
              <button v-if="canReview" type="button" @click="togglePinned(row)">{{ row.isPinned ? '取消置顶' : '置顶' }}</button>
              <button v-if="canDeleteActivity(row)" type="button" class="danger" @click="removeActivity(row.id)">删除</button>
              <button v-if="canOperate && row.isOrganizer" type="button" class="warning" @click="cancelActivity(row.id)">取消活动</button>
              <button v-if="canParticipate && !hasEnded(row) && !row.isJoined" type="button" class="research-sign-button" @click="joinActivity(row.id)">＋ 报名</button>
              <button v-if="canParticipate && !hasEnded(row) && row.isJoined" type="button" @click="cancelJoin(row.id)">取消报名</button>
              <button v-if="canReview && row.status === 'PENDING'" type="button" class="success" @click="openReview(row)">审核</button>
            </div>
          </div>
        </article>
      </section>

      <section v-else class="activity-ledger" v-loading="loading" aria-label="教研活动台账">
        <table class="activity-ledger-table">
        <colgroup>
          <col class="col-cover" /><col class="col-title" /><col class="col-organization" />
          <col class="col-signups" /><col class="col-type" /><col class="col-status" /><col class="col-time" /><col class="col-actions" />
        </colgroup>
        <thead>
          <tr>
            <th>封面</th><th>活动名</th><th>组织信息</th>
            <th>报名人数</th><th>分类</th><th>状态</th><th>开始 - 结束</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
        <td class="ledger-cell" data-label="封面">
          <img v-if="row.coverUrl" class="activity-cover" :src="resolveAssetUrl(row.coverUrl)" :alt="row.title" />
          <span v-else class="activity-cover activity-cover-empty">无封面</span>
        </td>
        <td class="ledger-cell" data-label="活动名">
          <div class="ledger-title">
            <el-tag v-if="row.isPinned" size="small" type="danger">置顶</el-tag>
            <span>{{ row.title }}</span>
          </div>
        </td>
        <td class="ledger-cell" data-label="组织信息">
          <div class="activity-organization">
            <strong>{{ row.organizer?.nickname || row.organizer?.username || '--' }}</strong>
            <span v-if="row.organizerUnit || row.sponsor">{{ [row.organizerUnit, row.sponsor].filter(Boolean).join(' · ') }}</span>
          </div>
        </td>
        <td class="ledger-cell" data-label="报名人数">{{ row.participantCount || row.currentParticipants || 0 }} / {{ row.maxParticipants || 0 }}</td>
        <td class="ledger-cell" data-label="分类">{{ optionLabel(activityTypeOptions, row.type) }}</td>
        <td class="ledger-cell" data-label="状态">{{ optionLabel(activityStatusOptions, row.status) }}</td>
        <td class="ledger-cell activity-time-cell" data-label="开始 - 结束">
          <div class="activity-time-range">
            <time class="activity-date" :datetime="row.activityTime">{{ formatDateTime(row.activityTime, 'MM-DD') }}</time>
            <time :datetime="row.activityTime">{{ formatDateTime(row.activityTime, 'HH:mm') }}</time>
            <span class="activity-time-separator">-</span>
            <time :datetime="activityEndTime(row)">{{ formatDateTime(activityEndTime(row), 'HH:mm') }}</time>
          </div>
        </td>
        <td class="ledger-cell" data-label="操作">
          <div class="activity-row-actions">
            <el-button link @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="canReview || (canOperate && row.isOrganizer)" link @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canReview" link :type="row.isPinned ? 'info' : 'warning'" @click="togglePinned(row)">{{ row.isPinned ? '取消置顶' : '置顶' }}</el-button>
            <el-button v-if="canDeleteActivity(row)" link type="danger" @click="removeActivity(row.id)">删除</el-button>
            <el-button v-if="canOperate && row.isOrganizer && row.status === 'REJECTED' && (row.courseId || row.classroomId)" link type="warning" @click="appealActivity(row)">申诉</el-button>
            <el-button v-if="canOperate && row.isOrganizer" link type="warning" @click="cancelActivity(row.id)">取消活动</el-button>
            <el-button v-if="canParticipate && !hasEnded(row) && !row.isJoined" plain size="small" class="activity-join-button" @click="joinActivity(row.id)">
              <el-icon><Plus /></el-icon>
              报名
            </el-button>
            <el-tag v-if="canParticipate && row.isJoined" class="activity-joined-tag" type="success" effect="plain" size="small">已报名</el-tag>
            <el-button v-if="canParticipate && !hasEnded(row) && row.isJoined" link @click="cancelJoin(row.id)">取消报名</el-button>
            <el-button v-if="canReview && row.status === 'PENDING'" link type="success" @click="openReview(row)">审核</el-button>
          </div>
        </td>
          </tr>
        </tbody>
        </table>
      </section>
      <div v-if="!loading && !list.length" class="research-empty-state"><div class="research-empty-seal">塾</div><p>暂无符合条件的教研活动</p><button v-if="canOperate" type="button" class="research-btn research-btn-primary" @click="openCreate">创建第一场活动</button></div>
      <div class="research-pagination"><span>共录得 {{ total }} 场活动</span><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" layout="prev, pager, next" :total="total" @current-change="loadList" /></div>
    </section>

    <el-dialog
      v-model="editorVisible"
      :title="editingId ? '编辑活动' : '创建活动'"
      width="820px"
    >
      <el-form label-position="top">
        <div class="two-column">
          <div>
            <el-form-item label="标题">
              <el-input v-model="editor.title" />
            </el-form-item>
            <el-form-item label="封面">
              <input type="file" accept="image/*" @change="uploadCover" />
              <img v-if="editor.coverUrl" class="activity-cover-preview" :src="resolveAssetUrl(editor.coverUrl)" alt="活动封面" />
            </el-form-item>
            <el-form-item label="所属单位">
              <el-input v-model="editor.organizerUnit" placeholder="例如：计算机学院" />
            </el-form-item>
            <el-form-item label="活动类型">
              <el-select v-model="editor.type" style="width: 100%;">
                <el-option
                  v-for="item in activityTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="活动时间">
              <el-date-picker
                v-model="editor.activityTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%;"
              />
            </el-form-item>
            <el-form-item label="结束时间">
              <el-date-picker v-model="editor.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%;" />
            </el-form-item>
          </div>
          <div>
            <el-form-item label="主办方">
              <el-input v-model="editor.sponsor" placeholder="例如：计算机学院教学发展中心" />
            </el-form-item>
            <el-form-item label="地点">
              <el-input v-model="editor.location" />
            </el-form-item>
            <el-form-item label="时长（分钟）">
              <el-input-number v-model="editor.duration" :min="10" :max="1440" />
            </el-form-item>
            <el-form-item label="最大参与人数">
              <el-input-number v-model="editor.maxParticipants" :min="1" :max="1000" />
            </el-form-item>
          </div>
        </div>
        <el-form-item label="活动内容">
          <el-input v-model="editor.content" type="textarea" :rows="10" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="saveActivity">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="校级活动发布审核" width="520px">
      <el-form label-position="top">
        <el-form-item label="审核结果">
          <el-radio-group v-model="reviewForm.action">
            <el-radio-button label="approve">通过</el-radio-button>
            <el-radio-button label="reject">拒绝</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="拒绝原因">
          <el-input v-model="reviewForm.rejectReason" type="textarea" :rows="5" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交结果</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="feedbackVisible" title="提交活动反馈" width="520px">
      <el-form label-position="top">
        <el-form-item label="评分">
          <el-rate v-model="feedbackForm.rating" />
        </el-form-item>
        <el-form-item label="反馈内容">
          <el-input v-model="feedbackForm.feedback" type="textarea" :rows="5" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFeedback">提交反馈</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="活动详情" size="65%">
      <el-skeleton v-if="detailLoading" :rows="12" animated />
      <template v-else-if="detail">
        <div class="page-stack">
          <div class="card-head">
            <div>
              <h3>{{ detail.title }}</h3>
              <p>
                {{ optionLabel(activityTypeOptions, detail.type) }} ·
                {{ formatDateTime(detail.activityTime) }} ·
                {{ optionLabel(activityStatusOptions, detail.status) }}
              </p>
            </div>
            <div class="chips">
              <el-tag v-if="detail.organizerUnit">{{ detail.organizerUnit }}</el-tag>
              <el-tag v-if="detail.sponsor">{{ detail.sponsor }}</el-tag>
              <el-tag>{{ detail.location || '未设置地点' }}</el-tag>
              <el-tag type="success">{{ detail.participantCount || detail.currentParticipants || 0 }} 人参与</el-tag>
            </div>
          </div>

          <div style="white-space: pre-wrap; line-height: 1.85;">{{ detail.content }}</div>

          <div class="toolbar" v-if="canOperate">
            <el-button v-if="detail.isJoined" type="success" @click="checkin(detail.id)">
              签到
            </el-button>
            <el-button v-if="detail.isJoined" plain @click="feedbackVisible = true">
              提交反馈
            </el-button>
          </div>

          <el-card class="surface-card">
            <template #header>
              <div class="card-head">
                <div>
                  <h3>签到统计</h3>
                  <p>数据来自 `/checkin-stats`。</p>
                </div>
              </div>
            </template>

            <pre class="code-panel">{{ JSON.stringify(checkinStats, null, 2) }}</pre>
          </el-card>

          <el-card class="surface-card">
            <template #header>
              <div class="card-head">
                <div>
                  <h3>活动讨论</h3>
                  <p>支持发表、点赞和删除讨论。</p>
                </div>
              </div>
            </template>

            <div v-if="authStore.isLoggedIn" class="page-stack" style="margin-bottom: 18px;">
              <el-input v-model="discussionText" type="textarea" :rows="4" placeholder="写下你的讨论观点" />
              <div>
                <el-button type="primary" @click="addDiscussion">发布讨论</el-button>
              </div>
            </div>

            <div v-if="discussions.length" class="list-stack">
              <article
                v-for="item in discussions"
                :key="item.id"
                class="list-card"
              >
                <div class="card-head">
                  <div>
                    <h4>{{ item.teacher?.nickname || item.teacher?.username || `教师 ${item.teacherId}` }}</h4>
                    <p>{{ formatDateTime(item.createdAt) }}</p>
                  </div>
                  <div class="chips">
                    <el-tag type="success">赞 {{ item.likeCount || 0 }}</el-tag>
                  </div>
                </div>
                <p>{{ item.content }}</p>
                <div class="toolbar">
                  <el-button link type="primary" @click="likeDiscussion(item.id)">点赞</el-button>
                  <el-button
                    v-if="authStore.user?.id === item.teacherId || authStore.isAdmin"
                    link
                    type="danger"
                    @click="removeDiscussion(item.id)"
                  >
                    删除
                  </el-button>
                </div>
              </article>
            </div>
            <div v-else class="empty-block">当前还没有讨论内容。</div>
          </el-card>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.activity-cover {
  display: block;
  width: 72px;
  height: 48px;
  border-radius: 4px;
  object-fit: cover;
  background: var(--bg-soft);
}

.activity-cover-empty {
  display: grid;
  place-items: center;
  color: var(--text-muted);
  font-size: 12px;
}

.activity-cover-preview {
  display: block;
  width: 180px;
  height: 96px;
  margin-top: 10px;
  border: 1px solid var(--line);
  border-radius: 4px;
  object-fit: cover;
}

.activity-ledger {
  width: 100%;
}

.activity-ledger-table {
  width: 100%;
  table-layout: fixed;
  border: 1px solid var(--line);
  border-collapse: collapse;
}

.col-cover { width: 5%; }
.col-title { width: 15%; }
.col-organization { width: 16%; }
.col-signups { width: 8%; }
.col-type { width: 7%; }
.col-status { width: 8%; }
.col-time { width: 20%; }
.col-actions { width: 21%; }

.activity-ledger-table th {
  padding: 14px 12px;
  color: var(--text-muted);
  background: var(--bg-soft);
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  font-size: 13px;
  font-weight: 600;
  text-align: left;
}

.activity-ledger-table th:last-child,
.activity-ledger-table td:last-child {
  border-right: 0;
}

.ledger-cell {
  min-width: 0;
  padding: 14px 12px;
  vertical-align: middle;
  background: #fff;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  overflow-wrap: anywhere;
}

.activity-ledger-table tbody tr:hover .ledger-cell {
  background: var(--bg-soft);
}

.ledger-title {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--text-main);
  font-weight: 600;
  line-height: 1.55;
}

.activity-organization {
  display: grid;
  gap: 4px;
}

.activity-organization strong {
  color: var(--text-main);
  font-weight: 600;
}

.activity-organization span {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.activity-time-cell {
  overflow: hidden;
}

.activity-time-range {
  display: flex;
  align-items: baseline;
  gap: 6px;
  white-space: nowrap;
}

.activity-date {
  color: var(--text-main);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.activity-time-separator {
  color: var(--text-muted);
}

.activity-time-range time {
  color: var(--text-main);
  font-variant-numeric: tabular-nums;
  line-height: 1.4;
  white-space: nowrap;
}

.activity-row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 8px;
  min-height: 42px;
}

.activity-row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.activity-join-button.el-button {
  min-height: 28px;
  padding: 0 9px;
  color: var(--brand-deep) !important;
  background: #fff !important;
  border: 1px solid rgba(48, 112, 255, 0.4) !important;
  border-radius: 4px !important;
  box-shadow: none !important;
  font-size: 13px;
  font-weight: 600;
}

.activity-join-button.el-button:hover {
  color: #fff !important;
  background: var(--brand-deep) !important;
  border-color: var(--brand-deep) !important;
  transform: none;
  box-shadow: none !important;
}

.activity-join-button .el-icon {
  margin-right: 3px;
  font-size: 14px;
}

.activity-joined-tag {
  height: 28px;
  padding: 0 8px;
  border-radius: 4px;
  font-weight: 600;
  line-height: 26px;
}

.activity-ledger-empty {
  padding: 42px 18px;
  border: 1px solid var(--line);
  border-top: 0;
  color: var(--text-muted);
  text-align: center;
}

@media (max-width: 1250px) {
  .activity-ledger-table,
  .activity-ledger-table tbody,
  .activity-ledger-table tr,
  .activity-ledger-table td {
    display: block;
  }

  .activity-ledger-table {
    border: 0;
  }

  .activity-ledger-table thead {
    display: none;
  }

  .activity-ledger-table tbody tr {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-bottom: 14px;
    border: 1px solid var(--line);
  }
  .ledger-cell {
    display: grid;
    grid-template-columns: 90px minmax(0, 1fr);
    gap: 10px;
    align-items: center;
    border: 0;
    border-bottom: 1px solid var(--line);
  }
  .ledger-cell::before {
    content: attr(data-label);
    color: var(--text-muted);
    font-size: 13px;
  }
  .activity-ledger-table tbody tr .ledger-cell:nth-last-child(-n + 2) { border-bottom: 0; }
  .activity-row-actions { align-content: center; }
}

@media (max-width: 680px) {
  .activity-ledger-table tbody tr { grid-template-columns: 1fr; }
  .activity-ledger-table tbody tr .ledger-cell:last-child { border-bottom: 0; }
}

.research-activity-page { position:relative; min-height:calc(100vh - 48px); overflow:hidden; padding:30px clamp(16px,3vw,42px) 70px; background:#2a1d14; color:#2c1810; }
.research-activity-room { position:absolute; inset:0; background:radial-gradient(ellipse at 50% 12%,rgba(139,111,71,.32),transparent 52%),repeating-linear-gradient(90deg,rgba(255,255,255,.015) 0 78px,rgba(0,0,0,.14) 80px 82px),linear-gradient(180deg,#2a1d14,#3d2b1f 52%,#241810); }
.research-activity-room::after { position:absolute; top:6%; left:50%; width:min(420px,42vw); height:180px; border:8px solid rgba(60,40,25,.6); background:radial-gradient(ellipse at 30% 60%,rgba(60,80,70,.42),transparent 50%),radial-gradient(ellipse at 70% 40%,rgba(50,70,60,.34),transparent 45%); box-shadow:0 4px 20px rgba(0,0,0,.3); content:""; opacity:.58; transform:translateX(-50%); }
.research-activity-desk { position:absolute; right:0; bottom:0; left:0; height:78px; background:linear-gradient(180deg,#4a3525,#3d2b1f 50%,#2a1d14); box-shadow:0 -5px 30px rgba(0,0,0,.3); }
.research-activity-sheet { position:relative; z-index:1; width:min(1220px,100%); margin:0 auto; padding:28px 30px 22px; border:1px solid #d4c4a0; border-radius:16px; background:repeating-linear-gradient(45deg,rgba(120,95,60,.035) 0 2px,transparent 2px 7px),linear-gradient(168deg,#f6f0de,#f2ead8 55%,#e9dcc2); box-shadow:0 20px 60px rgba(0,0,0,.4),0 0 0 1px rgba(42,29,20,.25); }
.research-sheet-tag { position:absolute; top:-14px; left:30px; padding:7px 12px 6px; border:2px solid #b8956a; border-radius:8px; color:#f2ead8; background:linear-gradient(180deg,#a84030,#7c2b20); box-shadow:0 6px 14px rgba(0,0,0,.3); font-size:17px; font-weight:900; letter-spacing:.14em; transform:rotate(-2deg); }
.research-sheet-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; flex-wrap:wrap; }
.research-sheet-head h1 { display:flex; align-items:center; gap:12px; margin:0; color:#2c1810; font-size:clamp(24px,3vw,30px); font-weight:900; letter-spacing:.14em; }
.research-sheet-head h1 i { width:10px; height:10px; border:2px solid #8b3a3a; transform:rotate(45deg); }
.research-sheet-head p { max-width:680px; margin:7px 0 0; color:#8a7560; font-size:12.5px; letter-spacing:.05em; line-height:1.7; }
.research-sheet-actions { display:flex; flex-wrap:wrap; gap:10px; }
.research-btn { display:inline-flex; align-items:center; gap:7px; padding:9px 16px; border:1px solid #d4c4a0; border-radius:9px; color:#5c4a3a; background:#e8dcc0; cursor:pointer; font-family:inherit; font-size:13.5px; letter-spacing:.1em; transition:transform .25s,box-shadow .25s,background .25s; }
.research-btn:hover:not(:disabled) { box-shadow:0 6px 14px rgba(0,0,0,.14); transform:translateY(-1px); }
.research-btn-primary { border-color:#b8956a; color:#f2ead8; background:linear-gradient(180deg,#a84030,#7c2b20); }
.research-btn-query { padding-inline:20px; border-color:#8b3a3a; color:#f2ead8; background:#8b3a3a; }
.research-btn:disabled { cursor:not-allowed; opacity:.55; }
.research-filter-bar { display:flex; align-items:center; flex-wrap:wrap; gap:10px; margin:20px 0 18px; padding:13px 14px; border:1px solid #d4c4a0; border-radius:12px; background:rgba(42,29,20,.05); }
.research-search { flex:1 1 220px; min-width:180px; }
.research-select { width:160px; }
.research-filter-bar :deep(.el-input__wrapper),.research-filter-bar :deep(.el-select__wrapper) { border:1px solid #d4c4a0; border-radius:9px; background:#f2ead8; box-shadow:none; }
.research-filter-bar :deep(.el-input__inner),.research-filter-bar :deep(.el-select__selected-item) { color:#2c1810; font-family:inherit; }
.research-tabs,.research-view-toggle { display:inline-flex; overflow:hidden; border-radius:9px; background:rgba(42,29,20,.07); padding:3px; }
.research-tabs button,.research-view-toggle button { padding:7px 13px; border:0; border-radius:7px; color:#5c4a3a; background:transparent; cursor:pointer; font-family:inherit; font-size:13px; white-space:nowrap; }
.research-tabs button.active { color:#f2ead8; background:#2c1810; font-weight:700; }
.research-view-toggle { margin-left:auto; border:1px solid #d4c4a0; background:#f2ead8; padding:0; }
.research-view-toggle button { border-radius:0; border-right:1px solid #d4c4a0; }
.research-view-toggle button:last-child { border-right:0; }
.research-view-toggle button.active { color:#f2ead8; background:#8b3a3a; font-weight:700; }
.research-activity-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:20px; min-height:170px; }
.research-activity-card { position:relative; overflow:hidden; border:1px solid #d4c4a0; border-radius:13px; background:linear-gradient(168deg,#fbf5e6,#f0e5cc); box-shadow:0 6px 18px rgba(0,0,0,.14); transition:transform .35s,box-shadow .35s; }
.research-activity-card:hover { box-shadow:0 16px 34px rgba(0,0,0,.22); transform:translateY(-4px); }
.research-activity-cover { position:relative; aspect-ratio:16/9; overflow:hidden; background:#3d2b1f; }
.research-activity-cover img { width:100%; height:100%; object-fit:cover; transition:transform .5s; }
.research-activity-card:hover .research-activity-cover img { transform:scale(1.06); }
.research-activity-cover::after { position:absolute; inset:0; pointer-events:none; background:linear-gradient(to top,rgba(42,29,20,.38),transparent 42%); content:""; }
.research-cover-placeholder { display:flex; height:100%; align-items:center; justify-content:center; flex-direction:column; color:#d4c4a0; background:radial-gradient(circle at 50% 35%,rgba(184,149,106,.3),transparent 55%),linear-gradient(145deg,#5c4030,#2a1d14); }
.research-cover-placeholder span { color:#e3c98a; font-family:"STKaiti","KaiTi",serif; font-size:48px; }
.research-cover-placeholder small { color:#d4c4a0; letter-spacing:.25em; }
.research-status-seal { position:absolute; top:10px; left:10px; z-index:2; padding:3px 10px; border:1px solid #b8956a; border-radius:6px; color:#5c4a3a; background:rgba(242,234,216,.94); font-size:12px; letter-spacing:.1em; }
.research-status-seal.status-ongoing,.research-status-seal.status-approved { color:#2d5a3d; border-color:#2d5a3d; }
.research-status-seal.status-cancelled,.research-status-seal.status-rejected { color:#8b3a3a; border-color:#8b3a3a; }
.research-category-seal { position:absolute; bottom:10px; left:10px; z-index:2; display:flex; width:38px; height:36px; align-items:center; justify-content:center; border:1px solid rgba(242,234,216,.5); border-radius:7px; color:#f2ead8; box-shadow:0 4px 10px rgba(0,0,0,.3); font-size:12px; font-weight:700; writing-mode:vertical-rl; }
.category-lecture,.category-seminar { background:#8b3a3a; }.category-lesson-prep,.category-training { color:#2a1d14; background:#b8956a; }.category-open-lesson,.category-observation,.category-experience { background:#2d5a3d; }.category-other { background:#5c4030; }
.research-card-body { position:relative; padding:14px 16px 12px; }
.research-card-title { display:flex; align-items:flex-start; gap:7px; min-height:46px; }
.research-card-title h2 { display:-webkit-box; flex:1; min-width:0; margin:0; overflow:hidden; color:#2c1810; font-size:16px; font-weight:700; line-height:1.45; text-overflow:ellipsis; -webkit-box-orient:vertical; -webkit-line-clamp:2; }
.research-card-org,.research-card-time { margin:6px 0 0; color:#8a7560; font-size:12px; line-height:1.55; }
.research-card-time { color:#5c4a3a; }
.research-card-time span { color:#8a7560; }
.research-sign-row { display:flex; align-items:center; gap:9px; margin-top:13px; color:#8a7560; font-size:12px; }
.research-sign-row b { color:#5c4a3a; font-size:12px; font-weight:500; }
.research-progress { flex:1; height:8px; overflow:hidden; border-radius:5px; background:#d4c4a0; }
.research-progress i { display:block; height:100%; border-radius:5px; background:linear-gradient(90deg,#b8956a,#a84a4a); }
.research-joined-chip { display:inline-block; margin-top:9px; padding:2px 9px; border:1px solid rgba(45,90,61,.4); border-radius:999px; color:#2d5a3d; background:rgba(45,90,61,.12); font-size:11.5px; }
.research-card-actions { display:flex; flex-wrap:wrap; align-items:center; gap:2px; margin-top:12px; padding-top:10px; border-top:1px dashed #d4c4a0; }
.research-card-actions button { padding:5px 8px; border:0; border-radius:7px; color:#5c4a3a; background:transparent; cursor:pointer; font-family:inherit; font-size:12.5px; }
.research-card-actions button:hover { color:#8b3a3a; background:rgba(139,58,58,.09); }
.research-card-actions button.danger { color:#8b3a3a; }.research-card-actions button.warning { color:#8b3a3a; }.research-card-actions button.success { color:#2d5a3d; }
.research-sign-button { border:1px solid rgba(139,58,58,.35)!important; color:#8b3a3a!important; }
.research-empty-state { padding:52px 20px 46px; color:#5c4a3a; text-align:center; }
.research-empty-seal { display:grid; width:64px; height:64px; place-items:center; margin:0 auto 16px; border:2px solid #b8956a; border-radius:8px; color:#8b3a3a; background:#e8dcc0; font-family:"STKaiti","KaiTi",serif; font-size:34px; transform:rotate(-4deg); }
.research-empty-state p { margin:0; font-size:14px; letter-spacing:.08em; }
.research-empty-state .research-btn { margin-top:16px; }
.research-pagination { display:flex; align-items:center; justify-content:space-between; gap:14px; flex-wrap:wrap; margin-top:22px; padding-top:16px; border-top:1px dashed #d4c4a0; color:#5c4a3a; font-size:13px; }
.research-pagination :deep(.el-pagination) { --el-pagination-bg-color:transparent; --el-pagination-text-color:#5c4a3a; --el-pagination-button-color:#5c4a3a; }
.research-pagination :deep(.el-pager li.is-active) { color:#f2ead8; background:#2c1810; }

@media (max-width:1100px) { .research-activity-grid { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media (max-width:760px) { .research-activity-page { padding:24px 12px 56px; }.research-activity-sheet { padding:24px 14px 18px; }.research-sheet-tag { left:16px; }.research-sheet-actions { width:100%; }.research-sheet-actions .research-btn { flex:1; justify-content:center; }.research-filter-bar { align-items:stretch; flex-direction:column; }.research-search,.research-select { width:100%; }.research-tabs,.research-view-toggle { margin-left:0; justify-content:center; }.research-tabs button,.research-view-toggle button { flex:1; }.research-btn-query { justify-content:center; }.research-activity-grid { grid-template-columns:1fr; }.research-pagination { align-items:flex-start; flex-direction:column; } }
</style>
