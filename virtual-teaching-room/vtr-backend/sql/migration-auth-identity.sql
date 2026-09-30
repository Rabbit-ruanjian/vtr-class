-- 验证码登录与单位/学号工号绑定功能
-- 生产环境请先备份数据库，再执行本脚本；开发环境 ddl-auto=update 也会自动补列。

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS unit_name VARCHAR(200) NULL,
    ADD COLUMN IF NOT EXISTS identity_type VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS identity_number VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS identity_status VARCHAR(20) NOT NULL DEFAULT 'UNBOUND',
    ADD COLUMN IF NOT EXISTS phone_verified_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS email_verified_at DATETIME NULL;

CREATE UNIQUE INDEX uk_sys_user_phone ON sys_user(phone);
CREATE UNIQUE INDEX uk_sys_user_identity
    ON sys_user(unit_name, identity_type, identity_number);

-- 老用户原本把学号/工号放在 username 中，可按实际数据回填：
-- UPDATE sys_user
-- SET identity_type = CASE WHEN role = 'TEACHER' THEN 'TEACHER' ELSE 'STUDENT' END,
--     identity_number = username,
--     identity_status = 'VERIFIED'
-- WHERE username IS NOT NULL AND username NOT LIKE 'u_%';
