<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import { ArrowRight, Bell, Calendar, Delete, Document, EditPen, Plus, Promotion, Refresh, Search, Setting, Upload, View } from '@element-plus/icons-vue'
import PageHero from '@/components/PageHero.vue'
import { noticeApi, uploadApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { noticeTargetOptions, noticeTypeOptions } from '@/config/options'
import { formatDateTime, formatFileSize, resolveAssetUrl } from '@/utils/format'
import { normalizePage } from '@/utils/page'

const authStore = useAuthStore()
const router = useRouter()
const loading = ref(false)
const submitLoading = ref(false)
const notices = ref([])
const total = ref(0)
const topNotices = ref([])
const editorVisible = ref(false)
const editingId = ref(null)
const coverInputRef = ref(null)
const attachmentInputRef = ref(null)
const coverUploading = ref(false)
const attachmentUploading = ref(false)

const query = reactive({ keyword: '', type: '', status: '', page: 1, size: 10 })
const editor = reactive({
  title: '', content: '', coverUrl: '', attachments: [], type: 'NORMAL', targetUser: 'ALL', publishTime: '', expireTime: '', isTop: false, showAdvanced: ''
})

const quillToolbar = [
  [{ header: [1, 2, 3, false] }],
  ['bold', 'italic', 'underline', 'strike'],
  [{ color: [] }, { background: [] }],
  [{ align: [] }],
  [{ list: 'ordered' }, { list: 'bullet' }],
  ['link'],
  ['clean']
]

const canManage = computed(() => authStore.isAdmin)
const editorTitle = computed(() => (editingId.value ? '编辑公告' : '发布新公告'))
const featuredNotice = computed(() => topNotices.value[0] || notices.value[0] || null)
const visibleNotices = computed(() => notices.value)

function excerpt(content, length = 150) {
  const text = String(content || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim()
  return text.length > length ? `${text.slice(0, length)}…` : text
}
function coverUrl(item) { return resolveAssetUrl(item?.attachmentUrl || item?.coverUrl) }
function formatNoticeDate(item) { return formatDateTime(item?.publishTime || item?.createdAt, 'YYYY.MM.DD') }
function resetEditor() {
  editingId.value = null
  Object.assign(editor, { title: '', content: '', coverUrl: '', attachments: [], type: 'NORMAL', targetUser: 'ALL', publishTime: '', expireTime: '', isTop: false, showAdvanced: '' })
}
function openNewEditor() { resetEditor(); editorVisible.value = true }
async function loadTop() { topNotices.value = await noticeApi.top() }
async function loadList() {
  loading.value = true
  try {
    const params = { ...query, status: canManage.value ? query.status : 'PUBLISHED' }
    const pageData = normalizePage(await noticeApi.list(params))
    notices.value = pageData.list
    total.value = pageData.total
  } finally { loading.value = false }
}
function openDetail(id) { router.push({ name: 'notice-detail', params: { id } }) }
async function editNotice(row) {
  const source = await noticeApi.detail(row.id)
  editingId.value = row.id
  Object.assign(editor, {
    title: source.title || row.title || '', content: source.content || row.content || '', coverUrl: source.attachmentUrl || row.attachmentUrl || row.coverUrl || '',
    attachments: source.attachments || [], type: source.type || row.type || 'NORMAL', targetUser: source.targetUser || row.targetUser || 'ALL',
    publishTime: source.publishTime || row.publishTime || '', expireTime: source.expireTime || row.expireTime || '', isTop: Boolean(source.isTop ?? row.isTop), showAdvanced: ''
  })
  editorVisible.value = true
}
async function handleCoverFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  coverUploading.value = true
  try {
    editor.coverUrl = await uploadApi.image(file)
    ElMessage.success('封面上传成功')
  } finally { coverUploading.value = false }
}
async function handleAttachmentFiles(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  attachmentUploading.value = true
  try {
    for (const file of files) {
      const uploaded = await uploadApi.noticeAttachment(file)
      editor.attachments.push(uploaded)
    }
    ElMessage.success(`已添加 ${files.length} 个附件`)
  } finally { attachmentUploading.value = false }
}
async function removeEditorAttachment(item, index) {
  if (item.id && editingId.value) {
    await noticeApi.removeAttachment(editingId.value, item.id)
  }
  editor.attachments.splice(index, 1)
}
async function saveNotice(shouldPublish = false) {
  const plainContent = String(editor.content || '').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim()
  if (!editor.title.trim() || !plainContent) { ElMessage.warning('请先填写公告标题和正文'); return }
  submitLoading.value = true
  const payload = {
    title: editor.title.trim(), content: editor.content, attachmentUrl: editor.coverUrl.trim() || null, type: editor.type, targetUser: editor.targetUser,
    publishTime: editor.publishTime || null, expireTime: editor.expireTime || null, isTop: editor.isTop
  }
  try {
    let noticeId = editingId.value
    if (noticeId) { await noticeApi.update(noticeId, payload); ElMessage.success('公告已保存') }
    else { noticeId = await noticeApi.create(payload); ElMessage.success('公告草稿已保存') }
    const newAttachments = editor.attachments.filter(item => !item.id)
    for (const attachment of newAttachments) {
      await noticeApi.addAttachment(noticeId, attachment)
    }
    if (shouldPublish && noticeId) { await noticeApi.publish(noticeId); ElMessage.success('公告已发布，老师和学生现在可以查看') }
    editorVisible.value = false
    resetEditor()
    await Promise.all([loadList(), loadTop()])
  } finally { submitLoading.value = false }
}
async function removeNotice(id) {
  await ElMessageBox.confirm('删除后不可恢复，确认继续吗？', '删除公告', { type: 'warning' })
  await noticeApi.remove(id); ElMessage.success('公告已删除'); await Promise.all([loadList(), loadTop()])
}
async function changeStatus(row, action) {
  if (action === 'publish') { await noticeApi.publish(row.id); ElMessage.success('公告已发布') }
  else { await noticeApi.withdraw(row.id); ElMessage.success('公告已撤回') }
  await Promise.all([loadList(), loadTop()])
}
async function toggleTop(row) {
  await noticeApi.pin(row.id, !row.isTop)
  ElMessage.success(row.isTop ? '已取消重点展示' : '已设为重点公告')
  await Promise.all([loadList(), loadTop()])
}
async function handleAdminCommand(command, row) {
  if (command === 'edit') await editNotice(row)
  if (command === 'publish') await changeStatus(row, 'publish')
  if (command === 'withdraw') await changeStatus(row, 'withdraw')
  if (command === 'top') await toggleTop(row)
  if (command === 'delete') await removeNotice(row.id)
}
async function searchNotices() { query.page = 1; await loadList() }
onMounted(() => { Promise.all([loadList(), loadTop()]) })
</script>

<template>
  <div class="page-stack notice-page">
    <PageHero title="通知公告" description="了解最新教学安排、平台动态与重要提醒。">
      <template #actions>
        <el-button :icon="Refresh" @click="loadList" :loading="loading">刷新</el-button>
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openNewEditor">发布公告</el-button>
      </template>
    </PageHero>

    <section class="notice-board surface-card">
      <article v-if="featuredNotice" class="notice-featured-row" :class="{ 'no-cover': !coverUrl(featuredNotice) }" @click="openDetail(featuredNotice.id)">
        <div v-if="coverUrl(featuredNotice)" class="notice-cover-wrap"><img class="notice-cover" :src="coverUrl(featuredNotice)" :alt="featuredNotice.title" /></div>
        <div class="notice-featured-copy">
          <div class="notice-featured-label">{{ featuredNotice.isTop ? '重点公告' : '最新公告' }}</div>
          <h2>{{ featuredNotice.title }}</h2>
          <p>{{ excerpt(featuredNotice.content) }}</p>
          <div class="notice-featured-meta"><span><Calendar /> {{ formatNoticeDate(featuredNotice) }}</span><span v-if="featuredNotice.viewCount !== null && featuredNotice.viewCount !== undefined"><View /> {{ featuredNotice.viewCount || 0 }} 次</span><span class="notice-featured-more">查看详情 <ArrowRight /></span><el-dropdown v-if="canManage" trigger="click" @command="handleAdminCommand($event, featuredNotice)" @click.stop><button class="notice-featured-menu" type="button" aria-label="公告管理">管理</button><template #dropdown><el-dropdown-menu><el-dropdown-item command="edit" :icon="EditPen">编辑</el-dropdown-item><el-dropdown-item v-if="featuredNotice.status !== 'PUBLISHED'" command="publish" :icon="Promotion">发布</el-dropdown-item><el-dropdown-item v-else command="withdraw">撤回</el-dropdown-item><el-dropdown-item command="top" :icon="Setting">{{ featuredNotice.isTop ? '取消重点' : '设为重点' }}</el-dropdown-item><el-dropdown-item command="delete" :icon="Delete" divided>删除公告</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
        </div>
      </article>

      <div class="notice-list-head">
        <div><span class="notice-list-kicker">LATEST UPDATES</span><h2>公告列表</h2></div>
        <div class="notice-list-tools">
          <el-input v-model="query.keyword" class="notice-search" placeholder="搜索公告标题" clearable :prefix-icon="Search" @keyup.enter="searchNotices" @clear="searchNotices" />
          <el-button type="primary" :icon="Search" @click="searchNotices">搜索</el-button>
        </div>
      </div>

      <div v-loading="loading" class="notice-list">
        <article v-for="item in visibleNotices" :key="item.id" class="notice-list-row" @click="openDetail(item.id)">
          <h3>{{ item.title }}</h3>
          <span class="notice-row-date">{{ formatNoticeDate(item) }}</span>
          <el-dropdown v-if="canManage" trigger="click" @command="handleAdminCommand($event, item)" @click.stop>
            <button class="notice-row-menu" type="button" aria-label="公告管理">管理</button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item command="edit" :icon="EditPen">编辑</el-dropdown-item>
              <el-dropdown-item v-if="item.status !== 'PUBLISHED'" command="publish" :icon="Promotion">发布</el-dropdown-item>
              <el-dropdown-item v-else command="withdraw">撤回</el-dropdown-item>
              <el-dropdown-item command="top" :icon="Setting">{{ item.isTop ? '取消重点' : '设为重点' }}</el-dropdown-item>
              <el-dropdown-item command="delete" :icon="Delete" divided>删除</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
          <ArrowRight v-else class="notice-row-arrow" />
        </article>
      </div>

      <div v-if="!loading && !visibleNotices.length" class="notice-empty-state"><div class="notice-empty-icon"><Bell /></div><h3>{{ query.keyword.trim() ? '没有匹配的公告' : '暂时没有公告' }}</h3><p>{{ query.keyword.trim() ? '换个关键词试试，或清空搜索条件。' : '有新的教学安排时，我们会第一时间在这里通知你。' }}</p><el-button v-if="canManage && !query.keyword.trim()" type="primary" :icon="Plus" @click="openNewEditor">发布第一条公告</el-button></div>
      <div class="notice-pagination"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" layout="total, prev, pager, next" :total="total" @current-change="loadList" /></div>
    </section>

    <el-dialog v-model="editorVisible" :title="editorTitle" width="760px" class="notice-editor-dialog">
      <div class="notice-editor-tip"><Bell /> 公告发布后，老师和学生会在公告中心看到它。</div>
      <el-form label-position="top" class="notice-editor-form">
        <el-form-item label="公告标题"><el-input v-model="editor.title" maxlength="200" show-word-limit placeholder="例如：2026 春季学期第一次教研活动安排" /></el-form-item>
        <el-form-item label="公告内容">
          <div class="notice-rich-editor">
            <QuillEditor v-model:content="editor.content" content-type="html" theme="snow" :toolbar="quillToolbar" placeholder="把最重要的事情写在开头，分段说明时间、地点和需要完成的动作。" />
          </div>
          <div class="notice-field-help">可以设置字体颜色、背景色、加粗、标题和列表，用颜色标注重点内容。</div>
        </el-form-item>
        <el-form-item label="公告封面（可选）">
          <div class="notice-cover-picker">
            <div v-if="editor.coverUrl" class="notice-cover-preview"><img :src="resolveAssetUrl(editor.coverUrl)" alt="公告封面预览" /><el-button link type="danger" @click="editor.coverUrl = ''">移除封面</el-button></div>
            <div v-else class="notice-cover-placeholder">未选择封面，公告将不显示封面</div>
            <input ref="coverInputRef" class="notice-file-input" type="file" accept="image/jpeg,image/png,image/gif,image/webp" @change="handleCoverFile" />
            <el-button plain :icon="Upload" :loading="coverUploading" @click="coverInputRef?.click()">从本地选择封面</el-button>
            <span class="notice-field-help">支持 JPG、PNG、GIF、WebP，建议尺寸不小于 800×300。</span>
          </div>
        </el-form-item>
        <el-form-item label="公告附件（可选）">
          <div class="notice-attachment-picker">
            <input ref="attachmentInputRef" class="notice-file-input" type="file" multiple accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar,.jpg,.jpeg,.png,.gif,.webp" @change="handleAttachmentFiles" />
            <el-button plain :icon="Document" :loading="attachmentUploading" @click="attachmentInputRef?.click()">从本地选择附件</el-button>
            <span class="notice-field-help">支持 PDF、Word、Excel、PPT、压缩包和常见图片，单个文件不超过 100MB。</span>
            <div v-if="editor.attachments.length" class="notice-attachment-list">
              <div v-for="(attachment, index) in editor.attachments" :key="attachment.id || `${attachment.fileUrl}-${index}`" class="notice-attachment-item"><Document /><span>{{ attachment.fileName }}</span><small>{{ formatFileSize(attachment.fileSize) }}</small><el-button link type="danger" @click="removeEditorAttachment(attachment, index)">移除</el-button></div>
            </div>
          </div>
        </el-form-item>
        <el-collapse v-model="editor.showAdvanced" class="notice-advanced"><el-collapse-item name="advanced" title="更多发布设置（可选）"><div class="two-column">
          <el-form-item label="公告类型"><el-select v-model="editor.type" style="width: 100%;"><el-option v-for="item in noticeTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="可见范围"><el-select v-model="editor.targetUser" style="width: 100%;"><el-option v-for="item in noticeTargetOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="计划发布时间"><el-date-picker v-model="editor.publishTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="立即发布" style="width: 100%;" /></el-form-item>
          <el-form-item label="结束展示时间"><el-date-picker v-model="editor.expireTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="长期展示" style="width: 100%;" /></el-form-item>
          <div class="notice-switch-field"><span>展示在重点区域</span><el-switch v-model="editor.isTop" /></div>
        </div></el-collapse-item></el-collapse>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button @click="saveNotice(false)" :loading="submitLoading">保存草稿</el-button><el-button type="primary" :icon="Promotion" :loading="submitLoading" @click="saveNotice(true)">保存并发布</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.notice-page { gap: 18px; }
.notice-board { padding: 34px 36px 24px; background: rgba(255,255,255,.82); }
.notice-featured-row { display: grid; grid-template-columns: 264px minmax(0, 1fr); gap: 30px; align-items: stretch; padding-bottom: 30px; border-bottom: 1px solid rgba(91,120,177,.16); cursor: pointer; }
.notice-featured-row.no-cover { grid-template-columns: minmax(0, 1fr); }
.notice-cover-wrap { overflow: hidden; border-radius: 16px; background: #edf2fb; }
.notice-cover { display: block; flex: 0 0 264px; width: 264px; height: 166px; object-fit: cover; }
.notice-featured-copy { min-width: 0; display: flex; flex-direction: column; padding: 3px 0; }
.notice-featured-label, .notice-list-kicker { color: #7085ad; font-size: 11px; font-weight: 800; letter-spacing: .16em; }
.notice-featured-label::before { display: inline-block; width: 7px; height: 7px; margin: 0 8px 1px 0; border-radius: 50%; background: #eea22d; content: ''; }
.notice-featured-copy h2 { display: -webkit-box; overflow: hidden; margin: 12px 0 9px; color: #263348; font-size: 25px; line-height: 1.42; letter-spacing: -.025em; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.notice-featured-copy p { display: -webkit-box; overflow: hidden; max-width: 780px; margin: 0; color: #758196; font-size: 15px; line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.notice-featured-meta { display: flex; align-items: center; gap: 22px; margin-top: auto; padding-top: 15px; color: #a0aabd; font-size: 13px; }
.notice-featured-meta span { display: inline-flex; align-items: center; gap: 6px; }
.notice-featured-meta svg { width: 15px; }
.notice-featured-more { margin-left: auto; color: #526fae; font-weight: 700; }
.notice-featured-more svg { width: 14px; }
.notice-featured-menu { display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-width: 54px; height: 28px; padding: 0 9px; border: 1px solid #dfe8f6; border-radius: 8px; color: #6d83aa; background: #fbfcff; cursor: pointer; font-size: 12px; }
.notice-featured-menu:hover { color: #4f78cd; background: #edf4ff; }
.notice-list-head { display: flex; align-items: end; justify-content: space-between; gap: 18px; padding: 28px 5px 12px; }
.notice-list-head h2 { margin: 7px 0 0; color: #34435a; font-size: 21px; }
.notice-list-tools { display: flex; align-items: center; gap: 9px; }
.notice-search { width: 230px; }
.notice-list { min-height: 110px; }
.notice-list-row { display: grid; grid-template-columns: minmax(0, 1fr) 110px 60px; align-items: center; gap: 18px; padding: 17px 5px; border-bottom: 1px solid rgba(91,120,177,.12); cursor: pointer; transition: background .16s ease, padding .16s ease; }
.notice-list-row:hover { padding-left: 11px; padding-right: 0; background: rgba(239,245,255,.72); }
.notice-list-row h3 { overflow: hidden; margin: 0; color: #3d4b60; font-size: 17px; font-weight: 500; line-height: 1.5; text-overflow: ellipsis; white-space: nowrap; }
.notice-row-date { color: #a0aabd; font-size: 14px; text-align: right; }
.notice-row-arrow { width: 15px; color: #aab5c8; }
.notice-row-menu { display: inline-flex; align-items: center; justify-content: center; gap: 4px; width: 58px; height: 28px; padding: 0 8px; border: 1px solid #dfe8f6; border-radius: 8px; color: #6d83aa; background: #fbfcff; cursor: pointer; font-size: 12px; }
.notice-row-menu:hover { color: #4f78cd; background: #edf4ff; }
.notice-pagination { display: flex; justify-content: flex-end; margin-top: 20px; }
.notice-empty-state { padding: 54px 20px 42px; text-align: center; }
.notice-empty-icon { display: grid; place-items: center; width: 58px; height: 58px; margin: 0 auto; border-radius: 20px; color: #6293ed; background: #edf4ff; }
.notice-empty-icon svg { width: 25px; }
.notice-empty-state h3 { margin: 16px 0 6px; color: #2f4c80; }
.notice-empty-state p { margin: 0 0 18px; color: #91a3c6; font-size: 13px; }
.notice-rich-editor { overflow: hidden; width: 100%; border: 1px solid #dfe7f2; border-radius: 10px; background: #fff; }
.notice-rich-editor :deep(.ql-toolbar) { border: 0; border-bottom: 1px solid #e8edf4; background: #fbfcff; }
.notice-rich-editor :deep(.ql-container) { min-height: 245px; border: 0; color: #34445c; font-size: 15px; }
.notice-rich-editor :deep(.ql-editor) { min-height: 245px; line-height: 1.8; }
.notice-field-help { margin-top: 7px; color: #98a6ba; font-size: 12px; line-height: 1.5; }
.notice-cover-picker, .notice-attachment-picker { display: grid; gap: 10px; width: 100%; }
.notice-file-input { display: none; }
.notice-cover-preview { display: flex; align-items: flex-end; gap: 12px; flex-wrap: wrap; }
.notice-cover-preview img { width: 264px; height: 130px; object-fit: cover; border-radius: 10px; border: 1px solid #e2e9f4; }
.notice-cover-placeholder { padding: 18px; color: #9aa7b8; border: 1px dashed #cfd9e8; border-radius: 10px; background: #fbfcfe; }
.notice-attachment-list { display: grid; gap: 8px; padding: 10px; border: 1px solid #e5ebf4; border-radius: 10px; background: #fbfcfe; }
.notice-attachment-item { display: flex; align-items: center; gap: 8px; min-width: 0; padding: 7px 8px; color: #53647c; font-size: 13px; }
.notice-attachment-item > svg { flex: 0 0 auto; width: 16px; color: #6c8fd4; }
.notice-attachment-item span { overflow: hidden; min-width: 0; text-overflow: ellipsis; white-space: nowrap; }
.notice-attachment-item small { margin-left: auto; color: #9aa7b8; white-space: nowrap; }
.notice-editor-tip { display: flex; align-items: center; gap: 8px; margin: -4px 0 20px; padding: 12px 14px; border-radius: 12px; color: #5274b3; background: #eef5ff; font-size: 12px; }
.notice-editor-tip svg { width: 15px; color: #5f92f0; }
.notice-editor-form .el-form-item { margin-bottom: 20px; }
.notice-advanced { border-top: 1px solid rgba(90,126,199,.12); border-bottom: none; }
.notice-advanced :deep(.el-collapse-item__header) { color: #6d84b4; font-size: 13px; }
.notice-advanced :deep(.el-collapse-item__wrap) { background: transparent; border-bottom: none; }
.notice-switch-field { display: flex; align-items: center; justify-content: space-between; padding-top: 30px; color: #6b83b3; font-size: 13px; }
@media (max-width: 720px) { .notice-board { padding: 20px 18px 18px; } .notice-featured-row { grid-template-columns: 1fr; gap: 18px; } .notice-cover, .notice-cover-wrap { width: 100%; height: 190px; } .notice-cover { flex-basis: auto; } .notice-featured-copy h2 { font-size: 21px; } .notice-featured-meta { gap: 12px; flex-wrap: wrap; } .notice-featured-more { margin-left: 0; } .notice-list-head { align-items: flex-start; flex-direction: column; padding-top: 22px; } .notice-list-tools, .notice-search { width: 100%; } .notice-list-tools .el-button { flex: 0 0 auto; } .notice-list-row { grid-template-columns: minmax(0, 1fr) 82px 58px; gap: 8px; padding: 15px 3px; } .notice-list-row h3 { font-size: 15px; } .notice-row-date { font-size: 12px; } }
</style>
