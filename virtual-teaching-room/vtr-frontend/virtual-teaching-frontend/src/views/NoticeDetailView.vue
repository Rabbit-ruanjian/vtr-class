<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Calendar, Delete, Document, User, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noticeApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, formatFileSize, resolveAssetUrl } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const notice = ref(null)
const canManage = computed(() => authStore.isAdmin)

function formatNoticeDate(value) {
  return formatDateTime(value, 'YYYY年MM月DD日 HH:mm')
}

function sanitizeHtml(value) {
  const parser = new DOMParser()
  const document = parser.parseFromString(`<div>${String(value || '')}</div>`, 'text/html')
  const root = document.body.firstElementChild
  const allowedTags = new Set(['P', 'BR', 'STRONG', 'B', 'EM', 'I', 'U', 'S', 'H1', 'H2', 'H3', 'OL', 'UL', 'LI', 'A', 'SPAN'])
  const allowedStyle = new Set(['color', 'background-color', 'font-weight', 'font-style', 'text-decoration', 'text-align'])

  root.querySelectorAll('*').forEach((element) => {
    if (!allowedTags.has(element.tagName)) {
      element.replaceWith(...Array.from(element.childNodes))
      return
    }

    Array.from(element.attributes).forEach((attribute) => {
      if (attribute.name === 'style') {
        const styles = attribute.value.split(';').map((item) => item.trim()).filter(Boolean)
        const safeStyles = styles.filter((item) => allowedStyle.has(item.split(':')[0].trim().toLowerCase()) && !/url\s*\(|expression\s*\(/i.test(item))
        if (safeStyles.length) element.setAttribute('style', safeStyles.join('; '))
        else element.removeAttribute('style')
      } else if (attribute.name === 'href') {
        if (!/^(https?:\/\/|\/|mailto:)/i.test(attribute.value)) element.removeAttribute('href')
      } else if (attribute.name === 'class') {
        const classes = attribute.value.split(/\s+/).filter((item) => /^ql-(align|size|indent|direction)-/.test(item))
        if (classes.length) element.setAttribute('class', classes.join(' '))
        else element.removeAttribute('class')
      } else if (!['target', 'rel'].includes(attribute.name)) {
        element.removeAttribute(attribute.name)
      }
    })

    if (element.tagName === 'A' && element.getAttribute('href')) {
      element.setAttribute('target', '_blank')
      element.setAttribute('rel', 'noopener noreferrer')
    }
  })

  return root.innerHTML
}

const renderedContent = computed(() => sanitizeHtml(notice.value?.content))

async function loadDetail() {
  loading.value = true
  notice.value = null
  try {
    notice.value = await noticeApi.detail(route.params.id)
  } catch (error) {
    ElMessage.error('公告加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function deleteNotice() {
  await ElMessageBox.confirm('删除后公告及其附件将不可恢复，确认继续吗？', '删除公告', { type: 'warning' })
  await noticeApi.remove(notice.value.id)
  ElMessage.success('公告已删除')
  await router.push('/notices')
}

onMounted(loadDetail)
watch(() => route.params.id, loadDetail)
</script>

<template>
  <div class="notice-detail-page" v-loading="loading">
    <div class="notice-detail-toolbar">
      <el-button text :icon="ArrowLeft" @click="router.push('/notices')">返回公告列表</el-button>
      <el-button v-if="canManage && notice" type="danger" plain :icon="Delete" @click="deleteNotice">删除公告</el-button>
    </div>

    <article v-if="notice" class="notice-document">
      <header class="notice-document-title">
        <h1>{{ notice.title }}</h1>
      </header>

      <div class="notice-document-meta">
        <span><Calendar /> {{ formatNoticeDate(notice.publishTime || notice.createdAt) }}</span>
        <span><User /> {{ notice.authorNickname || notice.authorName || '平台' }}</span>
        <span><View /> {{ notice.viewCount || 0 }} 次浏览</span>
      </div>

      <div class="notice-document-body" v-html="renderedContent"></div>

      <section v-if="notice.attachments?.length" class="notice-document-attachments">
        <h2>附件</h2>
        <a v-for="attachment in notice.attachments" :key="attachment.id" class="notice-attachment-link" :href="resolveAssetUrl(attachment.fileUrl)" target="_blank" rel="noopener noreferrer" :download="attachment.fileName">
          <Document />
          <span>{{ attachment.fileName }}</span>
          <small>{{ formatFileSize(attachment.fileSize) }}</small>
        </a>
      </section>
    </article>

    <section v-else-if="!loading" class="notice-detail-empty">
      <h2>公告不存在或已被删除</h2>
      <el-button type="primary" @click="router.push('/notices')">返回公告列表</el-button>
    </section>
  </div>
</template>

<style scoped>
.notice-detail-page {
  min-height: 640px;
}

.notice-detail-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-start;
  margin-bottom: 16px;
}

.notice-detail-toolbar :deep(.el-button) {
  color: #5c78aa;
  font-size: 14px;
}

.notice-document {
  overflow: hidden;
  background: #fff;
  border: 1px solid rgba(70, 111, 190, .12);
  border-radius: 4px;
  box-shadow: 0 12px 32px rgba(58, 91, 143, .08);
}

.notice-document-title {
  padding: 38px 50px 34px;
  background: linear-gradient(110deg, #2563d8, #3478eb);
}

.notice-document-title h1 {
  margin: 0;
  color: #fff;
  font-size: clamp(24px, 3vw, 40px);
  font-weight: 800;
  line-height: 1.45;
  letter-spacing: .01em;
}

.notice-document-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 22px;
  padding: 18px 50px;
  color: #9aa9c0;
  border-bottom: 1px solid #edf1f7;
  font-size: 13px;
}

.notice-document-meta span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.notice-document-meta svg {
  width: 15px;
}

.notice-document-body {
  min-height: 420px;
  padding: 48px 74px 70px;
  color: #202b3b;
  font-size: 18px;
  line-height: 2.25;
  white-space: pre-wrap;
  word-break: break-word;
}

.notice-document-body :deep(p) { margin: 0 0 1em; }
.notice-document-body :deep(h1), .notice-document-body :deep(h2), .notice-document-body :deep(h3) { margin: 1.1em 0 .5em; color: #1d3557; line-height: 1.5; }
.notice-document-body :deep(ol), .notice-document-body :deep(ul) { padding-left: 1.6em; }
.notice-document-body :deep(a) { color: #1769d2; text-decoration: underline; }
.notice-document-body :deep(.ql-align-center) { text-align: center; }
.notice-document-body :deep(.ql-align-right) { text-align: right; }
.notice-document-body :deep(.ql-align-justify) { text-align: justify; }

.notice-document-attachments {
  padding: 25px 74px 30px;
  border-top: 1px solid #edf1f7;
  background: #fbfcff;
}

.notice-document-attachments h2 {
  margin: 0 0 14px;
  color: #344b70;
  font-size: 17px;
}

.notice-attachment-link {
  display: flex;
  align-items: center;
  gap: 9px;
  max-width: 760px;
  padding: 10px 12px;
  color: #3473c8;
  border: 1px solid #e2eaf5;
  border-radius: 8px;
  background: #fff;
  text-decoration: none;
}

.notice-attachment-link + .notice-attachment-link { margin-top: 8px; }
.notice-attachment-link svg { flex: 0 0 auto; width: 17px; }
.notice-attachment-link span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-attachment-link small { margin-left: auto; color: #9aa9bc; white-space: nowrap; }

.notice-detail-empty {
  padding: 100px 20px;
  color: #71809a;
  text-align: center;
  background: #fff;
  border: 1px solid rgba(70, 111, 190, .12);
  border-radius: 4px;
}

.notice-detail-empty h2 {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 500;
}

@media (max-width: 720px) {
  .notice-document-title {
    padding: 28px 22px 26px;
  }

  .notice-document-title h1 {
    font-size: 24px;
  }

  .notice-document-meta {
    gap: 10px 17px;
    padding: 15px 22px;
  }

  .notice-document-body {
    min-height: 360px;
    padding: 30px 22px 44px;
    font-size: 16px;
    line-height: 2;
  }

  .notice-document-attachments {
    padding: 22px;
  }
}
</style>
