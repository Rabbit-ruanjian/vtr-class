-- 清理旧数据（避免主键冲突）
DELETE FROM user_role;
DELETE FROM user;
DELETE FROM role;

-- 插入角色
INSERT INTO role (id, name, description) VALUES
                                             (1, 'ADMIN', '系统管理员'),
                                             (2, 'TEACHER', '教师'),
                                             (3, 'STUDENT', '学生');

-- 插入用户（密码：123456）
INSERT INTO user (id, username, password, email, role, status) VALUES
                                                                   (1, 'admin', '$2a$10$C0lIXUJ9jL8Wqh1GcH8RceL5eRjqTpJdXk0SdUq8SqDz5YpE9ZkP6', 'admin@vtr.com', 'ADMIN', 'ACTIVE'),
                                                                   (2, 'teacher', '$2a$10$C0lIXUJ9jL8Wqh1GcH8RceL5eRjqTpJdXk0SdUq8SqDz5YpE9ZkP6', 'teacher@vtr.com', 'TEACHER', 'ACTIVE'),
                                                                   (3, 'student', '$2a$10$C0lIXUJ9jL8Wqh1GcH8RceL5eRjqTpJdXk0SdUq8SqDz5YpE9ZkP6', 'student@vtr.com', 'STUDENT', 'ACTIVE');

-- 分配角色
INSERT INTO user_role (user_id, role_id) VALUES
                                             (1, 1),
                                             (2, 2),
                                             (3, 3);
