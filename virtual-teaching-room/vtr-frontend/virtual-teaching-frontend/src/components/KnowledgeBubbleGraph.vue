<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Close, Delete, EditPen, FullScreen, Plus, RefreshLeft, ZoomIn, ZoomOut } from '@element-plus/icons-vue'

const props = defineProps({
  courseName: { type: String, default: '' },
  chapter: { type: String, default: '' },
  chapters: { type: Array, default: () => [] },
  sections: { type: Array, default: () => [] },
  resources: { type: Array, default: () => [] },
  editable: { type: Boolean, default: false },
  editorOnly: { type: Boolean, default: false },
  editorOpen: { type: Boolean, default: false },
  submitDisabled: { type: Boolean, default: false },
  submissionHint: { type: String, default: '' }
})

const emit = defineEmits(['submit', 'invalid'])

const palette = [
  { module: '基础概念', core: '#4b9ef8', edge: '#bfe4ff', icon: '#2274c8' },
  { module: '核心方法', core: '#f17848', edge: '#ffc59d', icon: '#c94f24' },
  { module: '案例分析', core: '#ec7196', edge: '#ffc1d2', icon: '#bf4169' },
  { module: '实践应用', core: '#6bc38c', edge: '#c0efd0', icon: '#398c57' },
  { module: '拓展学习', core: '#9b79df', edge: '#dbc8ff', icon: '#704ab7' },
  { module: '学习重点', core: '#e6b83f', edge: '#ffe9a6', icon: '#af8115' }
]

const scale = ref(1)
const offset = ref({ x: 0, y: 0 })
const graphRoot = ref(null)
const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const hoverId = ref(null)
const selectedId = ref(null)
const canvasEl = ref(null)
const canvasSize = ref({ width: 900, height: 600 })
let canvasObserver = null

function safeGraphDocument() {
  for (const resource of props.resources) {
    const text = String(resource?.description || '').trim()
    if (!text.startsWith('{')) continue
    try {
      const config = JSON.parse(text)
      if (Array.isArray(config.nodes) && config.nodes.length) return config
    } catch {
      // A normal resource description is not graph configuration.
    }
  }
  return null
}

function safeGraphConfig() {
  return safeGraphDocument()?.nodes || null
}

function frameworkNodes() {
  const root = { id: 'course-root', parentId: null, name: props.courseName || '课程知识图谱' }
  const nodes = [root]
  if (!props.chapter) {
    props.chapters.forEach((chapter, index) => {
      const chapterId = `chapter-${chapter.chapterId || chapter.id || index}`
      const chapterName = chapter.title || chapter.id || `第${index + 1}章`
      nodes.push({ id: chapterId, parentId: root.id, name: chapterName, type: 'chapter', module: palette[index % palette.length].module, description: '课程章节目录节点。' })
      props.sections.filter((section) => String(section.chapterId) === String(chapter.chapterId || chapter.id))
        .forEach((section, sectionIndex) => {
          const sectionId = `${chapterId}-section-${section.id || sectionIndex}`
          nodes.push({ id: sectionId, parentId: chapterId, name: section.title || `第${sectionIndex + 1}节`, type: 'section', module: palette[index % palette.length].module, description: '课程小节目录节点。' })
          const subtitle = String(section.subtitle || '').split(/[；;、，,|/]/)[0].trim()
          if (subtitle) nodes.push({ id: `${sectionId}-sub`, parentId: sectionId, name: subtitle, type: 'subsection', module: palette[index % palette.length].module, description: '课程子小节目录节点。' })
        })
    })
    if (nodes.length === 1) nodes.push({ id: 'chapter-placeholder', parentId: root.id, name: '请添加章节' })
    return nodes
  }
  props.chapters.forEach((chapter, index) => {
    const chapterName = chapter.title || chapter.id || `第${index + 1}章`
    if (chapterName !== props.chapter && String(chapter.chapterId || chapter.id) !== String(props.chapter)) return
    const chapterId = `chapter-${chapter.chapterId || chapter.id || index}`
    nodes.push({ id: chapterId, parentId: root.id, name: chapterName, module: palette[index % palette.length].module })
    props.sections.filter((section) => String(section.chapterId) === String(chapter.chapterId || chapter.id))
      .slice(0, 9)
      .forEach((section, sectionIndex) => nodes.push({
        id: `${chapterId}-section-${section.id || sectionIndex}`,
        parentId: chapterId,
        name: section.title || `第${sectionIndex + 1}节`,
        module: palette[index % palette.length].module
      }))
  })
  if (nodes.length === 1) nodes.push({ id: 'chapter-placeholder', parentId: root.id, name: '请添加章节和小节知识点' })
  return nodes
}

function resourceNodes() {
  const resources = props.resources.filter((resource) => resource?.title || resource?.name).slice(0, 12)
  if (!resources.length) return []
  const rootId = 'resource-root'
  const groupId = 'resource-group'
  return [
    { id: rootId, parentId: null, name: props.chapter || props.courseName || '课程知识图谱', importance: 5, module: '基础概念', type: 'core', description: '当前课程的知识图谱入口。', related: [groupId] },
    { id: groupId, parentId: rootId, name: '相关教学资源', importance: 4, module: '实践应用', type: 'module', description: '根据当前课程已配置的真实教学资源整理。', related: resources.map((resource, index) => String(resource.id ?? `resource-${index}`)) },
    ...resources.map((resource, index) => ({
      id: String(resource.id ?? `resource-${index}`),
      parentId: groupId,
      name: resource.title || resource.name,
      importance: 2,
      module: palette[(index + 3) % palette.length].module,
      type: 'resource',
      description: resource.description || '课程中已配置的教学资源。',
      related: [groupId]
    }))
  ]
}

const nodes = computed(() => {
  const configured = safeGraphConfig()
  // 课程目录是知识图谱的权威来源；已有旧图谱只作为无目录时的兼容回退，避免展示过期或无关节点。
  const rawNodes = props.chapters.length ? frameworkNodes() : (props.chapter ? frameworkNodes() : (configured || resourceNodes()))

  return rawNodes.map((node, index) => {
    const importance = Math.min(5, Math.max(1, Number(node.importance) || (index === 0 ? 5 : 3)))
    const colors = palette.find((item) => item.module === node.module) || palette[index % palette.length]
    return {
      ...node,
      id: String(node.id ?? index),
      parentId: node.parentId == null ? (node.parent == null ? null : String(node.parent)) : String(node.parentId),
      name: String(node.name || '').trim(),
      importance,
      related: (node.related || []).map(String),
      colors
    }
  }).filter((node) => node.name)
})

const hoveredNode = computed(() => nodes.value.find((node) => node.id === hoverId.value) || null)
const selectedNode = computed(() => nodes.value.find((node) => node.id === selectedId.value) || nodes.value[0] || null)
const graphRootNode = computed(() => nodes.value.find((node) => node.parentId == null) || nodes.value[0] || null)
const graphGroups = computed(() => {
  if (!graphRootNode.value) return []
  return nodes.value
    .filter((node) => node.id !== graphRootNode.value.id && node.parentId === graphRootNode.value.id)
    .map((group) => ({
      ...group,
      children: nodes.value.filter((node) => node.parentId === group.id)
    }))
})
// 课程知识点是一棵树，这里用“径向圆形气泡”布局把它铺满整个画布：
// 课程核心是最中心的大气泡，知识模块按子节点数量分配扇区、均匀落在内圈，
// 知识点再向外扩散到外圈。所有节点都是圆形气泡，半径随节点数量自适应，尽量不重叠。
const mapLayout = computed(() => {
  const root = graphRootNode.value
  if (!root) return { nodes: [], edges: [], size: 900, leafD: 120 }
  const groups = graphGroups.value

  // 总览图按“课程—章—节—小节”分层展示，保留目录关系与章节前置依赖。
  if (!props.chapter) {
    const allNodes = nodes.value
    const depthOf = (node) => {
      let depth = 0
      let current = node
      const seen = new Set()
      while (current?.parentId && !seen.has(current.id)) {
        seen.add(current.id)
        current = allNodes.find((item) => item.id === current.parentId)
        depth += 1
      }
      return depth
    }
    const levels = new Map()
    allNodes.forEach((node) => {
      const depth = depthOf(node)
      if (!levels.has(depth)) levels.set(depth, [])
      levels.get(depth).push(node)
    })
    const maxDepth = Math.max(...levels.keys(), 0)
    const colGap = 270
    const rowGap = 150
    const maxRows = Math.max(...Array.from(levels.values()).map((items) => items.length), 1)
    const size = Math.max(1100, 260 + maxDepth * colGap, 180 + maxRows * rowGap)
    const layoutNodes = []
    const positions = new Map()
    levels.forEach((items, depth) => {
      const x = 140 + depth * colGap
      const startY = size / 2 - ((items.length - 1) * rowGap) / 2
      items.forEach((node, index) => {
        const diameter = depth === 0 ? 220 : depth === 1 ? 180 : 145
        const item = { ...node, x, y: startY + index * rowGap, diameter, level: depth }
        layoutNodes.push(item)
        positions.set(node.id, item)
      })
    })
    const edges = []
    const link = (from, to) => {
      const dx = to.x - from.x
      const dy = to.y - from.y
      const len = Math.hypot(dx, dy) || 1
      return `M ${(from.x + (dx / len) * (from.diameter / 2)).toFixed(1)} ${(from.y + (dy / len) * (from.diameter / 2)).toFixed(1)} L ${(to.x - (dx / len) * (to.diameter / 2)).toFixed(1)} ${(to.y - (dy / len) * (to.diameter / 2)).toFixed(1)}`
    }
    allNodes.forEach((node) => {
      if (!node.parentId) return
      const from = positions.get(node.parentId)
      const to = positions.get(node.id)
      if (from && to) edges.push({ from: from.id, to: to.id, color: '#aeb0b4', path: link(from, to) })
    })
    ;(safeGraphDocument()?.edges || []).filter((edge) => edge.type === 'prerequisite' || edge.label === '前置依赖').forEach((edge) => {
      const from = positions.get(String(edge.from))
      const to = positions.get(String(edge.to))
      if (from && to) edges.push({ from: from.id, to: to.id, color: '#e6b83f', path: link(from, to) })
    })
    // 即使课程尚未保存新的图谱资源，也按当前章节顺序展示前置依赖。
    const chapterNodes = allNodes.filter((node) => node.type === 'chapter' && node.parentId === root.id)
    chapterNodes.slice(1).forEach((chapter, index) => {
      const previous = chapterNodes[index]
      const from = positions.get(previous.id)
      const to = positions.get(chapter.id)
      if (from && to && !edges.some((edge) => edge.from === from.id && edge.to === to.id)) {
        edges.push({ from: from.id, to: to.id, color: '#e6b83f', path: link(from, to) })
      }
    })
    return { nodes: layoutNodes, edges, size, leafD: 145 }
  }

  const rootD = 220
  const moduleD = 220
  const leafD = 180
  const TAU = Math.PI * 2

  const leafCount = groups.reduce((sum, group) => sum + (group.children || []).length, 0)
  const totalUnits = groups.reduce((sum, group) => sum + Math.max((group.children || []).length, 1), 0) || 1

  // 外圈半径：保证外圈知识点沿周长排开时互不重叠
  const outerRing = Math.max(360, ((leafCount || groups.length || 1) * (leafD + 36)) / TAU)
  // 内圈半径：容得下模块气泡，同时与外圈拉开足够间距
  const innerRing = Math.min(
    Math.max((groups.length * (moduleD + 42)) / TAU, outerRing * 0.46),
    outerRing - (leafD / 2 + moduleD / 2 + 36)
  )

  const maxR = outerRing + leafD / 2 + 52
  const size = maxR * 2
  const cx = maxR
  const cy = maxR

  const layoutNodes = []
  const edges = []

  // 沿两个气泡的圆心连线，从边缘连到边缘
  const link = (x1, y1, d1, x2, y2, d2) => {
    const dx = x2 - x1
    const dy = y2 - y1
    const len = Math.hypot(dx, dy) || 1
    const ux = dx / len
    const uy = dy / len
    const sx = x1 + ux * (d1 / 2)
    const sy = y1 + uy * (d1 / 2)
    const ex = x2 - ux * (d2 / 2)
    const ey = y2 - uy * (d2 / 2)
    return `M ${sx.toFixed(1)} ${sy.toFixed(1)} L ${ex.toFixed(1)} ${ey.toFixed(1)}`
  }

  layoutNodes.push({ ...root, x: cx, y: cy, diameter: rootD, level: 0 })

  let angleCursor = -Math.PI / 2
  groups.forEach((group) => {
    const children = group.children || []
    const span = (Math.max(children.length, 1) / totalUnits) * TAU
    const centerAngle = angleCursor + span / 2

    const mx = cx + innerRing * Math.cos(centerAngle)
    const my = cy + innerRing * Math.sin(centerAngle)
    layoutNodes.push({ ...group, x: mx, y: my, diameter: moduleD, level: 1 })
    edges.push({
      from: root.id,
      to: group.id,
      color: group.colors.edge,
      path: link(cx, cy, rootD, mx, my, moduleD)
    })

    if (children.length) {
      const pad = span * 0.12
      const usable = Math.max(span - pad * 2, 0)
      children.forEach((child, index) => {
        const t = children.length === 1 ? 0.5 : index / (children.length - 1)
        const angle = angleCursor + pad + usable * t
        const lx = cx + outerRing * Math.cos(angle)
        const ly = cy + outerRing * Math.sin(angle)
        layoutNodes.push({ ...child, x: lx, y: ly, diameter: leafD, level: 2 })
        edges.push({
          from: group.id,
          to: child.id,
          color: group.colors.core,
          path: link(mx, my, moduleD, lx, ly, leafD)
        })
      })
    }
    angleCursor += span
  })

  return { nodes: layoutNodes, edges, size, leafD }
})
const editing = ref(props.editorOnly || props.editorOpen)
const editorNodes = ref([])
const editorRows = computed(() => {
  const byParent = new Map()
  editorNodes.value.forEach((node) => {
    const key = node.parentId || '__root__'
    if (!byParent.has(key)) byParent.set(key, [])
    byParent.get(key).push(node)
  })
  const rows = []
  const visit = (parentId, depth, trail = new Set()) => {
    for (const node of byParent.get(parentId || '__root__') || []) {
      if (trail.has(node.id)) continue
      rows.push({ node, depth })
      const nextTrail = new Set(trail)
      nextTrail.add(node.id)
      visit(node.id, depth + 1, nextTrail)
    }
  }
  visit(null, 0)
  return rows
})
const fitScale = computed(() => {
  const size = mapLayout.value.size || 900
  const pad = 48
  const availW = Math.max(canvasSize.value.width - pad, 120)
  const availH = Math.max(canvasSize.value.height - pad, 120)
  const contain = Math.min(availW / size, availH / size, 1)
  // 保证知识点气泡在屏幕上不小于可读尺寸；图谱较大时改为可拖动浏览而不是整体缩到看不清。
  const leafD = mapLayout.value.leafD || 120
  const minLeafOnScreen = 96
  const readableFloor = Math.min(minLeafOnScreen / leafD, 1)
  return Math.max(contain, readableFloor)
})

const graphStyle = computed(() => {
  const size = mapLayout.value.size || 900
  const fit = fitScale.value
  const drawn = size * fit
  const left = (canvasSize.value.width - drawn) / 2
  const top = (canvasSize.value.height - drawn) / 2
  return {
    width: `${size}px`,
    height: `${size}px`,
    transform: `translate(${left + offset.value.x}px, ${top + offset.value.y}px) scale(${fit * scale.value})`,
    transformOrigin: 'top left'
  }
})

function loadEditorNodes() {
  const configured = safeGraphConfig()
  editorNodes.value = (configured?.length ? configured : frameworkNodes()).map((node, index) => ({
    id: String(node.id ?? `node-${index}`),
    parentId: node.parentId == null ? (node.parent == null ? null : String(node.parent)) : String(node.parentId),
    name: String(node.name || '').trim()
  }))
}

watch(() => [props.resources, props.chapter], loadEditorNodes, { deep: true, immediate: true })
watch(() => props.editorOnly, (value) => {
  editing.value = value
})
watch(() => props.editorOpen, (value) => {
  if (!props.editorOnly) editing.value = value
})

function addChild(parentId) {
  const id = `node-${Date.now()}-${editorNodes.value.length}`
  editorNodes.value.push({ id, parentId: parentId || null, name: '' })
}

function removeNode(id) {
  if (id === 'root' || editorNodes.value.find((node) => node.id === id)?.parentId == null) return
  const removed = new Set([id])
  let changed = true
  while (changed) {
    changed = false
    editorNodes.value.forEach((node) => {
      if (removed.has(node.parentId) && !removed.has(node.id)) {
        removed.add(node.id)
        changed = true
      }
    })
  }
  editorNodes.value = editorNodes.value.filter((node) => !removed.has(node.id))
}

function startEditing() {
  loadEditorNodes()
  editing.value = true
}

function cancelEditing() {
  loadEditorNodes()
  if (!props.editorOnly) editing.value = false
}

function submitEditing() {
  const invalid = editorNodes.value.some((node) => !String(node.name || '').trim())
  if (invalid) {
    emit('invalid')
    return
  }
  emit('submit', {
    version: 1,
    nodes: editorNodes.value.map((node) => ({ id: node.id, parentId: node.parentId, name: node.name.trim() }))
  })
  if (!props.editorOnly) editing.value = false
}

function isRelated(node) {
  const hovered = hoveredNode.value
  if (!hovered || node.id === hovered.id) return true
  const parentOf = (childId) => nodes.value.find((item) => item.id === childId)?.parentId
  const contains = (ancestorId, childId) => {
    let current = childId
    const seen = new Set()
    while (current && !seen.has(current)) {
      if (current === ancestorId) return true
      seen.add(current)
      current = parentOf(current)
    }
    return false
  }
  return node.related.includes(hovered.id)
    || hovered.related.includes(node.id)
    || contains(node.id, hovered.id)
    || contains(hovered.id, node.id)
}

function setZoom(value) {
  scale.value = Math.min(2.4, Math.max(0.4, Number(value.toFixed(2))))
}

function resetView() {
  scale.value = 1
  offset.value = { x: 0, y: 0 }
}

function requestFullscreen() {
  graphRoot.value?.requestFullscreen?.()
}

function measureCanvas() {
  const el = canvasEl.value
  if (!el) return
  canvasSize.value = { width: el.clientWidth, height: el.clientHeight }
}

onMounted(() => {
  measureCanvas()
  if (typeof ResizeObserver !== 'undefined' && canvasEl.value) {
    canvasObserver = new ResizeObserver(measureCanvas)
    canvasObserver.observe(canvasEl.value)
  } else {
    window.addEventListener('resize', measureCanvas)
  }
})

onBeforeUnmount(() => {
  canvasObserver?.disconnect()
  canvasObserver = null
  window.removeEventListener('resize', measureCanvas)
})

function handleWheel(event) {
  setZoom(scale.value + (event.deltaY < 0 ? 0.08 : -0.08))
}

function startDrag(event) {
  if (event.button !== 0) return
  dragging.value = true
  dragStart.value = { x: event.clientX - offset.value.x, y: event.clientY - offset.value.y }
  event.currentTarget.setPointerCapture?.(event.pointerId)
}

function moveDrag(event) {
  if (!dragging.value) return
  offset.value = {
    x: Math.max(-600, Math.min(600, event.clientX - dragStart.value.x)),
    y: Math.max(-600, Math.min(600, event.clientY - dragStart.value.y))
  }
}

function stopDrag(event) {
  dragging.value = false
  event.currentTarget.releasePointerCapture?.(event.pointerId)
}
</script>

<template>
  <section ref="graphRoot" class="knowledge-graph" :class="{ 'knowledge-graph-editor-only': editorOnly }" :aria-label="editorOnly ? '知识图谱编辑器' : '知识气泡图谱'">
    <header class="knowledge-graph-header">
      <div>
        <span class="knowledge-graph-eyebrow">{{ editorOnly ? 'TEACHING OUTLINE' : 'KNOWLEDGE ATLAS' }}</span>
        <h3>{{ editorOnly ? '编辑知识图谱' : `${courseName || '课程资源'}${chapter ? ` / ${chapter}` : ''}` }}</h3>
        <p v-if="editorOnly" class="knowledge-graph-editor-subtitle">按“课程核心—知识模块—知识点”组织内容，完成后保存并在课程内直接发布。</p>
      </div>
      <div v-if="!editorOnly" class="knowledge-graph-actions" aria-label="图谱视图控制">
        <button v-if="editable && !editing" type="button" class="knowledge-graph-edit-action" title="编辑思维导图" @click="startEditing"><EditPen /><span>编辑图谱</span></button>
        <button type="button" title="缩小图谱" aria-label="缩小图谱" @click="setZoom(scale - 0.1)"><ZoomOut /></button>
        <span>{{ Math.round(scale * 100) }}%</span>
        <button type="button" title="放大图谱" aria-label="放大图谱" @click="setZoom(scale + 0.1)"><ZoomIn /></button>
        <button type="button" title="复位视图" aria-label="复位视图" @click="resetView"><RefreshLeft /></button>
        <button type="button" title="全屏显示" aria-label="全屏显示" @click="requestFullscreen"><FullScreen /></button>
      </div>
    </header>

    <div v-if="!editorOnly" class="knowledge-graph-canvas-wrap">
      <div
        ref="canvasEl"
        class="knowledge-graph-canvas"
        :class="{ dragging }"
        @wheel.prevent="handleWheel"
        @pointerdown="startDrag"
        @pointermove="moveDrag"
        @pointerup="stopDrag"
        @pointercancel="stopDrag"
        @pointerleave="dragging = false"
      >
        <div v-if="nodes.length" class="knowledge-graph-stage" :style="graphStyle">
          <div class="knowledge-map-stage" :style="{ width: `${mapLayout.size}px`, height: `${mapLayout.size}px` }">
            <div class="knowledge-map-grid" aria-hidden="true"></div>
            <svg class="knowledge-map-edges" :viewBox="`0 0 ${mapLayout.size} ${mapLayout.size}`" preserveAspectRatio="none" aria-hidden="true">
              <path v-for="edge in mapLayout.edges" :key="`${edge.from}-${edge.to}`" :d="edge.path" :style="{ '--edge-color': edge.color }" />
            </svg>
            <button
              v-for="node in mapLayout.nodes"
              :key="node.id"
              type="button"
              class="knowledge-map-node"
              :class="[`level-${node.level}`, `node-${node.type || 'concept'}`, { 'is-muted': !isRelated(node), 'is-selected': selectedId === node.id }]"
              :style="{ left: `${node.x}px`, top: `${node.y}px`, '--node-size': `${node.diameter}px`, '--node-core': node.colors.core, '--node-edge': node.colors.edge }"
              :title="node.description"
              @pointerdown.stop
              @mouseenter="hoverId = node.id"
              @mouseleave="hoverId = null"
              @focus="hoverId = node.id"
              @blur="hoverId = null"
              @click="selectedId = node.id"
            >
              <span class="knowledge-map-kicker"><i></i>{{ node.level === 0 ? '课程核心' : node.level === 1 ? '知识模块' : '知识点' }}</span>
              <strong>{{ node.name }}</strong>
            </button>
          </div>
        </div>
         <div v-else class="knowledge-graph-empty">
          <span class="knowledge-graph-empty-mark">◎</span>
          <strong>暂未生成知识图谱</strong>
            <p>请由教师设计或上传知识图谱。</p>
          <div class="knowledge-graph-empty-flow" aria-label="知识图谱层级示意">
            <span>课程核心</span><i>→</i><span>知识模块</span><i>→</i><span>知识点</span>
          </div>
        </div>
      </div>
    </div>

    <section v-if="editable && editing" class="knowledge-graph-editor" :aria-label="editorOnly ? '编辑知识图谱节点' : '编辑知识图谱节点'">
      <div class="knowledge-graph-editor-header">
        <div>
           <strong>编辑知识图谱</strong>
           <span>按“课程核心—知识模块—知识点”组织内容。</span>
        </div>
        <div class="knowledge-graph-editor-actions">
          <button type="button" class="editor-secondary" @click="cancelEditing"><Close />取消</button>
           <button type="button" class="editor-primary" :disabled="submitDisabled" @click="submitEditing">保存知识图谱</button>
        </div>
      </div>
      <p v-if="submissionHint" class="knowledge-graph-editor-hint">{{ submissionHint }}</p>
      <div class="knowledge-graph-editor-list">
        <div v-for="row in editorRows" :key="row.node.id" class="knowledge-graph-editor-row" :style="{ '--depth': row.depth }">
          <span class="editor-branch-mark" aria-hidden="true"></span>
          <input v-model="row.node.name" :placeholder="row.depth === 0 ? '填写课程核心' : row.depth === 1 ? '填写章节名称' : '填写小节或知识点'" maxlength="100" />
          <button type="button" title="添加子知识点" aria-label="添加子知识点" @click="addChild(row.node.id)"><Plus /></button>
          <button v-if="row.depth > 0" type="button" class="editor-delete" title="删除节点及其子节点" aria-label="删除节点及其子节点" @click="removeNode(row.node.id)"><Delete /></button>
        </div>
      </div>
    </section>

  </section>
</template>

<style scoped>
.knowledge-graph {
  overflow: hidden;
  border: 1px solid #dfe5ec;
  border-radius: 8px;
  background: #fff;
  color: #263347;
  box-shadow: 0 10px 30px rgba(47, 70, 99, 0.08);
}

.knowledge-graph-header {
  display: flex;
  min-height: 76px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 20px;
  border-bottom: 1px solid #e7ebf0;
  background: #fff;
}

.knowledge-graph-eyebrow {
  display: block;
  color: #7c8ba0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.2px;
}

.knowledge-graph-header h3 {
  margin: 4px 0 0;
  color: #263347;
  font-size: 18px;
}

.knowledge-graph-header h3 i {
  color: #758195;
  font-size: 14px;
  font-style: normal;
  font-weight: 400;
}

.knowledge-graph-editor-subtitle {
  margin: 5px 0 0;
  color: #758195;
  font-size: 13px;
}

.knowledge-graph-editor-only .knowledge-graph-header {
  min-height: 86px;
}

.knowledge-graph-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.knowledge-graph-actions button {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: #526174;
  cursor: pointer;
}

.knowledge-graph-actions button:hover {
  border-color: #d8e0e9;
  background: #f5f8fb;
  color: #2779d6;
}

.knowledge-graph-edit-action {
  display: inline-flex !important;
  width: auto !important;
  align-items: center;
  gap: 5px;
  padding: 0 9px;
  border-color: #cfe0f5 !important;
  background: #f4f8fd !important;
  color: #2779d6 !important;
  font-size: 12px;
}

.knowledge-graph-edit-action :deep(svg) {
  width: 14px;
  height: 14px;
}

.knowledge-graph-editor {
  border-top: 1px solid #e7ebf0;
  background: #fbfcfe;
}

.knowledge-graph-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 15px 20px;
  border-bottom: 1px solid #e7ebf0;
}

.knowledge-graph-editor-header > div:first-child {
  display: grid;
  gap: 4px;
}

.knowledge-graph-editor-header strong {
  color: #263347;
  font-size: 14px;
}

.knowledge-graph-editor-header span {
  color: #7b8798;
  font-size: 12px;
}

.knowledge-graph-editor-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.knowledge-graph-editor-actions button,
.knowledge-graph-editor-row button {
  display: inline-flex;
  min-height: 30px;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px solid #d9e1eb;
  border-radius: 5px;
  background: #fff;
  color: #536276;
  cursor: pointer;
}

.knowledge-graph-editor-actions button {
  padding: 0 11px;
  font-size: 12px;
}

.knowledge-graph-editor-actions svg,
.knowledge-graph-editor-row button :deep(svg) {
  width: 14px;
  height: 14px;
}

.knowledge-graph-editor-actions .editor-primary {
  border-color: #2779d6;
  background: #2779d6;
  color: #fff;
}

.knowledge-graph-editor-actions .editor-primary:disabled {
  border-color: #b8c7d9;
  background: #b8c7d9;
  cursor: not-allowed;
}

.knowledge-graph-editor-hint {
  margin: 12px 20px -4px;
  color: #6c7c91;
  font-size: 12px;
}

.knowledge-graph-editor-list {
  display: grid;
  gap: 7px;
  padding: 14px 20px 18px;
}

.knowledge-graph-editor-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: calc(var(--depth) * 28px);
}

.editor-branch-mark {
  width: 12px;
  height: 12px;
  flex: 0 0 12px;
  border-left: 1px solid #b8c8dd;
  border-bottom: 1px solid #b8c8dd;
}

.knowledge-graph-editor-row input {
  min-width: 0;
  flex: 1;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #d9e1eb;
  border-radius: 5px;
  background: #fff;
  color: #263347;
  outline: none;
}

.knowledge-graph-editor-row input:focus {
  border-color: #5797e2;
  box-shadow: 0 0 0 2px rgba(39, 121, 214, 0.12);
}

.knowledge-graph-editor-row button {
  width: 30px;
  padding: 0;
}

.knowledge-graph-editor-row button:hover {
  border-color: #aac8e9;
  color: #2779d6;
}

.knowledge-graph-editor-row .editor-delete:hover {
  border-color: #f1b7b7;
  color: #d64c4c;
}

.knowledge-graph-actions button :deep(svg) {
  width: 16px;
  height: 16px;
}

.knowledge-graph-actions > span {
  width: 40px;
  color: #65758a;
  font-size: 12px;
  text-align: center;
}

.knowledge-graph-canvas-wrap {
  overflow: hidden;
  background: #fff;
}

.knowledge-graph-canvas {
  position: relative;
  width: 100%;
  height: clamp(620px, 80vh, 1100px);
  overflow: hidden;
  cursor: grab;
  background-color: #fff;
  touch-action: none;
}

.knowledge-graph-canvas.dragging {
  cursor: grabbing;
}

.knowledge-graph-stage {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: top left;
  transition: transform 160ms ease-out;
}

.dragging .knowledge-graph-stage {
  transition: none;
}

.knowledge-bubble {
  position: absolute;
  left: var(--x);
  top: var(--y);
  display: flex;
  width: var(--bubble-size);
  height: var(--bubble-size);
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 4px;
  padding: 14px;
  border: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, var(--bubble-edge) 0%, var(--bubble-core) 100%);
  box-shadow: inset 0 2px 15px rgba(255, 255, 255, 0.48), 0 12px 22px rgba(75, 99, 128, 0.14);
  color: #243449;
  cursor: pointer;
  opacity: 0.76;
  text-align: center;
  transform: translate(-50%, -50%);
  transition: transform 180ms ease, opacity 180ms ease, box-shadow 180ms ease;
}

.knowledge-bubble:hover,
.knowledge-bubble:focus-visible,
.knowledge-bubble.is-selected {
  z-index: 2;
  opacity: 0.94;
  outline: none;
  transform: translate(-50%, -50%) scale(1.1);
  box-shadow: inset 0 2px 15px rgba(255, 255, 255, 0.58), 0 16px 28px rgba(55, 78, 109, 0.22);
}

.knowledge-bubble.is-muted {
  opacity: 0.22;
}

.knowledge-bubble.is-core {
  z-index: 1;
}

.knowledge-bubble-icon {
  color: var(--bubble-icon);
  font-size: clamp(14px, 2vw, 19px);
  line-height: 1;
}

.knowledge-bubble strong {
  display: -webkit-box;
  max-width: 88%;
  overflow: hidden;
  color: #243449;
  font-size: clamp(12px, 1.25vw, 16px);
  font-weight: 700;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.knowledge-bubble-importance {
  color: rgba(36, 52, 73, 0.68);
  font-size: 10px;
}

.knowledge-bubble-tooltip {
  position: absolute;
  bottom: calc(100% + 12px);
  left: 50%;
  width: min(230px, 52vw);
  padding: 8px 10px;
  border-radius: 5px;
  background: #253448;
  color: #fff;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.45;
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, 4px);
  transition: opacity 160ms ease, transform 160ms ease;
}

.knowledge-bubble:hover .knowledge-bubble-tooltip,
.knowledge-bubble:focus-visible .knowledge-bubble-tooltip {
  opacity: 1;
  transform: translate(-50%, 0);
}

.knowledge-graph-footer {
  display: flex;
  min-height: 72px;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 12px 20px;
  border-top: 1px solid #e7ebf0;
  background: #fff;
}

.knowledge-graph-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 14px;
}

.knowledge-graph-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #647286;
  font-size: 12px;
}

.knowledge-graph-legend i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.knowledge-graph-focus {
  min-width: 220px;
  text-align: right;
}

.knowledge-graph-focus span {
  margin-right: 8px;
  color: #7b8798;
  font-size: 12px;
}

.knowledge-graph-focus strong {
  color: #2d3d52;
  font-size: 13px;
}

.knowledge-graph-focus p {
  max-width: 330px;
  margin: 3px 0 0 auto;
  overflow: hidden;
  color: #748196;
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-graph-canvas {
  min-height: 0;
}

.knowledge-graph-stage {
  min-height: 0;
  padding: 0;
}

.knowledge-map-stage {
  position: relative;
  isolation: isolate;
  overflow: visible;
  border: 1px solid #dfe4ea;
  border-radius: 20px;
  background: #fff;
}

.knowledge-map-grid {
  position: absolute;
  inset: 0;
  z-index: -2;
  display: none;
}

.knowledge-map-edges {
  position: absolute;
  inset: 0;
  z-index: -1;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.knowledge-map-edges path {
  fill: none;
  stroke: #aeb0b4;
  stroke-width: 2;
  stroke-opacity: 1;
  stroke-linecap: butt;
  vector-effect: non-scaling-stroke;
}

.knowledge-map-node {
  position: absolute;
  z-index: 1;
  display: flex;
  width: max(220px, var(--node-size));
  height: 120px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 3px;
  padding: 10px;
  border: 2px solid #6ea1ff;
  border-radius: 0;
  background: #e8f6ff;
  color: #263347;
  cursor: pointer;
  text-align: center;
  transform: translate(-50%, -50%);
  box-shadow: 0 5px 12px rgba(74, 112, 155, .12);
  transition: opacity 160ms ease, transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease;
}

.knowledge-map-node:hover,
.knowledge-map-node:focus-visible,
.knowledge-map-node.is-selected {
  z-index: 3;
  border-color: var(--node-core);
  outline: none;
  transform: translate(-50%, -50%) scale(1.06);
  box-shadow: 0 14px 30px rgba(48, 84, 124, .22), inset 0 1px 0 rgba(255, 255, 255, .6);
}

.knowledge-map-node.is-muted { opacity: .25; }

.knowledge-map-node.level-0 { border-color: #6ea1ff; color: #27313d; }

.knowledge-map-node.level-0:hover,
.knowledge-map-node.level-0:focus-visible,
.knowledge-map-node.level-0.is-selected { box-shadow: 0 18px 38px rgba(38, 121, 210, .38); }

.knowledge-map-node.level-1 { border-color: #6ea1ff; color: #27313d; }

.knowledge-map-node.level-1::after { content: none; }

.knowledge-map-node.level-2 {
  gap: 2px;
  border-radius: 0;
  border-color: #6ea1ff;
  padding: 8px;
  background: #e8f6ff;
}

.knowledge-map-node.node-chapter {
  border-radius: 0;
  background: #e8f6ff;
}

.knowledge-map-node.node-section,
.knowledge-map-node.node-subsection {
  border-radius: 18px;
  background: #f4f9ff;
}

.knowledge-map-node.node-subsection {
  border-style: dashed;
  background: #fffaf0;
}

.knowledge-map-node.level-2 .knowledge-map-kicker { display: none; }
.knowledge-map-node.level-2 strong { -webkit-line-clamp: 3; font-size: 14px; }

.knowledge-map-node strong {
  display: -webkit-box;
  width: 100%;
  padding: 0 4px;
  overflow: hidden;
  color: inherit;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.3;
  text-align: center;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.knowledge-map-node.level-0 strong { font-size: 22px; -webkit-line-clamp: 3; }

.knowledge-map-kicker {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #71849a;
  font-size: 12px;
  letter-spacing: .4px;
  line-height: 1;
}

.knowledge-map-kicker i { width: 6px; height: 6px; border-radius: 50%; background: var(--node-core); }
.knowledge-map-node.level-0 .knowledge-map-kicker,
.knowledge-map-node.level-1 .knowledge-map-kicker { color: #4c5968; }
.knowledge-map-node.level-0 .knowledge-map-kicker i,
.knowledge-map-node.level-1 .knowledge-map-kicker i { background: #6ea1ff; }

.knowledge-tree {
  width: min(100%, 1060px);
  margin: 0 auto;
}

.knowledge-tree-node {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--node-edge);
  background: linear-gradient(135deg, color-mix(in srgb, var(--node-edge) 68%, #fff), color-mix(in srgb, var(--node-core) 54%, #fff));
  color: #263b53;
  cursor: pointer;
  transition: opacity 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}

.knowledge-tree-node:hover,
.knowledge-tree-node:focus-visible,
.knowledge-tree-node.is-selected {
  z-index: 2;
  outline: none;
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(59, 91, 128, .18);
}

.knowledge-tree-node.is-muted {
  opacity: .28;
}

.knowledge-tree-root {
  position: relative;
  display: flex;
  width: 156px;
  height: 156px;
  min-width: 156px;
  min-height: 156px;
  margin: 0 auto 38px;
  padding: 24px;
  flex-direction: column;
  gap: 4px;
  border-radius: 50%;
  border-color: #a9cef7;
  background: linear-gradient(135deg, #dceeff, #a9cef7);
  box-shadow: 0 8px 20px rgba(70, 133, 208, .16);
}

.knowledge-tree-root::after {
  position: absolute;
  bottom: -39px;
  left: 50%;
  height: 39px;
  border-left: 1px solid #bfd0e3;
  content: '';
}

.knowledge-tree-kicker {
  color: #637b97;
  font-size: 10px;
  letter-spacing: .4px;
}

.knowledge-tree-root strong {
  max-width: 112px;
  overflow: hidden;
  font-size: 17px;
  line-height: 1.4;
  text-align: center;
  word-break: break-word;
}

.knowledge-tree-branches {
  position: relative;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 22px 16px;
  padding-top: 1px;
}

.knowledge-tree-branches::before {
  position: absolute;
  top: 0;
  right: 9%;
  left: 9%;
  border-top: 1px solid #bfd0e3;
  content: '';
}

.knowledge-tree-branch {
  position: relative;
  display: flex;
  min-width: 0;
  align-items: center;
  flex-direction: column;
}

.knowledge-tree-branch::before {
  position: absolute;
  top: 0;
  height: 15px;
  border-left: 1px solid #bfd0e3;
  content: '';
}

.knowledge-tree-group {
  width: 118px;
  height: 118px;
  min-width: 118px;
  max-width: 118px;
  min-height: 118px;
  margin-top: 15px;
  padding: 18px;
  flex-direction: column;
  gap: 2px;
  border-radius: 50%;
}

.knowledge-tree-group strong {
  max-width: 82px;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.35;
  text-align: center;
  word-break: break-word;
}

.knowledge-tree-children {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  width: 100%;
  gap: 7px;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid #d1dce8;
}

.knowledge-tree-leaf {
  width: 88px;
  height: 88px;
  min-height: 88px;
  padding: 12px;
  gap: 5px;
  border-radius: 50%;
  background: #fff;
  font-size: 12px;
  text-align: center;
}

.knowledge-tree-leaf > span:last-child {
  overflow: hidden;
  line-height: 1.3;
  word-break: break-word;
}

.knowledge-tree-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 7px;
  border-radius: 50%;
  background: var(--node-core);
}

.knowledge-tree-empty {
  margin: 14px 0 0;
  color: #8a98a9;
  font-size: 12px;
}

.knowledge-tree-empty-root {
  margin-top: 0;
  padding: 18px;
  border: 1px dashed #cbd7e4;
  text-align: center;
}

.knowledge-graph-empty {
  display: flex;
  min-height: 460px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  padding: 28px;
  color: #718096;
  text-align: center;
}

.knowledge-graph-empty-mark {
  color: #9ab6d0;
  font-size: 34px;
  line-height: 1;
}

.knowledge-graph-empty strong {
  color: #3d5067;
  font-size: 15px;
}

.knowledge-graph-empty p {
  max-width: 360px;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.knowledge-graph-empty-flow {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  color: #54708f;
  font-size: 12px;
}

.knowledge-graph-empty-flow span {
  padding: 7px 10px;
  border: 1px solid #cfe0f2;
  border-radius: 6px;
  background: #fff;
}

.knowledge-graph-empty-flow i {
  color: #8ca8c4;
  font-style: normal;
}

.knowledge-tree-children {
  border-top-style: solid;
}

.knowledge-tree-empty-root {
  border-style: solid;
}

@media (max-width: 760px) {
  .knowledge-graph-header,
  .knowledge-graph-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .knowledge-graph-actions {
    align-self: flex-end;
    margin-top: -36px;
  }

  .knowledge-graph-focus {
    min-width: 0;
    text-align: left;
  }

  .knowledge-graph-focus p {
    margin-left: 0;
  }

  .knowledge-graph-editor-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .knowledge-graph-editor-actions {
    align-self: flex-end;
  }

  .knowledge-graph-stage {
    min-width: 680px;
    padding: 28px 22px 38px;
  }

  .knowledge-tree-branches {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}
</style>
