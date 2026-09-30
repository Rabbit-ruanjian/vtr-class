-- AI 助手回答追踪与用户反馈闭环。
-- 开发环境 ddl-auto=update 会自动创建；生产环境 ddl-auto=validate 时执行本文件。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    document_id BIGINT NULL,
    question LONGTEXT NOT NULL,
    answer LONGTEXT NOT NULL,
    model VARCHAR(100) NULL,
    route VARCHAR(40) NULL,
    answer_mode VARCHAR(40) NULL,
    confidence VARCHAR(20) NULL,
    has_evidence BIT(1) NOT NULL DEFAULT b'0',
    latency_ms BIGINT NULL,
    retrieval_mode VARCHAR(40) NULL,
    sources_json LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_conversation_request (request_id),
    KEY idx_ai_conversation_user_created (user_id, created_at),
    KEY idx_ai_conversation_course_created (course_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    helpful BIT(1) NOT NULL,
    feedback_type VARCHAR(30) NULL,
    comment VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_feedback_conversation_user (conversation_id, user_id),
    KEY idx_ai_feedback_conversation (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
