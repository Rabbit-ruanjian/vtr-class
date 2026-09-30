<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import gateInner from '@/assets/courtyard/gate-inner.jpg'
import gateWallLeft from '@/assets/courtyard/gate-wall-left.jpg'
import gateDoorLeft from '@/assets/courtyard/gate-door-left.jpg'
import gateDoorRight from '@/assets/courtyard/gate-door-right.jpg'
import gateWallRight from '@/assets/courtyard/gate-wall-right.jpg'
import guideGate from '@/assets/courtyard/guide-gate.jpg'
import courtyardImage from '@/assets/courtyard/courtyard-v2.jpg'
import guideYard from '@/assets/courtyard/guide-yard.jpg'

const router = useRouter()
const authStore = useAuthStore()
const scene = ref('gate')
const gateOpening = ref(false)
const guideVisible = ref(true)
const designVisible = ref(false)
const selectedRoom = ref(null)
const yardRef = ref(null)
const guideRef = ref(null)
const guidePosition = ref({ x: 49, y: 76 })
const guideDragging = ref(false)
const guideWalking = ref(false)
let guideWalkTimer = null

const isGuest = computed(() => !authStore.isLoggedIn)
const isAdmin = computed(() => authStore.isAdmin)
const displayName = computed(() => authStore.user?.nickname || authStore.user?.username || '来客')
const rooms = [
  { key: 'forum', name: '聚贤亭', module: '教研社区', path: '/forum', className: 'room-forum', description: '进入项目现有教研社区，与同仁论道、分享教学经验与成果。' },
  { key: 'activities', name: '雅集台', module: '教研活动', path: '/activities', className: 'room-activities', description: '进入项目现有教研活动，参加集体备课、沙龙和主题研讨。' },
  { key: 'courseware', name: '传习堂', module: '教学中心', path: '/courseware', className: 'room-courseware', description: '进入项目现有教学中心，使用课程、课件、题库和科举闯关。' },
  { key: 'admin', name: '守正阁', module: '管理中心', path: '/admin', className: 'room-admin', description: '进入项目现有管理中心，处理身份、内容审核与平台事务。', adminOnly: true },
  { key: 'notifications', name: '信笺阁', module: '消息中心', path: '/notifications', className: 'room-notifications', description: '进入项目现有消息中心，查看通知、待办和往来消息。' }
]
const visibleRooms = computed(() => rooms.filter((room) => !room.adminOnly || isAdmin.value))
const guideStyle = computed(() => ({ left: guidePosition.value.x + '%', top: guidePosition.value.y + '%' }))

function enterCourtyard() {
  if (gateOpening.value) return
  gateOpening.value = true
  window.setTimeout(() => {
    scene.value = 'yard'
    guideVisible.value = false
    gateOpening.value = false
  }, 1250)
}
function returnToGate() {
  scene.value = 'gate'
  guideVisible.value = true
  selectedRoom.value = null
}
function selectRoom(room) {
  selectedRoom.value = room
  guideVisible.value = false
}
function enterRoom(room = selectedRoom.value) {
  if (room) router.push(room.path)
}
function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}
function setGuidePosition(clientX, clientY) {
  const rect = yardRef.value?.getBoundingClientRect()
  if (!rect) return
  guidePosition.value = {
    x: clamp(((clientX - rect.left) / rect.width) * 100, 8, 92),
    y: clamp(((clientY - rect.top) / rect.height) * 100, 43, 88)
  }
}
function beginGuideMotion() {
  guideWalking.value = true
  window.clearTimeout(guideWalkTimer)
  guideWalkTimer = window.setTimeout(() => { guideWalking.value = false }, 850)
}
function moveGuideTo(event) {
  if (guideDragging.value) return
  setGuidePosition(event.clientX, event.clientY)
  guideVisible.value = false
  beginGuideMotion()
}
function startGuideDrag(event) {
  event.preventDefault()
  event.stopPropagation()
  guideDragging.value = true
  guideWalking.value = true
  guideVisible.value = false
  guideRef.value?.setPointerCapture?.(event.pointerId)
}
function dragGuide(event) {
  if (!guideDragging.value) return
  setGuidePosition(event.clientX, event.clientY)
}
function stopGuideDrag(event) {
  if (!guideDragging.value) return
  guideDragging.value = false
  guideWalking.value = false
  guideRef.value?.releasePointerCapture?.(event.pointerId)
}
</script>

<template>
  <main class="courtyard-home">
    <header class="courtyard-topbar">
      <button class="brand-block" type="button" @click="returnToGate">
        <span class="brand-seal">薪</span>
        <span><strong>薪火研堂</strong><small>虚拟教研室 · 研学庭院</small></span>
      </button>
      <div class="visitor-block">
        <span>{{ isGuest ? '访客游览' : displayName + ' · 已入堂' }}</span>
        <button type="button" @click="router.push(isGuest ? '/login' : '/profile')">{{ isGuest ? '登录' : '我的资料' }}</button>
      </div>
    </header>
    <div class="gold-divider"></div>

    <section v-if="scene === 'gate'" class="scene gate-scene" :class="{ opening: gateOpening }" aria-label="薪火研堂大门">
      <img class="gate-inner" :src="gateInner" alt="门后的薪火研堂" />
      <div class="gate-wall gate-wall-left"><img :src="gateWallLeft" alt="" /></div>
      <button class="gate-door gate-door-left" type="button" aria-label="推开左门进入庭院" @click="enterCourtyard"><img :src="gateDoorLeft" alt="左门扇" /><span>推门</span></button>
      <button class="gate-door gate-door-right" type="button" aria-label="推开右门进入庭院" @click="enterCourtyard"><img :src="gateDoorRight" alt="右门扇" /><span>入内</span></button>
      <div class="gate-wall gate-wall-right"><img :src="gateWallRight" alt="" /></div>
      <div class="gate-plaque"><strong>薪火研堂</strong><small>虚 拟 教 研 室</small></div>
      <div v-if="gateOpening" class="entering-person" aria-label="访客正在进入庭院">
        <img :src="guideYard" alt="进入庭院的访客" /><span>入庭</span>
      </div>
      <div class="gate-guide">
        <div class="guide-bubble">先生远来，欢迎光临。推门入内，先看一看属于你的研学庭院。</div>
        <img :src="guideGate" alt="在门前迎客的研学童子" /><span>研学童子</span>
      </div>
    </section>

    <section v-else ref="yardRef" class="scene yard-scene" aria-label="薪火研堂四合院中庭">
      <img class="yard-background" :src="courtyardImage" alt="薪火研堂四合院庭院景色" />
      <div class="yard-wash"></div>
      <div class="yard-walk-area" aria-label="点击庭院地面让童子移动" @pointerdown="moveGuideTo"></div>
      <button class="return-gate" type="button" @click="returnToGate">‹ 返回院门</button>
      <button class="continue-design" type="button" @click="designVisible = true">继续设计庭院</button>
      <button v-for="room in visibleRooms" :key="room.key" class="yard-room" :class="[room.className, { active: selectedRoom?.key === room.key }]" type="button" @click="selectRoom(room)">
        <span class="room-pulse"></span><span class="room-label"><strong>{{ room.name }}</strong><small>{{ room.module }}</small></span>
      </button>
      <div ref="guideRef" class="yard-guide" :class="{ dragging: guideDragging, walking: guideWalking }" :style="guideStyle" role="button" tabindex="0" aria-label="研学童子，可以拖动" @pointerdown="startGuideDrag" @pointermove="dragGuide" @pointerup="stopGuideDrag" @pointercancel="stopGuideDrag">
        <div v-if="guideVisible" class="yard-guide-bubble">{{ isAdmin ? '掌印先生请看：正厅传习，两侧论学，守正阁也已为您开启。按住我可以在院中移动。' : '先生请看：点击庭院地面让我走过去，也可以用鼠标按住我拖动。' }}</div>
        <img :src="guideYard" alt="站在庭院中的研学童子" /><span>研学童子</span>
      </div>
      <aside v-if="selectedRoom" class="room-card">
        <button class="card-close" type="button" aria-label="关闭房间介绍" @click="selectedRoom = null; guideVisible = true">×</button>
        <span>{{ selectedRoom.module }}</span><h2>{{ selectedRoom.name }}</h2><p>{{ selectedRoom.description }}</p>
        <button type="button" @click="enterRoom()">随童子进入</button>
      </aside>
      <div class="yard-caption"><strong>薪火相传 · 研思共进</strong><span>建筑原有门匾即为入口，点击进入真实功能模块</span></div>
    </section>

    <footer class="courtyard-footer"><span>庭院导览</span><p>传习堂授课 · 聚贤亭论道 · 雅集台研讨 · 信笺阁传音<span v-if="isAdmin"> · 守正阁理事</span></p></footer>

    <div v-if="designVisible" class="design-overlay" role="dialog" aria-modal="true" aria-label="庭院后续设计" @click.self="designVisible = false">
      <section class="design-card">
        <button type="button" aria-label="关闭" @click="designVisible = false">×</button><span>下一阶段 · 等你定方向</span>
        <h2>庭院已经搭好，接下来可以继续设计</h2><p>当前先完成“推门入院”和“点院入舍”的主体验。下一步可以从下面三种方向继续深化：</p>
        <div class="design-options"><article><b>人物动线</b><small>童子在庭院中行走，带用户逐间参观。</small></article><article><b>时辰景色</b><small>按早晚切换光影、灯笼、落叶与天气。</small></article><article><b>AI 对谈</b><small>用户直接告诉童子来意，由 AI 推荐去处。</small></article></div>
        <em>你看完这一版后告诉我想先做哪一项，我再继续往下设计。</em>
      </section>
    </div>
  </main>
</template>

<style scoped>
.courtyard-home{--paper:#fdf8ec;--ink:#3a332a;--gold:#c08d3c;--red:#a83d2c;--green:#5d7d6e;min-height:100vh;padding:12px clamp(12px,2vw,24px) 18px;color:var(--ink);background:#eae0cb;font-family:"Noto Sans SC","Microsoft YaHei",sans-serif}.courtyard-topbar{display:flex;align-items:center;justify-content:space-between;max-width:1220px;margin:0 auto;padding:2px 4px 10px}.brand-block{display:flex;align-items:center;gap:12px;padding:0;border:0;color:inherit;background:transparent;text-align:left;cursor:pointer}.brand-seal{display:grid;width:46px;height:46px;place-items:center;border-radius:9px;color:#f9edd6;background:linear-gradient(145deg,#b24734,#7f2c20);box-shadow:0 5px 16px rgba(123,55,35,.28);font:700 25px "STKaiti",serif}.brand-block strong,.brand-block small{display:block}.brand-block strong{font:900 25px "STKaiti","KaiTi",serif;letter-spacing:.25em}.brand-block small{margin-top:3px;color:var(--green);font-size:11px;letter-spacing:.25em}.visitor-block{display:flex;align-items:center;gap:10px;color:#795040;font-size:12px}.visitor-block button{padding:6px 14px;border:1px solid rgba(93,125,110,.5);border-radius:999px;color:var(--green);background:rgba(255,255,255,.5);cursor:pointer}.gold-divider{max-width:1220px;height:2px;margin:0 auto 12px;background:linear-gradient(90deg,transparent,var(--gold),transparent)}
.scene{position:relative;width:min(1220px,100%);height:min(720px,calc(100vh - 125px));min-height:590px;margin:0 auto;overflow:hidden;border:1px solid rgba(178,155,110,.58);border-radius:16px;background:#cdb98d;box-shadow:0 16px 38px rgba(90,72,40,.28)}.gate-inner,.yard-background{position:absolute;inset:0;width:100%;height:100%;object-fit:fill}.gate-wall,.gate-door{position:absolute;top:0;bottom:0;z-index:3;padding:0;border:0;background:transparent;overflow:visible}.gate-wall img,.gate-door img{width:100%;height:100%;object-fit:fill;transition:transform 1.2s cubic-bezier(.55,.02,.25,1),filter .2s}.gate-wall-left{left:0;width:20%}.gate-wall-right{right:0;width:20%}.gate-door{width:30%;cursor:pointer}.gate-door-left{left:20%}.gate-door-right{left:50%}.gate-door:hover img{filter:brightness(1.08)}.gate-door span{position:absolute;top:47%;left:50%;padding:12px 7px;transform:translate(-50%,-50%);border:1px solid #b9a57e;border-radius:9px;color:#784a29;background:rgba(253,248,236,.92);writing-mode:vertical-rl;letter-spacing:.28em;opacity:0;transition:opacity .2s}.gate-door:hover span{opacity:1}.opening .gate-door-left img{transform:translateX(-102%)}.opening .gate-door-right img{transform:translateX(102%)}
.gate-plaque{position:absolute;top:7%;left:50%;z-index:5;display:flex;flex-direction:column;align-items:center;padding:9px 32px 11px;transform:translateX(-50%);border:2px solid var(--gold);border-radius:9px;color:#784a29;background:linear-gradient(#fffaf0,#ecddbc);box-shadow:0 8px 20px rgba(90,60,20,.25);transition:.8s}.gate-plaque strong{font:900 31px "STKaiti",serif;letter-spacing:.35em;text-indent:.35em}.gate-plaque small{margin-top:3px;color:#9a7541;letter-spacing:.4em}.opening .gate-plaque{transform:translate(-50%,-32px);opacity:0}.opening .gate-guide,.opening .gate-entry-card{opacity:0}.gate-guide{position:absolute;bottom:72px;left:9%;z-index:6;width:145px;text-align:center;filter:drop-shadow(0 12px 15px rgba(60,40,10,.3));transition:.7s}.gate-guide img{width:100%;max-height:215px;object-fit:contain;border:2px solid rgba(255,255,255,.85);border-radius:14px}.gate-guide>span,.yard-guide>span{display:inline-block;margin-top:4px;padding:3px 12px;border:1px solid rgba(93,125,110,.45);border-radius:999px;color:var(--green);background:rgba(253,248,236,.94);font-size:12px;letter-spacing:.2em}.guide-bubble{position:absolute;top:-86px;left:50%;width:280px;padding:9px 14px;transform:translateX(-38%);border:2px solid #b9a57e;border-radius:14px;color:#4d4033;background:rgba(253,248,236,.97);font:13px/1.7 "STKaiti",serif;box-shadow:0 8px 22px rgba(90,72,40,.25)}.gate-entry-card{position:absolute;top:34%;right:7%;z-index:6;width:min(390px,38%);padding:24px 28px;border:1px solid rgba(178,155,110,.82);border-radius:17px;background:rgba(253,248,236,.92);box-shadow:0 18px 42px rgba(62,43,20,.28);transition:.7s}.gate-entry-card>span{color:var(--red);font-size:11px;letter-spacing:.24em}.gate-entry-card h1{margin:10px 0 12px;font:700 clamp(27px,3vw,39px)/1.35 "STKaiti",serif}.gate-entry-card p{color:#6b6253;font-size:13px;line-height:1.9}.gate-entry-card div{display:flex;gap:10px;margin-top:19px}.gate-entry-card button{padding:9px 17px;border:1px solid var(--green);border-radius:999px;color:var(--green);background:#fffaf0;cursor:pointer}.gate-entry-card .primary-entry{border-color:var(--red);color:#fff;background:var(--red)}
.entering-person{position:absolute;bottom:7%;left:50%;z-index:7;width:92px;transform:translateX(-50%);text-align:center;animation:walk-in 1.15s ease-in forwards;filter:drop-shadow(0 9px 10px rgba(50,30,10,.38))}.entering-person img{width:100%;height:125px;object-fit:contain;border-radius:12px}.entering-person span{display:inline-block;margin-top:-5px;padding:2px 9px;border-radius:999px;color:#6b4a2f;background:rgba(253,248,236,.9);font-size:10px;letter-spacing:.18em}@keyframes walk-in{0%{transform:translate(-50%,22px) scale(1);opacity:0}15%{opacity:1}100%{transform:translate(-50%,-230px) scale(.38);opacity:.2}}
.yard-scene{animation:yard-in .8s ease both}@keyframes yard-in{from{opacity:0;transform:scale(1.025)}}.yard-wash{position:absolute;inset:0;z-index:1;background:radial-gradient(circle at 50% 45%,transparent 34%,rgba(47,30,10,.18));pointer-events:none}.return-gate,.continue-design{position:absolute;top:14px;z-index:12;padding:7px 14px;border:1px solid rgba(93,125,110,.58);border-radius:999px;color:var(--green);background:rgba(253,248,236,.93);box-shadow:0 4px 12px rgba(60,40,10,.2);cursor:pointer}.return-gate{left:14px}.continue-design{right:14px;border-color:rgba(176,64,46,.5);color:var(--red)}.yard-room{position:absolute;z-index:5;padding:0;border:2px solid transparent;border-radius:10px;background:transparent;cursor:pointer;transition:.25s}.yard-room:hover,.yard-room.active{border-color:#efd49c;box-shadow:0 0 0 5px rgba(239,212,156,.3),inset 0 0 30px rgba(255,226,163,.42)}.room-forum{left:6%;top:32%;width:16%;height:13%}.room-activities{left:20%;top:37%;width:10%;height:11%}.room-courseware{left:43%;top:35%;width:15%;height:13%}.room-admin{left:71%;top:36%;width:12%;height:11%}.room-notifications{left:82%;top:34%;width:13%;height:12%}.room-label{position:absolute;bottom:110%;left:50%;display:flex;flex-direction:column;min-width:104px;padding:7px 11px;transform:translateX(-50%);border:1px solid #d1aa65;border-radius:5px;color:#f4dfb3;background:rgba(48,29,14,.9);opacity:0;transition:.2s}.yard-room:hover .room-label,.yard-room.active .room-label{opacity:1}.room-label strong{font:700 15px "STKaiti",serif;letter-spacing:.15em}.room-label small{margin-top:2px;font-size:10px}.room-pulse{position:absolute;top:50%;left:50%;width:12px;height:12px;transform:translate(-50%,-50%);border-radius:50%;background:#f7dda7;box-shadow:0 0 0 7px rgba(247,221,167,.22),0 0 22px #f7dda7;animation:pulse 2.2s infinite}@keyframes pulse{50%{box-shadow:0 0 0 14px rgba(247,221,167,.05),0 0 30px #f7dda7}}
.yard-guide{position:absolute;bottom:31px;left:43%;z-index:8;width:124px;text-align:center;filter:drop-shadow(0 10px 14px rgba(60,40,10,.28))}.yard-guide img{width:100%;max-height:185px;object-fit:contain;border:2px solid rgba(255,255,255,.85);border-radius:14px}.yard-guide-bubble{position:absolute;top:-102px;left:50%;width:330px;padding:10px 15px;transform:translateX(-50%);border:2px solid #b9a57e;border-radius:14px;color:#473b30;background:rgba(253,248,236,.97);font:13px/1.8 "STKaiti",serif;box-shadow:0 8px 22px rgba(90,72,40,.25)}.room-card{position:absolute;right:4%;bottom:7%;z-index:11;width:300px;padding:23px;border:2px solid #b9a57e;border-radius:16px;color:#453b31;background:rgba(253,248,236,.96);box-shadow:0 18px 40px rgba(55,35,15,.3)}.room-card>span{color:var(--red);font-size:11px;letter-spacing:.2em}.room-card h2{margin:5px 0 9px;font:700 28px "STKaiti",serif}.room-card p{color:#6b6253;font-size:13px;line-height:1.8}.room-card>button:last-child{width:100%;margin-top:15px;padding:9px;border:0;border-radius:9px;color:white;background:var(--red);cursor:pointer}.card-close{position:absolute;top:8px;right:10px;border:0;color:#887a66;background:transparent;font-size:22px;cursor:pointer}.yard-caption{position:absolute;bottom:15px;left:20px;z-index:4;display:flex;flex-direction:column;color:#fff1d1;text-shadow:0 2px 8px rgba(0,0,0,.9)}.yard-caption strong{font:700 17px "STKaiti",serif;letter-spacing:.18em}.yard-caption span{margin-top:3px;font-size:10px;letter-spacing:.12em}.courtyard-footer{display:flex;align-items:center;gap:12px;width:min(1220px,100%);margin:11px auto 0;padding:8px 16px;border:1px solid rgba(178,155,110,.65);border-radius:999px;background:rgba(253,248,236,.78);font-size:12px}.courtyard-footer>span{color:var(--red);font-family:"STKaiti",serif;letter-spacing:.2em}.courtyard-footer p{color:#766b5a}
.design-overlay{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:20px;background:rgba(48,35,20,.55);backdrop-filter:blur(5px)}.design-card{position:relative;width:min(690px,94vw);padding:30px;border:2px solid #b9a57e;border-radius:18px;background:var(--paper);box-shadow:0 24px 70px rgba(0,0,0,.35)}.design-card>button{position:absolute;top:10px;right:13px;border:0;color:#766b5a;background:transparent;font-size:25px;cursor:pointer}.design-card>span{color:var(--red);font-size:11px;letter-spacing:.22em}.design-card h2{margin:9px 0;font:700 27px "STKaiti",serif}.design-card p{color:#6b6253;font-size:13px;line-height:1.8}.design-options{display:grid;grid-template-columns:repeat(3,1fr);gap:11px;margin:20px 0}.design-options article{padding:15px;border:1px solid #d4c19d;border-radius:11px;background:#f8f0de}.design-options b,.design-options small{display:block}.design-options b{color:#795040;font:700 17px "STKaiti",serif}.design-options small{margin-top:7px;color:#756b5a;line-height:1.65}.design-card em{color:var(--green);font-style:normal;font-size:13px}
@media(max-width:760px){.courtyard-home{padding:8px}.brand-block strong{font-size:21px}.brand-block small,.visitor-block>span{display:none}.scene{height:calc(100svh - 105px);min-height:600px;border-radius:11px}.gate-plaque{top:4%;padding:7px 18px}.gate-plaque strong{font-size:24px}.gate-guide{left:5%;bottom:55px;width:100px}.guide-bubble{display:none}.gate-entry-card{top:25%;right:7%;width:58%;padding:18px}.gate-entry-card h1{font-size:26px}.gate-entry-card p{font-size:11px;line-height:1.65}.gate-entry-card div{flex-direction:column}.gate-entry-card button{padding:8px}.yard-guide{left:39%;bottom:38px;width:92px}.yard-guide-bubble{width:245px;top:-105px;font-size:12px}.room-label{min-width:78px;padding:5px}.room-label strong{font-size:12px}.room-card{right:4%;bottom:4%;left:4%;width:auto;padding:18px}.yard-caption{display:none}.continue-design{top:53px;right:10px}.design-options{grid-template-columns:1fr}.courtyard-footer{border-radius:12px}.courtyard-footer p{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}}
.yard-walk-area{position:absolute;inset:13% 3% 3%;z-index:2;cursor:crosshair;touch-action:none}
.yard-guide{bottom:auto;transform:translate(-50%,-50%);cursor:grab;user-select:none;touch-action:none;transition:left .75s cubic-bezier(.34,.8,.36,1),top .75s cubic-bezier(.34,.8,.36,1),filter .2s}
.yard-guide.dragging{cursor:grabbing;transition:none;filter:drop-shadow(0 15px 19px rgba(60,40,10,.4))}
.yard-guide img{pointer-events:none}
.yard-guide.walking:not(.dragging) img{animation:guide-step .32s ease-in-out infinite alternate}
.yard-guide.dragging img{transform:scale(1.04)}
.yard-guide-bubble{pointer-events:none}
@keyframes guide-step{from{transform:translateY(0) rotate(-1.2deg)}to{transform:translateY(-7px) rotate(1.2deg)}}
@media(max-width:760px){.yard-guide{bottom:auto;left:auto;width:92px}}
/* 新版参考庭院：只借用庭院景色，匾额仍进入本项目真实模块 */
.yard-room{width:76px;height:auto;border:0;overflow:visible;transform:translateX(-50%);box-shadow:none}
.yard-room:hover,.yard-room.active{border-color:transparent;box-shadow:none;transform:translateX(-50%) scale(1.08)}
.room-forum{left:17.5%;top:40%;width:76px;height:auto}.room-activities{left:26.5%;top:40%;width:76px;height:auto}.room-courseware{left:47.5%;top:35%;width:76px;height:auto}.room-admin{left:71.5%;top:40%;width:76px;height:auto}.room-notifications{left:80%;top:40%;width:76px;height:auto}
.room-pulse{display:none}
.room-label{position:relative;bottom:auto;left:auto;display:flex;width:76px;min-width:76px;flex-direction:column;align-items:center;padding:11px 6px 12px;transform:none;border:2px solid #c89a4a;border-radius:8px;color:#f3d9a5;background:linear-gradient(180deg,#513924,#2e2015);box-shadow:0 7px 18px rgba(0,0,0,.43),inset 0 0 0 1px rgba(255,255,255,.1);opacity:1;filter:drop-shadow(0 3px 3px rgba(0,0,0,.28))}
.room-label::before{position:absolute;top:-10px;left:50%;width:31px;height:17px;transform:translateX(-50%);border-radius:50%;background:radial-gradient(ellipse at 50% 30%,#e0b35f,#8b6123 72%);box-shadow:0 2px 6px rgba(0,0,0,.35);content:""}
.room-label::after{position:absolute;bottom:-7px;left:50%;width:10px;height:10px;transform:translateX(-50%) rotate(45deg);border-radius:2px;background:#a83d2c;content:""}
.room-label strong{min-height:88px;font:700 16px/1.3 "STKaiti",serif;letter-spacing:.24em;writing-mode:vertical-rl}
.room-label small{margin-top:5px;color:#d8aa59;font-size:10px;letter-spacing:.12em;white-space:nowrap}
.yard-room:hover .room-label,.yard-room.active .room-label{border-color:#f3d89e;background:linear-gradient(180deg,#6d4c2e,#3a2818);box-shadow:0 8px 25px rgba(0,0,0,.42),0 0 24px rgba(225,180,96,.55)}
@media(max-width:760px){.yard-room,.room-forum,.room-activities,.room-courseware,.room-admin,.room-notifications{width:53px}.room-label{width:53px;min-width:53px;padding:8px 3px}.room-label strong{min-height:72px;font-size:12px}.room-label small{font-size:8px;letter-spacing:0}.room-forum{left:14%;top:38%}.room-activities{left:31%;top:42%}.room-courseware{left:50%;top:34%}.room-admin{left:69%;top:42%}.room-notifications{left:86%;top:38%}}
/* 将入口文字直接放进庭院建筑原有留白门匾 */
.yard-room,.yard-room:hover,.yard-room.active{height:7%;min-height:30px;transform:translate(-50%,-50%);border:0;border-radius:6px;background:transparent;box-shadow:none}
.yard-room:hover,.yard-room.active{transform:translate(-50%,-50%) scale(1.06);box-shadow:0 0 0 3px rgba(218,174,91,.26),0 0 18px rgba(218,174,91,.38)}
.room-forum{left:11.3%;top:51%;width:8%;height:6%}.room-activities{left:21.4%;top:51%;width:8%;height:6%}.room-courseware{left:49%;top:51%;width:7.5%;height:6%}.room-admin{left:79.5%;top:51%;width:8%;height:6%}.room-notifications{left:90%;top:51%;width:8%;height:6%}
.room-label{position:absolute;inset:0;display:flex;width:auto;min-width:0;height:100%;flex-direction:row;align-items:center;justify-content:center;padding:0;transform:none;border:0;border-radius:5px;color:#4c3421;background:transparent;box-shadow:none;opacity:1;filter:none}
.room-label::before,.room-label::after{display:none}
.room-label strong{min-height:0;font:800 clamp(12px,1.35vw,20px)/1 "STKaiti","KaiTi",serif;letter-spacing:.18em;white-space:nowrap;writing-mode:horizontal-tb;text-shadow:0 1px 0 rgba(255,255,255,.72),0 0 5px rgba(255,244,211,.55)}
.room-label small{position:absolute;top:calc(100% + 8px);left:50%;margin:0;padding:5px 10px;transform:translateX(-50%);border:1px solid #c89a4a;border-radius:999px;color:#f2d49a;background:rgba(50,31,17,.92);font-size:10px;letter-spacing:.1em;white-space:nowrap;opacity:0;transition:opacity .2s}
.yard-room:hover .room-label,.yard-room.active .room-label{border:0;background:rgba(255,232,176,.16);box-shadow:none}
.yard-room:hover .room-label small,.yard-room.active .room-label small{opacity:1}
@media(max-width:760px){.yard-room,.yard-room:hover,.yard-room.active{height:6%;min-height:24px}.room-forum{left:11.3%;top:51%;width:10%}.room-activities{left:21.4%;top:51%;width:10%}.room-courseware{left:49%;top:51%;width:10%}.room-admin{left:79.5%;top:51%;width:10%}.room-notifications{left:90%;top:51%;width:10%}.room-label{width:auto;min-width:0;padding:0}.room-label strong{min-height:0;font-size:10px;letter-spacing:.05em;writing-mode:horizontal-tb}.room-label small{font-size:8px}}
.room-forum{left:12.8%;top:52.7%;width:8.4%;height:6%}.room-activities{left:22.1%;top:52.7%;width:8.4%;height:6%}.room-courseware{left:51.8%;top:52.7%;width:7.6%;height:6%}.room-admin{left:84.6%;top:52.7%;width:8.2%;height:6%}.room-notifications{left:92.4%;top:52.7%;width:8.2%;height:6%}
.room-label strong{color:#4a3322;font-size:clamp(12px,1.3vw,19px);font-weight:800;text-shadow:0 1px 0 rgba(255,255,255,.9),0 0 4px rgba(255,244,211,.6)}
@media(max-width:760px){.room-forum{left:12.8%;top:52.7%;width:11%}.room-activities{left:22.1%;top:52.7%;width:11%}.room-courseware{left:51.8%;top:52.7%;width:11%}.room-admin{left:84.6%;top:52.7%;width:11%}.room-notifications{left:92.4%;top:52.7%;width:11%}}
</style>
