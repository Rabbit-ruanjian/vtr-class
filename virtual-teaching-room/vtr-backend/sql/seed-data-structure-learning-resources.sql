SET NAMES utf8mb4;

INSERT INTO learning_question
    (title, stem, question_type, options, reference_answer, analysis, difficulty, knowledge_point, course_id, chapter, teacher_id, status, created_at, updated_at)
SELECT '算法复杂度判断', '若算法的基本操作次数为 3n^2 + 2n + 1，则该算法的时间复杂度是？', 'SINGLE_CHOICE',
       'A. O(n)\nB. O(n log n)\nC. O(n^2)\nD. O(2^n)', 'C',
       '在渐进分析中保留增长最快的项，常数因子和低阶项可以忽略，因此复杂度为 O(n^2)。', 2, '算法复杂度分析', 2, '第1章 绪论', 8, 'PUBLISHED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_question WHERE course_id = 2 AND title = '算法复杂度判断');

INSERT INTO learning_question
    (title, stem, question_type, options, reference_answer, analysis, difficulty, knowledge_point, course_id, chapter, teacher_id, status, created_at, updated_at)
SELECT '线性表顺序存储', '在长度为 n 的顺序表第 i 个位置插入一个元素，最坏情况下需要移动多少个元素？', 'SINGLE_CHOICE',
       'A. 1\nB. i\nC. n-i+1\nD. n', 'D',
       '在表头插入时，原有 n 个元素都要向后移动一位，因此最坏移动次数为 n。', 2, '顺序表插入与删除', 2, '第2章 线性表', 8, 'PUBLISHED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_question WHERE course_id = 2 AND title = '线性表顺序存储');

INSERT INTO learning_question
    (title, stem, question_type, options, reference_answer, analysis, difficulty, knowledge_point, course_id, chapter, teacher_id, status, created_at, updated_at)
SELECT '线性表操作特点', '下列哪些操作适合使用单链表实现？', 'MULTIPLE_CHOICE',
       'A. 在已知结点后插入元素\nB. 频繁在表头插入元素\nC. 按下标随机访问第 k 个元素\nD. 不要求连续存储空间', 'A,B,D',
       '链表插入和删除只需修改指针，并且不要求连续存储；但按下标访问需要从头遍历，效率较低。', 3, '单链表的基本操作', 2, '第2章 线性表', 8, 'PUBLISHED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_question WHERE course_id = 2 AND title = '线性表操作特点');

INSERT INTO learning_question
    (title, stem, question_type, options, reference_answer, analysis, difficulty, knowledge_point, course_id, chapter, teacher_id, status, created_at, updated_at)
SELECT '栈的出栈顺序', '依次将 1、2、3、4 入栈，入栈过程中允许出栈，则不可能得到哪一种出栈序列？', 'SINGLE_CHOICE',
       'A. 1,2,3,4\nB. 2,1,4,3\nC. 3,2,4,1\nD. 4,3,1,2', 'D',
       '栈遵循后进先出。输出 4、3、1 后，2 仍在 1 的下面，不可能再先于 1 输出。', 3, '栈的特性与应用', 2, '第3章 栈和队列', 8, 'PUBLISHED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_question WHERE course_id = 2 AND title = '栈的出栈顺序');

INSERT INTO learning_question
    (title, stem, question_type, options, reference_answer, analysis, difficulty, knowledge_point, course_id, chapter, teacher_id, status, created_at, updated_at)
SELECT '循环队列判空', '在循环队列中，队头指针 front 指向队首元素，队尾指针 rear 指向下一个入队位置，队列为空的条件是？', 'FILL',
       NULL, 'front == rear', '采用“队尾指向下一个位置”的约定时，front 与 rear 相等表示没有元素。', 2, '循环队列的队空与队满判断', 2, '第3章 栈和队列', 8, 'PUBLISHED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_question WHERE course_id = 2 AND title = '循环队列判空');

INSERT INTO learning_question
    (title, stem, question_type, options, reference_answer, analysis, difficulty, knowledge_point, course_id, chapter, teacher_id, status, created_at, updated_at)
SELECT '二叉树遍历基础', '一棵二叉树的前序遍历序列为 A、B、D、E、C，中序遍历序列为 D、B、E、A、C，则根结点是？', 'SINGLE_CHOICE',
       'A. A\nB. B\nC. C\nD. D', 'A',
       '前序遍历的第一个结点就是根结点，因此根结点为 A。', 2, '二叉树前序与中序遍历', 2, '第5章 树和二叉树', 8, 'PUBLISHED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM learning_question WHERE course_id = 2 AND title = '二叉树遍历基础');
