-- Operating Systems courseware seed data for the teaching-resource co-construction workspace.
-- This script is idempotent: it can be executed repeatedly without creating duplicate records.

INSERT INTO courseware (
    title, description, file_url, file_name, file_type, resource_type, course_id,
    semester, chapter, grade_level, version, file_size, teacher_id,
    visibility, target_audience, classroom_id, download_count, view_count, status,
    created_at, updated_at
)
SELECT seed.title, seed.description, seed.file_url, seed.file_name, seed.file_type,
       seed.resource_type, 1, '2026-2027-1', seed.chapter, '本科二年级', '2026 秋季修订版',
       seed.file_size, 8, 'PUBLIC', 'ALL', NULL, seed.download_count, seed.view_count,
       'ACTIVE', NOW(), NOW()
FROM (
    SELECT '操作系统知识体系与章节关联图' AS title,
           '覆盖进程管理、内存管理、文件系统、I/O 与并发控制五个模块，标注先修知识、核心概念和实验关联，供课程组统一备课与学生复习使用。' AS description,
           '/uploads/courseware/2026/07/37773eb4-6145-4e04-b4bc-a6257e23fc61.jpg' AS file_url,
           '操作系统知识体系图.jpg' AS file_name, 'jpg' AS file_type, 'knowledge-map' AS resource_type,
           '课程导学' AS chapter, 428736 AS file_size, 86 AS download_count, 245 AS view_count
    UNION ALL SELECT '进程同步与死锁课后习题集', '包含信号量、管程、经典同步问题、银行家算法和资源分配图五类题目，附参考解题思路与评分要点。', '/uploads/courseware/2026/05/cda7f33e-f6f3-4bbc-8123-6c82f75dfd4f.pdf', '进程同步与死锁习题集.pdf', 'pdf', 'assignments', '第3章 进程同步与死锁', 842156, 112, 286
    UNION ALL SELECT '虚拟内存与页面置换算法微课', '以地址转换、缺页中断和 FIFO/LRU/OPT 页面置换比较为主线的 18 分钟微课，可作为课前预习或实验前复习材料。', '/uploads/courseware/2026/07/088a3db8-d129-4931-ad99-540d65c5351f.mp4', '虚拟内存与页面置换算法微课.mp4', 'mp4', 'teaching-video', '第4章 存储管理', 18643752, 148, 362
    UNION ALL SELECT '操作系统课程教学大纲（OBE 版）', '明确课程目标、毕业要求支撑关系、16 周教学安排、实验项目、考核方式和课程达成度评价依据。', '/uploads/courseware/2026/05/a006016d-f205-4b63-b31e-1a914e6fd149.pdf', '操作系统课程教学大纲（OBE版）.pdf', 'pdf', 'teaching-outline', '课程导学', 734918, 35, 116
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM courseware existing
    WHERE existing.course_id = 1 AND existing.title = seed.title AND existing.status = 'ACTIVE'
);
