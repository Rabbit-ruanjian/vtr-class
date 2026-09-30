<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, schoolApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
let codeTimer = null
const schools = ref([])
const accountType = ref('STUDENT')

const form = reactive({
  channel: 'EMAIL',
  target: '',
  code: '',
  password: '',
  confirmPassword: '',
  unitName: '',
  identityNumber: '',
  schoolId: null
})

const contactLabel = computed(() => '邮箱')
const contactPlaceholder = computed(() => '请输入邮箱')
const identityLabel = computed(() => accountType.value === 'STUDENT' ? '学号' : '工号')

watch(() => form.schoolId, (schoolId) => {
  if (!schoolId) {
    form.unitName = ''
    form.identityNumber = ''
  }
})

watch(() => form.channel, () => {
  form.target = ''
  form.code = ''
  countdown.value = 0
  window.clearInterval(codeTimer)
  codeTimer = null
})

function startCountdown(seconds = 60) {
  countdown.value = seconds
  window.clearInterval(codeTimer)
  codeTimer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      window.clearInterval(codeTimer)
      codeTimer = null
    }
  }, 1000)
}

async function sendRegistrationCode() {
  if (sendingCode.value || countdown.value > 0) return
  const target = form.target.trim()
  if (!target) {
    ElMessage.warning(`请输入${contactLabel.value}`)
    return
  }
  sendingCode.value = true
  try {
    const result = await authApi.sendCode({ channel: form.channel, target, scene: 'REGISTER' })
    startCountdown(result?.cooldownSeconds || 60)
    ElMessage.success(`验证码已发送至${contactLabel.value}，请注意查收`)
  } finally {
    sendingCode.value = false
  }
}

async function submit() {
  if (!form.target.trim()) {
    ElMessage.warning(`请输入${contactLabel.value}`)
    return
  }
  if (!/^\d{6}$/.test(form.code.trim())) {
    ElMessage.warning('请输入6位验证码，验证后才能注册')
    return
  }
  if (form.password.length < 6 || form.password.length > 20) {
    ElMessage.warning('密码长度需要为6-20位')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (accountType.value === 'TEACHER' && !form.schoolId) {
    ElMessage.warning('教师账号必须选择所属学校')
    return
  }
  if (accountType.value === 'TEACHER' && !form.identityNumber.trim()) {
    ElMessage.warning('教师账号必须填写工号')
    return
  }
  if (form.schoolId && !form.identityNumber.trim()) {
    ElMessage.warning(`请填写${identityLabel.value}`)
    return
  }
  if (!form.schoolId && (form.identityNumber.trim() || form.unitName.trim())) {
    ElMessage.warning('未选择学校时不能填写学号、工号或单位信息')
    return
  }
  loading.value = true
  try {
    const payload = {
      password: form.password,
      role: accountType.value,
      code: form.code.trim(),
      unitName: accountType.value === 'TEACHER' ? form.unitName.trim() : null,
      identityType: accountType.value,
      identityNumber: form.identityNumber.trim(),
      schoolId: form.schoolId,
      email: form.channel === 'EMAIL' ? form.target.trim() : null,
  phone: null
    }
    await authStore.register(payload)
    ElMessage.success('注册成功，请使用邮箱/手机号和密码登录')
    router.push('/login')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  schools.value = await schoolApi.active()
})

onBeforeUnmount(() => {
  window.clearInterval(codeTimer)
})
</script>

<template>
  <main class="page-stack register-page">
    <section class="surface-card register-panel">
      <div class="register-panel-head">
        <div>
          <h3>创建账号</h3>
          <p>学生用学校名单中的学号注册，教师用学校预置工号注册；不选学校即可创建普通账号。</p>
        </div>
        <el-tag effect="light" :type="accountType === 'STUDENT' ? 'success' : 'warning'">
          {{ accountType === 'STUDENT' ? '学生' : '教师' }}
        </el-tag>
      </div>

      <el-form label-position="top" autocomplete="off" class="register-form" @submit.prevent="submit">
        <el-form-item label="账号类型">
          <el-radio-group v-model="accountType">
            <el-radio-button value="STUDENT">学生 / 学号</el-radio-button>
            <el-radio-button value="TEACHER">教师 / 工号</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <div class="register-grid">
          <el-form-item :label="contactLabel" class="register-span-2">
            <el-input v-model="form.target" name="registration-contact" autocomplete="off" :placeholder="contactPlaceholder" size="large" />
          </el-form-item>

          <el-form-item label="验证码" class="register-span-2">
            <el-input v-model="form.code" name="registration-code" autocomplete="one-time-code" inputmode="numeric" maxlength="6" placeholder="请输入收到的6位验证码" size="large">
              <template #append>
                <el-button type="button" :disabled="sendingCode || countdown > 0" :loading="sendingCode" @click="sendRegistrationCode">
                  {{ countdown > 0 ? `${countdown}s后重发` : '获取验证码' }}
                </el-button>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="设置密码">
            <el-input v-model="form.password" name="registration-password" type="password" autocomplete="new-password" show-password placeholder="请输入6-20位密码" size="large" />
          </el-form-item>

          <el-form-item label="确认密码">
            <el-input v-model="form.confirmPassword" name="registration-password-confirm" type="password" autocomplete="new-password" show-password placeholder="请再次输入密码" size="large" />
          </el-form-item>

          <el-form-item label="所属学校" class="register-span-2" :required="accountType === 'TEACHER'">
            <el-select v-model="form.schoolId" clearable :placeholder="accountType === 'TEACHER' ? '教师必须选择所属学校' : '可不选择，暂不绑定学校'" style="width:100%" size="large">
              <el-option v-for="school in schools" :key="school.id" :label="`${school.name}（${school.code}）`" :value="school.id" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="accountType === 'TEACHER' && form.schoolId" label="单位/院系（可选）" class="register-span-2">
            <el-input v-model="form.unitName" name="registration-unit" autocomplete="off" placeholder="可留空，系统使用学校教师名单中的部门" size="large" />
          </el-form-item>

          <el-form-item v-if="form.schoolId || accountType === 'TEACHER'" :label="identityLabel" class="register-span-2" required>
            <el-input v-model="form.identityNumber" name="registration-identity-number" autocomplete="off" :placeholder="accountType === 'STUDENT' ? '请输入学校名单中的学号' : '请输入工号'" size="large" />
          </el-form-item>
        </div>

        <div class="register-hint">
          {{ accountType === 'STUDENT' ? '必须先完成邮箱验证码验证；选择学校后，学号必须存在于该校行政班名单中，注册后自动绑定姓名和行政班。' : '必须先完成邮箱验证码验证；教师必须选择学校，并使用学校教师工号名单中的有效工号，注册后自动绑定教师身份。' }}
        </div>

        <div class="hero-actions register-actions">
          <el-button type="primary" native-type="submit" size="large" :loading="loading">
            创建账号
          </el-button>
          <el-button size="large" plain @click="router.push('/login')">
            返回登录
          </el-button>
        </div>
      </el-form>
    </section>
  </main>
</template>
