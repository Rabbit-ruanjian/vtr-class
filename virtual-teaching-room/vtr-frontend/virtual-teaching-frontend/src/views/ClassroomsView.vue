<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHero from '@/components/PageHero.vue'
import { classroomApi, studentApi, userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { classroomStatusOptions, roleLabel } from '@/config/options'
import { formatDateTime, optionLabel, splitLines } from '@/utils/format'
import { normalizePage } from '@/utils/page'

const authStore = useAuthStore()
const loading = ref(false)
const submitLoading = ref(false)
const drawerVisible = ref(false)
const editorVisible = ref(false)
const addStudentVisible = ref(false)
const assignTeacherVisible = ref(false)
const editingId = ref(null)
const joinLoading = ref(false)
const inviteCodeToJoin = ref('')

const mode = ref(authStore.isAdmin ? 'admin' : 'mine')
const list = ref([])
const total = ref(0)
const detail = ref(null)
const students = ref([])
const studentTotal = ref(0)
const teacherOptions = ref([])

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  status: ''
})

const studentQuery = reactive({
  page: 1,
  size: 20,
  keyword: ''
})

const editor = reactive({
  className: '',
  description: '',
  grade: '',
  semester: '',
  inviteCode: ''
})

const addStudentForm = reactive({
  classroomId: null,
  studentIdsText: '',
  studentNumbersText: ''
})

const assignTeacherForm = reactive({
  teacherId: null
})

const canManage = computed(() => authStore.isTeacher)
const canAdmin = computed(() => authStore.isAdmin)
const isStudent = computed(() => authStore.isStudent)

function resetEditor() {
  editingId.value = null
  editor.className = ''
  editor.description = ''
  editor.grade = ''
  editor.semester = ''
  editor.inviteCode = ''
}

function generateInviteCode() {
  const characters = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  const values = new Uint32Array(8)
  crypto.getRandomValues(values)
  editor.inviteCode = Array.from(values, (value) => characters[value % characters.length]).join('')
}

async function loadList() {
  loading.value = true

  try {
    const params = {
      page: query.page,
      size: query.size,
      keyword: query.keyword,
      status: query.status
    }

    const payload =
      isStudent.value
        ? await classroomApi.joined()
        : mode.value === 'admin' && canAdmin.value
        ? await classroomApi.adminList(params)
        : await classroomApi.mine(params)

    const pageData = normalizePage(payload)
    list.value = pageData.list
    total.value = pageData.total
  } finally {
    loading.value = false
  }
}

async function loadStudents(classroomId) {
  const pageData = normalizePage(await classroomApi.students(classroomId, studentQuery))
  students.value = pageData.list
  studentTotal.value = pageData.total
}

async function loadTeacherOptions() {
  if (!canAdmin.value) {
    teacherOptions.value = []
    return
  }

  const payload = await userApi.list({
    page: 1,
    size: 100,
    role: 'TEACHER'
  })

  teacherOptions.value = normalizePage(payload).list
}

function openStudents(row) {
  detail.value = row
  drawerVisible.value = true
  studentQuery.page = 1
  studentQuery.keyword = ''
  loadStudents(row.id)
}

function openCreate() {
  resetEditor()
  editorVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  editor.className = row.className
  editor.description = row.description || ''
  editor.grade = row.grade || ''
  editor.semester = row.semester || ''
  editor.inviteCode = row.inviteCode || ''
  editorVisible.value = true
}

async function saveClassroom() {
  submitLoading.value = true

  const payload = {
    className: editor.className,
    description: editor.description,
    grade: editor.grade,
    semester: editor.semester
  }

  try {
    if (editingId.value) {
      await classroomApi.update(editingId.value, payload)
      ElMessage.success('班级已更新')
    } else {
      await classroomApi.create({ ...payload, inviteCode: editor.inviteCode || null })
      ElMessage.success('班级已创建')
    }

    editorVisible.value = false
    resetEditor()
    await loadList()
  } finally {
    submitLoading.value = false
  }
}

async function joinClassroom() {
  const inviteCode = inviteCodeToJoin.value.trim()
  if (!inviteCode) {
    ElMessage.warning('请输入邀请码')
    return
  }

  joinLoading.value = true
  try {
    await classroomApi.join(inviteCode)
    inviteCodeToJoin.value = ''
    ElMessage.success('已加入班级')
    await loadList()
  } finally {
    joinLoading.value = false
  }
}

async function leaveClassroom(row) {
  try {
    await ElMessageBox.confirm(
      `退出“${row.className}”后，你将不再看到该教学班的课程资源；账号和行政班归属不会受影响。`,
      '确认退出教学班',
      { type: 'warning', confirmButtonText: '确认退出', cancelButtonText: '取消' }
    )
    await classroomApi.leave(row.id)
    ElMessage.success('已退出教学班')
    await loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '退出教学班失败')
  }
}

async function archiveClassroom(id) {
  await classroomApi.archive(id)
  ElMessage.success('班级已归档')
  await loadList()
}

async function restoreClassroom(id) {
  await classroomApi.restore(id)
  ElMessage.success('班级已恢复')
  await loadList()
}

async function removeClassroom(id) {
  await classroomApi.remove(id)
  ElMessage.success('班级已删除')
  await loadList()
}

async function adminRemoveClassroom(id) {
  await classroomApi.adminRemove(id)
  ElMessage.success('管理员已删除班级')
  await loadList()
}

async function removeStudent(classroomId, studentId) {
  await classroomApi.removeStudent(classroomId, studentId)
  ElMessage.success('学生已从班级移除')
  await loadStudents(classroomId)
  await loadList()
}

function openAddStudent() {
  addStudentForm.classroomId = detail.value?.id || null
  addStudentForm.studentIdsText = ''
  addStudentForm.studentNumbersText = ''
  addStudentVisible.value = true
}

async function saveAddStudents() {
  const studentIds = splitLines(addStudentForm.studentIdsText)
    .map((item) => Number(item))
    .filter((item) => !Number.isNaN(item))
  const studentNumbers = splitLines(addStudentForm.studentNumbersText)

  await classroomApi.addStudents({
    classroomId: addStudentForm.classroomId,
    studentIds: studentIds.length ? studentIds : null,
    studentNumbers: studentNumbers.length ? studentNumbers : null
  })
  ElMessage.success('学生已添加')
  addStudentVisible.value = false
  await Promise.all([loadList(), loadStudents(addStudentForm.classroomId)])
}

function openAssignTeacher(row) {
  detail.value = row
  assignTeacherForm.teacherId = row.teacherId || null
  assignTeacherVisible.value = true
}

async function saveAssignTeacher() {
  await classroomApi.assignTeacher(detail.value.id, {
    teacherId: assignTeacherForm.teacherId
  })
  ElMessage.success('班级教师已更新')
  assignTeacherVisible.value = false
  await loadList()
}

onMounted(() => {
  loadList()
  loadTeacherOptions()
})
</script>

<template>
  <div class="page-stack">
    <PageHero
      title="班级管理"
      description="教师可维护自己的班级，管理员可以切换到全量列表、分配教师并执行物理删除。"
    >
      <template #actions>
        <el-button type="primary" @click="loadList" :loading="loading">刷新班级</el-button>
        <el-button v-if="canManage" plain @click="openCreate">创建班级</el-button>
      </template>
    </PageHero>

    <div class="toolbar" v-if="canAdmin">
      <el-radio-group v-model="mode">
        <el-radio-button label="mine">我的班级</el-radio-button>
        <el-radio-button label="admin">全部班级</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="isStudent" class="toolbar">
      <el-input
        v-model="inviteCodeToJoin"
        class="toolbar-grow"
        maxlength="16"
        placeholder="输入教师提供的班级邀请码"
        @keyup.enter="joinClassroom"
      />
      <el-button type="primary" :loading="joinLoading" @click="joinClassroom">加入班级</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="query.keyword" class="toolbar-grow" clearable placeholder="搜索班级名称或描述" />
      <el-select v-model="query.status" clearable placeholder="状态" style="width: 160px;">
        <el-option
          v-for="item in classroomStatusOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-button type="primary" @click="query.page = 1; loadList()">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading">
      <el-table-column prop="className" label="班级名称" min-width="220" />
      <el-table-column prop="grade" label="年级" width="120" />
      <el-table-column prop="semester" label="学期" width="140" />
      <el-table-column v-if="canManage" label="邀请码" width="140">
        <template #default="{ row }">
          <el-tag effect="plain">{{ row.inviteCode || '--' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="教师" width="160">
        <template #default="{ row }">
          {{ row.teacherName || row.teacherId || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="学生数" width="110">
        <template #default="{ row }">
          {{ row.studentCount || 0 }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          {{ optionLabel(classroomStatusOptions, row.status) }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="280">
        <template #default="{ row }">
          <el-button v-if="!isStudent" link @click="openStudents(row)">学生列表</el-button>
          <el-button v-if="isStudent" link type="danger" @click="leaveClassroom(row)">退出教学班</el-button>
          <el-button v-if="canManage" link @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="canManage && row.status === 'ACTIVE'"
            link
            type="warning"
            @click="archiveClassroom(row.id)"
          >
            归档
          </el-button>
          <el-button
            v-if="canManage && row.status === 'ARCHIVED'"
            link
            type="success"
            @click="restoreClassroom(row.id)"
          >
            恢复
          </el-button>
          <el-button
            v-if="canAdmin"
            link
            type="primary"
            @click="openAssignTeacher(row)"
          >
            分配教师
          </el-button>
          <el-button
            v-if="canAdmin"
            link
            type="danger"
            @click="adminRemoveClassroom(row.id)"
          >
            管理员删除
          </el-button>
          <el-button
            v-else-if="canManage"
            link
            type="danger"
            @click="removeClassroom(row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="display: flex; justify-content: flex-end;">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="loadList"
      />
    </div>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑班级' : '创建班级'" width="640px">
      <el-form label-position="top">
        <el-form-item label="班级名称">
          <el-input v-model="editor.className" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="邀请码">
          <div style="display: flex; width: 100%; gap: 10px;">
            <el-input
              v-model="editor.inviteCode"
              maxlength="16"
              placeholder="留空由系统自动生成"
              style="flex: 1;"
            />
            <el-button @click="generateInviteCode">生成</el-button>
          </div>
        </el-form-item>
        <div class="two-column">
          <div>
            <el-form-item label="年级">
              <el-input v-model="editor.grade" />
            </el-form-item>
          </div>
          <div>
            <el-form-item label="学期">
              <el-input v-model="editor.semester" />
            </el-form-item>
          </div>
        </div>
        <el-form-item label="描述">
          <el-input v-model="editor.description" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="saveClassroom">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="班级学生" size="60%">
      <template v-if="detail">
        <div class="page-stack">
          <div class="card-head">
            <div>
              <h3>{{ detail.className }}</h3>
              <p>
                {{ detail.grade || '未设置年级' }} · {{ detail.semester || '未设置学期' }} ·
                {{ detail.teacherName || detail.teacherId || '未分配教师' }}
              </p>
            </div>
            <div class="chips">
              <el-tag>{{ optionLabel(classroomStatusOptions, detail.status) }}</el-tag>
              <el-tag type="success">{{ detail.studentCount || 0 }} 人</el-tag>
            </div>
          </div>

          <div v-if="canManage" class="toolbar">
            <el-button type="primary" @click="openAddStudent">添加学生</el-button>
          </div>

          <el-table :data="students" v-loading="loading">
            <el-table-column prop="username" label="用户名" width="160" />
            <el-table-column prop="nickname" label="昵称" width="160" />
            <el-table-column prop="email" label="邮箱" min-width="220" />
            <el-table-column prop="role" label="角色" width="100">
              <template #default="{ row }">
                {{ roleLabel(row.role) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="160">
              <template #default="{ row }">
                <el-button
                  v-if="canManage"
                  link
                  type="danger"
                  @click="removeStudent(detail.id, row.id)"
                >
                  移除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div style="display: flex; justify-content: flex-end;">
            <el-pagination
              v-model:current-page="studentQuery.page"
              v-model:page-size="studentQuery.size"
              layout="total, prev, pager, next"
              :total="studentTotal"
              @current-change="loadStudents(detail.id)"
            />
          </div>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="addStudentVisible" title="添加学生到班级" width="720px">
      <el-form label-position="top">
        <el-form-item label="班级">
          <el-select v-model="addStudentForm.classroomId" style="width: 100%;">
            <el-option
              v-for="item in list"
              :key="item.id"
              :label="item.className"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <div class="two-column">
          <div>
            <el-form-item label="学生ID（每行一个）">
              <el-input v-model="addStudentForm.studentIdsText" type="textarea" :rows="8" />
            </el-form-item>
          </div>
          <div>
            <el-form-item label="学号（每行一个）">
              <el-input v-model="addStudentForm.studentNumbersText" type="textarea" :rows="8" />
            </el-form-item>
          </div>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="addStudentVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAddStudents">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignTeacherVisible" title="分配教师" width="520px">
      <el-form label-position="top">
        <el-form-item label="教师">
          <el-select v-model="assignTeacherForm.teacherId" filterable style="width: 100%;">
            <el-option
              v-for="item in teacherOptions"
              :key="item.id"
              :label="item.nickname || item.username"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="assignTeacherVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAssignTeacher">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
