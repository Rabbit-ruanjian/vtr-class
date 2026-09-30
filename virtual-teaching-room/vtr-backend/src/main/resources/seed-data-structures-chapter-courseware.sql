-- Chapter-specific resource data for the Data Structures course (course_id = 2).
-- Existing mock resources remain as Chapter 1 introductory materials; this script
-- adds the supported resource types for Chapters 2 through 8.

UPDATE courseware
SET chapter = '第1章 绪论',
    title = CONCAT('第1章 绪论 - ', CASE resource_type
        WHEN 'knowledge-map' THEN '数据结构知识体系与学习路径'
        WHEN 'assignments' THEN '抽象数据类型与复杂度分析练习'
        WHEN 'teaching-video' THEN '数据结构与算法课程导入微课'
        WHEN 'teaching-outline' THEN '课程教学大纲与考核说明'
        ELSE title
    END),
    description = '面向数据结构课程第一章绪论，帮助学生建立抽象数据类型、算法设计、复杂度分析、实验规范与课程学习路径的整体认识。',
    updated_at = NOW()
WHERE course_id = 2 AND status = 'ACTIVE';

INSERT INTO courseware (
    title, description, file_url, file_name, file_type, resource_type, course_id,
    semester, chapter, grade_level, version, file_size, teacher_id,
    visibility, target_audience, classroom_id, download_count, view_count, status,
    created_at, updated_at
)
SELECT '第1章 绪论 - 教学大纲',
       '本章课程定位、教学内容、学习要求与知识点结构。',
       '/uploads/courseware/2026/05/a006016d-f205-4b63-b31e-1a914e6fd149.pdf',
       '第1章绪论-教学大纲.pdf', 'pdf', 'teaching-outline', 2,
       '2026-2027-1', '第1章 绪论', '本科二年级', '2026 秋季修订版',
       734918, 8, 'PUBLIC', 'ALL', NULL, 0, 0, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM courseware existing
    WHERE existing.course_id = 2
      AND existing.chapter = '第1章 绪论'
      AND existing.resource_type = 'teaching-outline'
      AND existing.status <> 'DELETED'
);

INSERT INTO courseware (
    title, description, file_url, file_name, file_type, resource_type, course_id,
    semester, chapter, grade_level, version, file_size, teacher_id,
    visibility, target_audience, classroom_id, download_count, view_count, status,
    created_at, updated_at
)
WITH chapters AS (
    SELECT '第2章 线性表' AS chapter, '顺序表、单链表、双向链表和循环链表的存储结构、基本操作与应用' AS focus
    UNION ALL SELECT '第3章 栈和队列', '栈与队列的顺序和链式实现、递归调用、表达式求值及典型应用'
    UNION ALL SELECT '第4章 串', '串的顺序存储、链式存储、朴素模式匹配与 KMP 算法'
    UNION ALL SELECT '第5章 树和二叉树', '树的基本概念、二叉树遍历、线索化、哈夫曼树与并查集'
    UNION ALL SELECT '第6章 图', '图的存储结构、深度和广度优先遍历、最小生成树与最短路径'
    UNION ALL SELECT '第7章 查找', '顺序查找、折半查找、二叉排序树、平衡树与散列表'
    UNION ALL SELECT '第8章 排序', '插入、交换、选择、归并、快速和堆排序的实现与性能比较'
),
resource_types AS (
    SELECT 'knowledge-map' AS resource_type, '知识图谱' AS resource_label, '/uploads/courseware/2026/07/37773eb4-6145-4e04-b4bc-a6257e23fc61.jpg' AS file_url, 'jpg' AS file_type, 447218 AS file_size
    UNION ALL SELECT 'assignments', '题库', '/uploads/courseware/2026/05/cda7f33e-f6f3-4bbc-8123-6c82f75dfd4f.pdf', 'pdf', 842156
    UNION ALL SELECT 'teaching-video', '教学视频', '/uploads/courseware/2026/07/088a3db8-d129-4931-ad99-540d65c5351f.mp4', 'mp4', 18643752
    UNION ALL SELECT 'teaching-outline', '教学大纲', '/uploads/courseware/2026/05/a006016d-f205-4b63-b31e-1a914e6fd149.pdf', 'pdf', 734918
)
SELECT
    CONCAT(ch.chapter, ' - ', type.resource_label),
    CONCAT('围绕', ch.focus, '，提供', CASE type.resource_type
        WHEN 'knowledge-map' THEN '核心概念、逻辑关系与先后依赖的可视化梳理。'
        WHEN 'assignments' THEN '分层练习、典型算法推演、代码阅读题和参考评分要点。'
        WHEN 'teaching-video' THEN '结合实例的关键算法讲解与实现演示。'
        WHEN 'teaching-outline' THEN '本章目标、建议学时、知识要求和形成性评价安排。'
    END),
    type.file_url,
    CONCAT(ch.chapter, '-', type.resource_label, '.', type.file_type),
    type.file_type, type.resource_type, 2, '2026-2027-1', ch.chapter,
    '本科二年级', '2026 秋季修订版', type.file_size, 8,
    'PUBLIC', 'ALL', NULL,
    20 + LENGTH(ch.chapter) * 3, 80 + LENGTH(ch.focus), 'ACTIVE', NOW(), NOW()
FROM chapters ch CROSS JOIN resource_types type
WHERE NOT EXISTS (
    SELECT 1 FROM courseware existing
    WHERE existing.course_id = 2
      AND existing.chapter = ch.chapter
      AND existing.resource_type = type.resource_type
      AND existing.status = 'ACTIVE'
);
