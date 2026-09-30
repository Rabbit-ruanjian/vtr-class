-- Data Structures courseware seed data for the teaching-resource co-construction workspace.
-- This script is idempotent: it can be executed repeatedly without creating duplicate records.

INSERT INTO courseware (
    title, description, file_url, file_name, file_type, resource_type, course_id,
    semester, chapter, grade_level, version, file_size, teacher_id,
    visibility, target_audience, classroom_id, download_count, view_count, status,
    created_at, updated_at
)
SELECT seed.title, seed.description, seed.file_url, seed.file_name, seed.file_type,
       seed.resource_type, 2, '2026-2027-1', seed.chapter, '本科二年级', '2026 秋季修订版',
       seed.file_size, 8, 'PUBLIC', 'ALL', NULL, seed.download_count, seed.view_count,
       'ACTIVE', NOW(), NOW()
FROM (
    SELECT '数据结构知识体系与算法关联图' AS title,
           '以线性表、栈与队列、串、树、图、查找和排序为主线，标注抽象数据类型、典型算法、时间复杂度与实验项目之间的关联。' AS description,
           '/uploads/courseware/2026/07/37773eb4-6145-4e04-b4bc-a6257e23fc61.jpg' AS file_url,
           '数据结构知识体系图.jpg' AS file_name, 'jpg' AS file_type, 'knowledge-map' AS resource_type,
           '课程导学' AS chapter, 447218 AS file_size, 94 AS download_count, 263 AS view_count
    UNION ALL SELECT '排序算法比较与复杂度分析习题集', '包含插入、交换、选择、归并、快速、堆排序的手工推演、代码阅读、稳定性判断和复杂度分析题，附参考答案。', '/uploads/courseware/2026/05/cda7f33e-f6f3-4bbc-8123-6c82f75dfd4f.pdf', '排序算法比较与复杂度分析习题集.pdf', 'pdf', 'assignments', '第8章 排序', 842156, 136, 328
    UNION ALL SELECT '链表操作与内存管理微课', '以单链表插入、删除、反转和循环链表约瑟夫问题为例，讲解指针修改、边界处理与调试方法的 20 分钟微课。', '/uploads/courseware/2026/07/088a3db8-d129-4931-ad99-540d65c5351f.mp4', '链表操作与内存管理微课.mp4', 'mp4', 'teaching-video', '第2章 线性表', 17284620, 163, 395
    UNION ALL SELECT '数据结构课程教学大纲（OBE 版）', '明确课程目标、毕业要求支撑关系、16 周教学安排、上机实验、过程性考核及课程目标达成度评价方法。', '/uploads/courseware/2026/05/a006016d-f205-4b63-b31e-1a914e6fd149.pdf', '数据结构课程教学大纲（OBE版）.pdf', 'pdf', 'teaching-outline', '课程导学', 734918, 42, 128
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM courseware existing
    WHERE existing.course_id = 2 AND existing.title = seed.title AND existing.status = 'ACTIVE'
);
