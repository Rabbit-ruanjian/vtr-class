-- Database Systems course outline.
-- Sources:
--   https://open.umn.edu/opentextbooks/textbooks/database-design-2nd-edition
--   https://www.db-book.com/toc-dir/toc.pdf
-- This script stores the catalog only; it does not copy copyrighted book text.

SET NAMES utf8mb4;
USE vtr_db;
START TRANSACTION;

INSERT INTO course (
    course_name, course_code, description, semester, credits, course_category,
    teaching_department, assessment_method, created_by, status, created_at, updated_at
)
SELECT
    '数据库系统原理',
    'DBSYS2026',
    '系统学习数据库系统概念、关系模型、SQL、数据库设计、规范化、存储、索引、查询优化、事务、并发控制、恢复、分布式数据库与数据分析。课程目录参考开放教材 Database Design（第2版）及 Database System Concepts 公开目录。',
    '2026-2027-1', 3.0, '数据库', '计算机科学与技术', '过程性考核+实验', 8, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM course WHERE course_code = 'DBSYS2026');

SET @database_course_id = (SELECT id FROM course WHERE course_code = 'DBSYS2026' LIMIT 1);

INSERT INTO course_member (course_id, user_id, joined_at, role, status)
SELECT @database_course_id, 8, NOW(), 'OWNER', 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM course_member WHERE course_id = @database_course_id AND user_id = 8
);

-- Students can open course content only after joining an active classroom.
-- The default class keeps this seeded demo course visible to existing students.
INSERT INTO classroom (
    class_name, created_at, description, grade, semester, status,
    student_count, teacher_id, updated_at, invite_code, course_id
)
SELECT '数据库系统原理默认班', NOW(), '数据库系统原理课程默认学习班级。', '本科二年级',
       '2026-2027-1', 'ACTIVE', 0, 8, NOW(), 'DBSYS26', @database_course_id
WHERE NOT EXISTS (
    SELECT 1 FROM classroom WHERE course_id = @database_course_id AND status = 'ACTIVE'
);

SET @database_classroom_id = (
    SELECT id FROM classroom
    WHERE course_id = @database_course_id AND status = 'ACTIVE'
    ORDER BY id LIMIT 1
);

INSERT INTO classroom_student_relation (
    classroom_id, created_at, status, student_id, student_number
)
SELECT @database_classroom_id, NOW(), 'ACTIVE', student.id, student.username
FROM sys_user student
WHERE student.role = 'STUDENT' AND student.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM classroom_student_relation existing
      WHERE existing.classroom_id = @database_classroom_id
        AND existing.student_id = student.id
  );

UPDATE classroom target
SET target.student_count = (
    SELECT COUNT(*) FROM classroom_student_relation relation
    WHERE relation.classroom_id = target.id AND relation.status = 'ACTIVE'
), target.updated_at = NOW()
WHERE target.id = @database_classroom_id;

INSERT INTO course_chapter (
    course_id, title, subtitle, description, sort_order, status, created_at, updated_at
)
SELECT @database_course_id, seed.title, seed.subtitle, seed.description, seed.sort_order,
       'ACTIVE', NOW(), NOW()
FROM (
    SELECT 1 sort_order, '第1章 数据库系统概述' title, '数据库系统的基本概念与发展脉络' subtitle, '认识数据库、数据库管理系统、数据库用户和典型应用架构。' description
    UNION ALL SELECT 2, '第2章 关系模型', '关系数据库的结构、模式与关系代数', '掌握关系、元组、属性、键、模式图和关系查询语言。'
    UNION ALL SELECT 3, '第3章 SQL基础与数据定义', '从数据定义到基础查询', '学习 SQL 数据定义、查询、集合运算、空值、聚集与嵌套查询。'
    UNION ALL SELECT 4, '第4章 高级SQL与数据库编程', '视图、事务、约束与数据库程序', '学习连接、视图、事务、完整性约束、索引、授权、函数、过程和触发器。'
    UNION ALL SELECT 5, '第5章 E-R模型与数据库设计', '从需求分析到概念结构设计', '使用实体-联系模型表达业务对象、属性、联系和基数约束。'
    UNION ALL SELECT 6, '第6章 关系数据库设计与规范化', '函数依赖、分解与范式', '理解好的关系模式、函数依赖、无损分解、保持依赖和规范化过程。'
    UNION ALL SELECT 7, '第7章 复杂数据类型', '半结构化、对象、文本与空间数据', '了解传统关系模型之外的数据表达方式及其数据库支持。'
    UNION ALL SELECT 8, '第8章 应用开发与安全', '数据库应用架构、性能和安全', '把数据库接入应用程序、Web 服务和安全体系，理解常见开发边界。'
    UNION ALL SELECT 9, '第9章 物理存储与数据组织', '存储介质、文件组织和缓冲管理', '理解数据库如何组织记录、数据字典、缓冲区和列式存储。'
    UNION ALL SELECT 10, '第10章 索引技术', 'B+树、哈希、位图及空间索引', '根据查询特征选择和维护索引，分析索引的代价与收益。'
    UNION ALL SELECT 11, '第11章 查询处理', '查询代价、算子与执行表达式', '理解选择、排序、连接等操作及查询执行的基本过程。'
    UNION ALL SELECT 12, '第12章 查询优化', '执行计划、统计信息与物化视图', '学习关系表达式变换、统计估计、执行计划选择和查询优化。'
    UNION ALL SELECT 13, '第13章 事务、并发控制与恢复', '正确性、隔离性与故障恢复', '掌握事务、可串行化、锁、死锁、恢复算法和高可用思路。'
    UNION ALL SELECT 14, '第14章 并行与分布式数据库', '分区、复制、分布式查询和事务', '认识并行数据库、分布式存储、分布式查询和分布式事务。'
    UNION ALL SELECT 15, '第15章 大数据、分析与现代数据库', '数据仓库、流数据、图数据库与区块链', '了解 MapReduce、数据仓库、OLAP、数据挖掘、流处理和现代数据库专题。'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM course_chapter existing
    WHERE existing.course_id = @database_course_id AND existing.title = seed.title
);

INSERT INTO course_section (
    course_id, chapter_id, title, subtitle, description, sort_order, status, created_at, updated_at
)
SELECT @database_course_id, chapter.id, seed.title, seed.subtitle, seed.description,
       seed.sort_order, 'ACTIVE', NOW(), NOW()
FROM (
    SELECT 1 chapter_order, 1 sort_order, '1.1 数据库系统的应用' title, '数据库在现实系统中的典型应用' subtitle, '从银行、校园、电子商务和政务系统认识数据库应用。' description
    UNION ALL SELECT 1, 2, '1.2 数据库系统的目标', '为什么需要数据库管理系统', '理解数据持久化、共享、完整性、安全性和并发访问的基本目标。'
    UNION ALL SELECT 1, 3, '1.3 数据视图', '从用户视角理解数据', '区分物理层、逻辑层和视图层，建立数据抽象的认识。'
    UNION ALL SELECT 1, 4, '1.4 数据库语言', 'DDL、DML与查询语言', '认识模式定义、数据操作和查询语言的职责边界。'
    UNION ALL SELECT 1, 5, '1.5 数据库设计', '从需求到可实现模式', '了解数据库设计的阶段、产物和常见风险。'
    UNION ALL SELECT 1, 6, '1.6 数据库引擎', '数据库系统的执行核心', '理解存储、查询处理、事务和恢复等引擎模块。'
    UNION ALL SELECT 1, 7, '1.7 数据库与应用架构', '集中式、客户-服务器与Web架构', '比较常见数据库部署结构及其适用场景。'
    UNION ALL SELECT 1, 8, '1.8 数据库用户与管理员', '角色、职责与运维边界', '认识终端用户、应用开发者、设计者和数据库管理员。'
    UNION ALL SELECT 1, 9, '1.9 数据库系统发展历史', '从文件系统到现代数据库', '梳理数据库模型、硬件和应用需求的演进。'
    UNION ALL SELECT 1, 10, '1.10 本章小结', '概念回顾与学习检查', '回顾数据库系统的组成、目标、用户和发展历程。'

    UNION ALL SELECT 2, 1, '2.1 关系数据库结构', '关系、元组、属性与域', '掌握关系表的基本组成和数学表示。'
    UNION ALL SELECT 2, 2, '2.2 数据库模式', '关系模式与实例', '区分模式和实例，理解模式变化对应用的影响。'
    UNION ALL SELECT 2, 3, '2.3 键', '超键、候选键与主键', '使用键唯一标识元组并表达实体约束。'
    UNION ALL SELECT 2, 4, '2.4 模式图', '用图描述关系结构', '阅读模式图并分析主键、外键和关系之间的联系。'
    UNION ALL SELECT 2, 5, '2.5 关系查询语言', '关系查询的基本思想', '认识声明式查询和关系操作的对应关系。'
    UNION ALL SELECT 2, 6, '2.6 关系代数', '选择、投影、连接与集合运算', '用关系代数表达和推导常见查询。'
    UNION ALL SELECT 2, 7, '2.7 本章小结', '关系模型回顾', '整理关系结构、模式、键和关系代数的核心概念。'

    UNION ALL SELECT 3, 1, '3.1 SQL查询语言概览', 'SQL的组成与使用场景', '认识 SQL 的数据定义、操纵、查询和控制能力。'
    UNION ALL SELECT 3, 2, '3.2 SQL数据定义', 'CREATE、ALTER与DROP', '使用 DDL 创建和维护数据库对象。'
    UNION ALL SELECT 3, 3, '3.3 SQL查询基本结构', 'SELECT-FROM-WHERE', '编写单表查询并理解查询的逻辑执行顺序。'
    UNION ALL SELECT 3, 4, '3.4 其他基本操作', '重命名、字符串与日期操作', '使用表达式和内置函数完成常见数据处理。'
    UNION ALL SELECT 3, 5, '3.5 集合操作', 'UNION、INTERSECT与EXCEPT', '合并和比较兼容关系的查询结果。'
    UNION ALL SELECT 3, 6, '3.6 空值', 'NULL与三值逻辑', '理解空值参与比较、聚集和条件判断时的行为。'
    UNION ALL SELECT 3, 7, '3.7 聚集函数', '分组与统计', '使用 COUNT、SUM、AVG、MAX、MIN 和 GROUP BY。'
    UNION ALL SELECT 3, 8, '3.8 嵌套子查询', '子查询与相关子查询', '使用 IN、EXISTS 和派生表表达复杂查询。'
    UNION ALL SELECT 3, 9, '3.9 数据库修改', 'INSERT、UPDATE与DELETE', '安全地新增、修改和删除关系中的数据。'
    UNION ALL SELECT 3, 10, '3.10 本章小结', 'SQL基础回顾', '综合练习数据定义、查询、聚集、子查询和修改操作。'

    UNION ALL SELECT 4, 1, '4.1 连接表达式', '内连接、外连接与自然连接', '根据连接条件组合多张表并处理缺失匹配。'
    UNION ALL SELECT 4, 2, '4.2 视图', '用视图组织和保护数据', '创建逻辑数据窗口，理解视图更新的限制。'
    UNION ALL SELECT 4, 3, '4.3 事务', '事务边界与提交回滚', '理解事务是保证一组操作一致完成的基本单位。'
    UNION ALL SELECT 4, 4, '4.4 完整性约束', '实体、参照与业务约束', '使用约束阻止非法数据进入数据库。'
    UNION ALL SELECT 4, 5, '4.5 SQL数据类型与模式', '类型选择与模式组织', '为属性选择合适的数据类型并规划模式对象。'
    UNION ALL SELECT 4, 6, '4.6 索引定义', 'CREATE INDEX与索引策略', '理解索引定义方式以及过度建索引的代价。'
    UNION ALL SELECT 4, 7, '4.7 授权', '用户权限与角色', '使用授权和回收控制数据库对象访问。'
    UNION ALL SELECT 4, 8, '4.8 本章小结', '高级SQL回顾', '综合回顾连接、视图、事务、约束、索引和授权。'

    UNION ALL SELECT 5, 1, '5.1 数据库设计过程', '需求分析、概念设计与实现', '理解从现实需求到关系模式的设计流程。'
    UNION ALL SELECT 5, 2, '5.2 实体-联系模型', '实体、实体集与联系集', '用 E-R 模型描述业务对象和对象之间的关系。'
    UNION ALL SELECT 5, 3, '5.3 复杂属性', '复合、多值与派生属性', '根据业务含义选择属性表达方式。'
    UNION ALL SELECT 5, 4, '5.4 映射基数', '一对一、一对多与多对多', '表达联系的数量约束和参与约束。'
    UNION ALL SELECT 5, 5, '5.5 主键设计', '实体标识与候选键选择', '选择稳定、唯一、可维护的实体标识。'
    UNION ALL SELECT 5, 6, '5.6 消除实体集中的冗余属性', '避免重复存储与更新异常', '识别并拆分不应直接放入实体集的属性。'
    UNION ALL SELECT 5, 7, '5.7 将E-R图转换为关系模式', '概念结构到逻辑结构', '把实体、联系和约束系统地映射到关系表。'
    UNION ALL SELECT 5, 8, '5.8 扩展E-R特性', '特化、泛化与聚集', '处理继承结构和高层概念关系。'
    UNION ALL SELECT 5, 9, '5.9 E-R设计问题', '冗余、弱实体与建模取舍', '分析建模中常见的歧义和结构性问题。'
    UNION ALL SELECT 5, 10, '5.10 数据建模的替代表示法', 'UML与其他建模记法', '比较不同建模表示方式的优缺点。'
    UNION ALL SELECT 5, 11, '5.11 数据库设计的其他问题', '可维护性、性能与演进', '在正确性之外考虑系统落地和长期演进。'
    UNION ALL SELECT 5, 12, '5.12 本章小结', 'E-R设计回顾', '回顾从需求分析到关系模式转换的完整过程。'

    UNION ALL SELECT 6, 1, '6.1 良好关系设计的特征', '减少冗余与异常', '用数据依赖和使用场景判断关系模式质量。'
    UNION ALL SELECT 6, 2, '6.2 使用函数依赖分解', '分解的动机与原则', '利用函数依赖拆分关系并保持语义。'
    UNION ALL SELECT 6, 3, '6.3 范式', '1NF、2NF、3NF与BCNF', '理解规范化层次及其解决的问题。'
    UNION ALL SELECT 6, 4, '6.4 函数依赖理论', '闭包、推理规则与最小覆盖', '推导属性闭包并判断依赖关系。'
    UNION ALL SELECT 6, 5, '6.5 基于函数依赖的分解算法', '无损连接与依赖保持', '应用算法构造满足设计目标的关系模式。'
    UNION ALL SELECT 6, 6, '6.6 基于多值依赖的分解', '多值依赖与4NF', '处理独立多值事实带来的冗余。'
    UNION ALL SELECT 6, 7, '6.7 更高范式', '5NF及连接依赖', '认识更高范式适用的特殊设计场景。'
    UNION ALL SELECT 6, 8, '6.8 原子域与第一范式', '原子性与重复组', '判断属性域是否满足关系模型的原子性要求。'
    UNION ALL SELECT 6, 9, '6.9 数据库设计过程', '规范化在项目中的落地', '把依赖分析、分解和评审纳入设计流程。'
    UNION ALL SELECT 6, 10, '6.10 时态数据建模', '记录随时间变化的事实', '设计有效时间、事务时间和历史记录。'
    UNION ALL SELECT 6, 11, '6.11 本章小结', '规范化回顾', '综合判断依赖、分解和范式选择。'

    UNION ALL SELECT 7, 1, '7.1 半结构化数据', 'XML、JSON与灵活结构', '理解结构不完全固定的数据表示和查询需求。'
    UNION ALL SELECT 7, 2, '7.2 面向对象数据', '对象、继承与持久化', '认识面向对象思想与数据库类型系统的结合。'
    UNION ALL SELECT 7, 3, '7.3 文本数据', '全文检索与文本索引', '理解长文本存储、分词和检索的基本问题。'
    UNION ALL SELECT 7, 4, '7.4 空间数据', '几何对象与空间查询', '了解位置、距离、范围和空间索引。'
    UNION ALL SELECT 7, 5, '7.5 本章小结', '复杂数据类型回顾', '比较关系数据与复杂数据类型的表达和处理方式。'

    UNION ALL SELECT 8, 1, '8.1 应用程序与用户界面', '数据库应用的分层职责', '理解界面、业务服务和数据访问层的职责。'
    UNION ALL SELECT 8, 2, '8.2 Web基础', 'HTTP、会话与数据访问', '认识 Web 应用连接数据库时的基本交互过程。'
    UNION ALL SELECT 8, 3, '8.3 服务端程序', '服务端渲染与接口服务', '理解服务端程序如何组织查询和事务。'
    UNION ALL SELECT 8, 4, '8.4 客户端代码与Web服务', 'API与异步访问', '使用服务接口向客户端提供数据能力。'
    UNION ALL SELECT 8, 5, '8.5 应用架构', '单体、分层与服务化架构', '比较不同架构下的数据边界和一致性问题。'
    UNION ALL SELECT 8, 6, '8.6 应用性能', '连接池、缓存与批处理', '分析数据库应用常见性能瓶颈。'
    UNION ALL SELECT 8, 7, '8.7 应用安全', '注入、越权与最小权限', '建立数据库应用安全的基本防线。'
    UNION ALL SELECT 8, 8, '8.8 加密及其应用', '传输、存储与密钥管理', '了解数据库数据保护中的加密使用边界。'

    UNION ALL SELECT 9, 1, '9.1 物理存储介质概览', '数据如何落到存储设备', '认识磁盘、闪存和其他物理存储介质。'
    UNION ALL SELECT 9, 2, '9.2 存储接口', '操作系统与数据库的存储接口', '理解数据库读写数据时经过的接口层。'
    UNION ALL SELECT 9, 3, '9.3 磁盘', '磁盘结构与访问特征', '分析磁盘寻道、旋转和块访问的成本。'
    UNION ALL SELECT 9, 4, '9.4 闪存', 'SSD与持久化存储', '理解闪存读写、擦除和寿命特征。'
    UNION ALL SELECT 9, 5, '9.5 RAID', '冗余阵列与可靠性', '比较不同 RAID 级别的性能和容错能力。'
    UNION ALL SELECT 9, 6, '9.6 磁盘块访问', '块、页与I/O代价', '掌握数据库按块组织和访问数据的基本方式。'
    UNION ALL SELECT 9, 7, '9.7 数据库存储架构', '文件、页与记录的层次', '连接物理存储与数据库内部组织。'
    UNION ALL SELECT 9, 8, '9.8 文件组织', '堆文件与有序文件', '选择文件组织方式并分析访问代价。'
    UNION ALL SELECT 9, 9, '9.9 文件中的记录组织', '定长、变长与跨页记录', '理解记录布局和更新时的空间管理。'
    UNION ALL SELECT 9, 10, '9.10 数据字典存储', '元数据与系统目录', '认识数据库描述自身结构的元数据。'
    UNION ALL SELECT 9, 11, '9.11 数据库缓冲区', '缓冲池与页面替换', '理解缓冲区如何减少磁盘访问。'
    UNION ALL SELECT 9, 12, '9.12 列式存储', '按列组织与分析查询', '比较行式和列式存储在不同负载下的差异。'
    UNION ALL SELECT 9, 13, '9.13 内存数据库中的存储组织', '以内存为主的存储方式', '了解内存数据库的组织和持久化挑战。'

    UNION ALL SELECT 10, 1, '10.1 索引基本概念', '索引结构与查找代价', '理解索引如何用空间换取查询速度。'
    UNION ALL SELECT 10, 2, '10.2 有序索引', '顺序访问与范围查询', '分析有序索引适合的查询模式。'
    UNION ALL SELECT 10, 3, '10.3 B+树索引文件', '搜索、插入与删除', '掌握 B+ 树索引的层次结构和维护过程。'
    UNION ALL SELECT 10, 4, '10.4 B+树扩展', '复合键与并发访问', '理解 B+ 树在工程实现中的常见扩展。'
    UNION ALL SELECT 10, 5, '10.5 哈希索引', '等值查询与哈希桶', '分析哈希索引的适用条件和局限。'
    UNION ALL SELECT 10, 6, '10.6 多键访问', '多列条件与索引组合', '根据多个查询条件选择索引访问方式。'
    UNION ALL SELECT 10, 7, '10.7 索引创建', '索引设计、维护与删除', '用查询负载指导索引生命周期管理。'
    UNION ALL SELECT 10, 8, '10.8 写优化索引结构', '面向写入的索引组织', '认识日志结构和写优化索引的基本思想。'
    UNION ALL SELECT 10, 9, '10.9 位图索引', '低基数属性的索引', '了解位图索引在分析型查询中的应用。'
    UNION ALL SELECT 10, 10, '10.10 空间与时态数据索引', '特殊数据的索引方法', '选择适合空间范围和时间范围查询的索引。'

    UNION ALL SELECT 11, 1, '11.1 查询处理概览', '从SQL到执行结果', '了解查询解析、优化、执行和返回结果的基本流程。'
    UNION ALL SELECT 11, 2, '11.2 查询代价度量', 'I/O、CPU与内存代价', '建立比较不同执行方案的成本模型。'
    UNION ALL SELECT 11, 3, '11.3 选择操作', '谓词过滤与选择性', '分析过滤条件的执行方式和选择性。'
    UNION ALL SELECT 11, 4, '11.4 排序', '外部排序与归并', '理解大数据量排序的磁盘和内存协同。'
    UNION ALL SELECT 11, 5, '11.5 连接操作', '嵌套循环、排序合并与哈希连接', '比较常见连接算法及其适用场景。'
    UNION ALL SELECT 11, 6, '11.6 其他关系操作', '投影、聚集与集合操作', '分析非选择和连接操作的执行方法。'
    UNION ALL SELECT 11, 7, '11.7 表达式求值', '执行计划中的算子组合', '将关系表达式转换为可执行的算子树。'
    UNION ALL SELECT 11, 8, '11.8 内存中的查询处理', '缓存、流式与中间结果', '理解内存资源对查询执行的影响。'

    UNION ALL SELECT 12, 1, '12.1 查询优化概览', '为什么需要查询优化', '认识同一语义查询可能对应多个执行计划。'
    UNION ALL SELECT 12, 2, '12.2 关系表达式变换', '等价变换与下推', '使用等价规则减少中间结果和执行代价。'
    UNION ALL SELECT 12, 3, '12.3 表达式结果统计估计', '基数与选择率估计', '理解优化器如何估算中间结果规模。'
    UNION ALL SELECT 12, 4, '12.4 执行计划选择', '代价优化与计划比较', '根据统计信息和代价模型选择计划。'
    UNION ALL SELECT 12, 5, '12.5 物化视图', '预计算与查询重写', '用物化结果提升重复分析查询性能。'
    UNION ALL SELECT 12, 6, '12.6 查询优化高级专题', '自适应和规则增强', '了解查询优化器在复杂负载下的扩展方向。'

    UNION ALL SELECT 13, 1, '13.1 事务概念', '事务的ACID属性', '理解原子性、一致性、隔离性和持久性。'
    UNION ALL SELECT 13, 2, '13.2 简单事务模型', '事务读写与提交', '用读写操作描述事务执行过程。'
    UNION ALL SELECT 13, 3, '13.3 存储结构与原子性', '数据页、日志与持久化', '理解事务保证依赖的存储结构。'
    UNION ALL SELECT 13, 4, '13.4 隔离性与可串行化', '并发执行的正确性', '判断并发调度是否等价于串行执行。'
    UNION ALL SELECT 13, 5, '13.5 锁协议', '共享锁、排他锁与两阶段锁', '使用锁控制并发事务的冲突。'
    UNION ALL SELECT 13, 6, '13.6 死锁处理', '检测、预防与恢复', '分析死锁形成条件并选择处理策略。'
    UNION ALL SELECT 13, 7, '13.7 多粒度与时间戳协议', '不同并发控制协议', '比较多粒度锁和基于时间戳的控制方法。'
    UNION ALL SELECT 13, 8, '13.8 多版本与快照隔离', 'MVCC与读一致性', '理解多版本并发控制如何减少读写阻塞。'
    UNION ALL SELECT 13, 9, '13.9 事务恢复', '故障分类与恢复流程', '识别故障并保证已提交事务的持久性。'
    UNION ALL SELECT 13, 10, '13.10 ARIES与高可用', '日志恢复与远程备份', '了解 ARIES 和高可用部署的基本思路。'

    UNION ALL SELECT 14, 1, '14.1 数据库系统架构', '集中式、并行式与分布式', '比较不同数据库系统架构的资源组织方式。'
    UNION ALL SELECT 14, 2, '14.2 数据分区', '水平、垂直与范围分区', '根据数据访问模式设计分区方案。'
    UNION ALL SELECT 14, 3, '14.3 分区倾斜处理', '热点与负载均衡', '识别数据倾斜并降低局部热点。'
    UNION ALL SELECT 14, 4, '14.4 复制', '副本、读扩展与一致性', '理解复制带来的性能、可靠性和一致性取舍。'
    UNION ALL SELECT 14, 5, '14.5 并行索引', '并行构建与访问索引', '认识并行环境下索引的构建和使用。'
    UNION ALL SELECT 14, 6, '14.6 分布式文件系统与键值存储', '分布式存储基础', '了解分布式文件系统和并行键值存储。'
    UNION ALL SELECT 14, 7, '14.7 并行查询处理', '并行排序、连接与执行', '分析查询任务在多个节点上的拆分和合并。'
    UNION ALL SELECT 14, 8, '14.8 分布式事务', '提交协议与一致性', '理解分布式事务的提交、复制和协调问题。'
    UNION ALL SELECT 14, 9, '14.9 云数据库服务', '弹性、托管与按需资源', '认识云数据库服务的基本能力和工程边界。'

    UNION ALL SELECT 15, 1, '15.1 大数据动机与特征', '规模、速度、类型与价值', '从业务需求理解大数据系统的产生背景。'
    UNION ALL SELECT 15, 2, '15.2 大数据存储系统', '分布式文件与列式数据', '比较大规模数据存储的组织方式。'
    UNION ALL SELECT 15, 3, '15.3 MapReduce范式', '批处理任务的拆分与归并', '理解 MapReduce 的计算模型和适用场景。'
    UNION ALL SELECT 15, 4, '15.4 流数据', '持续到达的数据处理', '认识流式计算、窗口和实时结果。'
    UNION ALL SELECT 15, 5, '15.5 图数据库', '节点、边与图查询', '理解图数据模型及其关联查询特征。'
    UNION ALL SELECT 15, 6, '15.6 数据仓库与OLAP', '面向分析的数据组织', '认识维度建模、数据仓库和联机分析。'
    UNION ALL SELECT 15, 7, '15.7 数据挖掘', '从数据中发现模式', '了解分类、聚类、关联规则等基础任务。'
    UNION ALL SELECT 15, 8, '15.8 区块链数据库专题', '共识、账本与智能合约', '了解区块链数据管理和智能合约的基本概念。'
    UNION ALL SELECT 15, 9, '15.9 本章小结', '现代数据库专题回顾', '比较关系数据库、大数据平台、图数据库和区块链数据库。'
) seed
JOIN course_chapter chapter
  ON chapter.course_id = @database_course_id
 AND chapter.sort_order = seed.chapter_order
 AND chapter.status = 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM course_section existing
    WHERE existing.course_id = @database_course_id
      AND existing.chapter_id = chapter.id
      AND existing.title = seed.title
);

COMMIT;
