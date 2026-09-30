<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { activityApi } from '@/api'
import { activityTypeOptions } from '@/config/options'
import { formatDateTime, optionLabel } from '@/utils/format'
import { normalizePage } from '@/utils/page'

const loading = ref(false)
const activities = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('PENDING')

const queueLabels = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已退回'
}

async function load() {
  loading.value = true
  try {
    const result = normalizePage(await activityApi.list({ page: page.value, size: 10, status: status.value, schoolLevelOnly: true }))
    activities.value = result.list
    total.value = result.total
  } finally { loading.value = false }
}

async function review(row, action) {
  let rejectReason = ''
  if (action === 'reject') {
    const result = await ElMessageBox.prompt('请输入退回原因', '退回活动', { inputType: 'textarea' })
    rejectReason = result.value
  } else {
    await ElMessageBox.confirm(`确认通过“${row.title}”吗？`, '审核活动', { type: 'warning' })
  }
  await activityApi.review(row.id, { action, rejectReason })
  ElMessage.success(action === 'approve' ? '活动已通过' : '活动已退回')
  await load()
}

function switchQueue(nextStatus) {
  status.value = nextStatus
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page-stack" v-loading="loading">
    <div class="queue-tabs">
      <el-button v-for="(label, key) in queueLabels" :key="key" :type="status === key ? 'primary' : 'default'" @click="switchQueue(key)">{{ label }}</el-button>
    </div>
    <div class="queue-head"><strong>{{ queueLabels[status] }} {{ total }} 项</strong><el-button plain @click="load">刷新</el-button></div>
    <el-empty v-if="!activities.length" :description="`当前没有${queueLabels[status]}活动`" />
    <el-table v-else :data="activities" row-key="id">
      <el-table-column prop="title" label="活动标题" min-width="260" />
      <el-table-column label="类型" width="130"><template #default="{ row }">{{ optionLabel(activityTypeOptions, row.type) }}</template></el-table-column>
      <el-table-column label="活动时间" width="180"><template #default="{ row }">{{ formatDateTime(row.activityTime) }}</template></el-table-column>
      <el-table-column prop="organizerUnit" label="组织单位" min-width="180" />
      <el-table-column label="操作" width="180" fixed="right"><template #default="{ row }"><el-button v-if="status === 'PENDING'" link type="success" @click="review(row, 'approve')">通过</el-button><el-button v-if="status === 'PENDING'" link type="danger" @click="review(row, 'reject')">退回</el-button><span v-else class="history-label">已记录</span></template></el-table-column>
    </el-table>
    <div class="pagination"><el-pagination v-model:current-page="page" layout="prev, pager, next" :page-size="10" :total="total" @current-change="load" /></div>
  </div>
</template>

<style scoped>
.queue-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.queue-tabs { display: flex; gap: 8px; margin-bottom: 14px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
.history-label { color: var(--text-muted); font-size: 13px; }
</style>
