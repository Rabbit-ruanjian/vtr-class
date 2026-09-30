<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, VideoCamera } from '@element-plus/icons-vue'
import { coursewareApi, uploadApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  courseId: { type: [Number, String], required: true },
  courseName: { type: String, default: '' },
  chapter: { type: String, default: '' },
  sectionId: { type: [Number, String], default: null }
})

const authStore = useAuthStore()
const loading = ref(false)
const videos = ref([])
const selectedVideo = ref(null)
const videoUrl = ref('')
const player = ref(null)
const uploadVisible = ref(false)
const saving = ref(false)
const uploading = ref(false)
const editingId = ref(null)
const versionFor = ref(null)
const selectedStats = ref(null)
const progress = ref({ watchedSeconds: 0, durationSeconds: 0, completed: false })
const lastSavedSecond = ref(0)

const form = reactive({
  title: '',
  description: '',
  knowledgePoint: '',
  videoType: 'CORE',
  featured: false,
  visibility: 'COURSE',
  targetAudience: 'ALL',
  fileUrl: '',
  fileName: '',
  fileType: '',
  fileSize: null
})

const isStudent = computed(() => authStore.isStudent)
const isAdmin = computed(() => authStore.isAdmin)
const isVideoTeacher = computed(() => authStore.role === 'TEACHER')
const canUpload = computed(() => isVideoTeacher.value)
const activeVideos = computed(() => videos.value.filter((video) => video.status === 'ACTIVE'))
const featuredVideo = computed(() => activeVideos.value.find((video) => video.featured) || activeVideos.value[0] || null)

const videoTypes = [
  { value: 'CORE', label: '核心精讲' },
  { value: 'EXERCISE', label: '例题讲解' },
  { value: 'LAB', label: '实验操作' },
  { value: 'EXTENSION', label: '拓展提升' }
]

function videoTypeLabel(value) {
  return videoTypes.find((item) => item.value === value)?.label || '教学视频'
}

function statusInfo(status) {
  return {
    ACTIVE: { label: '已发布', type: 'success' },
    PENDING: { label: '待审核', type: 'warning' },
    REJECTED: { label: '已驳回', type: 'danger' },
    ARCHIVED: { label: '已归档', type: 'info' }
  }[status] || { label: status || '未知状态', type: 'info' }
}

function disposeVideoUrl() {
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
  videoUrl.value = ''
}

async function loadVideos() {
  if (!props.courseId || !props.chapter) return
  loading.value = true
  try {
    videos.value = await coursewareApi.videos({ courseId: props.courseId, chapter: props.chapter, ...(props.sectionId ? { sectionId: props.sectionId } : {}) })
    const next = videos.value.find((video) => video.id === selectedVideo.value?.id)
      || featuredVideo.value
      || videos.value[0]
    if (next) await selectVideo(next)
    else {
      selectedVideo.value = null
      selectedStats.value = null
      disposeVideoUrl()
    }
  } catch (error) {
    videos.value = []
    selectedVideo.value = null
    ElMessage.error(error?.message || '教学视频加载失败')
  } finally {
    loading.value = false
  }
}

async function selectVideo(video) {
  if (!video) return
  if (selectedVideo.value?.id === video.id && videoUrl.value) {
    // 刷新列表后保留正在播放的视频对象，及时显示新的 AI 索引状态。
    selectedVideo.value = video
    return
  }
  disposeVideoUrl()
  selectedVideo.value = video
  selectedStats.value = null
  progress.value = { watchedSeconds: 0, durationSeconds: 0, completed: false }
  lastSavedSecond.value = 0
  try {
    const blob = await coursewareApi.preview(video.id)
    videoUrl.value = URL.createObjectURL(blob)
    if (isStudent.value) {
      const payload = await coursewareApi.myVideoProgress(video.id)
      progress.value = { ...progress.value, ...payload }
    } else if (isVideoTeacher.value || isAdmin.value) {
      selectedStats.value = await coursewareApi.videoStatistics(video.id)
    }
  } catch (error) {
    ElMessage.error(error?.message || '视频加载失败')
  }
}

function resetForm() {
  editingId.value = null
  versionFor.value = null
  Object.assign(form, {
    title: '', description: '', knowledgePoint: '', videoType: 'CORE', featured: false,
    visibility: 'COURSE', targetAudience: 'ALL', fileUrl: '', fileName: '', fileType: '', fileSize: null
  })
}

function openCreate() {
  resetForm()
  uploadVisible.value = true
}

function openEdit(video) {
  editingId.value = video.id
  versionFor.value = null
  Object.assign(form, {
    title: video.title, description: video.description || '', knowledgePoint: video.knowledgePoint || '',
    videoType: video.videoType || 'CORE', featured: Boolean(video.featured), visibility: video.visibility || 'COURSE',
    targetAudience: video.targetAudience || 'ALL', fileUrl: video.fileUrl, fileName: video.fileName,
    fileType: video.fileType, fileSize: video.fileSize
  })
  uploadVisible.value = true
}

function openNewVersion(video) {
  openEdit(video)
  editingId.value = null
  versionFor.value = video
  form.fileUrl = ''
  form.fileName = ''
  form.fileType = ''
  form.fileSize = null
}

async function uploadVideo(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!/\.(mp4|webm|ogg|mov|m4v)$/i.test(file.name)) {
    ElMessage.warning('仅支持 mp4、webm、ogg、mov、m4v 格式的视频')
    event.target.value = ''
    return
  }
  uploading.value = true
  try {
    form.fileUrl = await uploadApi.courseware(file)
    form.fileName = file.name
    form.fileType = file.name.split('.').pop().toLowerCase()
    form.fileSize = file.size
    ElMessage.success('视频文件已上传')
  } catch (error) {
    ElMessage.error(error?.message || '视频上传失败')
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

async function uploadTranscript(event) {
  const file = event.target.files?.[0]
  if (!file || !selectedVideo.value) return
  if (!/\.(srt|vtt|txt)$/i.test(file.name)) {
    ElMessage.warning('字幕/转写文件仅支持 SRT、VTT、TXT 格式')
    event.target.value = ''
    return
  }
  uploading.value = true
  try {
    await coursewareApi.uploadVideoTranscript(selectedVideo.value.id, file)
    ElMessage.success('字幕已上传，视频内容正在建立 AI 检索索引')
    await loadVideos()
  } catch (error) {
    ElMessage.error(error?.message || '字幕上传失败')
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

function formPayload() {
  return {
    title: form.title.trim(),
    description: form.description.trim(),
    knowledgePoint: form.knowledgePoint.trim(),
    videoType: form.videoType,
    featured: form.featured,
    visibility: form.visibility,
    targetAudience: form.targetAudience,
    resourceType: 'teaching-video',
    courseId: Number(props.courseId),
    chapter: props.chapter,
    sectionId: props.sectionId ? Number(props.sectionId) : null,
    fileUrl: form.fileUrl,
    fileName: form.fileName,
    fileType: form.fileType,
    fileSize: form.fileSize
  }
}

async function saveVideo() {
  if (!form.title.trim()) return ElMessage.warning('请填写视频标题')
  if (!editingId.value && !form.fileUrl) return ElMessage.warning('请先上传视频文件')
  saving.value = true
  try {
    const payload = formPayload()
    const publicRelease = form.visibility === 'PUBLIC'
    if (versionFor.value) {
      await coursewareApi.createVideoVersion(versionFor.value.id, payload)
      ElMessage.success(publicRelease ? '新版本已提交全校公开审核，旧版本已归档' : '新版本已保存并直接发布；高风险内容会进入复核，旧版本已归档')
    } else if (editingId.value) {
      await coursewareApi.update(editingId.value, payload)
      ElMessage.success(publicRelease ? '视频更新已提交全校公开审核' : '视频已更新并直接发布；高风险内容会进入复核')
    } else {
      await coursewareApi.create(payload)
      ElMessage.success(publicRelease ? '视频已提交全校公开审核' : '视频已直接发布；高风险内容会进入复核')
    }
    uploadVisible.value = false
    await loadVideos()
  } catch (error) {
    ElMessage.error(error?.message || '视频保存失败')
  } finally {
    saving.value = false
  }
}

async function removeVideo(video) {
  try {
    await ElMessageBox.confirm('删除后学生将无法继续学习该视频。', '删除教学视频', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    await coursewareApi.remove(video.id)
    ElMessage.success('视频已删除')
    await loadVideos()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除失败')
  }
}

async function reviewVideo(video, action) {
  try {
    const { value } = await ElMessageBox.prompt(
      action === 'APPROVE' ? '可选填写审核备注。' : '请填写驳回原因，教师将据此修改。',
      action === 'APPROVE' ? '审核通过' : '驳回视频',
      {
        inputType: 'textarea',
        inputPlaceholder: action === 'APPROVE' ? '审核备注（可选）' : '请输入驳回原因',
        inputValidator: (text) => action === 'REJECT' && !String(text || '').trim() ? '驳回原因不能为空' : true,
        confirmButtonText: '确认', cancelButtonText: '取消'
      }
    )
    await coursewareApi.reviewVideo(video.id, { action, remark: value || '' })
    ElMessage.success(action === 'APPROVE' ? '视频已发布给学生' : '视频已驳回')
    await loadVideos()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '审核操作失败')
  }
}

async function archiveVideo(video) {
  try {
    await ElMessageBox.confirm('下架后学生将无法继续学习该视频，但管理员和教师仍可查看归档版本。', '下架教学视频', {
      type: 'warning', confirmButtonText: '确认下架', cancelButtonText: '取消'
    })
    await coursewareApi.archiveVideo(video.id)
    ElMessage.success('视频已归档下架')
    await loadVideos()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '下架失败')
  }
}

async function withdrawVideo(video) {
  try {
    await ElMessageBox.confirm('撤回后学生将无法继续观看，但视频文件和已有学习记录会保留在归档中。', '撤回并归档教学视频', {
      type: 'warning', confirmButtonText: '确认撤回', cancelButtonText: '取消'
    })
    await coursewareApi.withdrawVideo(video.id)
    ElMessage.success('视频已撤回并归档')
    await loadVideos()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '撤回失败')
  }
}

async function saveProgress(force = false) {
  if (!isStudent.value || !selectedVideo.value || !player.value?.duration) return
  const watchedSeconds = Math.floor(player.value.currentTime)
  if (!force && watchedSeconds - lastSavedSecond.value < 10) return
  lastSavedSecond.value = watchedSeconds
  try {
    await coursewareApi.saveVideoProgress(selectedVideo.value.id, {
      watchedSeconds,
      durationSeconds: Math.ceil(player.value.duration)
    })
    progress.value = {
      watchedSeconds,
      durationSeconds: Math.ceil(player.value.duration),
      completed: watchedSeconds >= player.value.duration * 0.9
    }
  } catch {
    // Progress recording must not interrupt video playback.
  }
}

function resumeVideo() {
  if (isStudent.value && progress.value.watchedSeconds > 0 && player.value) {
    player.value.currentTime = progress.value.watchedSeconds
  }
}

function canEdit(video) {
  return isVideoTeacher.value && Number(video.teacherId) === Number(authStore.user?.id)
}

watch(() => [props.courseId, props.chapter], loadVideos, { immediate: true })
onBeforeUnmount(disposeVideoUrl)
</script>

<template>
  <section class="video-workspace" aria-label="章节教学视频">
    <header class="video-workspace-header">
      <div class="video-workspace-actions">
        <el-button plain :loading="loading" @click="loadVideos">刷新</el-button>
        <el-button v-if="canUpload" type="primary" @click="openCreate"><UploadFilled />上传视频</el-button>
      </div>
    </header>

    <div v-if="selectedVideo" class="video-stage">
      <div class="video-player-shell">
        <video
          v-if="videoUrl"
          ref="player"
          :src="videoUrl"
          controls
          controlslist="nodownload"
          preload="metadata"
          @loadedmetadata="resumeVideo"
          @timeupdate="saveProgress(false)"
          @pause="saveProgress(true)"
          @ended="saveProgress(true)"
        />
        <div v-else class="video-player-loading"><VideoCamera /><span>正在加载视频</span></div>
      </div>
      <article class="video-current-info">
        <div class="video-current-title">
          <div>
            <div class="video-tags">
              <el-tag size="small" :type="statusInfo(selectedVideo.status).type">{{ statusInfo(selectedVideo.status).label }}</el-tag>
              <el-tag size="small" effect="plain">{{ videoTypeLabel(selectedVideo.videoType) }}</el-tag>
              <el-tag v-if="selectedVideo.featured" size="small" type="warning" effect="plain">重点推荐</el-tag>
            </div>
            <h4>{{ selectedVideo.title }}</h4>
          </div>
          <div v-if="selectedStats" class="video-learning-stat">
            <strong>{{ selectedStats.completionRate }}%</strong>
            <span>{{ selectedStats.completedCount || 0 }}/{{ selectedStats.learnerCount || 0 }} 人完成</span>
          </div>
        </div>
        <p>{{ selectedVideo.description || '教师暂未补充视频简介。' }}</p>
        <dl>
          <div><dt>上传教师</dt><dd>{{ selectedVideo.teacherName || '课程教师' }}</dd></div>
          <div v-if="isStudent"><dt>学习进度</dt><dd>{{ progress.completed ? '已完成学习' : `${progress.watchedSeconds || 0} 秒` }}</dd></div>
        </dl>
        <div v-if="canEdit(selectedVideo) || isAdmin" class="video-current-actions">
          <template v-if="canEdit(selectedVideo)">
            <label class="video-transcript-button">
              <UploadFilled />上传字幕/转写
              <input type="file" accept=".srt,.vtt,.txt,text/plain" :disabled="uploading" @change="uploadTranscript" />
            </label>
            <el-button v-if="selectedVideo.status === 'PENDING' || selectedVideo.status === 'REJECTED'" plain @click="openEdit(selectedVideo)">编辑</el-button>
            <el-button v-if="selectedVideo.status === 'PENDING' || selectedVideo.status === 'REJECTED'" type="danger" plain @click="removeVideo(selectedVideo)">删除</el-button>
            <el-button v-if="selectedVideo.status === 'ACTIVE' || selectedVideo.status === 'ARCHIVED'" plain @click="openNewVersion(selectedVideo)">上传新版本</el-button>
            <el-button v-if="selectedVideo.status === 'ACTIVE'" type="warning" plain @click="withdrawVideo(selectedVideo)">撤回归档</el-button>
          </template>
          <template v-else-if="isAdmin">
            <el-button v-if="selectedVideo.visibility === 'PUBLIC' && (selectedVideo.status === 'PENDING' || selectedVideo.status === 'REJECTED')" type="success" plain @click="reviewVideo(selectedVideo, 'APPROVE')">审核通过</el-button>
            <el-button v-if="selectedVideo.visibility === 'PUBLIC' && selectedVideo.status === 'PENDING'" type="danger" plain @click="reviewVideo(selectedVideo, 'REJECT')">驳回</el-button>
            <el-button v-if="selectedVideo.visibility === 'PUBLIC' && selectedVideo.status === 'ACTIVE'" type="warning" plain @click="archiveVideo(selectedVideo)">下架归档</el-button>
          </template>
        </div>
        <p v-if="selectedVideo.aiIndexStatus && selectedVideo.aiIndexStatus !== 'READY'" class="video-ai-index-note">
          AI 检索：{{ selectedVideo.aiIndexMessage || '视频内容索引处理中' }}
        </p>
        <p v-if="selectedVideo.status === 'REJECTED' && selectedVideo.auditRemark" class="video-audit-note">驳回原因：{{ selectedVideo.auditRemark }}</p>
      </article>
    </div>

    <div v-else-if="!loading" class="video-empty">
      <VideoCamera />
      <strong>{{ isStudent ? '本章节暂无可学习的视频' : '本章节暂无教学视频' }}</strong>
    </div>

    <el-dialog v-model="uploadVisible" :title="versionFor ? '上传视频新版本' : editingId ? '编辑教学视频' : '上传教学视频'" width="min(760px, 94vw)">
      <el-form label-position="top">
        <div class="video-form-grid">
          <el-form-item label="视频标题" required><el-input v-model="form.title" maxlength="200" show-word-limit /></el-form-item>
          <el-form-item label="视频类型"><el-select v-model="form.videoType"><el-option v-for="item in videoTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="对应知识点"><el-input v-model="form.knowledgePoint" placeholder="例如：KMP 算法、二叉树遍历" /></el-form-item>
          <el-form-item label="发布范围"><el-select v-model="form.visibility"><el-option label="本课程可见（直接发布）" value="COURSE" /><el-option label="全校公开（需要审核）" value="PUBLIC" /><el-option label="仅教师可见" value="PRIVATE" /></el-select></el-form-item>
        </div>
        <el-form-item label="视频简介"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <div class="video-upload-row">
          <div>
            <strong>{{ versionFor ? '新版本视频文件' : '视频文件' }}</strong>
            <span>{{ form.fileName || '支持 mp4、webm、ogg、mov、m4v 格式' }}</span>
          </div>
          <label class="video-file-button"><UploadFilled />{{ uploading ? '上传中' : versionFor || !editingId ? '选择视频' : '替换文件' }}<input type="file" accept="video/mp4,video/webm,video/ogg,video/quicktime,.m4v" :disabled="uploading" @change="uploadVideo" /></label>
        </div>
        <el-checkbox v-model="form.featured">设为本章节重点推荐视频</el-checkbox>
      </el-form>
      <template #footer><el-button @click="uploadVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveVideo">提交</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.video-workspace { display: flex; flex-direction: column; gap: 18px; }
.video-workspace-header { display: flex; justify-content: flex-end; padding: 4px 0; }.video-workspace-actions { display: flex; align-items: center; gap: 8px; }.video-workspace-actions :deep(svg), .video-file-button :deep(svg) { width: 16px; height: 16px; }
.video-stage { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(260px, .55fr); min-height: 410px; border: 1px solid #dfe6ef; border-radius: 8px; overflow: hidden; background: #fff; }
.video-player-shell { display: grid; min-height: 410px; place-items: center; background: #182536; }.video-player-shell video { width: 100%; max-height: 560px; background: #000; }.video-player-loading { display: flex; align-items: center; gap: 10px; color: #d8e4f2; }.video-player-loading :deep(svg) { width: 26px; height: 26px; color: #78b3ef; }
.video-current-info { display: flex; flex-direction: column; padding: 24px; background: #fff; }.video-current-title { display: flex; justify-content: space-between; gap: 12px; }.video-tags { display: flex; flex-wrap: wrap; gap: 6px; }.video-current-info h4 { margin: 12px 0 0; color: #263548; font-size: 18px; line-height: 1.45; }.video-current-info > p { color: #68778c; font-size: 13px; line-height: 1.7; }.video-current-info dl { display: grid; gap: 12px; margin: auto 0 0; }.video-current-info dl div { padding-top: 10px; border-top: 1px solid #edf0f4; }.video-current-info dt { color: #8a96a6; font-size: 12px; }.video-current-info dd { margin: 4px 0 0; color: #34465b; font-size: 13px; }.video-current-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }.video-learning-stat { flex: 0 0 auto; text-align: right; }.video-learning-stat strong { display: block; color: #2d8b65; font-size: 20px; }.video-learning-stat span { color: #78869a; font-size: 11px; white-space: nowrap; }.video-audit-note { margin-top: 16px !important; padding: 9px 11px; background: #fff4f3; color: #c6574f !important; }
.video-playlist { display: grid; gap: 8px; }.video-item { display: grid; grid-template-columns: 116px minmax(0, 1fr) auto; align-items: center; gap: 14px; min-height: 96px; padding: 10px; border: 1px solid #e3e9f0; border-radius: 7px; background: #fff; cursor: pointer; transition: border-color 140ms ease, background 140ms ease; }.video-item:hover, .video-item.active { border-color: #9cc8ef; background: #f8fbff; }.video-item-preview { position: relative; display: grid; height: 74px; place-items: center; border: 0; border-radius: 5px; background: linear-gradient(135deg, #284d74, #6e98c8); color: #fff; cursor: pointer; }.video-item-preview :deep(svg) { width: 28px; height: 28px; }.video-item-preview span { position: absolute; right: 6px; top: 6px; padding: 2px 5px; background: #eea73e; color: #fff; font-size: 10px; }.video-item-main { min-width: 0; }.video-item-labels { display: flex; align-items: center; gap: 7px; color: #77869a; font-size: 12px; }.video-item-main strong { display: block; overflow: hidden; margin-top: 5px; color: #304157; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }.video-item-main p, .video-item-main small { display: block; overflow: hidden; margin: 5px 0 0; color: #7a8797; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.video-item-main small { color: #c65b55; }.video-item-actions { display: flex; align-items: center; gap: 2px; }.video-empty { display: flex; min-height: 360px; align-items: center; justify-content: center; flex-direction: column; gap: 8px; border: 1px dashed #cbd6e2; color: #718096; text-align: center; }.video-empty :deep(svg) { width: 38px; height: 38px; color: #91b7db; }.video-empty strong { color: #3d5067; }.video-empty span { max-width: 340px; font-size: 13px; line-height: 1.65; }.video-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }.video-form-grid :deep(.el-select) { width: 100%; }.video-upload-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 4px 0 20px; padding: 14px; border: 1px solid #dce5ef; background: #f8fbfe; }.video-upload-row div { min-width: 0; }.video-upload-row strong, .video-upload-row span { display: block; }.video-upload-row strong { color: #3a4e66; font-size: 13px; }.video-upload-row span { overflow: hidden; margin-top: 4px; color: #7d8a9b; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.video-file-button { display: inline-flex; align-items: center; gap: 6px; flex: 0 0 auto; padding: 8px 11px; border-radius: 5px; background: #e8f2fd; color: #2779c9; cursor: pointer; font-size: 13px; }.video-file-button input { display: none; }
@media (max-width: 900px) { .video-stage { grid-template-columns: 1fr; }.video-player-shell { min-height: 300px; }.video-current-info { min-height: 230px; }.video-item-actions { flex-wrap: wrap; justify-content: flex-end; } }
@media (max-width: 640px) { .video-workspace-header { align-items: flex-start; flex-direction: column; }.video-workspace-actions { align-self: stretch; }.video-workspace-actions :deep(.el-button) { flex: 1; }.video-item { grid-template-columns: 86px minmax(0, 1fr); }.video-item-preview { height: 64px; }.video-item-actions { grid-column: 2; justify-content: flex-start; }.video-form-grid { grid-template-columns: 1fr; }.video-upload-row { align-items: flex-start; flex-direction: column; }.video-current-title { align-items: flex-start; flex-direction: column; }.video-learning-stat { text-align: left; } }
/* AI transcript upload status */
.video-transcript-button { display: inline-flex; align-items: center; gap: 6px; padding: 8px 11px; border: 1px solid #cfe1f3; border-radius: 5px; background: #e8f2fd; color: #2779c9; cursor: pointer; font-size: 13px; }
.video-transcript-button input { display: none; }
.video-ai-index-note { margin: 16px 0 0 !important; padding: 9px 11px; border: 1px solid #dce9f7; border-radius: 5px; background: #f6faff; color: #4c7095 !important; font-size: 12px !important; }
</style>
