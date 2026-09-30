<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, schoolApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import loginBackground from '@/assets/login/login-background.jpg'
import loginStudent from '@/assets/login/login-student.jpg'
import loginTeacher from '@/assets/login/login-teacher.jpg'
import loginAdmin from '@/assets/login/login-admin.png'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)
const resetLoading = ref(false)
const schools = ref([])
const sendingCode = ref(false)
const loginMode = ref('PASSWORD')
const currentView = ref('roles')
const selectedRole = ref('student')
const countdown = ref(0)
let timer = null

const roleMeta = {
  student: { label: '生徒', title: '生徒入席', welcome: '晨诵暮读 · 笃学不倦', placeholder: '请输入学号、邮箱或姓名', button: '入 学', subtitle: '童子问道', description: '晨诵暮读 · 笃学不倦', image: loginStudent },
  teacher: { label: '山长', title: '山长登堂', welcome: '传道授业 · 解惑明经', placeholder: '请输入教职编号、邮箱或姓名', button: '登 堂', subtitle: '授业夫子', description: '传道授业 · 解惑明经', image: loginTeacher },
  admin: { label: '监院', title: '监院掌院', welcome: '掌院治学 · 纲纪四方', placeholder: '请输入院务账号或邮箱', button: '掌 院', subtitle: '掌院老者', description: '掌院治学 · 纲纪四方', image: loginAdmin }
}
const selectedRoleMeta = computed(() => roleMeta[selectedRole.value])
const passwordForm = reactive({ account: '', password: '', schoolCode: '' })
const resetForm = reactive({ channel: 'EMAIL', target: '', code: '', newPassword: '', confirmPassword: '' })
const resetTargetLabel = computed(() => '邮箱')
const resetTargetPlaceholder = computed(() => '请输入邮箱')

function selectRole(role) {
  if (!roleMeta[role]) return
  selectedRole.value = role
  currentView.value = 'login'
  loginMode.value = 'PASSWORD'
}
function returnToRoles() {
  currentView.value = 'roles'
  loginMode.value = 'PASSWORD'
}
function startCountdown(seconds = 60) {
  countdown.value = seconds
  window.clearInterval(timer)
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      window.clearInterval(timer)
      timer = null
    }
  }, 1000)
}
async function sendResetCode() {
  if (sendingCode.value || countdown.value > 0) return
  if (!resetForm.target.trim()) {
    ElMessage.warning(`请输入${resetTargetLabel.value}`)
    return
  }
  sendingCode.value = true
  try {
    const result = await authApi.sendCode({ channel: resetForm.channel, target: resetForm.target, scene: 'RESET_PASSWORD' })
    startCountdown(result?.cooldownSeconds || 60)
    ElMessage.success('验证码已发送，请注意查收')
  } finally {
    sendingCode.value = false
  }
}
async function submitPassword() {
  if (!passwordForm.account.trim() || !passwordForm.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await authStore.login({ username: passwordForm.account.trim(), password: passwordForm.password, schoolCode: passwordForm.schoolCode || null })
    ElMessage.success('登录成功')
    goAfterLogin()
  } finally {
    loading.value = false
  }
}
async function resetPassword() {
  if (!resetForm.target.trim() || !resetForm.code.trim()) {
    ElMessage.warning(`请输入${resetTargetLabel.value}和验证码`)
    return
  }
  if (resetForm.newPassword.length < 6 || resetForm.newPassword.length > 20) {
    ElMessage.warning('新密码长度需要为6-20位')
    return
  }
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  resetLoading.value = true
  try {
    await authApi.resetPasswordByCode({ ...resetForm })
    Object.assign(resetForm, { target: '', code: '', newPassword: '', confirmPassword: '' })
    loginMode.value = 'PASSWORD'
    ElMessage.success('密码重置成功，请使用邮箱和新密码登录')
  } finally {
    resetLoading.value = false
  }
}
function goAfterLogin() {
  if (authStore.user?.identityStatus !== 'VERIFIED') {
    router.push('/profile')
    return
  }
  router.push(route.query.redirect || '/dashboard')
}
onMounted(async () => {
  schools.value = await schoolApi.active()
  const routeRole = String(route.query.role || '')
  if (roleMeta[routeRole]) selectRole(routeRole)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <main class="academy-login-page" :style="{ '--academy-login-background': `url(${loginBackground})` }">
    <div class="academy-login-bg" aria-hidden="true"></div>
    <div class="academy-login-veil" aria-hidden="true"></div>
    <section v-if="currentView === 'roles'" class="academy-view academy-role-view">
      <div class="academy-role-stage">
        <h1 class="academy-main-title">请选择你的角色</h1>
        <span class="academy-title-seal">書院</span>
        <div class="academy-rule-line"><span></span></div>
        <p class="academy-sub-title">生徒 · 山长 · 监院</p>
        <div class="academy-role-cards">
          <button v-for="role in Object.keys(roleMeta)" :key="role" class="academy-role-card" type="button" @click="selectRole(role)">
            <span class="academy-card-frame">
              <img :src="roleMeta[role].image" :alt="`${roleMeta[role].label}角色`" />
              <span class="academy-enter-seal">{{ role === 'student' ? '入' : role === 'teacher' ? '登' : '掌' }}</span>
            </span>
            <strong>{{ roleMeta[role].label }}</strong>
            <small>{{ roleMeta[role].subtitle }}</small>
            <em>{{ roleMeta[role].description }}</em>
          </button>
        </div>
        <p class="academy-tip-line">—— 移步而至，即见其人 · 轻叩而入 ——</p>
      </div>
    </section>
    <section v-else class="academy-view academy-login-view">
      <div class="academy-login-wrap">
        <section class="academy-login-panel">
          <button class="academy-back-button" type="button" @click="returnToRoles">← 返回选择角色</button>
          <div class="academy-plaque">{{ selectedRoleMeta.title }}</div>
          <p class="academy-login-welcome">{{ selectedRoleMeta.welcome }}</p>
          <form v-if="loginMode === 'PASSWORD'" class="academy-form" @submit.prevent="submitPassword">
            <label class="academy-field"><span>账号</span><input v-model="passwordForm.account" name="login-account" autocomplete="username" :placeholder="selectedRoleMeta.placeholder" /></label>
            <label class="academy-field"><span>学校 <small>（使用学号/工号登录时可选）</small></span><select v-model="passwordForm.schoolCode" name="login-school"><option value="">不选择学校</option><option v-for="school in schools" :key="school.id" :value="school.code">{{ school.name }}（{{ school.code }}）</option></select></label>
            <label class="academy-field"><span>密码</span><input v-model="passwordForm.password" name="login-password" type="password" autocomplete="current-password" placeholder="请输入密码" /></label>
            <button class="academy-login-button" type="submit" :disabled="loading">{{ loading ? '正在进入…' : selectedRoleMeta.button }}</button>
            <div class="academy-login-foot"><button type="button" @click="router.push('/register')">注册账号</button><button type="button" @click="loginMode = 'RESET'">忘记密码？</button></div>
          </form>
          <form v-else class="academy-form" @submit.prevent="resetPassword">
            <label class="academy-field"><span>{{ resetTargetLabel }}</span><input v-model="resetForm.target" name="password-reset-target" autocomplete="email" :placeholder="resetTargetPlaceholder" /></label>
            <label class="academy-field"><span>验证码</span><div class="academy-code-row"><input v-model="resetForm.code" name="verification-code" inputmode="numeric" placeholder="请输入6位验证码" /><button type="button" :disabled="sendingCode || countdown > 0" @click="sendResetCode">{{ countdown > 0 ? `${countdown}s` : '获取验证码' }}</button></div></label>
            <label class="academy-field"><span>新密码</span><input v-model="resetForm.newPassword" name="password-reset-new" type="password" autocomplete="new-password" placeholder="请输入6-20位新密码" /></label>
            <label class="academy-field"><span>确认新密码</span><input v-model="resetForm.confirmPassword" name="password-reset-confirm" type="password" autocomplete="new-password" placeholder="请再次输入新密码" /></label>
            <button class="academy-login-button" type="submit" :disabled="resetLoading">{{ resetLoading ? '正在重置…' : '重置密码' }}</button>
            <div class="academy-login-foot academy-login-foot-single"><button type="button" @click="loginMode = 'PASSWORD'">返回登录</button></div>
          </form>
          <p class="academy-demo-note">云塾虚拟教研室 · 安全登录</p>
        </section>
      </div>
    </section>
  </main>
</template>

<style scoped>
:global(html),:global(body),:global(#app){min-height:100%;}:global(body){margin:0;}
.academy-login-page{position:relative;min-height:100vh;overflow:hidden;color:#2e2318;font-family:"Noto Serif SC","Songti SC","SimSun",serif;background:#1e1712}.academy-login-bg{position:fixed;inset:-40px;z-index:0;background:var(--academy-login-background) center/cover no-repeat;animation:academy-kb 28s ease-in-out infinite alternate}.academy-login-veil{position:fixed;inset:0;z-index:1;background:linear-gradient(180deg,rgba(24,15,8,.62),rgba(24,15,8,.28) 42%,rgba(24,15,8,.72))}.academy-view{position:relative;z-index:2;min-height:100vh}.academy-role-stage{display:flex;min-height:100vh;flex-direction:column;align-items:center;justify-content:center;padding:5vh 24px 6vh;text-align:center}.academy-main-title{margin:0;color:#f5edda;font-family:"STKaiti","KaiTi","楷体",serif;font-size:clamp(46px,6.4vw,78px);font-weight:400;letter-spacing:.22em;line-height:1.15;text-indent:.22em;text-shadow:0 6px 24px rgba(0,0,0,.6),0 2px 6px rgba(0,0,0,.4)}.academy-title-seal{display:inline-block;margin:14px auto 0;padding:5px 10px 3px;border:3px solid #96372a;border-radius:6px;color:#96372a;background:rgba(245,237,218,.14);font-family:"STKaiti","KaiTi","楷体",serif;font-size:20px;letter-spacing:.2em;text-indent:.2em;transform:rotate(-4deg)}.academy-rule-line{display:flex;width:min(440px,70vw);align-items:center;justify-content:center;gap:16px;margin:22px 0 6px}.academy-rule-line::before,.academy-rule-line::after{flex:1;height:1px;background:linear-gradient(90deg,transparent,rgba(227,201,138,.85));content:""}.academy-rule-line::after{background:linear-gradient(90deg,rgba(227,201,138,.85),transparent)}.academy-rule-line span{width:8px;height:8px;border:1px solid #e3c98a;transform:rotate(45deg)}.academy-sub-title{margin:10px 0 0;color:#e3c98a;font-size:clamp(13px,1.6vw,16px);letter-spacing:.6em;text-indent:.6em;text-shadow:0 2px 8px rgba(0,0,0,.6)}.academy-role-cards{display:flex;flex-wrap:wrap;align-items:flex-start;justify-content:center;gap:clamp(26px,4.5vw,58px);margin-top:clamp(34px,5vh,56px)}.academy-role-card{width:clamp(150px,17vw,224px);padding:0;border:0;color:inherit;background:transparent;cursor:pointer;font:inherit;transition:transform .45s cubic-bezier(.2,.8,.2,1)}.academy-card-frame{position:relative;display:block;aspect-ratio:3/4;overflow:hidden;border:2px solid rgba(227,201,138,.5);border-radius:14px;background:#101010;box-shadow:0 20px 44px rgba(0,0,0,.5);transition:border-color .4s,box-shadow .4s}.academy-card-frame img{width:100%;height:100%;object-fit:cover;object-position:center top;transition:transform .6s cubic-bezier(.2,.8,.2,1)}.academy-card-frame::after{position:absolute;inset:0;pointer-events:none;background:linear-gradient(to top,rgba(24,15,8,.62),transparent 42%);content:""}.academy-enter-seal{position:absolute;right:12px;bottom:12px;z-index:2;display:flex;width:40px;height:40px;align-items:center;justify-content:center;border:2px solid #e3c98a;border-radius:7px;color:#f5edda;background:linear-gradient(180deg,#a3402f,#7c2b20);font-family:"STKaiti","KaiTi","楷体",serif;font-size:19px;opacity:0;transform:scale(.6) rotate(-10deg);transition:opacity .35s,transform .35s}.academy-role-card:hover,.academy-role-card:focus-visible{outline:none;transform:translateY(-14px)}.academy-role-card:hover .academy-enter-seal,.academy-role-card:focus-visible .academy-enter-seal{opacity:1;transform:scale(1) rotate(0)}.academy-role-card:hover .academy-card-frame,.academy-role-card:focus-visible .academy-card-frame{border-color:#e3c98a;box-shadow:0 30px 66px rgba(0,0,0,.6),0 0 0 3px rgba(201,161,91,.35)}.academy-role-card:hover .academy-card-frame img,.academy-role-card:focus-visible .academy-card-frame img{transform:scale(1.1)}.academy-role-card strong{display:block;margin-top:16px;color:#f5edda;font-family:"STKaiti","KaiTi","楷体",serif;font-size:clamp(28px,3vw,40px);font-weight:400;letter-spacing:.34em;line-height:1.2;text-indent:.34em;text-shadow:0 3px 12px rgba(0,0,0,.65)}.academy-role-card small{display:block;margin-top:4px;color:#e3c98a;font-size:12px;letter-spacing:.3em;text-indent:.3em}.academy-role-card em{display:block;margin-top:8px;color:rgba(245,237,218,.72);font-size:13px;font-style:normal;letter-spacing:.14em}.academy-tip-line{margin:clamp(26px,4vh,44px) 0 0;color:rgba(227,201,138,.65);font-size:12px;letter-spacing:.3em;text-indent:.3em;animation:academy-breathe 2.6s ease-in-out infinite}.academy-login-wrap{display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px}.academy-login-panel{position:relative;width:min(430px,94vw);padding:52px 40px 34px;border:1px solid rgba(201,161,91,.45);border-radius:18px;background:repeating-linear-gradient(45deg,rgba(120,95,60,.045) 0 2px,transparent 2px 6px),linear-gradient(165deg,#f9f2e1,#ede2c7);box-shadow:0 34px 90px rgba(0,0,0,.6),0 0 0 1px rgba(46,35,24,.08)}.academy-back-button{position:absolute;top:16px;left:18px;padding:3px 0;border:0;color:#6b5944;background:transparent;cursor:pointer;font:inherit;font-size:12px}.academy-back-button:hover{color:#96372a}.academy-plaque{position:absolute;top:-27px;left:50%;padding:9px 30px 6px;border:2px solid #c9a15b;border-radius:10px;color:#f5edda;background:linear-gradient(180deg,#a3402f,#7c2b20);box-shadow:0 10px 22px rgba(0,0,0,.4);font-family:"STKaiti","KaiTi","楷体",serif;font-size:30px;letter-spacing:.34em;text-indent:.34em;transform:translateX(-50%);white-space:nowrap}.academy-login-welcome{margin:2px 0 0;color:#6b5944;font-size:14px;letter-spacing:.34em;text-align:center;text-indent:.34em}.academy-form{margin-top:20px}.academy-field{display:block;margin-top:22px}.academy-field>span{display:block;margin-bottom:7px;color:#6b5944;font-size:12px;letter-spacing:.22em}.academy-field>span small{color:rgba(107,89,68,.7);font-size:11px;letter-spacing:0}.academy-field input,.academy-field select{width:100%;padding:9px 2px 8px;border:0;border-bottom:1.5px solid rgba(46,35,24,.32);border-radius:0;outline:none;color:#2e2318;background:transparent;font:inherit;font-size:16px;transition:border-color .3s}.academy-field select{cursor:pointer;font-size:14px}.academy-field input::placeholder{color:rgba(107,89,68,.5);font-size:14px}.academy-field input:focus,.academy-field select:focus{border-bottom-color:#96372a}.academy-login-button{width:100%;margin-top:32px;padding:13px 0 11px;border:1px solid #c9a15b;border-radius:10px;color:#f5edda;background:linear-gradient(180deg,#8e3b2b,#6e261b);box-shadow:0 12px 24px rgba(0,0,0,.32);cursor:pointer;font-family:"STKaiti","KaiTi","楷体",serif;font-size:22px;letter-spacing:.44em;text-indent:.44em;transition:transform .3s,box-shadow .3s,filter .3s}.academy-login-button:hover:not(:disabled){filter:brightness(1.07);box-shadow:0 16px 34px rgba(0,0,0,.4);transform:translateY(-2px)}.academy-login-button:disabled{cursor:not-allowed;opacity:.6}.academy-login-foot{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-top:22px}.academy-login-foot button{padding:0 0 2px;border:0;border-bottom:1px dashed rgba(107,89,68,.5);color:#6b5944;background:transparent;cursor:pointer;font:inherit;font-size:13px;letter-spacing:.12em}.academy-login-foot button:hover{color:#96372a}.academy-login-foot-single{justify-content:flex-end}.academy-code-row{display:flex;align-items:stretch;gap:10px}.academy-code-row input{flex:1;min-width:0}.academy-code-row button{padding:0 12px;border:1px solid rgba(150,55,42,.45);border-radius:6px;color:#96372a;background:rgba(255,255,255,.18);cursor:pointer;font:inherit;font-size:12px;white-space:nowrap}.academy-code-row button:disabled{cursor:not-allowed;opacity:.5}.academy-demo-note{margin:26px 0 0;color:rgba(107,89,68,.55);font-size:11px;letter-spacing:.08em;text-align:center}@keyframes academy-kb{from{transform:scale(1) translate(0,0)}to{transform:scale(1.08) translate(-10px,-6px)}}@keyframes academy-breathe{0%,100%{opacity:.45}50%{opacity:1}}
@media (max-width:760px){.academy-role-stage{justify-content:flex-start;overflow-y:auto;padding-top:7vh}.academy-role-cards{flex-direction:column;align-items:center;gap:30px}.academy-role-card{width:min(58vw,236px)}.academy-login-panel{padding:48px 26px 30px}.academy-plaque{padding:8px 20px 5px;font-size:24px}.academy-sub-title{font-size:15px}.academy-role-card small,.academy-role-card em,.academy-tip-line{font-size:14px}.academy-login-foot button,.academy-demo-note{font-size:13px}}
@media (prefers-reduced-motion:reduce){.academy-login-bg,.academy-tip-line{animation:none}.academy-role-card,.academy-card-frame img,.academy-enter-seal{transition:none}}
</style>
