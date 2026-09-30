export const roleOptions = [
  { label: '学生', value: 'STUDENT' },
  { label: '教师', value: 'TEACHER' },
  { label: '管理员', value: 'ADMIN' }
]

export function roleLabel(value, fallback = '--') {
  if (['ADMIN', 'ADMINISTRATOR', 'SUPER_ADMIN'].includes(value)) return '管理员'
  return roleOptions.find((item) => item.value === value)?.label || value || fallback
}

export const userStatusOptions = [
  { label: '待审核', value: 'PENDING' },
  { label: '正常', value: 'ACTIVE' },
  { label: '停用', value: 'SUSPENDED' },
  { label: '封禁', value: 'BANNED' },
  { label: '已删除', value: 'DELETED' }
]

export const noticeTypeOptions = [
  { label: '紧急', value: 'URGENT' },
  { label: '重要', value: 'IMPORTANT' },
  { label: '普通', value: 'NORMAL' },
  { label: '系统', value: 'SYSTEM' }
]

export const noticeStatusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已撤回', value: 'WITHDRAWN' }
]

export const noticeTargetOptions = [
  { label: '所有人', value: 'ALL' },
  { label: '仅学生', value: 'STUDENTS' },
  { label: '仅教师', value: 'TEACHERS' },
  { label: '仅管理员', value: 'ADMINS' }
]

export const carouselStatusOptions = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'INACTIVE' }
]

export const languageOptions = [
  { label: 'Java', value: 'java' },
  { label: 'Python', value: 'python' },
  { label: 'C++', value: 'cpp' },
  { label: 'JavaScript', value: 'javascript' },
  { label: 'TypeScript', value: 'typescript' },
  { label: 'Go', value: 'go' },
  { label: 'Rust', value: 'rust' },
  { label: '文本作业', value: 'TEXT' }
]

export const coursewareVisibilityOptions = [
  { label: '本课程可见（直接发布）', value: 'COURSE' },
  { label: '指定班级可见（直接发布）', value: 'CLASS' },
  { label: '仅自己可见', value: 'PRIVATE' },
  { label: '全校公开（需要审核）', value: 'PUBLIC' }
]

export const targetAudienceOptions = [
  { label: '全部', value: 'ALL' },
  { label: '教师', value: 'TEACHER' },
  { label: '学生', value: 'STUDENT' }
]

export const classroomStatusOptions = [
  { label: '活跃', value: 'ACTIVE' },
  { label: '已归档', value: 'ARCHIVED' }
]

export const assignmentTypeOptions = [
  { label: '编程作业', value: 'PROGRAMMING' },
  { label: '文本作业', value: 'TEXT' }
]

export const assignmentStatusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已关闭', value: 'CLOSED' }
]

export const publishTypeOptions = [
  { label: '发布给全部学生', value: 'ALL' },
  { label: '仅指定学生', value: 'SELECTED' }
]

export const activityTypeOptions = [
  { label: '讲座', value: 'LECTURE' },
  { label: '研讨会', value: 'SEMINAR' },
  { label: '集体备课', value: 'LESSON_PREP' },
  { label: '公开课', value: 'OPEN_LESSON' },
  { label: '听评课', value: 'LESSON_OBSERVATION' },
  { label: '专题培训', value: 'TRAINING' },
  { label: '经验分享', value: 'EXPERIENCE_SHARE' },
  { label: '其他', value: 'OTHER' }
]

export const activityStatusOptions = [
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '进行中', value: 'ONGOING' },
  { label: '已结束待归档', value: 'ENDED_PENDING_ARCHIVE' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' }
]

export const auditStatusOptions = [
  { label: '待审核', value: 'PENDING' },
  { label: '通过', value: 'PASS' },
  { label: '拒绝', value: 'REJECT' }
]

export const forumAuditOptions = [
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' }
]

export const submissionStatusOptions = [
  { label: '已提交', value: 'SUBMITTED' },
  { label: '排队中', value: 'QUEUED' },
  { label: '编译中', value: 'COMPILING' },
  { label: '测试中', value: 'TESTING' },
  { label: '测试通过', value: 'PASSED' },
  { label: '测试失败', value: 'FAILED' },
  { label: '编译错误', value: 'COMPILE_ERROR' },
  { label: '运行超时', value: 'TIMEOUT' },
  { label: '内存超限', value: 'MEMORY_LIMIT' },
  { label: '运行错误', value: 'RUNTIME_ERROR' },
  { label: '已评审', value: 'REVIEWED' },
  { label: '疑似抄袭', value: 'PLAGIARISM' },
  { label: '待评测', value: 'PENDING' },
  { label: '评测中', value: 'EVALUATING' },
  { label: '已评分', value: 'GRADED' }
]
