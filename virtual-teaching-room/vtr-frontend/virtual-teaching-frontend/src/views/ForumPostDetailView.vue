<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHero from '@/components/PageHero.vue'
import { forumApi, moderationApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, resolveAssetUrl, resolveAvatarUrl } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const submitting = ref(false)
const post = ref(null)
const commentEditor = reactive({ content: '' })
const postTypeLabels = { QUESTION: '课程问答', NOTE: '学习笔记', RESOURCE: '资源分享', TEACHING: '课堂复盘', CASE: '教学案例', RESULT: '学习成果' }
const audienceLabels = { ALL: '所有人可见', STUDENTS: '学生交流', TEACHERS: '教师教研' }
const images = computed(() => {
  const value = post.value?.imageUrls
  if (Array.isArray(value)) return value
  if (!value) return []
  try { return JSON.parse(value) || [] } catch { return String(value).split(',').map(item => item.trim()).filter(Boolean) }
})
function typeLabel(type) { return postTypeLabels[type] || '交流讨论' }
function audienceLabel(audience) { return audienceLabels[audience] || audienceLabels.ALL }
async function loadPost() {
  loading.value = true
  try { post.value = await forumApi.postDetail(Number(route.params.id)) } finally { loading.value = false }
}
async function addComment() {
  if (!commentEditor.content.trim()) return ElMessage.warning('请输入评论内容')
  submitting.value = true
  try {
    await forumApi.createComment({ postId: post.value.id, content: commentEditor.content.trim() })
    commentEditor.content = ''
    ElMessage.success('评论已发布')
    await loadPost()
  } finally { submitting.value = false }
}
async function toggleLike() {
  if (!authStore.isLoggedIn) return ElMessage.warning('登录后才能点赞')
  const result = await forumApi.toggleLike(post.value.id)
  post.value.likedByMe = result.liked
  post.value.likeCount = Math.max(0, (post.value.likeCount || 0) + (result.liked ? 1 : -1))
}
async function deletePost() {
  try { await ElMessageBox.confirm('删除后帖子及其评论将一并移除，确认继续吗？', '删除帖子', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }) } catch { return }
  await forumApi.removePost(post.value.id)
  ElMessage.success('帖子已删除')
  router.push({ name: 'forum' })
}
async function deleteComment(comment) {
  try { await ElMessageBox.confirm('删除后这条评论将无法恢复，确认继续吗？', '删除评论', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }) } catch { return }
  await forumApi.removeComment(comment.id)
  ElMessage.success('评论已删除')
  await loadPost()
}
async function reportContent(targetType, targetId, authorId) {
  if (!authStore.isLoggedIn) return ElMessage.warning('登录后才能举报')
  if (authStore.user?.id === authorId) return ElMessage.warning('不能举报自己发布的内容')
  try {
    const { value } = await ElMessageBox.prompt('请说明举报原因。', '举报内容', { inputType: 'textarea', inputValidator: value => String(value || '').trim() ? true : '请填写举报原因' })
    await moderationApi.report({ targetType, targetId, reason: value.trim() })
    ElMessage.success('举报已提交，管理员会进行复核')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '举报失败')
  }
}
onMounted(loadPost)
</script>

<template>
  <div class="detail-page">
    <PageHero title="交流详情" description="查看完整内容，参与问题讨论和经验分享。">
      <template #actions><el-button plain @click="router.back()">返回社区</el-button><el-button v-if="authStore.user?.id === post?.authorId || authStore.isAdmin" type="danger" plain @click="deletePost">删除帖子</el-button></template>
    </PageHero>

    <div v-loading="loading" class="detail-shell">
      <template v-if="post">
        <article class="content-paper">
          <div class="content-meta"><div class="author-block"><img :src="resolveAvatarUrl(post.authorAvatar)" alt="作者头像" /><div><strong>{{ post.authorName || '社区用户' }}</strong><span>{{ typeLabel(post.postType) }} · {{ formatDateTime(post.createTime) }}</span></div></div><div class="content-tags"><el-tag v-if="post.pinned" type="warning">置顶</el-tag><el-tag v-if="post.solved" type="success">已解决</el-tag><el-tag effect="plain">{{ audienceLabel(post.audience) }}</el-tag></div></div>
          <h1>{{ post.title }}</h1>
          <p class="author-caption">作者：{{ post.authorName || '社区用户' }}<span v-if="post.courseName">　·　关联课程：{{ post.courseName }}</span></p>
          <div class="article-body">{{ post.content }}</div>
          <div v-if="images.length" class="article-images"><img v-for="(image, index) in images" :key="index" :src="resolveAssetUrl(image)" alt="交流配图" /></div>
          <div class="article-actions"><button :class="{ liked: post.likedByMe }" @click="toggleLike">♧ {{ post.likeCount || 0 }}</button><span>◌ {{ post.replyCount || 0 }} 条评论</span><span>◉ {{ post.views || 0 }} 阅读</span><button v-if="authStore.isLoggedIn && authStore.user?.id !== post.authorId" @click="reportContent('FORUM_POST', post.id, post.authorId)">举报</button></div>
        </article>

        <section class="comment-paper">
          <div class="comment-heading"><h2>评论话题</h2><span>{{ post.replyCount || post.comments?.length || 0 }} 条评论</span></div>
          <div v-if="authStore.isLoggedIn" class="comment-composer"><div class="comment-user-avatar">{{ (authStore.user?.nickname || authStore.user?.username || '我').slice(0, 1) }}</div><div class="comment-composer-main"><el-input v-model="commentEditor.content" type="textarea" :rows="5" placeholder="评论话题" /><div class="comment-composer-footer"><div class="comment-tools"><span>▧</span><span>↗</span><small>上传图片、附件请遵守社区规范</small></div><div><el-button plain @click="commentEditor.content = ''">取消</el-button><el-button type="primary" :loading="submitting" @click="addComment">评论</el-button></div></div></div></div>
          <p v-else class="login-tip">登录后可以参与评论。</p>
          <div v-if="post.comments?.length" class="comment-list"><article v-for="comment in post.comments" :key="comment.id" class="comment-row"><div class="comment-user-avatar">{{ (comment.authorName || '用户').slice(0, 1) }}</div><div class="comment-row-body"><div class="comment-row-meta"><strong>{{ comment.authorName }}</strong><span>{{ formatDateTime(comment.createTime) }}</span></div><p>{{ comment.content }}</p><div class="comment-row-actions"><button>♡ 赞</button><button>回复</button><button v-if="authStore.isLoggedIn && authStore.user?.id !== comment.authorId" @click="reportContent('FORUM_COMMENT', comment.id, comment.authorId)">举报</button><button v-if="authStore.user?.id === comment.authorId || authStore.isAdmin" class="comment-delete" @click="deleteComment(comment)">删除</button></div></div></article></div><div v-else class="comment-empty">还没有评论，来分享你的想法吧。</div>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.detail-page { display:grid; gap:20px; }.detail-shell { display:grid; gap:20px; }.content-paper,.comment-paper { background:rgba(255,255,255,.92); border:1px solid var(--line); border-radius:18px; padding:28px 32px; box-shadow:0 10px 30px rgba(53,83,124,.06); }.content-meta,.author-block,.content-tags,.article-actions,.comment-heading,.comment-composer,.comment-composer-footer,.comment-tools,.comment-row,.comment-row-meta,.comment-row-actions { display:flex; align-items:center; }.content-meta,.comment-heading,.comment-composer-footer { justify-content:space-between; gap:16px; }.author-block { gap:12px; }.author-block img { width:44px; height:44px; border-radius:50%; object-fit:cover; }.author-block div { display:grid; gap:5px; }.author-block span,.author-caption,.article-actions,.comment-heading span,.comment-row-meta span,.comment-tools small { color:var(--text-muted); font-size:13px; }.content-tags { gap:8px; flex-wrap:wrap; }.content-paper h1 { margin:25px 0 10px; color:#18283c; font-size:30px; line-height:1.35; }.author-caption { margin:0 0 30px; }.article-body { color:#24364c; font-size:16px; line-height:2; white-space:pre-wrap; }.article-images { display:grid; gap:14px; margin:24px auto; max-width:920px; }.article-images img { display:block; width:100%; max-height:680px; object-fit:contain; border-radius:10px; background:#f3f6f8; }.article-actions { gap:22px; margin-top:28px; padding-top:18px; border-top:1px solid var(--line); font-size:14px; }.article-actions button,.comment-tools span,.comment-row-actions button { border:0; background:transparent; color:var(--text-muted); cursor:pointer; }.article-actions button.liked { color:#e18a50; }.comment-paper { padding-top:24px; }.comment-heading { padding-bottom:18px; border-bottom:1px solid var(--line); }.comment-heading h2 { margin:0; color:#18283c; font-size:22px; }.comment-composer { align-items:flex-start; gap:14px; padding:22px 0; }.comment-user-avatar { flex:none; display:grid; place-items:center; width:42px; height:42px; border-radius:50%; background:linear-gradient(135deg,#f2b07b,#86c8c1); color:#fff; font-weight:800; }.comment-composer-main { flex:1; min-width:0; }.comment-composer-footer { margin-top:12px; }.comment-tools { gap:13px; color:#77a2c9; }.comment-tools small { margin-left:5px; }.comment-list { border-top:1px solid var(--line); }.comment-row { align-items:flex-start; gap:14px; padding:20px 0; border-bottom:1px solid var(--line); }.comment-row-body { flex:1; min-width:0; }.comment-row-meta { justify-content:space-between; gap:10px; }.comment-row-meta strong { color:#24364c; }.comment-row p { margin:10px 0; color:#4e6175; line-height:1.8; white-space:pre-wrap; }.comment-row-actions { gap:16px; }.comment-row-actions button { font-size:13px; }.comment-row-actions .comment-delete { color:#bd6d6d; }.comment-empty,.login-tip { margin:0; padding:28px 0; text-align:center; color:var(--text-muted); }
@media (max-width:640px) { .content-paper,.comment-paper { padding:20px 16px; }.content-paper h1 { font-size:24px; }.content-meta { align-items:flex-start; flex-direction:column; }.content-tags { justify-content:flex-start; }.comment-composer-footer { align-items:flex-start; flex-direction:column; }.comment-tools { flex-wrap:wrap; } }
</style>
