import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/layouts/AppLayout.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import DashboardView from '@/views/DashboardView.vue'
import NoticesView from '@/views/NoticesView.vue'
import NoticeDetailView from '@/views/NoticeDetailView.vue'
import ForumView from '@/views/ForumView.vue'
import ForumPostDetailView from '@/views/ForumPostDetailView.vue'
import ActivitiesView from '@/views/ActivitiesView.vue'
import ActivityDetailView from '@/views/ActivityDetailView.vue'
import ResearchToolsView from '@/views/ResearchToolsView.vue'
import CoursewareView from '@/views/CoursewareView.vue'
import CourseWorkspaceView from '@/views/CourseWorkspaceView.vue'
import AssignmentsView from '@/views/AssignmentsView.vue'
import AssignmentAnswerView from '@/views/AssignmentAnswerView.vue'
import ExamDetailView from '@/views/ExamDetailView.vue'
import NotificationsView from '@/views/NotificationsView.vue'
import ProfileView from '@/views/ProfileView.vue'
import AdminCenterView from '@/views/AdminCenterView.vue'
import ResearchTasksView from '@/views/ResearchTasksView.vue'
import AdminActivityReviewView from '@/views/AdminActivityReviewView.vue'
import AcademicClassesView from '@/views/AcademicClassesView.vue'
import SchoolManagementView from '@/views/SchoolManagementView.vue'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: {
      title: '登录系统',
      guestOnly: true
    }
  },
  {
    path: '/register',
    name: 'register',
    component: RegisterView,
    meta: {
      title: '注册账号',
      guestOnly: true
    }
  },
  {
    path: '/',
    component: AppLayout,
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: DashboardView,
        meta: {
          title: '首页',
          subtitle: '查看轮播、公告、代码资源与角色相关的数据摘要。'
        }
      },
      {
        path: 'notices',
        name: 'notices',
        component: NoticesView,
        meta: {
          title: '公告中心',
          subtitle: '浏览公共公告，并在管理员权限下维护公告发布流程。'
        }
      },
      {
        path: 'notices/:id',
        name: 'notice-detail',
        component: NoticeDetailView,
        meta: {
          title: '公告详情',
          subtitle: '查看公告正文、发布时间与发布人信息。'
        }
      },
      {
        path: 'forum',
        name: 'forum',
        component: ForumView,
        meta: {
          title: '教研社区',
          subtitle: '管理帖子、评论与管理员审核。'
        }
      },
      {
        path: 'forum/post/:id',
        name: 'forum-post-detail',
        component: ForumPostDetailView,
        meta: {
          title: '交流详情',
          subtitle: '查看交流内容、图片和评论。'
        }
      },
      {
        path: 'activities',
        name: 'activities',
        component: ActivitiesView,
        meta: {
          title: '教研活动',
          subtitle: '活动创建、报名、签到、讨论和审核全部集中在这里。'
        }
      },
      {
        path: 'activities/:id',
        name: 'activity-detail',
        component: ActivityDetailView,
        meta: {
          title: '教研活动详情',
          subtitle: '查看活动组织信息、海报、时间与报名情况。'
        }
      },
      {
        path: 'courseware',
        name: 'courseware',
        component: CoursewareView,
        meta: {
          title: '教学中心',
          subtitle: '统一完成课程创建、章节建设和教学资源共建。',
          requiresAuth: true,
          roles: ['STUDENT', 'TEACHER', 'ADMIN', 'SUPER_ADMIN']
        }
      },
      {
        path: 'courseware/:resourceType',
        name: 'courseware-category',
        component: CoursewareView,
        meta: {
          title: '教学中心',
          requiresAuth: true,
          roles: ['STUDENT', 'TEACHER', 'ADMIN', 'SUPER_ADMIN']
        }
      },
      {
        path: 'research-tools',
        name: 'research-tools',
        component: ResearchToolsView,
        meta: {
          title: '教研工具',
          subtitle: '集中进入研讨、资源协作与知识沉淀功能。'
        }
      },
      {
        path: 'courses',
        redirect: '/courseware'
      },
      {
        path: 'my-courses',
        redirect: '/courseware',
        meta: {
          title: '教学中心',
          requiresAuth: true,
          roles: ['STUDENT']
        }
      },
      {
        path: 'courses/:id',
        name: 'course-workspace',
        component: CourseWorkspaceView,
        meta: {
          title: '课程工作台',
          requiresAuth: true,
          roles: ['TEACHER', 'ADMIN', 'SUPER_ADMIN']
        }
      },
      {
        path: 'classrooms/:pathMatch(.*)*',
        redirect: '/courses'
      },
      {
        path: 'students',
        redirect: '/courses'
      },
      {
        path: 'assignments',
        name: 'assignments',
        component: AssignmentsView,
        meta: {
          title: '课程作业',
          subtitle: '进入当前课程内的作业创建、发布、提交、评测和人工评审。',
          requiresAuth: true,
          roles: ['STUDENT', 'TEACHER', 'ADMIN', 'SUPER_ADMIN']
        }
      },
      {
        path: 'courses/:courseId/assignments/:assignmentId',
        name: 'assignment-answer',
        component: AssignmentAnswerView,
        meta: {
          title: '作业作答',
          requiresAuth: true,
          roles: ['STUDENT']
        }
      },
      {
        path: 'courses/:courseId/exams/:examId',
        name: 'exam-detail',
        component: ExamDetailView,
        meta: {
          title: '考试详情',
          requiresAuth: true,
          roles: ['STUDENT']
        }
      },
      {
        path: 'research-tasks',
        name: 'research-tasks',
        component: ResearchTasksView,
        meta: { title: '教研任务', requiresAuth: true, roles: ['TEACHER', 'ADMIN', 'SUPER_ADMIN'] }
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: NotificationsView,
        meta: {
          title: '消息通知',
          subtitle: '查看消息、统计未读并执行已读操作。',
          requiresAuth: true
        }
      },
      {
        path: 'profile',
        name: 'profile',
        component: ProfileView,
        meta: {
          title: '我的资料',
          subtitle: '维护资料、头像与密码。',
          requiresAuth: true
        }
      },
      {
        path: 'admin',
        name: 'admin',
        component: AdminCenterView,
        meta: {
          title: '管理中心',
          subtitle: '统一处理用户、班级、审核和系统统计。',
          requiresAuth: true,
          roles: ['ADMIN', 'ADMINISTRATOR', 'SUPER_ADMIN']
        }
      },
      {
        path: 'academic-classes',
        name: 'academic-classes',
        component: AcademicClassesView,
        meta: { title: '行政班管理', requiresAuth: true, roles: ['ADMIN', 'ADMINISTRATOR', 'SUPER_ADMIN'] }
      },
      {
        path: 'schools',
        name: 'school-management',
        component: SchoolManagementView,
        meta: { title: '学校管理', requiresAuth: true, roles: ['ADMIN', 'ADMINISTRATOR', 'SUPER_ADMIN'] }
      },
      {
        path: 'admin/activity-review',
        name: 'admin-activity-review',
        component: AdminActivityReviewView,
        meta: {
          title: '教研活动审核',
          requiresAuth: true,
          roles: ['ADMIN', 'SUPER_ADMIN']
        }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (!authStore.bootstrapped) {
    await authStore.bootstrap()
  }

  if (to.meta.guestOnly && authStore.isLoggedIn) {
    return to.query.redirect || '/dashboard'
  }

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath
      }
    }
  }

  if (to.meta.roles?.length && !to.meta.roles.includes(authStore.role)) {
    return '/dashboard'
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta?.title || '虚拟教研室'
  document.title = `${title} | 虚拟教研室`
})

export default router
