-- 将普通课程课件接入 AI 文本/向量索引。
-- 生产环境 ddl-auto=validate 时执行本文件；开发环境 ddl-auto=update 会自动补齐字段。
SET NAMES utf8mb4;

ALTER TABLE ai_document
    ADD COLUMN IF NOT EXISTS courseware_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS index_mode VARCHAR(30) NULL;

ALTER TABLE courseware
    ADD COLUMN IF NOT EXISTS ai_index_status VARCHAR(30) NOT NULL DEFAULT 'NOT_INDEXED',
    ADD COLUMN IF NOT EXISTS ai_index_message VARCHAR(1000) NULL,
    ADD COLUMN IF NOT EXISTS ai_document_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS ai_indexed_at DATETIME(6) NULL;

CREATE INDEX IF NOT EXISTS idx_ai_document_courseware
    ON ai_document (courseware_id, is_deleted);
