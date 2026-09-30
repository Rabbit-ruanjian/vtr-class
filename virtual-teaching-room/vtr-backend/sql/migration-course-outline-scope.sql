-- 将教学大纲统一为课程级资源。
-- 开发环境 ddl-auto=update 会自动补齐结构；生产环境请在备份后执行。

UPDATE courseware
SET chapter = NULL, section_id = NULL
WHERE resource_type = 'teaching-outline';

-- 历史上同一课程存在多份教学大纲时，请保留最新一份，其余按实际业务归档。
-- 下面的查询用于核对重复课程：
-- SELECT course_id, COUNT(*) AS outline_count
-- FROM courseware
-- WHERE resource_type = 'teaching-outline' AND status IN ('ACTIVE', 'PENDING')
-- GROUP BY course_id HAVING COUNT(*) > 1;
