-- 为已有数据库补充 AI 课程资源审核字段。
-- 新数据库可直接执行 ai-rag-agent-schema.sql；开发环境 ddl-auto=update 也会自动补齐字段。
SET NAMES utf8mb4;

ALTER TABLE ai_document
    ADD COLUMN IF NOT EXISTS review_remark VARCHAR(1000) NULL,
    ADD COLUMN IF NOT EXISTS reviewed_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at DATETIME(6) NULL;

-- 历史 READY 记录视为已经发布，保持升级前的可用行为；新上传记录由代码写入 PENDING_REVIEW。
