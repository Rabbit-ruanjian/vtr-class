-- 生产环境使用 ddl-auto=validate 时执行本文件。
-- 开发环境 ddl-auto=update 会自动创建同名表，无需重复执行。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ai_document (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    courseware_id BIGINT NULL,
    chapter VARCHAR(100) NULL,
    source_type VARCHAR(30) NULL,
    source_url VARCHAR(1000) NULL,
    license VARCHAR(100) NULL,
    source_author VARCHAR(255) NULL,
    attribution VARCHAR(1000) NULL,
    file_url VARCHAR(1000) NULL,
    document_name VARCHAR(255) NOT NULL,
    file_extension VARCHAR(20) NULL,
    content_type VARCHAR(100) NULL,
    file_size BIGINT NOT NULL,
    extracted_text LONGTEXT NOT NULL,
    text_length INT NOT NULL,
    chunk_count INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'READY',
    review_remark VARCHAR(1000) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    is_deleted BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    index_mode VARCHAR(30) NULL,
    PRIMARY KEY (id),
    KEY idx_ai_document_owner (owner_id),
    KEY idx_ai_document_owner_status (owner_id, status),
    KEY idx_ai_document_courseware (courseware_id, is_deleted),
    KEY idx_ai_document_course_status (course_id, status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_document_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    heading VARCHAR(255) NULL,
    embedding_json LONGTEXT NULL,
    content TEXT NOT NULL,
    char_start INT NOT NULL,
    char_end INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_document_chunk_index (document_id, chunk_index),
    KEY idx_ai_document_chunk_document (document_id),
    CONSTRAINT fk_ai_document_chunk_document FOREIGN KEY (document_id) REFERENCES ai_document (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_agent_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    sources_json LONGTEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_remark VARCHAR(1000) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_ai_artifact_course (course_id, status),
    KEY idx_ai_artifact_creator (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
