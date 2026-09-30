export const appMenus = [
  {
    path: '/dashboard',
    label: '首页',
    public: true
  },
  {
    path: '/notices',
    label: '通知公告',
    public: true
  },
  {
    path: '/courseware',
    label: '教学中心',
    roles: ['STUDENT', 'TEACHER', 'ADMIN', 'SUPER_ADMIN']
  },
  {
    path: '/activities',
    label: '教研活动',
    public: true
  },
  {
    path: '/forum',
    label: '教研社区',
    public: true
  },
  {
    path: '/notifications',
    label: '消息通知',
    roles: ['STUDENT', 'TEACHER', 'ADMIN', 'SUPER_ADMIN']
  },
  {
    path: '/admin',
    label: '管理中心',
    roles: ['ADMIN', 'ADMINISTRATOR', 'SUPER_ADMIN']
  }
]
