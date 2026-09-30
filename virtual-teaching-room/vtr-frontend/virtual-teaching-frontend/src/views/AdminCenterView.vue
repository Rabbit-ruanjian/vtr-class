<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHero from '@/components/PageHero.vue'
import AcademicClassesView from '@/views/AcademicClassesView.vue'
import AdminActivityReviewView from '@/views/AdminActivityReviewView.vue'
import SchoolManagementView from '@/views/SchoolManagementView.vue'
import {
  academicClassApi,
  adminApi,
  authApi,
  coursewareApi,
  userApi,
  schoolApi
} from '@/api'
import { useAuthStore } from '@/stores/auth'
import { roleLabel, roleOptions, userStatusOptions } from '@/config/options'
import { formatDateTime, optionLabel } from '@/utils/format'
import { normalizePage } from '@/utils/page'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const userLoading = ref(false)
const contentLoading = ref(false)
const resourceReviewLoading = ref(false)
const identityLoading = ref(false)

const activeTab = ref('users')
const auditTab = ref('identity')

const users = ref([])
const schools = ref([])
const userEditStructure = ref({ departments: [] })
const usersTotal = ref(0)
const contentList = ref([])
const contentTotal = ref(0)
const selectedUserIds = ref([])

const userQuery = reactive({
  page: 1,
  size: 20,
  keyword: '',
  role: '',
  status: '',
  schoolId: null
})

const contentQuery = reactive({
  page: 1,
  size: 10,
  status: 'PENDING'
})

const resourceReviewQuery = reactive({
  page: 1,
  size: 10,
  status: 'PENDING'
})

const userStatusDialogVisible = ref(false)
const userRoleDialogVisible = ref(false)
const userEditDialogVisible = ref(false)
const userEditSaving = ref(false)
const userEditClasses = ref([])
const userDetailVisible = ref(false)
const userDetailLoading = ref(false)
const academicClassDialogVisible = ref(false)
const academicClassLoading = ref(false)
const academicClassSaving = ref(false)
const batchReviewVisible = ref(false)
const contentReviewVisible = ref(false)
const contentDetailVisible = ref(false)

const userStatusForm = reactive({
  id: null,
  status: 'ACTIVE',
  reason: ''
})

const userRoleForm = reactive({
  id: null,
  role: 'STUDENT'
})

const userEditForm = reactive({
  id: null,
  nickname: '',
  email: '',
  phone: '',
  schoolId: null,
  identityType: '',
  identityNumber: '',
  unitName: '',
  academicClassId: null
})

const academicClassForm = reactive({
  userId: null,
  academicClassId: null
})

const batchReviewForm = reactive({
  action: 'APPROVE',
  reason: ''
})

const contentReviewForm = reactive({
  id: null,
  action: 'PASS',
  remark: ''
})

const contentDetail = ref(null)
const selectedUser = ref(null)
const academicClasses = ref([])
const resourceReviewList = ref([])
const resourceReviewTotal = ref(0)
const identityList = ref([])
const identityTotal = ref(0)
const identityQuery = reactive({ page: 1, size: 10, keyword: '', identityStatus: 'PENDING' })

const resourceTypeLabels = {
  'teaching-outline': '教学大纲',
  'teaching-video': '教学视频',
  'teaching-courseware': '教学课件',
  'knowledge-map': '知识图谱'
}

function handleUserSelectionChange(rows) {
  selectedUserIds.value = rows.map((item) => item.id)
}

async function loadUsers() {
  userLoading.value = true

  try {
    const pageData = normalizePage(await userApi.list(userQuery))
    users.value = pageData.list
    usersTotal.value = pageData.total
  } finally {
    userLoading.value = false
  }
}

async function loadContent() {
  contentLoading.value = true

  try {
    const pageData = normalizePage(await adminApi.pendingContent(contentQuery))
    contentList.value = pageData.list
    contentTotal.value = pageData.total
  } finally {
    contentLoading.value = false
  }
}

async function loadResourceReviews() {
  resourceReviewLoading.value = true
  try {
    const pageData = normalizePage(await coursewareApi.pendingReviews(resourceReviewQuery))
    resourceReviewList.value = pageData.list
    resourceReviewTotal.value = pageData.total
  } finally {
    resourceReviewLoading.value = false
  }
}

async function loadIdentityReviews() {
  identityLoading.value = true
  try {
    const pageData = normalizePage(await userApi.list(identityQuery))
    identityList.value = pageData.list
    identityTotal.value = pageData.total
  } finally {
    identityLoading.value = false
  }
}

async function reviewIdentity(row, approved) {
  let reason = ''
  try {
    if (approved) {
      await ElMessageBox.confirm(`确认通过“${row.nickname || row.username}”的身份认证吗？`, '身份认证', { type: 'warning' })
    } else {
      const result = await ElMessageBox.prompt('请填写退回原因，用户可修改后重新提交。', '退回身份认证', {
        inputType: 'textarea',
        inputValidator: value => String(value || '').trim() ? true : '请填写退回原因'
      })
      reason = result.value.trim()
    }
    await authApi.reviewIdentityBinding(row.id, approved, reason)
    ElMessage.success(approved ? '身份认证已通过' : '身份认证已退回')
    await loadIdentityReviews()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '身份认证处理失败')
  }
}

function resourceStatusInfo(status) {
  return {
    PENDING: ['待审核', 'warning'],
    ACTIVE: ['已发布', 'success'],
    REJECTED: ['已驳回', 'danger'],
    ARCHIVED: ['已归档', 'info']
  }[status] || [status || '未知', 'info']
}

function moderationReasonLabel(level) {
  return { HIGH: '系统高风险', MEDIUM: '人工升级', REPORTED: '用户举报', APPEAL: '用户申诉' }[level] || level || '未知'
}

async function reviewResource(row, action) {
  const isMindMapReview = row.resourceType === 'knowledge-map'
  const reviewTitle = isMindMapReview
     ? (action === 'APPROVE' ? '审核知识图谱' : '驳回知识图谱')
    : (action === 'APPROVE' ? '审核通过' : '驳回资源')

  try {
    const { value } = await ElMessageBox.prompt(
      action === 'APPROVE' ? '可填写审核备注。' : '请填写驳回原因。',
      reviewTitle,
      { inputType: 'textarea', inputValidator: (text) => action === 'REJECT' && !String(text || '').trim() ? '驳回原因不能为空' : true }
    )
    const payload = { action, remark: value || '' }
    if (row.resourceType === 'teaching-outline') await coursewareApi.reviewOutline(row.id, payload)
    if (row.resourceType === 'teaching-video') await coursewareApi.reviewVideo(row.id, payload)
    if (row.resourceType === 'teaching-courseware') await coursewareApi.reviewCourseware(row.id, payload)
    if (row.resourceType === 'knowledge-map') await coursewareApi.reviewKnowledgeMap(row.id, payload)
    ElMessage.success(
      isMindMapReview
         ? '知识图谱审核结果已提交，教师将收到消息通知'
        : '审核结果已提交，教师将收到消息通知'
    )
    await loadResourceReviews()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '审核失败')
  }
}

function openResourceReview(row) {
  const resourceType = row.resourceType === 'knowledge-map'
    ? 'teaching-outline'
    : row.resourceType

  router.push({
    path: `/courseware/${resourceType}`,
    query: {
      courseId: row.courseId,
      chapter: row.chapter
    }
  })
}

function openUserStatus(row) {
  userStatusForm.id = row.id
  userStatusForm.status = row.status || 'ACTIVE'
  userStatusForm.reason = ''
  userStatusDialogVisible.value = true
}

async function openUserDetail(row) {
  selectedUser.value = row
  userDetailVisible.value = true
  userDetailLoading.value = true
  try {
    selectedUser.value = await userApi.detail(row.id)
  } finally {
    userDetailLoading.value = false
  }
}

async function openAcademicClass(row = selectedUser.value) {
  if (!row?.id || row.role !== 'STUDENT') return
  academicClassForm.userId = row.id
  academicClassForm.academicClassId = row.academicClassId ?? null
  academicClassDialogVisible.value = true
  academicClassLoading.value = true
  try {
    const pageData = normalizePage(await academicClassApi.list({ page: 1, size: 100, status: 'ACTIVE', schoolId: row.schoolId || selectedUser.value?.schoolId || null }))
    academicClasses.value = pageData.list
  } finally {
    academicClassLoading.value = false
  }
}

async function saveAcademicClass() {
  if (!academicClassForm.userId || !academicClassForm.academicClassId) {
    ElMessage.warning('请选择行政班')
    return
  }
  academicClassSaving.value = true
  try {
    await academicClassApi.assignStudent(academicClassForm.userId, academicClassForm.academicClassId)
    ElMessage.success('行政班已更新')
    academicClassDialogVisible.value = false
    if (selectedUser.value?.id === academicClassForm.userId) {
      selectedUser.value = await userApi.detail(academicClassForm.userId)
    }
    await loadUsers()
  } finally {
    academicClassSaving.value = false
  }
}

async function resetUserPassword(row = selectedUser.value) {
  if (!row?.id) return
  try {
    await ElMessageBox.confirm(
      `确认重置“${row.nickname || row.username}”的密码吗？重置后原密码将立即失效。`,
      '重置密码',
      { type: 'warning', confirmButtonText: '确认重置', cancelButtonText: '取消' }
    )
    const temporaryPassword = await userApi.resetPassword(row.id)
    await ElMessageBox.alert(
      `临时密码：${temporaryPassword}\n\n请将临时密码安全转交给用户。关闭窗口后不会再次显示。`,
      '密码已重置',
      { confirmButtonText: '我已保存' }
    )
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '重置密码失败')
  }
}

function openUserRole(row) {
  userRoleForm.id = row.id
  userRoleForm.role = ['ADMIN', 'ADMINISTRATOR', 'SUPER_ADMIN'].includes(row.role)
    ? 'ADMIN'
    : row.role || 'STUDENT'
  userRoleDialogVisible.value = true
}

async function loadUserEditClasses() {
  if (!userEditForm.schoolId || userEditForm.identityType !== 'STUDENT') {
    userEditClasses.value = []
    return
  }
  const pageData = normalizePage(await academicClassApi.list({
    page: 1, size: 100, status: 'ACTIVE', schoolId: userEditForm.schoolId
  }))
  userEditClasses.value = pageData.list
}

async function loadUserEditStructure() {
  userEditStructure.value = userEditForm.schoolId
    ? await schoolApi.academicStructure(userEditForm.schoolId)
    : { departments: [] }
}

async function openUserEdit(row = selectedUser.value) {
  if (!row?.id) return
  Object.assign(userEditForm, {
    id: row.id,
    nickname: row.nickname || '',
    email: row.email || '',
    phone: row.phone || '',
    schoolId: row.schoolId || null,
    identityType: row.identityType || (row.role === 'STUDENT' ? 'STUDENT' : 'TEACHER'),
    identityNumber: row.identityNumber || '',
    unitName: row.unitName || '',
    academicClassId: row.academicClassId || null
  })
  userEditDialogVisible.value = true
  await Promise.all([loadUserEditClasses(), loadUserEditStructure()])
}

async function saveUserEdit() {
  if (selectedUser.value?.role === 'ADMIN' && !selectedUser.value?.adminScopeUnbound && !userEditForm.schoolId) {
    ElMessage.warning('管理员必须绑定一所学校')
    return
  }
  userEditSaving.value = true
  try {
    await userApi.update(userEditForm.id, { ...userEditForm })
    ElMessage.success('用户账号信息已更新')
    userEditDialogVisible.value = false
    await loadUsers()
    if (selectedUser.value?.id === userEditForm.id) selectedUser.value = await userApi.detail(userEditForm.id)
  } finally {
    userEditSaving.value = false
  }
}

async function saveUserStatus() {
  await userApi.updateStatus(
    userStatusForm.id,
    userStatusForm.status,
    userStatusForm.reason
  )
  ElMessage.success('用户状态已更新')
  userStatusDialogVisible.value = false
  await loadUsers()
}

async function saveUserRole() {
  await userApi.updateRole(userRoleForm.id, userRoleForm.role)
  ElMessage.success('用户角色已更新')
  userRoleDialogVisible.value = false
  await loadUsers()
}

async function removeUser(id) {
  await ElMessageBox.confirm('确定删除这个用户吗？', '删除用户', { type: 'warning' })
  await userApi.remove(id)
  ElMessage.success('用户已删除')
  await loadUsers()
}

async function batchReviewUsers() {
  await userApi.batchReview({
    userIds: selectedUserIds.value,
    action: batchReviewForm.action,
    reason: batchReviewForm.reason
  })
  ElMessage.success(batchReviewForm.action === 'APPROVE' ? '账号已批量启用' : '账号已批量封禁')
  batchReviewVisible.value = false
  selectedUserIds.value = []
  await loadUsers()
}

function openContentReview(row) {
  contentReviewForm.id = row.id
  contentReviewForm.action = 'PASS'
  contentReviewForm.remark = ''
  contentReviewVisible.value = true
}

async function saveContentReview() {
  if (contentReviewForm.action === 'REJECT' && !contentReviewForm.remark.trim()) {
    ElMessage.warning('下架内容时必须填写原因')
    return
  }
  await adminApi.reviewContent(contentReviewForm.id, {
    action: contentReviewForm.action,
    remark: contentReviewForm.remark
  })
  ElMessage.success(contentReviewForm.action === 'PASS' ? '内容已保留' : '内容已下架')
  contentReviewVisible.value = false
  await loadContent()
}

function openContentDetail(row) {
  contentDetail.value = row
  contentDetailVisible.value = true
}

watch(activeTab, (tab) => {
  if (tab === 'users') {
    loadUsers()
  }

  if (tab === 'audit') {
    if (auditTab.value === 'identity') loadIdentityReviews()
    if (auditTab.value === 'risk') loadContent()
    if (auditTab.value === 'resource') loadResourceReviews()
  }
})

watch(auditTab, (tab) => {
  if (activeTab.value !== 'audit') return
  if (tab === 'identity') loadIdentityReviews()
  if (tab === 'risk') loadContent()
  if (tab === 'resource') loadResourceReviews()
})

onMounted(async () => {
  const requestedTab = String(route.query.tab || '')
  const auditTabMap = {
    'activity-review': 'activity',
    content: 'risk',
    risk: 'risk',
    identity: 'identity',
    'resource-review': 'resource',
    'forum-review': 'risk'
  }

  if (requestedTab === 'classes') {
    activeTab.value = 'classes'
  } else if (auditTabMap[requestedTab]) {
    activeTab.value = 'audit'
    auditTab.value = auditTabMap[requestedTab]
  } else if (['schools', 'scope', 'users', 'audit'].includes(requestedTab)) {
    activeTab.value = requestedTab === 'scope' ? 'users' : requestedTab
  }

  schools.value = await schoolApi.list()
  if (activeTab.value === 'users') await loadUsers()
  if (activeTab.value === 'audit') {
    if (auditTab.value === 'identity') await loadIdentityReviews()
    if (auditTab.value === 'risk') await loadContent()
    if (auditTab.value === 'resource') await loadResourceReviews()
  }
})
</script>

<template>
  <div class="page-stack">
    <PageHero
      title="管理中心"
      description="管理组织与账号，只处理身份认证、风险举报和公开发布申请。"
    />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="学校管理" name="schools" />
      <el-tab-pane label="用户管理" name="users" />
      <el-tab-pane label="班级与学生" name="classes" />
      <el-tab-pane label="审核中心" name="audit" />
    </el-tabs>

    <template v-if="activeTab === 'schools'">
      <SchoolManagementView />
    </template>

    <template v-else-if="activeTab === 'users'">
      <div class="toolbar user-toolbar">
        <el-input v-model="userQuery.keyword" class="toolbar-grow" clearable placeholder="搜索姓名、学号、工号或用户名" @keyup.enter="userQuery.page = 1; loadUsers()" />
        <el-select v-model="userQuery.role" clearable placeholder="角色" style="width: 160px;">
          <el-option
            v-for="item in roleOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="userQuery.status" clearable placeholder="状态" style="width: 160px;">
          <el-option
            v-for="item in userStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" @click="userQuery.page = 1; loadUsers()">查询</el-button>
        <el-button plain @click="batchReviewVisible = true" :disabled="!selectedUserIds.length">
          批量调整状态
        </el-button>
      </div>

      <el-card class="surface-card">
        <el-table
          :data="users"
          v-loading="userLoading"
          @selection-change="handleUserSelectionChange"
        >
          <el-table-column type="selection" width="52" />
          <el-table-column label="用户" min-width="220">
            <template #default="{ row }">
              <strong>{{ row.nickname || row.username }}</strong>
              <div class="table-subtext">{{ row.username }}</div>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="120">
            <template #default="{ row }">
              {{ roleLabel(row.role) }}
            </template>
          </el-table-column>
          <el-table-column label="学号/工号" width="150">
            <template #default="{ row }">{{ row.identityNumber || '未绑定' }}</template>
          </el-table-column>
          <el-table-column label="行政班" min-width="180">
            <template #default="{ row }">
              {{ row.academicClassName || '未分配' }}
            </template>
          </el-table-column>
          <el-table-column label="学校" min-width="160">
            <template #default="{ row }">{{ row.schoolName || '未绑定' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              {{ optionLabel(userStatusOptions, row.status) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="340" fixed="right">
            <template #default="{ row }">
              <div class="user-row-actions">
                <el-button link type="primary" @click="openUserDetail(row)">查看</el-button>
                <el-button link type="primary" @click="openUserEdit(row)">编辑账号</el-button>
                <el-button v-if="row.role === 'STUDENT'" link type="primary" @click="openAcademicClass(row)">调整行政班</el-button>
                <el-button v-if="String(row.id) !== String(authStore.user?.id)" link @click="resetUserPassword(row)">重置密码</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div style="display: flex; justify-content: flex-end;">
          <el-pagination
            v-model:current-page="userQuery.page"
            v-model:page-size="userQuery.size"
            layout="total, prev, pager, next"
            :total="usersTotal"
            @current-change="loadUsers"
          />
        </div>
      </el-card>
    </template>

    <template v-else-if="activeTab === 'classes'">
      <AcademicClassesView />
    </template>

    <template v-else-if="activeTab === 'audit'">
      <el-tabs v-model="auditTab" type="card" class="audit-tabs">
        <el-tab-pane label="账号与身份" name="identity">
          <div class="toolbar">
            <el-input v-model="identityQuery.keyword" clearable placeholder="搜索姓名、学号或工号" style="max-width: 340px" @keyup.enter="identityQuery.page = 1; loadIdentityReviews()" />
            <el-button type="primary" :loading="identityLoading" @click="identityQuery.page = 1; loadIdentityReviews()">查询</el-button>
          </div>
          <el-alert title="普通注册账号无需审核；这里只处理用户主动提交的学生/教师身份认证。" type="info" :closable="false" show-icon />
          <el-table :data="identityList" v-loading="identityLoading" style="margin-top: 16px">
            <el-table-column label="用户" min-width="180"><template #default="{ row }"><strong>{{ row.nickname || row.username }}</strong><div class="table-subtext">{{ row.username }}</div></template></el-table-column>
            <el-table-column label="申请身份" width="120"><template #default="{ row }">{{ row.identityType === 'TEACHER' ? '教师' : '学生' }}</template></el-table-column>
            <el-table-column prop="identityNumber" label="学号/工号" width="170" />
            <el-table-column prop="schoolName" label="学校" min-width="180" />
            <el-table-column prop="unitName" label="院系/单位" min-width="180" />
            <el-table-column label="操作" width="200" fixed="right"><template #default="{ row }"><el-button link type="success" @click="reviewIdentity(row, true)">通过</el-button><el-button link type="danger" @click="reviewIdentity(row, false)">退回</el-button></template></el-table-column>
          </el-table>
          <div class="resource-pagination"><el-pagination v-model:current-page="identityQuery.page" layout="total, prev, pager, next" :page-size="identityQuery.size" :total="identityTotal" @current-change="loadIdentityReviews" /></div>
        </el-tab-pane>

        <el-tab-pane label="风险与举报" name="risk">
          <el-alert title="普通社区内容和课程资源不会进入这里；这里只处理系统高风险、用户举报和处置申诉。" type="warning" :closable="false" show-icon />
          <div class="queue-tabs">
            <el-button v-for="item in [{ label: '待处置', value: 'PENDING' }, { label: '保留内容', value: 'PASS' }, { label: '已下架', value: 'REJECT' }, { label: '全部记录', value: 'ALL' }]" :key="item.value" :type="contentQuery.status === item.value ? 'primary' : 'default'" @click="contentQuery.status = item.value; contentQuery.page = 1; loadContent()">{{ item.label }}</el-button>
          </div>
          <div class="toolbar">
            <el-button type="primary" @click="loadContent" :loading="contentLoading">刷新内容</el-button>
          </div>

      <el-table :data="contentList" v-loading="contentLoading">
        <el-table-column prop="contentType" label="对象类型" width="140" />
        <el-table-column prop="contentTitle" label="标题" min-width="240" />
        <el-table-column prop="authorName" label="作者" width="160" />
        <el-table-column label="进入原因" width="130"><template #default="{ row }">{{ moderationReasonLabel(row.riskLevel) }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column label="提交次数" width="100">
          <template #default="{ row }">
            {{ row.submitCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="{ row }">
            <el-button link @click="openContentDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="primary" @click="openContentReview(row)">处置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="contentQuery.page"
          v-model:page-size="contentQuery.size"
          layout="total, prev, pager, next"
          :total="contentTotal"
          @current-change="loadContent"
        />
      </div>
        </el-tab-pane>

        <el-tab-pane label="资源公开申请" name="resource">
          <el-alert title="课程内、班级内和个人资源直接发布；只有申请全校公开的教学资源进入这里。" type="info" :closable="false" show-icon />
          <div class="queue-tabs">
            <el-button v-for="item in [{ label: '待审核', value: 'PENDING' }, { label: '已通过', value: 'ACTIVE' }, { label: '已驳回', value: 'REJECTED' }, { label: '已归档', value: 'ARCHIVED' }]" :key="item.value" :type="resourceReviewQuery.status === item.value ? 'primary' : 'default'" @click="resourceReviewQuery.status = item.value; resourceReviewQuery.page = 1; loadResourceReviews()">{{ item.label }}</el-button>
          </div>
          <div class="toolbar">
            <el-button type="primary" @click="loadResourceReviews" :loading="resourceReviewLoading">刷新申请列表</el-button>
          </div>

      <el-table :data="resourceReviewList" v-loading="resourceReviewLoading">
        <el-table-column label="资源类型" width="130">
          <template #default="{ row }">{{ resourceTypeLabels[row.resourceType] }}</template>
        </el-table-column>
        <el-table-column label="标题" min-width="240">
          <template #default="{ row }">
            <strong>{{ row.title }}</strong>
            <div v-if="row.auditRemark" class="table-subtext">{{ row.auditRemark }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="课程" min-width="180" />
        <el-table-column prop="chapter" label="章节" width="160" />
        <el-table-column prop="teacherName" label="提交教师" width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="resourceStatusInfo(row.status)[1]">{{ resourceStatusInfo(row.status)[0] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openResourceReview(row)">查看资源</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="success" @click="reviewResource(row, 'APPROVE')">通过</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="danger" @click="reviewResource(row, 'REJECT')">驳回</el-button>
            <span v-if="row.status !== 'PENDING'" class="history-label">已记录</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="resource-pagination">
        <el-pagination
          v-model:current-page="resourceReviewQuery.page"
          v-model:page-size="resourceReviewQuery.size"
          layout="total, prev, pager, next"
          :total="resourceReviewTotal"
          @current-change="loadResourceReviews"
        />
      </div>
        </el-tab-pane>

        <el-tab-pane label="校级活动申请" name="activity">
          <el-alert title="课程或班级内部活动由教师直接发布；只有未关联课程/班级的校级活动需要审核。" type="info" :closable="false" show-icon />
          <AdminActivityReviewView />
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-drawer v-model="userDetailVisible" title="用户详情" size="460px">
      <div v-loading="userDetailLoading" class="user-detail-panel" v-if="selectedUser">
        <div class="user-detail-heading">
          <div>
            <h3>{{ selectedUser.nickname || selectedUser.username }}</h3>
            <p>{{ selectedUser.username }}</p>
          </div>
          <el-tag>{{ roleLabel(selectedUser.role) }}</el-tag>
        </div>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="所属学校">{{ selectedUser.schoolName || '未绑定' }}</el-descriptions-item>
          <el-descriptions-item label="账号状态">{{ optionLabel(userStatusOptions, selectedUser.status) }}</el-descriptions-item>
          <el-descriptions-item label="行政班">{{ selectedUser.academicClassName || '未分配' }}</el-descriptions-item>
          <el-descriptions-item label="教学班">{{ selectedUser.classroomName || '未分配' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ selectedUser.email || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="最近登录">{{ formatDateTime(selectedUser.lastLoginTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="user-detail-actions">
          <el-button v-if="String(selectedUser.id) !== String(authStore.user?.id)" type="primary" @click="resetUserPassword(selectedUser)">重置密码</el-button>
          <el-button type="primary" plain @click="openUserEdit(selectedUser)">编辑账号</el-button>
          <el-button v-if="selectedUser.role === 'STUDENT'" @click="openAcademicClass(selectedUser)">调整行政班</el-button>
          <el-button @click="openUserStatus(selectedUser)">修改状态</el-button>
          <el-button @click="openUserRole(selectedUser)">修改身份</el-button>
          <el-button v-if="String(selectedUser.id) !== String(authStore.user?.id)" type="danger" plain @click="removeUser(selectedUser.id); userDetailVisible = false">删除账号</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="userEditDialogVisible" title="编辑账号" width="600px">
      <el-form label-position="top">
        <div class="user-edit-grid">
          <el-form-item label="姓名/昵称">
            <el-input v-model="userEditForm.nickname" maxlength="50" placeholder="请输入姓名或昵称" />
          </el-form-item>
          <el-form-item v-if="selectedUser?.role !== 'SUPER_ADMIN'" label="所属学校" :required="!selectedUser?.adminScopeUnbound">
            <el-select v-model="userEditForm.schoolId" clearable style="width: 100%" placeholder="未绑定学校" @change="userEditForm.academicClassId = null; userEditForm.unitName = ''; loadUserEditClasses(); loadUserEditStructure()">
              <el-option v-for="school in schools.filter(item => item.status === 'ACTIVE')" :key="school.id" :label="`${school.name}（${school.code}）`" :value="school.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="userEditForm.schoolId && !['ADMIN', 'SUPER_ADMIN'].includes(selectedUser?.role)" label="身份类型">
            <el-select v-model="userEditForm.identityType" style="width: 100%" @change="userEditForm.academicClassId = null; loadUserEditClasses()">
              <el-option label="学生 / 学号" value="STUDENT" />
              <el-option label="教师 / 工号" value="TEACHER" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="userEditForm.schoolId && !['ADMIN', 'SUPER_ADMIN'].includes(selectedUser?.role)" :label="userEditForm.identityType === 'STUDENT' ? '学号' : '工号'" required>
            <el-input v-model="userEditForm.identityNumber" maxlength="50" />
          </el-form-item>
          <el-form-item label="行政班" v-if="userEditForm.schoolId && userEditForm.identityType === 'STUDENT' && selectedUser?.role === 'STUDENT'" class="user-edit-span-2">
            <el-select v-model="userEditForm.academicClassId" filterable style="width: 100%" :loading="!userEditClasses.length && userEditForm.schoolId">
              <el-option v-for="item in userEditClasses" :key="item.id" :label="`${item.name}（${item.grade} · ${item.major}）`" :value="item.id" />
            </el-select>
            <div class="user-edit-tip">只能选择该学生所属学校的有效行政班，学号必须存在于该班学生名单中。</div>
          </el-form-item>
          <el-form-item v-if="userEditForm.schoolId && !['ADMIN', 'SUPER_ADMIN'].includes(selectedUser?.role)" label="单位/院系" class="user-edit-span-2">
            <el-select v-model="userEditForm.unitName" filterable clearable style="width: 100%" placeholder="请选择院系，学生可留空">
              <el-option v-for="department in userEditStructure.departments" :key="department.id" :label="department.name" :value="department.name" />
              <el-option v-if="userEditForm.unitName && !userEditStructure.departments.some(item => item.name === userEditForm.unitName)" :label="`${userEditForm.unitName}（历史数据）`" :value="userEditForm.unitName" />
            </el-select>
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="userEditForm.email" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="userEditForm.phone" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="userEditDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userEditSaving" @click="saveUserEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="academicClassDialogVisible" title="调整行政班" width="520px">
      <el-form label-position="top">
        <el-form-item label="行政班">
          <el-select v-model="academicClassForm.academicClassId" filterable style="width: 100%;" :loading="academicClassLoading" placeholder="请选择行政班">
            <el-option
              v-for="item in academicClasses"
              :key="item.id"
              :label="`${item.name}（${item.grade} · ${item.major}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="academicClassDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="academicClassSaving" @click="saveAcademicClass">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userStatusDialogVisible" title="修改用户状态" width="520px">
      <el-form label-position="top">
        <el-form-item label="状态">
          <el-select v-model="userStatusForm.status" style="width: 100%;">
            <el-option
              v-for="item in userStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="userStatusForm.reason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="userStatusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUserStatus">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userRoleDialogVisible" title="修改用户身份" width="520px">
      <el-form label-position="top">
        <el-form-item label="身份">
          <el-select v-model="userRoleForm.role" style="width: 100%;">
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="userRoleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUserRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchReviewVisible" title="批量调整账号状态" width="520px">
      <el-form label-position="top">
        <el-form-item label="状态调整">
          <el-radio-group v-model="batchReviewForm.action">
            <el-radio-button label="APPROVE">启用账号</el-radio-button>
            <el-radio-button label="REJECT">封禁账号</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="batchReviewForm.reason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="batchReviewVisible = false">取消</el-button>
        <el-button type="primary" @click="batchReviewUsers">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="contentReviewVisible" title="风险内容处置" width="560px">
      <el-form label-position="top">
        <el-form-item label="处置结果">
          <el-radio-group v-model="contentReviewForm.action">
            <el-radio-button label="PASS">保留内容</el-radio-button>
            <el-radio-button label="REJECT">下架内容</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处置说明（下架时必填）">
          <el-input v-model="contentReviewForm.remark" type="textarea" :rows="5" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="contentReviewVisible = false">取消</el-button>
        <el-button type="primary" @click="saveContentReview">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="contentDetailVisible" title="风险与举报详情" width="760px">
      <el-descriptions v-if="contentDetail" :column="1" border>
        <el-descriptions-item label="对象类型">{{ contentDetail.contentType }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ contentDetail.contentTitle }}</el-descriptions-item>
        <el-descriptions-item label="发布者">{{ contentDetail.authorName }}</el-descriptions-item>
        <el-descriptions-item label="风险来源">{{ moderationReasonLabel(contentDetail.riskLevel) }}</el-descriptions-item>
        <el-descriptions-item label="内容摘要">{{ contentDetail.contentPreview }}</el-descriptions-item>
        <el-descriptions-item label="系统/举报说明"><pre class="detail-pre">{{ contentDetail.autoCheckResult || '暂无补充说明' }}</pre></el-descriptions-item>
      </el-descriptions>
    </el-dialog>

  </div>
</template>

<style scoped>
.user-toolbar { align-items: center; }
.user-row-actions { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.table-subtext { margin-top: 3px; color: var(--text-muted); font-size: 12px; }
.history-label { color: var(--text-muted); font-size: 13px; margin-left: 8px; }
.user-detail-panel { min-height: 260px; }
.user-detail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.user-detail-heading h3 { margin: 0; color: var(--text-main); font-size: 20px; }
.user-detail-heading p { margin: 6px 0 0; color: var(--text-muted); font-size: 13px; }
.user-detail-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.user-edit-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
.user-edit-span-2 { grid-column: 1 / -1; }
.user-edit-tip { margin-top: 5px; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.detail-pre { margin: 0; white-space: pre-wrap; font: inherit; color: var(--text-main); }
</style>
