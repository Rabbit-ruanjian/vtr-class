# 数据结构课程本地 AI 资源包

本项目的课程 AI 不在学生访问资源时请求外部网页。资源需要先下载到本地，再通过课程工作台的“AI知识库”上传；上传后，文件、提取文本和知识切片都保存在本项目中，课程 RAG 只检索本地数据。

## 推荐第一批资源

| 文件 | 章节 | 来源 | 许可证 |
| --- | --- | --- | --- |
| `an-open-guide-data-structures.pdf` | 算法复杂度、递归、排序、查找、链表、栈队列、树、图、动态规划 | [PALNI Open Press](https://pressbooks.palni.org/anopenguidetodatastructuresandalgorithms/) | CC BY 4.0 |
| `mit-6006-lecture-02-data-structures.pdf` | 数据结构接口、数组、链表、动态数组 | [MIT OCW Lecture 2](https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/mit6_006s20_lec2/) | MIT OCW CC BY-NC-SA |
| `mit-6006-lecture-03-sorting.pdf` | 排序、二分查找、复杂度 | [MIT OCW Lecture 3](https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/mit6_006s20_lec3/) | MIT OCW CC BY-NC-SA |
| `mit-6006-lecture-07-avl.pdf` | 二叉树、AVL 树 | [MIT OCW Lecture 7](https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/resources/mit6_006s20_lec7/) | MIT OCW CC BY-NC-SA |

下载时请使用上述官方页面中的 Download File / PDF 下载入口。文件下载后，在课程工作台中逐份上传，并填写：

- 章节：例如“第7章 排序”；
- 许可证：例如 `CC BY 4.0` 或 `MIT OCW CC BY-NC-SA 4.0`；
- 作者/机构；
- 原始来源地址；
- 署名信息。

目前课程资源上传支持 PDF、DOCX、TXT、MD；推荐优先使用官方 PDF 或 DOCX。上传完成后，系统会自动：

1. 保存本地原始文件；
2. 提取 PDF、DOCX、TXT 或 MD 文本，单个文件最多保存约 50 万字符；
3. 按自然边界切分知识片段；
4. 建立课程级 RAG 数据；
5. 在 AI 回答中显示本地资源和片段依据。

## 竞赛演示建议

先上传主教材和 3 份 MIT 讲义，再进入“AI教研助手”生成排序章节的备课方案。备课方案会保存为 `PENDING_REVIEW`，教师审核后再作为正式教学成果使用。学生提问、题目生成和学情分析均通过同一个课程知识库完成。

MIT OCW 的许可要求署名、遵守非商业使用和相同方式共享要求；本项目保留来源和许可证字段，后续正式部署前仍应根据比赛规则和实际发布范围复核资源许可。
