<script setup>
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import PageHero from '@/components/PageHero.vue'
import AiAssistantDrawer from '@/components/AiAssistantDrawer.vue'

const router = useRouter()
const route = useRoute()
const courseId = Number(route.query.courseId) || null
const aiVisible = ref(false)
const aiContext = reactive({ title: '', meta: '', questionType: '', questionId: null, type: 'research', excerpt: '' })

const tools = [
  {
    title: '教研活动',
    description: '创建、参与和跟进教研活动及讨论内容。',
    path: '/activities',
    action: '进入教研活动'
  },
  {
    title: '交流论坛',
    description: '围绕课程建设和教学实践开展主题交流。',
    path: '/forum',
    action: '前往交流论坛'
  },
  {
    title: 'AI教研助手',
    description: '围绕课程设计、题目分析和资料整理，获得 AI 协助。',
    path: '',
    action: '打开 AI 助手'
  }
]

function openTool(tool) {
  if (tool.path) {
    router.push(tool.path)
    return
  }
  Object.assign(aiContext, {
    title: 'AI教研助手',
    meta: '教研工具 · 课程协作',
    type: 'research',
    excerpt: '你可以先描述教学目标、课程内容或需要调研的问题。后续接入 Kimi 后，助手将支持联网检索和资料分析。'
  })
  aiVisible.value = true
}
</script>

<template>
  <div class="page-stack">
    <PageHero
      title="教研工具"
      description="从这里进入课程协作、教研活动与经验交流功能。"
    />

    <section class="tool-grid" aria-label="教研工具列表">
      <article v-for="tool in tools" :key="tool.path" class="tool-item">
        <h3>{{ tool.title }}</h3>
        <p>{{ tool.description }}</p>
        <el-button v-if="tool.path" type="primary" @click="openTool(tool)">
          {{ tool.action }}
        </el-button>
        <el-button v-else type="primary" @click="openTool(tool)">
          {{ tool.action }}
        </el-button>
      </article>
    </section>
    <AiAssistantDrawer v-model="aiVisible" :course-id="courseId" :context-title="aiContext.title" :context-meta="aiContext.meta" :question-type="aiContext.questionType" :question-id="aiContext.questionId" :context-type="aiContext.type" :context-excerpt="aiContext.excerpt" agent-task="LESSON_PLAN" />
  </div>
</template>

<style scoped>
.tool-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.tool-item {
  min-height: 190px;
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.tool-item h3 {
  margin: 0;
  font-size: 20px;
  color: var(--text-main);
}

.tool-item p {
  margin: 12px 0 22px;
  color: var(--text-muted);
  line-height: 1.65;
}

.tool-item :deep(.el-button) {
  margin-top: auto;
}

@media (max-width: 900px) {
  .tool-grid {
    grid-template-columns: 1fr;
  }
}
</style>
