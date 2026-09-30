import request, { postForm } from '@/utils/request'

export const authApi = {
  login: (payload) => request.post('/users/login', payload),
  sendCode: (payload) => request.post('/users/send-code', payload),
  resetPasswordByCode: (payload) => request.post('/users/forgot-password/reset', payload),
  register: (payload) => request.post('/users/register', payload),
  me: () => request.get('/users/me'),
  updateProfile: (payload) => request.put('/users/me', payload),
  bindIdentity: (payload) => request.post('/users/me/identity-binding', payload),
  reviewIdentityBinding: (id, approved, reason) =>
    request.put(`/users/${id}/identity-binding/review`, null, { params: { approved, reason } }),
  changePassword: (payload) => request.put('/users/me/password', payload),
  uploadAvatar(file) {
    const formData = new FormData()
    formData.append('file', file)
    return postForm('/users/me/avatar', formData)
  }
}

export const schoolApi = {
  active: () => request.get('/schools/active'),
  list: () => request.get('/schools'),
  create: (payload) => request.post('/schools', payload),
  update: (id, payload) => request.put(`/schools/${id}`, payload),
  updateStatus: (id, value) => request.post(`/schools/${id}/status`, null, { params: { value } }),
  academicStructure: (id) => request.get(`/schools/${id}/academic-structure`),
  updateAcademicStructure: (id, payload) => request.put(`/schools/${id}/academic-structure`, payload),
  teacherRoster: (schoolId) => request.get(`/schools/${schoolId}/teacher-roster`),
  addTeacherRoster: (schoolId, payload) => request.post(`/schools/${schoolId}/teacher-roster`, payload),
  updateTeacherRoster: (schoolId, id, payload) => request.put(`/schools/${schoolId}/teacher-roster/${id}`, payload),
  updateTeacherRosterStatus: (schoolId, id, value) => request.post(`/schools/${schoolId}/teacher-roster/${id}/status`, null, { params: { value } })
}

export const userApi = {
  statistics: () => request.get('/users/statistics'),
  list: (params) => request.get('/users', { params }),
  detail: (id) => request.get(`/users/${id}`),
  update: (id, payload) => request.put(`/users/${id}`, payload),
  updateStatus: (id, status, reason) =>
    request.put(`/users/${id}/status`, null, { params: { status, reason } }),
  updateRole: (id, role) =>
    request.put(`/users/${id}/role`, null, { params: { role } }),
  resetPassword: (id) => request.post(`/users/${id}/reset-password`),
  remove: (id) => request.delete(`/users/${id}`),
  batchReview: (payload) => request.post('/users/batch-review', payload)
}

export const noticeApi = {
  list: (params) => request.get('/notices', { params }),
  detail: (id) => request.get(`/notices/${id}`),
  active: () => request.get('/notices/active'),
  top: () => request.get('/notices/top'),
  create: (payload) => request.post('/notices', payload),
  update: (id, payload) => request.put(`/notices/${id}`, payload),
  remove: (id) => request.delete(`/notices/${id}`),
  publish: (id) => request.post(`/notices/${id}/publish`),
  withdraw: (id) => request.post(`/notices/${id}/withdraw`),
  pin: (id, isTop) => request.post(`/notices/${id}/pin`, null, { params: { isTop } }),
  addAttachment: (id, payload) => request.post(`/notices/${id}/attachments`, payload),
  removeAttachment: (noticeId, attachmentId) => request.delete(`/notices/${noticeId}/attachments/${attachmentId}`)
}

export const notificationApi = {
  list: (params, config = {}) => request.get('/notifications', { ...config, params }),
  unreadCount: () => request.get('/notifications/unread-count'),
  markRead: (id) => request.put(`/notifications/${id}/read`),
  markAllRead: () => request.put('/notifications/read-all'),
  remove: (id) => request.delete(`/notifications/${id}`),
  removeBatch: (ids) => request.delete('/notifications/batch', { data: ids })
}

export const carouselApi = {
  list: () => request.get('/carousel/list'),
  all: () => request.get('/carousel/all'),
  create: (payload) => request.post('/carousel', payload),
  update: (id, payload) => request.put(`/carousel/${id}`, payload),
  remove: (id) => request.delete(`/carousel/${id}`),
  activate: (id) => request.post(`/carousel/${id}/activate`),
  deactivate: (id) => request.post(`/carousel/${id}/deactivate`),
  click: (id) => request.post(`/carousel/${id}/click`),
  saveOrder: (ids) => request.put('/carousel/order', ids)
}

export const forumApi = {
  posts: (params) => request.get('/forum/posts', { params }),
  postDetail: (id) => request.get(`/forum/post/${id}`),
  createPost: (payload) => request.post('/forum/post', payload),
  createComment: (payload) => request.post('/forum/comment', payload),
  removeComment: (id) => request.delete(`/forum/comment/${id}`),
  toggleLike: (id) => request.post(`/forum/post/${id}/like`),
  removePost: (id) => request.delete(`/forum/post/${id}`),
  allPosts: (params) => request.get('/forum/admin/posts/all', { params }),
  pendingPosts: (params) => request.get('/forum/admin/posts/pending', { params }),
  approvedPosts: (params) => request.get('/forum/admin/posts/approved', { params }),
  rejectedPosts: (params) => request.get('/forum/admin/posts/rejected', { params }),
  auditPost: (id, payload) => request.post(`/forum/admin/post/${id}/audit`, payload),
  pendingComments: (params) => request.get('/forum/admin/comments/pending', { params }),
  approvedComments: (params) => request.get('/forum/admin/comments/approved', { params }),
  rejectedComments: (params) => request.get('/forum/admin/comments/rejected', { params }),
  allComments: (params) => request.get('/forum/admin/comments/all', { params }),
  auditComment: (id, payload) => request.post(`/forum/admin/comment/${id}/audit`, payload),
  cancelPin: (id) => request.post(`/forum/admin/post/${id}/cancel-pin`)
}

export const moderationApi = {
  report: (payload) => request.post('/moderation/reports', payload),
  appeal: (payload) => request.post('/moderation/appeals', payload)
}

export const activityApi = {
  list: (params) => request.get('/teaching-activities', { params }),
  detail: (id) => request.get(`/teaching-activities/${id}`),
  create: (payload) => request.post('/teaching-activities', payload),
  update: (id, payload) => request.put(`/teaching-activities/${id}`, payload),
  remove: (id) => request.delete(`/teaching-activities/${id}`),
  review: (id, payload) => request.put(`/teaching-activities/${id}/review`, payload),
  cancel: (id, reason) => request.post(`/teaching-activities/${id}/cancel`, null, { params: { reason } }),
  updatePinned: (id, pinned) => request.put(`/teaching-activities/${id}/pinned`, null, { params: { pinned } }),
  join: (id) => request.post(`/teaching-activities/${id}/join`),
  cancelJoin: (id) => request.delete(`/teaching-activities/${id}/join`),
  checkin: (id) => request.post(`/teaching-activities/${id}/checkin`),
  checkinStats: (id) => request.get(`/teaching-activities/${id}/checkin-stats`),
  feedback: (id, feedback, rating) =>
    request.post(`/teaching-activities/${id}/feedback`, null, {
      params: { feedback, rating }
    }),
  createDiscussion: (payload) => request.post('/teaching-activities/discussions', payload),
  removeDiscussion: (id) => request.delete(`/teaching-activities/discussions/${id}`),
  likeDiscussion: (id) => request.post(`/teaching-activities/discussions/${id}/like`),
  discussions: (id, params) => request.get(`/teaching-activities/${id}/discussions`, { params })
}

export const coursewareApi = {
  list: (params) => request.get('/courseware/list', { params }),
  pendingReviews: (params) => request.get('/courseware/pending-reviews', { params }),
  videos: (params) => request.get('/courseware/videos', { params }),
  videoOverview: (courseId) => request.get('/courseware/videos/overview', { params: { courseId } }),
  detail: (id) => request.get(`/courseware/${id}`),
  create: (payload) => request.post('/courseware', payload),
  createStructuredOutline: (payload) => request.post('/courseware/structured-outline', payload),
  update: (id, payload) => request.put(`/courseware/${id}`, payload),
  remove: (id) => request.delete(`/courseware/${id}`),
  createVideoVersion: (id, payload) => request.post(`/courseware/${id}/video-version`, payload),
  uploadVideoTranscript(id, file) {
    const formData = new FormData()
    formData.append('file', file)
    return postForm(`/courseware/${id}/ai-transcript`, formData, aiRequestConfig())
  },
  reviewVideo: (id, payload) => request.post(`/courseware/${id}/video-review`, payload),
  archiveVideo: (id) => request.post(`/courseware/${id}/video-archive`),
  reviewCourseware: (id, payload) => request.post(`/courseware/${id}/courseware-review`, payload),
  archiveCourseware: (id) => request.post(`/courseware/${id}/courseware-archive`),
  reviewOutline: (id, payload) => request.post(`/courseware/${id}/outline-review`, payload),
  archiveOutline: (id) => request.post(`/courseware/${id}/outline-archive`),
  reviewKnowledgeMap: (id, payload) => request.post(`/courseware/${id}/knowledge-map-review`, payload),
  withdrawVideo: (id) => request.post(`/courseware/${id}/video-withdraw`),
  saveVideoProgress: (id, payload) => request.put(`/courseware/${id}/video-progress`, payload),
  myVideoProgress: (id) => request.get(`/courseware/${id}/video-progress/me`),
  videoStatistics: (id) => request.get(`/courseware/${id}/video-statistics`),
  download: (id) => request.post(`/courseware/${id}/download`, null, { responseType: 'blob' }),
  preview: (id) => request.get(`/courseware/${id}/preview`, { responseType: 'blob' }),
  openLocal: (id) => request.post(`/courseware/${id}/open-local`)
}

export const learningResourceApi = {
  questions: (params, config = {}) => request.get('/learning-resources/questions', { params, ...config }),
  parseQuestionDocument(file, params) {
    const formData = new FormData()
    formData.append('file', file)
    return postForm('/learning-resources/questions/parse-document', formData, { params })
  },
  createQuestion: (payload) => request.post('/learning-resources/questions', payload),
  updateQuestion: (id, payload) => request.put(`/learning-resources/questions/${id}`, payload),
  removeQuestion: (id) => request.delete(`/learning-resources/questions/${id}`),
  favoriteQuestion: (id) => request.post(`/learning-resources/questions/${id}/favorite`),
  unfavoriteQuestion: (id) => request.delete(`/learning-resources/questions/${id}/favorite`),
  favorites: (params) => request.get('/learning-resources/favorites', { params }),
  submitAttempt: (id, payload) => request.post(`/learning-resources/questions/${id}/attempts`, payload),
  pendingAttempts: (params) => request.get('/learning-resources/attempts/pending', { params }),
  reviewAttempt: (id, payload) => request.post(`/learning-resources/attempts/${id}/review`, payload),
  review: (kind, id, payload) => request.post(`/learning-resources/${kind}/${id}/review`, payload),
  archive: (kind, id) => request.post(`/learning-resources/${kind}/${id}/archive`),
  myAttempts: (params) => request.get('/learning-resources/attempts/me', { params }),
  myMistakes: (params) => request.get('/learning-resources/mistakes/me', { params }),
  statistics: (params) => request.get('/learning-resources/statistics', { params })
}

export const chapterQuizApi = {
  list: (params) => request.get('/chapter-quizzes', { params }),
  detail: (id) => request.get(`/chapter-quizzes/${id}`),
  create: (payload) => request.post('/chapter-quizzes', payload),
  update: (id, payload) => request.put(`/chapter-quizzes/${id}`, payload),
  publish: (id) => request.post(`/chapter-quizzes/${id}/publish`),
  archive: (id) => request.post(`/chapter-quizzes/${id}/archive`),
  remove: (id) => request.delete(`/chapter-quizzes/${id}`),
  completedSections: (params) => request.get('/chapter-quizzes/completed-sections', { params }),
  start: (id) => request.post(`/chapter-quizzes/${id}/start`),
  myAttempts: (id) => request.get(`/chapter-quizzes/${id}/attempts/me`),
  attemptDetail: (id, attemptId) => request.get(`/chapter-quizzes/${id}/attempts/me/${attemptId}`),
  saveAnswers: (attemptId, payload) => request.post(`/chapter-quizzes/attempts/${attemptId}/answers`, payload),
  submit: (attemptId, payload) => request.post(`/chapter-quizzes/attempts/${attemptId}/submit`, payload),
  pending: (params) => request.get('/chapter-quizzes/review/pending', { params }),
  reviewAnswer: (answerId, payload) => request.post(`/chapter-quizzes/answers/${answerId}/review`, payload)
}

export const examApi = {
  list: (params) => request.get('/exams', { params }),
  detail: (id) => request.get(`/exams/${id}`),
  create: (payload) => request.post('/exams', payload),
  update: (id, payload) => request.put(`/exams/${id}`, payload),
  publish: (id) => request.post(`/exams/${id}/publish`),
  archive: (id) => request.post(`/exams/${id}/archive`),
  remove: (id) => request.delete(`/exams/${id}`)
}

export const courseApi = {
  list: () => request.get('/courses'),
  joined: () => request.get('/courses/joined'),
  manageList: (params) => request.get('/courses/manage', { params }),
  chapters: (courseId) => request.get(`/courses/${courseId}/chapters`),
  sections: (courseId) => request.get(`/courses/${courseId}/sections`),
  createChapter: (courseId, payload) => request.post(`/courses/${courseId}/chapters`, payload),
  syncLegacyChapters: (courseId) => request.post(`/courses/${courseId}/chapters/sync-legacy`),
  updateChapter: (courseId, chapterId, payload) => request.put(`/courses/${courseId}/chapters/${chapterId}`, payload),
  archiveChapter: (courseId, chapterId) => request.post(`/courses/${courseId}/chapters/${chapterId}/archive`),
  createSection: (courseId, chapterId, payload) => request.post(`/courses/${courseId}/chapters/${chapterId}/sections`, payload),
  updateSection: (courseId, chapterId, sectionId, payload) => request.put(`/courses/${courseId}/chapters/${chapterId}/sections/${sectionId}`, payload),
  archiveSection: (courseId, chapterId, sectionId) => request.post(`/courses/${courseId}/chapters/${chapterId}/sections/${sectionId}/archive`),
  detail: (id) => request.get(`/courses/${id}`),
  overview: (id) => request.get(`/courses/${id}/overview`),
  members: (id) => request.get(`/courses/${id}/members`),
  addMember: (id, payload) => request.post(`/courses/${id}/members`, payload),
  updateMember: (id, userId, payload) => request.put(`/courses/${id}/members/${userId}`, payload),
  removeMember: (id, userId) => request.delete(`/courses/${id}/members/${userId}`),
  create: (payload) => request.post('/courses', payload),
  update: (id, payload) => request.put(`/courses/${id}`, payload),
  archive: (id) => request.post(`/courses/${id}/archive`),
  restore: (id) => request.post(`/courses/${id}/restore`),
  classrooms: (id) => request.get(`/courses/${id}/classrooms`)
}

export const classroomApi = {
  create: (payload) => request.post('/classrooms', payload),
  update: (id, payload) => request.put(`/classrooms/${id}`, payload),
  remove: (id) => request.delete(`/classrooms/${id}`),
  archive: (id) => request.post(`/classrooms/${id}/archive`),
  restore: (id) => request.post(`/classrooms/${id}/restore`),
  join: (inviteCode) => request.post('/classrooms/join', { inviteCode }),
  joined: () => request.get('/classrooms/joined'),
  leave: (id) => request.delete(`/classrooms/${id}/leave`),
  detail: (id) => request.get(`/classrooms/${id}`),
  mine: (params) => request.get('/classrooms/my-classrooms', { params }),
  addStudents: (payload) => request.post('/classrooms/students', payload),
  removeStudent: (classroomId, studentId) =>
    request.delete(`/classrooms/${classroomId}/students/${studentId}`),
  students: (classroomId, params) =>
    request.get(`/classrooms/${classroomId}/students`, { params }),
  studentClassrooms: (studentId) => request.get(`/classrooms/student/${studentId}`),
  adminList: (params) => request.get('/classrooms/admin/list', { params }),
  assignTeacher: (id, payload) => request.put(`/classrooms/admin/${id}/teacher`, payload),
  adminRemove: (id) => request.delete(`/classrooms/admin/${id}`)
}

export const studentApi = {
  mine: (params) => request.get('/students/my-students', { params }),
  myTeachers: () => request.get('/students/my-teachers'),
  available: (params) => request.get('/students/available', { params }),
  search: (studentNumber) => request.get(`/students/search/${studentNumber}`),
  addToTeacher: (payload) => request.post('/students/add-to-teacher', payload),
  removeFromTeacher: (studentId) => request.delete(`/students/${studentId}/remove-from-teacher`),
  batchImport: (payload) => request.post('/students/batch-import', payload),
  myStudentIds: () => request.get('/students/my-student-ids'),
  classroomStudentIds: (classroomId) =>
    request.get(`/students/classroom/${classroomId}/student-ids`),
  changeClassroom: (payload) => request.post('/students/change-classroom', payload),
  studentClassroom: (studentId) => request.get(`/students/${studentId}/classroom`)
}

export const academicClassApi = {
  list: (params) => request.get('/academic-classes', { params }),
  summary: (params) => request.get('/academic-classes/summary', { params }),
  students: (id) => request.get(`/academic-classes/${id}/students`),
  create: (payload) => request.post('/academic-classes', payload),
  update: (id, payload) => request.put(`/academic-classes/${id}`, payload),
  archive: (id) => request.post(`/academic-classes/${id}/archive`),
  restore: (id) => request.post(`/academic-classes/${id}/restore`),
  removeStudent: (classId, studentId) => request.delete(`/academic-classes/${classId}/students/${studentId}`),
  removeRoster: (classId, rosterId) => request.delete(`/academic-classes/${classId}/roster/${rosterId}`),
  assignStudent: (studentId, academicClassId) => request.put(`/academic-classes/students/${studentId}`, { academicClassId }),
  batchAssign: (id, students) => request.post(`/academic-classes/${id}/students/batch`, { students }),
  clearStudents: (id) => request.delete(`/academic-classes/${id}/students`)
}

export const assignmentApi = {
  list: (params) => request.get('/assignments', { params }),
  detail: (id, config = {}) => request.get(`/assignments/${id}`, config),
  create: (payload) => request.post('/assignments', payload),
  update: (id, payload) => request.put(`/assignments/${id}`, payload),
  remove: (id) => request.delete(`/assignments/${id}`),
  publish: (id) => request.post(`/assignments/${id}/publish`),
  publishToStudents: (id, studentIds) =>
    request.post(`/assignments/${id}/publish-to-students`, studentIds),
  close: (id) => request.post(`/assignments/${id}/close`),
  reopen: (id) => request.post(`/assignments/${id}/reopen`),
  studentVisible: (params) => request.get('/assignments/student/visible', { params }),
  teacherManage: (params) => request.get('/assignments/teacher/manage', { params }),
  myAssignments: () => request.get('/assignments/my'),
  teachingAssignments: () => request.get('/assignments/teaching'),
  canView: (id) => request.get(`/assignments/${id}/can-view`),
  publicTestcases: (id) => request.get(`/assignments/${id}/testcases`),
  allTestcases: (id) => request.get(`/assignments/${id}/testcases/all`)
}

export const submissionApi = {
  submit: (assignmentId, payload) => request.post(`/assignments/${assignmentId}/submit`, payload),
  detail: (id, config = {}) => request.get(`/submissions/${id}`, config),
  list: (params) => request.get('/submissions', { params }),
  mySubmissions: (assignmentId) => request.get(`/assignments/${assignmentId}/submissions/my`),
  finalSubmission: (assignmentId) =>
    request.get(`/assignments/${assignmentId}/submissions/final`),
  allByAssignment: (assignmentId, params) =>
    request.get(`/assignments/${assignmentId}/submissions/all`, { params }),
  review: (id, payload) => request.post(`/submissions/${id}/review`, payload),
  progress: (id) => request.get(`/submissions/${id}/progress`)
}

export const adminApi = {
  pendingContent: (params) => request.get('/admin/content/pending', { params }),
  reviewContent: (id, payload) => request.post(`/admin/content/${id}/review`, payload),
  dashboard: () => request.get('/admin/dashboard'),
  publicOverview: () => request.get('/public/overview', { silent: true })
}

export const adminScopeApi = {
  schools: () => request.get('/admin/scope/schools'),
  admins: () => request.get('/admin/scope/admins'),
  update: (adminId, payload) => request.put(`/admin/scope/admins/${adminId}`, payload)
}

export const uploadApi = {
  registerAvatar(file) {
    const formData = new FormData()
    formData.append('file', file)
    return postForm('/upload/register-avatar', formData)
  },
  carousel(file) {
    const formData = new FormData()
    formData.append('file', file)
    return postForm('/upload/carousel', formData)
  },
  image(file) {
    const formData = new FormData()
    formData.append('file', file)
    return postForm('/upload/image', formData)
  },
  courseCover(file) {
    const formData = new FormData()
    formData.append('file', file)
    return postForm('/upload/course-cover', formData)
  },
  noticeAttachment(file) {
    const formData = new FormData()
    formData.append('file', file)
    return postForm('/upload/notice-attachment', formData)
  },
  courseware(file) {
    const formData = new FormData()
    formData.append('file', file)
    return postForm('/upload/courseware', formData)
  }
}

export const codeApi = {
  run: (payload) => request.post('/code/run', payload)
}

// AI 请求可能包含课程 RAG、Agent 编排或多模态分析，允许更长的处理时间。
// 只延长 AI 接口，不影响普通接口的响应超时。
const AI_REQUEST_TIMEOUT = 180000
const aiRequestConfig = (config = {}) => ({ timeout: AI_REQUEST_TIMEOUT, silent: true, ...config })

export const aiApi = {
  chat: (payload, config = {}) => request.post('/ai/chat', payload, aiRequestConfig(config)),
  webSearch: (query, config = {}) => request.post('/ai/web-search', { query }, aiRequestConfig(config)),
  history: () => request.get('/ai/history', { ...aiRequestConfig() }),
  clearHistory: () => request.delete('/ai/history', { ...aiRequestConfig(), silent: true }),
  deleteConversation: (conversationId) => request.delete(`/ai/history/${encodeURIComponent(conversationId)}`, { ...aiRequestConfig(), silent: true }),
  feedback: (payload) => request.post('/ai/feedback', payload, { silent: true }),
  media(file, payload = {}, config = {}) {
    const formData = new FormData()
    formData.append('file', file)
    for (const key of ['message', 'contextTitle', 'contextMeta', 'contextExcerpt', 'questionType', 'courseId', 'chapter', 'agentTask', 'conversationId']) {
      if (payload[key] !== null && payload[key] !== undefined && payload[key] !== '') {
        formData.append(key, String(payload[key]))
      }
    }
    if (payload.questionId !== null && payload.questionId !== undefined && payload.questionId !== '') {
      formData.append('questionId', String(payload.questionId))
    }
    if (payload.documentId !== null && payload.documentId !== undefined && payload.documentId !== '') {
      formData.append('documentId', String(payload.documentId))
    }
    formData.append('history', JSON.stringify(payload.history || []))
    return postForm('/ai/media', formData, aiRequestConfig(config))
  },
  // 课程备课/出题/学情分析必须进入真正的课程 Agent，普通 /ai/agent 只是一条兼容性问答入口。
  agent: (payload) => request.post('/ai/teaching-agent', payload, aiRequestConfig()),
  courseResources: (courseId) => request.get('/ai/course-resources', { params: { courseId } }),
  uploadCourseResource(file, payload = {}) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('courseId', String(payload.courseId))
    for (const key of ['title', 'chapter', 'sourceType', 'sourceUrl', 'license', 'sourceAuthor', 'attribution']) {
      if (payload[key]) formData.append(key, payload[key])
    }
    return postForm('/ai/course-resources', formData, aiRequestConfig())
  },
  reviewCourseResource: (id, action, remark) => request.post(`/ai/course-resources/${id}/review`, null, { params: { action, remark } }),
  reindexCourseResource: (id) => request.post(`/ai/course-resources/${id}/reindex`),
  agentArtifacts: (courseId) => request.get('/ai/teaching-agent/artifacts', { params: { courseId } }),
  reviewAgentArtifact: (id, action, remark) => request.post(`/ai/teaching-agent/artifacts/${id}/review`, null, { params: { action, remark } }),
  evaluationCases: (courseId) => request.get(`/ai/evaluations/${courseId}/cases`),
  createEvaluationCase: (courseId, payload) => request.post(`/ai/evaluations/${courseId}/cases`, payload),
  runEvaluations: (courseId) => request.post(`/ai/evaluations/${courseId}/run`),
  document(file, payload = {}, config = {}) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('message', payload.message || '')
    formData.append('contextTitle', payload.contextTitle || '')
    formData.append('contextMeta', payload.contextMeta || '')
    formData.append('contextExcerpt', payload.contextExcerpt || '')
    formData.append('questionType', payload.questionType || '')
    if (payload.courseId !== null && payload.courseId !== undefined && payload.courseId !== '') {
      formData.append('courseId', String(payload.courseId))
    }
    formData.append('chapter', payload.chapter || '')
    formData.append('agentTask', payload.agentTask || '')
    if (payload.conversationId !== null && payload.conversationId !== undefined && payload.conversationId !== '') {
      formData.append('conversationId', String(payload.conversationId))
    }
    if (payload.questionId !== null && payload.questionId !== undefined && payload.questionId !== '') {
      formData.append('questionId', String(payload.questionId))
    }
    if (payload.documentId !== null && payload.documentId !== undefined && payload.documentId !== '') {
      formData.append('documentId', String(payload.documentId))
    }
    formData.append('history', JSON.stringify(payload.history || []))
    return postForm('/ai/document', formData, aiRequestConfig(config))
  }
}

export const researchTaskApi = {
  list: (courseId) => request.get('/research-tasks', { params: { courseId } }),
  create: (payload) => request.post('/research-tasks', payload),
  claim: (id) => request.post(`/research-tasks/${id}/claim`),
  submit: (id, payload) => request.post(`/research-tasks/${id}/submissions`, payload),
  submissions: (id) => request.get(`/research-tasks/${id}/submissions`),
  review: (taskId, contributionId, payload) =>
    request.post(`/research-tasks/${taskId}/submissions/${contributionId}/review`, payload),
  archive: (id) => request.post(`/research-tasks/${id}/archive`)
}
