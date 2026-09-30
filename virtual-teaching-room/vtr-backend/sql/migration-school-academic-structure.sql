-- 学校基础资料：院系与专业。
-- 开发环境由 Hibernate ddl-auto=update 自动创建；生产环境执行本脚本后再启动。
CREATE TABLE IF NOT EXISTS school_department (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_school_department_name UNIQUE (school_id, name)
);

CREATE TABLE IF NOT EXISTS school_major (
    id BIGINT NOT NULL AUTO_INCREMENT,
    school_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_school_major_name UNIQUE (school_id, department_id, name)
);
