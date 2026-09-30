<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHero from '@/components/PageHero.vue'
import { courseApi, forumApi, moderationApi, uploadApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, resolveAssetUrl, resolveAvatarUrl } from '@/utils/format'
import { normalizePage } from '@/utils/page'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const courseLoading = ref(false)
const submitting = ref(false)
const postEditorVisible = ref(false)
const activeTab = ref('all')
const list = ref([])
const total = ref(0)
const courses = ref([])

const query = reactive({ page: 1, size: 8, keyword: '', courseId: null })
const postEditor = reactive({ title: '', content: '', postType: 'QUESTION', audience: 'ALL', courseId: null, courseName: '', imageUrls: [], requestPin: false, pinDays: 1 })

const studentTypes = [
  { value: 'QUESTION', label: '课程提问', icon: '？', tip: '遇到不会的问题，邀请同学和老师一起讨论' },
  { value: 'NOTE', label: '学习笔记', icon: '✎', tip: '记录你的理解、方法或学习收获' },
  { value: 'RESOURCE', label: '资料分享', icon: '▣', tip: '分享对学习有帮助的资料或整理' },
  { value: 'RESULT', label: '学习成果', icon: '★', tip: '展示作品、实验、解题过程或阶段成果' }
]
const teacherTypes = [
  { value: 'QUESTION', label: '发起讨论', icon: '？', tip: '和同行讨论一个真实的教学问题' },
  { value: 'RESOURCE', label: '分享资源', icon: '▣', tip: '分享教案、课件、案例或工具' },
  { value: 'TEACHING', label: '课堂复盘', icon: '◫', tip: '记录课堂实践和可复用的经验' },
  { value: 'CASE', label: '教学案例', icon: '◇', tip: '沉淀一个值得交流的教学案例' }
]
const postTypeLabels = { QUESTION: '课程问答', NOTE: '学习笔记', RESOURCE: '资源分享', TEACHING: '课堂复盘', CASE: '教学案例', RESULT: '学习成果' }
const audienceLabels = { ALL: '所有人可见', STUDENTS: '学生交流', TEACHERS: '教师教研' }
const editorTypes = computed(() => authStore.isTeacher ? teacherTypes : studentTypes)
const editorTitle = computed(() => postEditor.postType === 'QUESTION' ? (authStore.isStudent ? '提出一个课程问题' : '发起一个教研讨论') : `发布${postTypeLabels[postEditor.postType] || '交流内容'}`)
const currentTabType = computed(() => activeTab.value === 'all' || activeTab.value === 'unresolved' ? undefined : activeTab.value)

function parseImages(value) {
  if (Array.isArray(value)) return value
  if (!value) return []
  try { return JSON.parse(value) || [] } catch { return String(value).split(',').map(item => item.trim()).filter(Boolean) }
}
function postTypeLabel(type) { return postTypeLabels[type] || '交流讨论' }
function audienceLabel(audience) { return audienceLabels[audience] || audienceLabels.ALL }
function excerpt(content) { return String(content || '').replace(/\s+/g, ' ').trim() }
function resetEditor() { Object.assign(postEditor, { title: '', content: '', postType: 'QUESTION', audience: 'ALL', courseId: null, courseName: '', imageUrls: [], requestPin: false, pinDays: 1 }) }
function openComposer(type = 'QUESTION') { resetEditor(); postEditor.postType = type; postEditorVisible.value = true }
function removeImage(index) { postEditor.imageUrls.splice(index, 1) }
function setCourse(courseId) { const course = courses.value.find(item => String(item.id) === String(courseId)); postEditor.courseId = courseId || null; postEditor.courseName = course?.courseName || '' }
async function uploadPostImage(event) {
  const files = Array.from(event.target.files || []); event.target.value = ''
  if (!files.length) return
  if (postEditor.imageUrls.length + files.length > 6) return ElMessage.warning('一条内容最多上传 6 张图片')
  try { for (const file of files) postEditor.imageUrls.push(await uploadApi.image(file)) } catch { /* 拦截器已提示上传错误 */ }
}
async function loadPosts() {
  loading.value = true
  try { const pageData = normalizePage(await forumApi.posts({ ...query, postType: currentTabType.value, unresolved: activeTab.value === 'unresolved' })); list.value = pageData.list; total.value = pageData.total } finally { loading.value = false }
}
async function loadCourses() {
  if (!authStore.isLoggedIn) return
  courseLoading.value = true
  try { courses.value = authStore.isStudent ? await courseApi.joined() : await courseApi.list() } finally { courseLoading.value = false }
}
function loadDetail(id) { router.push({ name: 'forum-post-detail', params: { id } }) }
async function createPost() {
  if (!postEditor.title.trim() || !postEditor.content.trim()) return ElMessage.warning('请先填写标题和内容')
  submitting.value = true
  try { await forumApi.createPost({ ...postEditor, title: postEditor.title.trim(), content: postEditor.content.trim(), imageUrls: JSON.stringify(postEditor.imageUrls) }); ElMessage.success('发布成功；高风险内容将由管理员复核'); postEditorVisible.value = false; resetEditor(); await loadPosts() } finally { submitting.value = false }
}
async function toggleLike(item) {
  if (!authStore.isLoggedIn) return ElMessage.warning('登录后才能点赞')
  const result = await forumApi.toggleLike(item.id); item.likedByMe = result.liked; item.likeCount = Math.max(0, (item.likeCount || 0) + (result.liked ? 1 : -1))
}
async function removePost(id) {
  try { await ElMessageBox.confirm('删除后帖子及其评论将一并移除，确认继续吗？', '删除帖子', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }) } catch { return }
  await forumApi.removePost(id); ElMessage.success('帖子已删除'); await loadPosts()
}
async function reportPost(item) {
  if (!authStore.isLoggedIn) return ElMessage.warning('登录后才能举报')
  if (authStore.user?.id === item.authorId) return ElMessage.warning('不能举报自己发布的内容')
  try {
    const { value } = await ElMessageBox.prompt('请说明举报原因，管理员只处理异常和高风险内容。', '举报内容', { inputType: 'textarea', inputValidator: value => String(value || '').trim() ? true : '请填写举报原因' })
    await moderationApi.report({ targetType: 'FORUM_POST', targetId: item.id, reason: value.trim() })
    ElMessage.success('举报已提交，管理员会进行复核')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '举报失败')
  }
}
watch([activeTab, () => query.keyword, () => query.courseId], () => { query.page = 1; loadPosts() })
watch(() => postEditor.courseId, value => setCourse(value))
onMounted(async () => { await Promise.all([loadPosts(), loadCourses()]); if (route.query.postId) loadDetail(Number(route.query.postId)) })
</script>

<template>
  <div class="community-page">
    <PageHero title="学习交流广场" description="围绕课程提问、分享学习成果，也和同行交流真实的教学实践。">
      <template #actions>
        <el-button v-if="authStore.isLoggedIn" type="primary" @click="openComposer()">发布交流</el-button>
        <el-button plain @click="loadPosts" :loading="loading">刷新内容</el-button>
      </template>
    </PageHero>

    <section class="community-intro surface-card">
      <div><span class="eyebrow">EDUCATION COMMUNITY</span><h2>一节课、一个问题、一个灵感，都值得被看见。</h2><p>这里没有复杂的社交负担，只留下和课程、学习、教学有关的有效交流。</p></div>
      <div class="intro-stats"><div><strong>{{ total }}</strong><span>条交流内容</span></div><div><strong>{{ courses.length }}</strong><span>门相关课程</span></div></div>
    </section>

    <section class="community-grid">
      <main class="community-main">
        <div v-if="authStore.isLoggedIn" class="composer-card surface-card">
          <div class="avatar-placeholder">{{ (authStore.user?.nickname || authStore.user?.username || '我').slice(0, 1) }}</div>
          <button class="composer-trigger" @click="openComposer()">{{ authStore.isStudent ? '今天在课程学习中遇到什么问题？' : '今天想和大家交流什么教学想法？' }}</button>
          <div class="composer-shortcuts"><button @click="openComposer('QUESTION')">？ {{ authStore.isStudent ? '课程提问' : '发起讨论' }}</button><button @click="openComposer('RESOURCE')">▣ 分享资源</button><button @click="openComposer(authStore.isStudent ? 'RESULT' : 'TEACHING')">◫ {{ authStore.isStudent ? '学习成果' : '课堂复盘' }}</button></div>
        </div>

        <div class="feed-toolbar">
          <div class="feed-tabs"><button :class="{ active: activeTab === 'all' }" @click="activeTab = 'all'">全部讨论</button><button :class="{ active: activeTab === 'unresolved' }" @click="activeTab = 'unresolved'">未解决问题</button><button :class="{ active: activeTab === 'QUESTION' }" @click="activeTab = 'QUESTION'">课程问答</button><button v-if="authStore.isTeacher" :class="{ active: activeTab === 'TEACHING' }" @click="activeTab = 'TEACHING'">教师教研</button></div>
          <div class="feed-filters"><el-input v-model="query.keyword" clearable placeholder="搜索问题、资源或课程" /><el-select v-model="query.courseId" clearable placeholder="全部课程" :loading="courseLoading"><el-option v-for="course in courses" :key="course.id" :label="course.courseName" :value="course.id" /></el-select></div>
        </div>

        <div v-loading="loading" class="post-feed">
          <article v-for="item in list" :key="item.id" class="post-card surface-card" @click="loadDetail(item.id)">
            <div class="post-card-head"><div class="author-line"><img :src="resolveAvatarUrl(item.authorAvatar)" alt="作者头像" /><div><strong>{{ item.authorName || '社区用户' }}</strong><span>{{ item.authorName?.includes('老师') ? '教师' : '学习成员' }} · {{ formatDateTime(item.createTime) }}</span></div></div><div class="post-badges"><el-tag v-if="item.pinned" type="warning" effect="light">置顶</el-tag><el-tag v-if="item.solved" type="success" effect="light">已解决</el-tag><el-tag effect="plain">{{ postTypeLabel(item.postType) }}</el-tag></div></div>
            <h3>{{ item.title }}</h3><p class="post-excerpt">{{ excerpt(item.content) }}</p>
            <div v-if="parseImages(item.imageUrls).length" class="post-images"><img v-for="(image, index) in parseImages(item.imageUrls).slice(0, 3)" :key="index" :src="resolveAssetUrl(image)" alt="帖子配图" /></div>
            <div class="post-context"><span v-if="item.courseName">▣ {{ item.courseName }}</span><span>{{ audienceLabel(item.audience) }}</span></div>
            <div class="post-actions"><button @click.stop="toggleLike(item)" :class="{ liked: item.likedByMe }">♡ {{ item.likeCount || 0 }} 赞同</button><span>◌ {{ item.replyCount || 0 }} 条回答</span><span>◉ {{ item.views || 0 }} 阅读</span><button v-if="authStore.isLoggedIn && authStore.user?.id !== item.authorId" @click.stop="reportPost(item)">举报</button><button v-if="authStore.user?.id === item.authorId || authStore.isAdmin" class="delete-action" @click.stop="removePost(item.id)">删除</button></div>
          </article>
          <div v-if="!loading && !list.length" class="empty-community"><div class="empty-icon">✦</div><h3>这里还没有相关交流</h3><p>成为第一个提出问题或分享学习成果的人吧。</p><el-button v-if="authStore.isLoggedIn" type="primary" @click="openComposer()">发布第一条交流</el-button></div>
        </div>
        <div class="pagination-row"><el-pagination v-model:current-page="query.page" layout="total, prev, pager, next" :page-size="query.size" :total="total" @current-change="loadPosts" /></div>
      </main>

      <aside class="community-side">
        <section class="side-card surface-card"><div class="side-title"><h3>你可以在这里</h3><span>✦</span></div><button @click="openComposer('RESOURCE')"><i>▣</i><span><strong>{{ authStore.isStudent ? '分享学习资料' : '分享教学资源' }}</strong><small>让有价值的内容被更多人看到</small></span></button><button @click="openComposer(authStore.isStudent ? 'RESULT' : 'TEACHING')"><i>◫</i><span><strong>{{ authStore.isStudent ? '展示学习成果' : '记录课堂复盘' }}</strong><small>留下可复用的学习与教学经验</small></span></button></section>
        <section class="side-card surface-card"><div class="side-title"><h3>交流小提示</h3><span>☼</span></div><ul><li>标题尽量写清楚具体课程或问题。</li><li>可以上传板书、解题过程、课件截图等图片。</li><li>回复时给出方法和依据，让讨论更有帮助。</li></ul></section>
        <section class="side-card surface-card"><div class="side-title"><h3>社区说明</h3></div><p class="side-copy">登录后即可直接发布和评论。系统只将高风险、被举报或存在异常的内容交给管理员复核。</p></section>
      </aside>
    </section>

    <el-dialog v-model="postEditorVisible" :title="editorTitle" width="760px"><div class="type-picker"><button v-for="type in editorTypes" :key="type.value" :class="{ selected: postEditor.postType === type.value }" @click="postEditor.postType = type.value"><b>{{ type.icon }}</b><strong>{{ type.label }}</strong><small>{{ type.tip }}</small></button></div><el-form label-position="top"><el-form-item label="标题"><el-input v-model="postEditor.title" maxlength="200" show-word-limit :placeholder="postEditor.postType === 'QUESTION' ? '例如：数据库索引为什么能提高查询速度？' : '给这次分享起一个清楚的标题'" /></el-form-item><el-form-item label="内容"><el-input v-model="postEditor.content" type="textarea" :rows="8" maxlength="5000" show-word-limit placeholder="写下你的问题、方法、过程或经验……" /></el-form-item><el-form-item label="关联课程（可选）"><el-select v-model="postEditor.courseId" clearable filterable placeholder="选择相关课程" style="width:100%"><el-option v-for="course in courses" :key="course.id" :label="course.courseName" :value="course.id" /></el-select></el-form-item><div class="editor-options"><el-radio-group v-model="postEditor.audience"><el-radio label="ALL">所有人可见</el-radio><el-radio v-if="authStore.isStudent" label="STUDENTS">仅学生交流</el-radio><el-radio v-if="authStore.isTeacher" label="TEACHERS">仅教师教研</el-radio></el-radio-group><label class="image-upload"><input type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple @change="uploadPostImage" />＋ 添加图片</label></div><div v-if="postEditor.imageUrls.length" class="upload-previews"><div v-for="(image, index) in postEditor.imageUrls" :key="image" class="upload-preview"><img :src="resolveAssetUrl(image)" alt="待上传图片" /><button type="button" @click="removeImage(index)">×</button></div></div></el-form><template #footer><el-button @click="postEditorVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="createPost">提交交流</el-button></template></el-dialog>



  </div>
</template>

<style scoped>
.community-page { display:grid; gap:20px; }
.surface-card { background:rgba(255,255,255,.8); border:1px solid var(--line); border-radius:22px; box-shadow:0 14px 38px rgba(53,83,124,.08); }
.community-intro { display:flex; justify-content:space-between; gap:28px; padding:28px 32px; background:linear-gradient(135deg,#f5fbff,#fffaf0); }
.eyebrow { color:#5b7aa7; font-size:11px; letter-spacing:.16em; font-weight:800; }
.community-intro h2 { margin:10px 0 8px; font-size:25px; color:var(--text-main); }
.community-intro p { margin:0; color:var(--text-muted); }
.intro-stats { display:flex; align-items:center; gap:26px; min-width:190px; }
.intro-stats div { display:grid; gap:4px; }
.intro-stats strong { color:#2e6f9d; font-size:28px; }
.intro-stats span { color:var(--text-muted); font-size:13px; }
.community-grid { display:grid; grid-template-columns:minmax(0,1fr) 290px; gap:20px; align-items:start; }
.community-main,.community-side { display:grid; gap:16px; min-width:0; }
.composer-card { display:grid; grid-template-columns:auto 1fr; gap:12px; padding:18px; }
.avatar-placeholder { display:grid; place-items:center; width:42px; height:42px; border-radius:50%; color:#fff; font-weight:800; background:linear-gradient(135deg,#5b9bc6,#7dc6ab); }
.composer-trigger { border:0; border-radius:14px; background:#f3f7fa; color:#8a99a8; text-align:left; padding:12px 16px; cursor:pointer; font-size:14px; }
.composer-shortcuts { grid-column:2; display:flex; gap:10px; }
.composer-shortcuts button,.post-actions button,.feed-tabs button { border:0; background:transparent; color:var(--text-muted); cursor:pointer; }
.composer-shortcuts button { padding:4px 8px; }
.composer-shortcuts button:hover,.post-actions button:hover { color:#347eaa; }
.feed-toolbar { display:flex; justify-content:space-between; gap:14px; align-items:center; flex-wrap:wrap; }
.feed-tabs { display:flex; gap:5px; }
.feed-tabs button { padding:8px 12px; border-radius:10px; }
.feed-tabs button.active { background:#e7f3fa; color:#286e9d; font-weight:700; }
.feed-filters { display:flex; gap:8px; }
.feed-filters .el-input { width:210px; }
.feed-filters .el-select { width:150px; }
.post-feed { display:grid; gap:14px; min-height:240px; }
.post-card { padding:20px; cursor:pointer; transition:transform .2s,box-shadow .2s; }
.post-card:hover { transform:translateY(-2px); box-shadow:0 18px 42px rgba(53,83,124,.13); }
.post-card-head,.author-line,.post-badges,.post-actions,.post-context,.side-title { display:flex; align-items:center; justify-content:space-between; gap:10px; }
.author-line { justify-content:flex-start; }
.author-line img { width:38px; height:38px; border-radius:50%; object-fit:cover; }
.author-line div { display:grid; gap:3px; }
.author-line strong { color:var(--text-main); }
.author-line span,.post-context,.post-actions,.side-card small { color:var(--text-muted); font-size:12px; }
.post-card h3 { margin:17px 0 8px; font-size:19px; color:var(--text-main); }
.post-excerpt { margin:0; color:#617182; line-height:1.75; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
.post-images { display:grid; grid-template-columns:repeat(3,1fr); gap:8px; margin-top:14px; }
.post-images img { width:100%; height:120px; object-fit:cover; border-radius:12px; }
.post-context { justify-content:flex-start; gap:16px; margin-top:14px; }
.post-actions { justify-content:flex-start; gap:18px; border-top:1px solid var(--line); margin-top:16px; padding-top:14px; }
.post-actions .liked { color:#e58b4f; }
.post-actions .delete-action { margin-left:auto; color:#bd6d6d; }
.side-card { padding:20px; }
.side-title { margin-bottom:12px; }
.side-title h3 { margin:0; color:var(--text-main); font-size:16px; }
.side-title p { margin:5px 0 0; color:var(--text-muted); font-size:13px; }
.side-card > button { display:flex; gap:12px; width:100%; text-align:left; border:0; background:transparent; padding:12px 0; cursor:pointer; }
.side-card > button + button { border-top:1px solid var(--line); }
.side-card i { display:grid; place-items:center; width:34px; height:34px; border-radius:11px; background:#edf7f7; color:#3f8d91; font-style:normal; font-weight:800; }
.side-card button span { display:grid; gap:4px; }
.side-card strong { color:var(--text-main); font-size:14px; }
.side-card small { line-height:1.5; }
.side-card ul { margin:0; padding-left:18px; color:var(--text-muted); line-height:2; font-size:13px; }
.side-copy { color:var(--text-muted); font-size:13px; line-height:1.8; margin:0; }
.empty-community { text-align:center; color:var(--text-muted); padding:58px 20px; }
.empty-icon { font-size:34px; color:#e6b374; }
.empty-community h3 { color:var(--text-main); margin:10px 0 6px; }
.pagination-row { display:flex; justify-content:flex-end; }
.type-picker { display:grid; grid-template-columns:repeat(4,1fr); gap:8px; margin-bottom:20px; }
.type-picker button { min-height:100px; padding:12px; text-align:left; display:grid; gap:5px; border:1px solid var(--line); border-radius:14px; background:#fff; cursor:pointer; }
.type-picker button.selected { border-color:#66a4c4; background:#eef8fc; box-shadow:inset 0 0 0 1px #66a4c4; }
.type-picker b { color:#4b98ae; font-size:20px; }
.type-picker strong { color:var(--text-main); }
.type-picker small { color:var(--text-muted); line-height:1.45; }
.editor-options { display:flex; justify-content:space-between; align-items:center; gap:12px; flex-wrap:wrap; }
.image-upload { color:#347eaa; cursor:pointer; font-size:14px; }
.image-upload input { display:none; }
.upload-previews { display:flex; gap:10px; margin-top:12px; flex-wrap:wrap; }
.upload-preview { position:relative; }
.upload-preview img { width:84px; height:70px; object-fit:cover; border-radius:10px; }
.upload-preview button { position:absolute; top:-7px; right:-7px; border:0; border-radius:50%; background:#4c5968; color:#fff; width:20px; height:20px; cursor:pointer; }
@media (max-width:900px) { .community-grid { grid-template-columns:1fr; }.community-side { grid-template-columns:repeat(3,1fr); }.community-side .side-card { min-width:0; }.intro-stats { display:none; } }
@media (max-width:640px) { .community-intro { padding:22px; }.community-intro h2 { font-size:20px; }.community-side { grid-template-columns:1fr; }.feed-filters { width:100%; }.feed-filters .el-input,.feed-filters .el-select { flex:1; width:auto; }.composer-shortcuts { flex-wrap:wrap; }.type-picker { grid-template-columns:repeat(2,1fr); }.post-images img { height:82px; }.post-badges { flex-wrap:wrap; justify-content:flex-end; } }
</style>
