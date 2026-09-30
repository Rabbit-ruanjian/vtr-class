-- AI 检索与回答质量观测字段。
-- 开发环境 ddl-auto=update 会自动补齐；生产环境请在发布前执行本文件。
SET NAMES utf8mb4;

ALTER TABLE ai_document_chunk
    ADD COLUMN IF NOT EXISTS heading VARCHAR(255) NULL;

ALTER TABLE ai_document_chunk
    ADD COLUMN IF NOT EXISTS embedding_json LONGTEXT NULL;

ALTER TABLE ai_conversation
    ADD COLUMN IF NOT EXISTS retrieval_mode VARCHAR(40) NULL;

CREATE TABLE IF NOT EXISTS ai_evaluation_case (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    question TEXT NOT NULL,
    chapter VARCHAR(100) NULL,
    required_keywords TEXT NULL,
    forbidden_keywords TEXT NULL,
    expected_route VARCHAR(40) NULL,
    expected_evidence BIT(1) NOT NULL DEFAULT b'1',
    enabled BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_ai_eval_course_enabled (course_id, enabled),
    KEY idx_ai_eval_creator (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
