<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { appMenus } from '@/config/menu'
import { notificationApi } from '@/api'
import { resolveAssetUrl, resolveAvatarUrl } from '@/utils/format'
import { roleLabel } from '@/config/options'
import AiFloatingAssistant from '@/components/AiFloatingAssistant.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sidebarOpen = ref(false)
const unreadCount = ref(0)
let unreadPollTimer = null
const avatarFailed = ref(false)
const avatarUrl = computed(() => resolveAvatarUrl(authStore.user?.avatar))
const avatarInitial = computed(() => (authStore.user?.nickname || authStore.user?.username || 'U').slice(0, 1).toUpperCase())

const visibleMenus = computed(() =>
  appMenus.filter((item) => {
    if (item.path === '/notices') {
      return false
    }
    if (item.public) {
      return true
    }

    if (!authStore.isLoggedIn) {
      return false
    }

    if (!item.roles?.length) {
      return true
    }

    return item.roles.includes(authStore.role)
  })
)

const navigationMenus = computed(() => {
  const noticeMenu = { path: '/notices', label: '通知公告' }
  const dashboardIndex = visibleMenus.value.findIndex((item) => item.path === '/dashboard')

  if (dashboardIndex === -1) {
    return [noticeMenu, ...visibleMenus.value]
  }

  return [
    ...visibleMenus.value.slice(0, dashboardIndex + 1),
    noticeMenu,
    ...visibleMenus.value.slice(dashboardIndex + 1)
  ]
})

const roleText = computed(() => roleLabel(authStore.role, '访客'))

async function loadUnreadCount() {
  if (!authStore.isLoggedIn) {
    unreadCount.value = 0
    return
  }

  try {
    unreadCount.value = await notificationApi.unreadCount()
  } catch (error) {
    unreadCount.value = 0
  }
}

async function logout() {
  try {
    await ElMessageBox.confirm('确认退出当前账号吗？', '退出登录', {
      type: 'warning'
    })
    authStore.clear()
    unreadCount.value = 0
    router.push('/login')
    ElMessage.success('已退出登录')
  } catch (error) {
    return
  }
}

watch(
  () => route.fullPath,
  () => {
    sidebarOpen.value = false
  }
)

watch(
  () => authStore.isLoggedIn,
  () => {
    loadUnreadCount()
  }
)

watch(avatarUrl, () => {
  avatarFailed.value = false
})

function markAvatarFailed() {
  avatarFailed.value = true
}

onMounted(() => {
  loadUnreadCount()
  window.addEventListener('notifications-updated', loadUnreadCount)
  unreadPollTimer = window.setInterval(loadUnreadCount, 30000)
})

onUnmounted(() => {
  window.removeEventListener('notifications-updated', loadUnreadCount)
  if (unreadPollTimer) window.clearInterval(unreadPollTimer)
})
</script>

<template>
  <div class="app-shell" :class="{ 'home-shell': route.name === 'dashboard' }">
    <aside class="app-sidebar" :class="{ open: sidebarOpen }">
      <div class="brand-block">
        <div class="brand-logo">
          <span class="brand-mark" aria-hidden="true">塾</span>
          <div class="brand-copy">
            <span class="brand-title">云塾</span>
            <span class="brand-subtitle">数字时代的线上书院</span>
          </div>
        </div>
      </div>

      <div class="sidebar-section">
        <div class="sidebar-label">导航</div>
        <nav class="menu-list">
          <RouterLink
            v-for="item in navigationMenus"
            :key="item.path"
            :to="item.path"
            class="menu-link"
          >
            <span>{{ item.label }}</span>
            <el-badge
              v-if="item.path === '/notifications' && unreadCount"
              :value="unreadCount > 99 ? '99+' : unreadCount"
              class="menu-unread-badge"
            />
          </RouterLink>
        </nav>
      </div>

      <div class="sidebar-footer">
        <div v-if="authStore.isLoggedIn" class="sidebar-user">
          <img v-if="!avatarFailed" :src="avatarUrl" @error="markAvatarFailed" alt="头像" />
          <span v-else class="avatar-fallback" aria-label="头像">{{ avatarInitial }}</span>
          <div>
            <strong>{{ authStore.user?.nickname || authStore.user?.username }}</strong>
            <span>{{ roleText }}</span>
          </div>
        </div>
        <div v-else class="sidebar-user">
          <div>
            <strong>未登录访客</strong>
            <span>可先查看公开内容，再登录进入工作台</span>
          </div>
        </div>

        <div class="sidebar-actions">
          <el-button
            v-if="authStore.isLoggedIn"
            size="small"
            type="primary"
            @click="router.push('/profile')"
          >
            我的资料
          </el-button>
          <el-button
            v-if="authStore.isLoggedIn"
            size="small"
            plain
            @click="logout"
          >
            退出
          </el-button>
          <template v-else>
            <el-button size="small" type="primary" @click="router.push('/login')">
              登录
            </el-button>
            <el-button size="small" plain @click="router.push('/register')">
              注册
            </el-button>
          </template>
        </div>
      </div>
    </aside>

    <div
      v-if="sidebarOpen"
      class="mobile-overlay"
      @click="sidebarOpen = false"
    />

    <div class="app-main">
      <el-button class="mobile-toggle mobile-menu-button" plain @click="sidebarOpen = true">菜单</el-button>
      <main class="page-main">
        <router-view />
      </main>
    </div>

    <AiFloatingAssistant />
  </div>
</template>
