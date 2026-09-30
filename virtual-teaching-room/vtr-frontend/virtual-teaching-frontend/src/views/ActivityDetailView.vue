<script setup>
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHero from '@/components/PageHero.vue'
import { activityApi, moderationApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { activityStatusOptions, activityTypeOptions } from '@/config/options'
import { formatDateTime, optionLabel, resolveAssetUrl } from '@/utils/format'
import { normalizePage } from '@/utils/page'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const submitting = ref(false)
const activity = ref(null)
const participants = ref([])
const discussions = ref([])
const checkinStats = ref(null)
const feedbackVisible = ref(false)
const feedback = ref('')
const rating = ref(5)
const discussionText = ref('')

const participantCount = computed(() => activity.value?.participantCount || activity.value?.currentParticipants || 0)
const endTime = computed(() => {
  if (!activity.value?.activityTime) return null
  return activity.value.endTime || dayjs(activity.value.activityTime)
    .add(activity.value.duration ?? 120, 'minute')
    .format('YYYY-MM-DD HH:mm:ss')
})
const phase = computed(() => {
  if (!activity.value?.activityTime) return 'upcoming'
  const now = dayjs()
  if (now.isBefore(dayjs(activity.value.activityTime))) return 'upcoming'
  if (now.isBefore(dayjs(endTime.value))) return 'ongoing'
  return 'ended'
})
const canJoin = computed(() => authStore.isLoggedIn
  && activity.value?.status === 'APPROVED'
  && !activity.value?.isJoined
  && !activity.value?.isFull
  && phase.value === 'upcoming')
const canCancelJoin = computed(() => authStore.isLoggedIn
  && activity.value?.isJoined
  && phase.value === 'upcoming')
const canCheckin = computed(() => authStore.isLoggedIn
  && activity.value?.isJoined
  && !activity.value?.hasCheckedIn
  && phase.value === 'ongoing')
const canSubmitFeedback = computed(() => authStore.isLoggedIn
  && activity.value?.isJoined
  && phase.value === 'ended')
const canViewParticipants = computed(() => authStore.isAdmin || activity.value?.isOrganizer)
const statusMessage = computed(() => {
  if (!activity.value) return ''
  if (activity.value.status !== 'APPROVED') return optionLabel(activityStatusOptions, activity.value.status)
  if (activity.value.isJoined && activity.value.hasCheckedIn) return '已签到'
  if (activity.value.isJoined && phase.value === 'upcoming') return '报名已确认'
  if (phase.value === 'ongoing') return '活动进行中'
  if (phase.value === 'ended') return '活动已结束'
  if (activity.value.isFull) return '报名已满'
  return '开放报名'
})

async function loadDetail() {
  loading.value = true
  try {
    const [detail, stats, discussionPayload] = await Promise.all([
      activityApi.detail(route.params.id),
      activityApi.checkinStats(route.params.id),
      activityApi.discussions(route.params.id, { page: 1, size: 20 })
    ])
    activity.value = detail
    participants.value = detail.participants || []
    checkinStats.value = stats
    discussions.value = normalizePage(discussionPayload).list
  } finally {
    loading.value = false
  }
}

async function joinActivity() {
  submitting.value = true
  try {
    await activityApi.join(activity.value.id)
    ElMessage.success('报名成功')
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

async function cancelJoin() {
  submitting.value = true
  try {
    await activityApi.cancelJoin(activity.value.id)
    ElMessage.success('已取消报名')
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

async function checkIn() {
  submitting.value = true
  try {
    await activityApi.checkin(activity.value.id)
    ElMessage.success('签到成功')
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

async function submitFeedback() {
  if (!feedback.value.trim()) {
    ElMessage.warning('请填写活动反馈')
    return
  }

  submitting.value = true
  try {
    await activityApi.feedback(activity.value.id, feedback.value.trim(), rating.value)
    ElMessage.success('反馈已提交')
    feedbackVisible.value = false
    feedback.value = ''
    rating.value = 5
  } finally {
    submitting.value = false
  }
}

async function addDiscussion() {
  if (!discussionText.value.trim()) {
    ElMessage.warning('请输入讨论内容')
    return
  }

  submitting.value = true
  try {
    await activityApi.createDiscussion({
      activityId: activity.value.id,
      content: discussionText.value.trim(),
      parentId: null
    })
    discussionText.value = ''
    ElMessage.success('讨论已发布')
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

async function likeDiscussion(id) {
  await activityApi.likeDiscussion(id)
  await loadDetail()
}

async function removeDiscussion(id) {
  await activityApi.removeDiscussion(id)
  ElMessage.success('讨论已删除')
  await loadDetail()
}

async function reportActivity() {
  if (!authStore.isLoggedIn) return ElMessage.warning('登录后才能举报')
  if (authStore.user?.id === activity.value?.organizerId) return ElMessage.warning('不能举报自己发布的活动')
  try {
    const { value } = await ElMessageBox.prompt(
      '请说明举报原因，管理员只处理异常或高风险内容。',
      '举报活动',
      { inputType: 'textarea', inputValidator: value => String(value || '').trim() ? true : '请填写举报原因' }
    )
    await moderationApi.report({ targetType: 'ACTIVITY', targetId: activity.value.id, reason: value.trim() })
    ElMessage.success('举报已提交，管理员会进行复核')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '举报失败')
  }
}

onMounted(loadDetail)
</script>

<template>
  <div class="activity-detail-page" v-loading="loading">
    <PageHero
      :title="activity?.title || '教研活动详情'"
      :description="activity ? `${formatDateTime(activity.activityTime, 'YYYY年MM月DD日 HH:mm')} - ${formatDateTime(endTime, 'HH:mm')} · ${activity.location || '地点待定'}` : '查看活动安排、报名状态与参与信息。'"
    >
      <template #actions>
        <el-button @click="router.push('/activities')">返回活动列表</el-button>
        <el-button
          v-if="authStore.isLoggedIn && activity && authStore.user?.id !== activity.organizerId"
          type="danger"
          plain
          @click="reportActivity"
        >举报活动</el-button>
      </template>
    </PageHero>

    <template v-if="activity">
      <section class="activity-overview">
        <img
          v-if="activity.coverUrl"
          class="activity-poster"
          width="420"
          height="263"
          :src="resolveAssetUrl(activity.coverUrl)"
          :alt="`${activity.title} 海报`"
        />
        <div v-else class="activity-poster activity-poster-empty">教研活动</div>

        <div class="activity-summary">
          <div class="activity-badges">
            <el-tag v-if="activity.isPinned" type="danger">置顶</el-tag>
            <el-tag>{{ optionLabel(activityTypeOptions, activity.type) }}</el-tag>
            <el-tag :type="activity.status === 'APPROVED' ? 'success' : 'info'">
              {{ optionLabel(activityStatusOptions, activity.status) }}
            </el-tag>
          </div>

          <div class="schedule-strip">
            <div>
              <span>开始</span>
              <strong>{{ formatDateTime(activity.activityTime, 'YYYY-MM-DD HH:mm') }}</strong>
            </div>
            <div class="schedule-divider" aria-hidden="true"></div>
            <div>
              <span>结束</span>
              <strong>{{ formatDateTime(endTime, 'YYYY-MM-DD HH:mm') }}</strong>
            </div>
          </div>

          <p class="activity-lead">{{ activity.content }}</p>
          <div class="activity-status-line">
            <span class="status-dot" :class="`status-${phase}`"></span>
            {{ statusMessage }}
          </div>
          <div class="activity-actions" v-if="authStore.isLoggedIn">
            <el-button v-if="canJoin" type="primary" :loading="submitting" @click="joinActivity">立即报名</el-button>
            <el-button v-else-if="canCancelJoin" :loading="submitting" @click="cancelJoin">取消报名</el-button>
            <el-button v-if="canCheckin" type="success" :loading="submitting" @click="checkIn">签到</el-button>
            <el-button v-if="canSubmitFeedback" plain @click="feedbackVisible = true">提交反馈</el-button>
          </div>
        </div>
      </section>

      <section class="detail-band">
        <div class="detail-group">
          <h2>活动安排</h2>
          <dl class="detail-grid">
            <div><dt>活动时间</dt><dd>{{ formatDateTime(activity.activityTime, 'YYYY-MM-DD HH:mm') }} - {{ formatDateTime(endTime, 'HH:mm') }}</dd></div>
            <div><dt>活动地点</dt><dd>{{ activity.location || '待定' }}</dd></div>
            <div><dt>活动时长</dt><dd>{{ activity.duration || 120 }} 分钟</dd></div>
            <div><dt>活动状态</dt><dd>{{ statusMessage }}</dd></div>
            <div v-if="activity.cancelReason"><dt>取消原因</dt><dd>{{ activity.cancelReason }}</dd></div>
          </dl>
        </div>
        <div class="detail-group">
          <h2>组织信息</h2>
          <dl class="detail-grid">
            <div><dt>负责人</dt><dd>{{ activity.organizer?.nickname || activity.organizer?.username || '--' }}</dd></div>
            <div><dt>所属单位</dt><dd>{{ activity.organizerUnit || '--' }}</dd></div>
            <div><dt>主办方</dt><dd>{{ activity.sponsor || '--' }}</dd></div>
            <div><dt>报名情况</dt><dd>{{ participantCount }} / {{ activity.maxParticipants || '--' }}</dd></div>
          </dl>
        </div>
      </section>

      <section class="activity-detail-section">
        <div class="section-heading">
          <div>
            <h2>活动说明</h2>
            <p>请根据下列安排提前做好参与准备。</p>
          </div>
        </div>
        <div class="activity-content">{{ activity.content }}</div>
      </section>

      <section class="activity-detail-section participation-section">
        <div class="section-heading">
          <div>
            <h2>参与情况</h2>
            <p>已报名 {{ participantCount }} 人，已签到 {{ checkinStats?.checkedInCount || 0 }} 人。</p>
          </div>
          <el-tag type="success" effect="plain">签到率 {{ checkinStats?.checkinRate ?? 0 }}%</el-tag>
        </div>
        <el-table v-if="canViewParticipants" :data="participants" empty-text="暂无报名信息">
          <el-table-column label="姓名" min-width="160">
            <template #default="{ row }">{{ row.nickname || row.username || '--' }}</template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" min-width="220" />
          <el-table-column prop="username" label="账号" min-width="140" />
        </el-table>
        <p v-else class="privacy-note">报名名单仅对活动负责人和管理员开放。</p>
      </section>

      <section class="activity-detail-section">
        <div class="section-heading">
          <div>
            <h2>活动讨论</h2>
            <p>围绕活动主题交流想法和准备事项。</p>
          </div>
        </div>
        <div v-if="authStore.isTeacher" class="discussion-composer">
          <el-input v-model="discussionText" type="textarea" :rows="3" placeholder="写下你的讨论内容" />
          <el-button type="primary" :loading="submitting" @click="addDiscussion">发布讨论</el-button>
        </div>
        <p v-else-if="authStore.isLoggedIn" class="privacy-note">当前账号没有发布教研讨论的权限。</p>
        <div v-if="discussions.length" class="discussion-list">
          <article v-for="item in discussions" :key="item.id" class="discussion-item">
            <div>
              <strong>{{ item.teacher?.nickname || item.teacher?.username || `用户 ${item.teacherId}` }}</strong>
              <span>{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <p>{{ item.content }}</p>
            <div class="discussion-actions">
              <el-button link @click="likeDiscussion(item.id)">赞 {{ item.likeCount || 0 }}</el-button>
              <el-button
                v-if="authStore.user?.id === item.teacherId || authStore.isAdmin"
                link
                type="danger"
                @click="removeDiscussion(item.id)"
              >删除</el-button>
            </div>
          </article>
        </div>
        <p v-else class="empty-copy">暂时还没有讨论。</p>
      </section>
    </template>

    <el-dialog v-model="feedbackVisible" title="提交活动反馈" width="520px">
      <el-form label-position="top">
        <el-form-item label="评分"><el-rate v-model="rating" /></el-form-item>
        <el-form-item label="反馈内容"><el-input v-model="feedback" type="textarea" :rows="5" placeholder="请填写本次活动的收获或建议" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitFeedback">提交反馈</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.activity-detail-page { display: grid; width: 100%; min-width: 0; max-width: 100%; gap: 28px; font-size: 16px; overflow-wrap: anywhere; }
.activity-overview { display: grid; width: 100%; min-width: 0; grid-template-columns: minmax(0, 420px) minmax(0, 1fr); gap: 32px; align-items: start; }
.activity-poster { aspect-ratio: 420 / 263; }
.activity-summary, .detail-band, .detail-group { min-width: 0; }
.activity-poster { display: block; width: 100%; max-width: 100%; aspect-ratio: 420 / 263; height: auto; min-height: 0; max-height: none; padding: 0; overflow: hidden; border: 0; border-radius: 10px; background: var(--bg-soft); object-fit: cover; object-position: center; transform: none; }
.activity-poster-empty { display: grid; place-items: center; color: var(--text-muted); font-size: 15px; }
.activity-summary { display: flex; min-width: 0; max-width: 100%; flex-direction: column; align-items: flex-start; justify-content: center; }
.activity-badges { display: flex; flex-wrap: wrap; gap: 8px; }
.schedule-strip { display: flex; min-width: 0; max-width: 100%; align-items: stretch; gap: 22px; margin: 24px 0 18px; padding: 14px 0; overflow: hidden; }
.schedule-strip div:not(.schedule-divider) { display: grid; gap: 5px; }
.schedule-strip span, .detail-grid dt, .discussion-item span { color: var(--text-muted); font-size: 13px; }
.schedule-strip strong { max-width: 100%; overflow-wrap: anywhere; color: var(--text-main); font-size: 18px; font-variant-numeric: tabular-nums; }
.schedule-divider { width: 1px; background: var(--line); }
.activity-lead { display: -webkit-box; max-width: 760px; margin: 0; overflow: hidden; color: var(--text-muted); line-height: 1.8; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.activity-status-line { display: flex; align-items: center; gap: 8px; margin-top: 18px; color: var(--text-muted); }
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #8a94a6; }
.status-upcoming { background: #3d7cff; }.status-ongoing { background: #20a36a; }.status-ended { background: #8a94a6; }
.activity-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.detail-band { display: grid; min-width: 0; grid-template-columns: repeat(2, minmax(0, 1fr)); border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.detail-group { padding: 24px; }.detail-group + .detail-group { border-left: 1px solid var(--line); }
.detail-group h2, .activity-detail-section h2 { margin: 0; color: var(--text-main); font-size: 18px; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px 28px; margin: 20px 0 0; }
.detail-grid div { min-width: 0; }.detail-grid dt { margin-bottom: 6px; }.detail-grid dd { margin: 0; color: var(--text-main); line-height: 1.5; overflow-wrap: anywhere; }
.activity-detail-section { border-top: 1px solid var(--line); padding-top: 24px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 18px; }
.section-heading p { margin: 6px 0 0; color: var(--text-muted); }
.activity-content { width: 100%; max-width: 920px; overflow-wrap: anywhere; color: var(--text-main); line-height: 1.9; white-space: pre-wrap; }
.participation-section :deep(.el-table) { border: 1px solid var(--line); }
.privacy-note, .empty-copy { margin: 0; color: var(--text-muted); }
.discussion-composer { display: grid; gap: 12px; max-width: 800px; }
.discussion-composer .el-button { justify-self: start; }
.discussion-list { display: grid; gap: 14px; margin-top: 20px; }
.discussion-item { padding: 18px; border: 1px solid var(--line); border-radius: 6px; }
.discussion-item > div:first-child { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; }
.discussion-item strong { color: var(--text-main); }.discussion-item p { margin: 12px 0; color: var(--text-main); line-height: 1.75; white-space: pre-wrap; }.discussion-actions { display: flex; gap: 10px; }
@media (max-width: 900px) { .detail-band { grid-template-columns: 1fr; }.detail-group + .detail-group { border-top: 1px solid var(--line); border-left: 0; }.detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 900px) { .activity-overview { grid-template-columns: minmax(0, 36%) minmax(0, 1fr); gap: 24px; } }
@media (max-width: 640px) { .activity-overview { grid-template-columns: minmax(0, 1fr); }.activity-poster { aspect-ratio: 420 / 263; }.schedule-strip { gap: 12px; }.schedule-strip strong { font-size: 15px; }.detail-group { padding: 20px 0; }.detail-grid { grid-template-columns: minmax(0, 1fr); gap: 16px; }.section-heading, .discussion-item > div:first-child { align-items: flex-start; flex-direction: column; gap: 6px; } }
</style>
