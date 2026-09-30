<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHero from '@/components/PageHero.vue'
import { authApi, schoolApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, resolveAssetUrl, resolveAvatarUrl } from '@/utils/format'

const authStore = useAuthStore()
const profileLoading = ref(false)
const passwordLoading = ref(false)
const avatarUploading = ref(false)
const identityLoading = ref(false)
const schools = ref([])

const profile = reactive({
  nickname: '',
  email: '',
  phone: '',
  avatar: '',
  bio: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const identityForm = reactive({
  unitName: '',
  identityType: 'STUDENT',
  identityNumber: '',
  schoolId: null
})

function syncProfile() {
  profile.nickname = authStore.user?.nickname || ''
  profile.email = authStore.user?.email || ''
  profile.phone = authStore.user?.phone || ''
  profile.avatar = authStore.user?.avatar || ''
  profile.bio = authStore.user?.bio || ''
  identityForm.unitName = authStore.user?.unitName || ''
  identityForm.identityType = authStore.user?.identityType || 'STUDENT'
  identityForm.identityNumber = authStore.user?.identityNumber || ''
  identityForm.schoolId = authStore.user?.schoolId || null
}

async function saveIdentity() {
  identityLoading.value = true
  try {
    await authApi.bindIdentity(identityForm)
    await authStore.fetchMe()
    syncProfile()
    ElMessage.success(identityForm.schoolId ? '学校身份绑定成功，已完成名单核验' : '已取消学校身份绑定')
  } finally {
    identityLoading.value = false
  }
}

watch(() => identityForm.schoolId, (schoolId) => {
  if (!schoolId) {
    identityForm.unitName = ''
    identityForm.identityNumber = ''
  }
})

async function saveProfile() {
  profileLoading.value = true

  try {
    await authApi.updateProfile(profile)
    await authStore.fetchMe()
    syncProfile()
    ElMessage.success('个人资料已更新')
  } finally {
    profileLoading.value = false
  }
}

async function savePassword() {
  passwordLoading.value = true

  try {
    await authApi.changePassword(passwordForm)
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    ElMessage.success('密码已修改')
  } finally {
    passwordLoading.value = false
  }
}

async function onAvatarChange(event) {
  const file = event.target.files?.[0]

  if (!file) {
    return
  }

  avatarUploading.value = true

  try {
    const avatarUrl = await authApi.uploadAvatar(file)
    profile.avatar = avatarUrl
    await authStore.fetchMe()
    syncProfile()
    ElMessage.success('头像已上传')
  } finally {
    avatarUploading.value = false
    event.target.value = ''
  }
}

onMounted(async () => {
  schools.value = await schoolApi.active()
  syncProfile()
})
</script>

<template>
  <div class="page-stack">
    <PageHero
      title="我的资料"
      description="资料更新、头像上传和密码修改都直接走用户相关接口，不改后端逻辑。"
    />

    <section class="hero-grid">
      <el-card class="surface-card">
        <template #header>
          <div class="card-head">
            <div>
              <h3>个人资料</h3>
              <p>当前登录账号的基础信息与可编辑资料。</p>
            </div>
          </div>
        </template>

        <div class="sidebar-user" style="margin-bottom: 18px; color: var(--text-main);">
          <img :src="resolveAvatarUrl(authStore.user?.avatar)" alt="头像" @error="$event.target.src = '/uploads/default-avatar.png'" />
          <div>
            <strong>{{ authStore.user?.nickname || authStore.user?.username }}</strong>
            <span style="color: var(--text-muted);">
              {{ authStore.user?.role }} · 创建于 {{ formatDateTime(authStore.user?.createdAt) }}
            </span>
          </div>
        </div>

        <el-form label-position="top">
          <div class="two-column">
            <div>
              <el-form-item label="昵称">
                <el-input v-model="profile.nickname" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="profile.email" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="profile.phone" />
              </el-form-item>
            </div>

            <div>
              <el-form-item label="头像地址">
                <el-input v-model="profile.avatar" />
              </el-form-item>
              <el-form-item label="上传头像">
                <input type="file" accept="image/*" @change="onAvatarChange" />
                <div class="footer-note" style="margin-top: 8px;">
                  {{ avatarUploading ? '正在上传头像...' : '支持直接上传头像文件' }}
                </div>
              </el-form-item>
            </div>
          </div>

          <el-form-item label="个人简介">
            <el-input v-model="profile.bio" type="textarea" :rows="6" />
          </el-form-item>

          <el-button type="primary" :loading="profileLoading" @click="saveProfile">
            保存资料
          </el-button>
        </el-form>
      </el-card>

      <el-card class="surface-card">
        <template #header>
          <div class="card-head">
            <div>
              <h3>单位与身份绑定</h3>
              <p>提交单位和学号/工号后，由管理员审核确认。</p>
            </div>
            <el-tag :type="authStore.user?.identityStatus === 'VERIFIED' ? 'success' : 'warning'">
              {{ authStore.user?.identityStatus === 'VERIFIED' ? '已认证' : (authStore.user?.identityStatus || '未绑定') }}
            </el-tag>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="所属学校">
            <el-select v-model="identityForm.schoolId" clearable placeholder="请选择系统中的学校，也可留空" style="width:100%">
              <el-option v-for="school in schools" :key="school.id" :label="`${school.name}（${school.code}）`" :value="school.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="identityForm.schoolId" label="单位/院系（可选）">
            <el-input v-model="identityForm.unitName" placeholder="请输入学院、系部或工作单位" />
          </el-form-item>
          <el-form-item v-if="identityForm.schoolId" label="身份类型">
            <el-radio-group v-model="identityForm.identityType">
              <el-radio-button label="STUDENT">学生 / 学号</el-radio-button>
              <el-radio-button label="TEACHER">教师 / 工号</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="identityForm.schoolId" :label="identityForm.identityType === 'STUDENT' ? '学号' : '工号'">
            <el-input v-model="identityForm.identityNumber" placeholder="请输入对应编号" />
          </el-form-item>
          <el-button type="primary" :loading="identityLoading" @click="saveIdentity">
            {{ identityForm.schoolId ? '验证并绑定学校身份' : '取消学校绑定' }}
          </el-button>
        </el-form>
      </el-card>

      <el-card class="surface-card">
        <template #header>
          <div class="card-head">
            <div>
              <h3>修改密码</h3>
              <p>提交旧密码、新密码和确认密码。</p>
            </div>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="旧密码">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="passwordForm.newPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
          </el-form-item>
          <el-button type="primary" :loading="passwordLoading" @click="savePassword">
            更新密码
          </el-button>
        </el-form>
      </el-card>
    </section>
  </div>
</template>
