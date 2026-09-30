# 操作系统课程 RAG 测试资料包

## 用途

这是一套用于验证虚拟教研室 AI 闭环的最小课程数据包，覆盖：

- 课程资料导入
- 课程范围检索
- 基于资料回答
- 资料来源引用
- 资料不足时拒答
- 标准问题评测

## 资料来源与授权

本资料包中的课程笔记由项目整理者根据 MIT PDOS 的 xv6 官方教材进行中文概括和结构化整理，不复制教材的大段原文。

- 原始教材：<https://pdos.csail.mit.edu/6.1810/2025/xv6/book-riscv-rev5.pdf>
- 官方项目：<https://github.com/mit-pdos/xv6-riscv-book>
- 许可证：见同目录下的 `LICENSE-xv6-riscv-book.txt`
- 作者：Russ Cox、Frans Kaashoek、Robert Morris
- 许可证要求：再发布资料时保留版权声明和许可证声明

正式投入使用前，建议在系统中保存上述来源、作者、许可证和整理说明。不要把没有明确授权的普通商业教材直接上传到生产环境。

## 文件说明

| 文件 | 用途 |
|---|---|
| `operating-systems-course.json` | 课程和资源元数据 |
| `operating-systems-xv6-course-notes.md` | 可导入 RAG 的中文课程资料 |
| `operating-systems-rag-golden-set.json` | 20 个标准测试问题、参考答案和来源 |
| `LICENSE-xv6-riscv-book.txt` | 官方教材许可证文本 |

## 建议导入方式

1. 先创建课程《操作系统（xv6 RISC-V 测试课）》。
2. 将 `operating-systems-xv6-course-notes.md` 作为课程资源上传。
3. 审核并发布资源。
4. 使用 `operating-systems-rag-golden-set.json` 中的问题逐条测试。
5. 检查回答是否只依据当前课程、是否显示来源，以及资料外问题是否明确说明资料不足。
