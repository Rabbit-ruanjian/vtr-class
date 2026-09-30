-- 课程允许进入的行政班限制
-- 开发环境 ddl-auto=update 会自动建表；生产环境 ddl-auto=validate 时请先执行本脚本。

CREATE TABLE IF NOT EXISTS course_allowed_academic_class (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    academic_class_id BIGINT NOT NULL,
    created_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_allowed_academic_class (course_id, academic_class_id),
    KEY idx_course_allowed_academic_class_course (course_id),
    KEY idx_course_allowed_academic_class_class (academic_class_id)
);
