# AI 助手启动与部署说明

## 一次性配置

1. 将 `config/ai.env.example` 复制为 `config/ai.env`。
2. 只在 `config/ai.env` 中填写真实的 `KIMI_API_KEY`，不要把 Key 写进前端或 Java 源码。
3. 不需要代理时，保持 `KIMI_PROXY_HOST` 和 `KIMI_PROXY_PORT` 为空；校园网或服务器需要代理时再填写这两个配置。
4. Embedding 是可选增强：配置 `AI_EMBEDDING_ENABLED=true`、`AI_EMBEDDING_BASE_URL`、`AI_EMBEDDING_API_KEY` 和 `AI_EMBEDDING_MODEL` 后，课程 RAG 会在关键词/TF-IDF 基础上增加向量召回；未配置时仍可正常使用本地混合检索。
5. AI 默认按用户每分钟 30 次、同时 3 个请求保护，可通过 `AI_REQUESTS_PER_MINUTE` 和 `AI_MAX_CONCURRENT` 调整。

## 课程资源如何进入 AI 检索

教师上传课程 PDF、PPT/PPTX、DOC/DOCX、TXT、MD 后，系统会在上传事务提交后异步完成“文件读取 → 文本切片 → Embedding（已配置时）→ `ai_document_chunk` 入库”。课件审核状态与 AI 索引状态分开管理：审核通过前不会进入学生的课程 RAG。

视频文件无法直接从 MP4 中可靠提取讲解内容，因此视频先建立标题、章节和说明的元数据索引。教师可在教学视频详情点击“上传字幕/转写”，上传与视频对应的 `.srt`、`.vtt` 或 `.txt`，系统会自动重新索引字幕正文。没有字幕时会显示 `WAITING_TRANSCRIPT`，不会把视频元数据冒充视频正文。

视频字幕接口为：`POST /api/courseware/{coursewareId}/ai-transcript`，字段名为 `file`，仅视频上传教师可用，单个字幕文件不超过 20MB。

`config/ai.env` 已被忽略，不会被提交到版本库。启动脚本每次启动都会重新读取它，因此 Windows 重启后不会因为临时终端环境消失而失效。

## 启动方式

- 日常开发：双击 `start-backend.cmd`，需要重载时执行 `start-backend.cmd restart`。
- 部署/演示：执行 `start-backend-prod.cmd`，或执行 `start-backend.cmd prod`。该方式会先生成生产 JAR，再用 Java 17 启动。
- 一键启动本地前后端：执行 `start-all.cmd`，然后打开 `http://127.0.0.1:3000/`。

一键入口通过 `start-services.ps1` 启动独立的 Java/Node 后台进程，不依赖聊天窗口或启动终端保持打开。每次先用 Java 和配置中的密钥验证 Kimi `/models`，再检查后端源码是否比 JAR 更新；有更新时自动打包。后端或配置更新时会重启对应进程，最后确认后端健康和前端页面均就绪才报告成功。`start-all.cmd restart` 会重新打包并重启前后端。

Windows 登录时网络可能尚未就绪，`autostart` 模式会做最多 6 次 AI 连通性检查，普通启动最多 3 次；失败重试间隔为 5 秒。持续失败会返回错误，详情在 `logs/ai-check.log`，网络恢复后重新执行 `start-all.cmd`。

运行 `check-ai.cmd --chat` 可以用同一 Java 17 网络栈发送一道简单测试题，确认模型确实返回内容；只执行 `check-ai.cmd` 则检查鉴权，不产生问答。检查不会显示密钥，启动检查结果记录在 `logs/ai-check.log`。

浏览器显示“127.0.0.1 拒绝连接”时，通常是前端没有启动；前端页面能打开但接口报 `ECONNREFUSED 127.0.0.1:8080` 时，通常是后端没有启动。先执行 `start-all.cmd`，再查看下面的启动日志。

日志位于根目录的 `logs` 文件夹：

- `vtr-backend-runtime.log`：开发启动日志
- `vtr-backend-prod.log`：生产启动日志
- `vtr-backend-build.log`：生产打包日志

## 网络故障判断

一键启动会验证真实 Java HTTPS 连通性与 API 鉴权，失败时返回非零退出码，不会报告 AI 已就绪；原有单独后端启动脚本的 TCP 预检仍只输出警告。后端日志会记录 `apiKeyConfigured=true/false`，不会记录 Key 内容。

- `apiKeyConfigured=false`：启动进程没有读取到 Key，检查 `config/ai.env`。
- `Permission denied`、`Access denied`：也可能是启动 Java 的开发工具处于网络受限沙箱中，不能仅据此判定校园网或防火墙有问题。应从普通 Windows 终端运行 `check-ai.cmd --chat` 复查；由开发工具启动时使用获准的正常系统执行环境。
- `UnknownHostException`：DNS 或网络解析问题。
- `401`：Key 无效或过期。
- `403`：Key 没有当前模型权限。
- `404`：模型名称或 Kimi 地址不正确。
- Embedding 服务不可用时不会阻止平台启动，课程 RAG 会自动回退到关键词 + TF-IDF；Embedding 恢复后，教师可在课程知识库点击“重建向量”补齐历史片段。

部署服务器需要允许 `java.exe` 访问 Kimi 地址的 TCP 443；如果只能使用代理，在 `config/ai.env` 配置代理后重新启动。

本机已验证 `https://api.moonshot.cn/v1` 直连可返回真实模型回答；`127.0.0.1:7890` 当前没有代理监听，所以配置为直连。只有代理程序实际运行并通过检查后，才应启用代理；不要为消除错误气泡把固定规则回复当作在线模型回答。
