# What Did I Do — AI 股票分析面板 开发日志

## 项目搭建思路

将一个 Spring Boot + Vue 3 预约挂号项目（"硅谷小智"）全面改造为 **AI 股票分析面板**。

### 技术选型

| 层 | 技术 | 原因 |
|---|------|------|
| 后端框架 | Spring Boot 3.2.6 + Java 17 | 原有项目基础，保留 |
| AI 模型 | DeepSeek v4-flash | 性价比高，OpenAI 兼容接口，LangChain4j 原生支持 |
| AI 集成 | LangChain4j 1.0.0-beta3 (`langchain4j-open-ai-spring-boot-starter`) | 直接使用 `ChatLanguageModel.chat()` 而非 `@AiService`，需要严格控制 prompt 和 JSON 输出 |
| 数据库 | Supabase (PostgreSQL) | 免费额度、自带 REST API、无需自建服务器 |
| 数据库访问 | WebClient (Spring WebFlux) | 通过 Supabase REST API 访问，无需 JDBC 驱动 |
| 前端 | Vue 3 + Element Plus + Chart.js + Vite | 保留原有 Vue 3 框架，移除聊天组件，新增股票分析面板 |
| 部署 | Docker + Render.com | 多阶段构建（Node 编译前端 → Maven 编译后端 → JRE 运行） |

### 架构设计

```
Browser
  → StockAnalysisPanel.vue (Vue 3 + Element Plus + Chart.js)
    → POST /api/stock/analyze     { stockCode }
    → GET  /api/stock/detail      ?stockCode=
    → GET  /api/stock/realtime    ?stockCode=
    → POST /api/auth/login        { phone, password }
    → POST /api/auth/register     { phone, password, code }
    → POST /api/auth/send-code    { phone }
    → GET  /api/favorites/list    ?phone=
    → POST /api/favorites/add     { phone, stockCode }
    → POST /api/favorites/remove  { phone, stockCode }
  → StockAnalysisController
    → StockAnalysisService
      ├── MockStockApiService.fetchQuote()  → 模拟股票行情
      ├── buildPrompt() + chatLanguageModel.chat()  → 调用 DeepSeek LLM
      ├── parseJsonResponse()  → 解析 JSON 并校验
      └── getRealtimeData()  → 内存 FIFO 队列，每 5 秒追加模拟数据点
    → SupabaseService (WebClient → Supabase REST API)
      ├── saveAnalysis()  → stock_analysis 表
      ├── saveStockHistory()  → stock_history 表 (upsert)
      ├── getStockHistory()  → 读取历史 + 分析
      ├── findUserByPhone() / insertUser()  → users 表
      └── getFavorites() / addFavorite() / removeFavorite()  → user_favorites 表
Supabase (PostgreSQL: users, user_favorites, stock_history, stock_analysis)
```

### 关键设计决策

- **为什么不使用 `@AiService`**：需要严格控制 prompt 模板和 JSON 输出格式，`@AiService` 的自动工具调用和 streaming 功能不需要，直接用 `ChatLanguageModel.chat()` 更简洁
- **为什么用 Supabase REST API 而不是 JDBC**：项目已有 spring-boot-starter-webflux 依赖，无需引入 MyBatis/MySQL 驱动，直接 HTTP 调用即可
- **Mock 股票数据**：暂时没有真实股票 API，创建 `MockStockApiService` 生成随机行情数据，后续替换为真实 API 只需修改一个类
- **实时行情的 FIFO 队列**：内存中 `ConcurrentHashMap` 存储，每次调用追加一个新数据点，最多保留 120 个点（10 分钟），不持久化
- **AI 分析静态不自动刷新**：分析内容只在用户手动点击"重新 AI 分析"时更新，避免无限循环和 API 费用浪费
- **30 秒冷却**：防止用户频繁调用 LLM 产生费用

---

## 下达的指令记录

### 第一轮：初始改造
- "按照修改要求.txt 对项目进行改造" → 删除所有医疗/聊天/挂号代码，新建股票分析功能
- "创建一个测试类用来假装调用接口，随机生成一些股市的数据返回" → 创建 MockStockApiService

### 第二轮：登录注册 + 自选股
- "在前端中新增一个登录界面，有登录和注册两个功能，密码存储 MD5，注册使用手机号，验证码显示在控制台中"
- "按照修改要求.txt 里写的进行修改" → 侧边栏自选列表、per-user 收藏存储、股票详情含 AI 分析 + 走势图、历史数据存为 JSON

### 第三轮：双折线图 + 切换大模型
- "按照修改要求.txt 进行修改" → 修复无限循环、分析内容静态化、双折线图（实时行情 5 秒轮询 + 10 日历史）、切换到 DeepSeek v4-flash
- "我点击'开始AI分析'的按钮时，页面没有反应" → 修复 MouseEvent 传参错误
- "当我在前端，点进某一个股票的时候，会一直循环触发一些操作" → 修复无限循环 + JSON 双重编码

### 第四轮：本次会话
- "前端中的股票详情这个模块对于屏幕的适应性不太好，你来调整一下" → 响应式 CSS 优化
- "你审查一下这整个项目，看看有没有 key 这类不适合发布的东西" → 安全审计
- "清除已有的 git 历史，重新提交去除隐私信息的项目" → Git 历史清理
- "在 WhatDidIDo.md 中写入今天的工作内容" → 本文档

---

## 错误与 DEBUG 过程

### 错误 1：DashScope API URL 错误
- **现象**：启动后调用 LLM 报 `ApiException: url error`
- **原因**：`langchain4j.community.dashscope.chat-model.base-url` 配置了错误的地址
- **解决**：移除 `base-url` 配置，将模型名从 `qwen3.6-plus` 改为 `qwen-plus`（后续切换到了 DeepSeek）

### 错误 2：Supabase 404 Not Found
- **现象**：`POST /rest/v1/stock_analysis` 返回 404，随后 `user_favorites` 也 404
- **原因**：数据库表未创建
- **解决**：提供 CREATE TABLE SQL 语句，在 Supabase SQL Editor 中执行。执行时遇到 `relation "users" already exists` → 改用 `CREATE TABLE IF NOT EXISTS`。又遇到复制粘贴空格丢失（`idBIGSERIAL`）→ 格式化 SQL 加换行解决

### 错误 3：@RequestParam 编译错误
- **现象**：`IllegalArgumentException: Name for argument not specified`
- **原因**：Java 编译时未保留方法参数名（`-parameters` 未开启），Spring 无法自动推断 `@RequestParam` 的参数名
- **解决**：所有 `@RequestParam` 显式指定 value（如 `@RequestParam("stockCode")`），同时在 pom.xml 中添加 `<maven.compiler.parameters>true</maven.compiler.parameters>` 和 maven-compiler-plugin 配置

### 错误 4：前端 .trim() 报错 — 按钮点击无反应
- **现象**：点击"开始 AI 分析"按钮无反应，前端控制台报 `TypeError: .trim is not a function`
- **原因**：按钮 `@click="startAnalyze"` 将 MouseEvent 对象作为参数传入，导致 `searchCode.value` 变成了事件对象
- **解决**：改为 `@click="startAnalyze()"`，不传参数

### 错误 5：无限循环 — AI 分析每 5 秒触发一次
- **现象**：进入股票详情页后，后端日志每 5 秒输出 `Supabase 写入成功` 和 `股票历史保存成功`，持续不断
- **原因**：两个问题叠加——
  1. `fetchDetail()` 在发现没有 AI 分析时自动调用 `reanalyze()`
  2. Supabase 中 `last_analysis` JSON 双重编码（字符串被再套一层引号），Jackson `treeToValue()` 无法解析 `TextNode`，每次读取都判断"无分析"，再次触发 reanalyze → 写入 → 下次又失败 → 死循环
- **解决**：(1) 移除 `fetchDetail()` 中的自动分析触发；(2) `getStockDetail()` 同时处理 `TextNode`（字符串）和 `ObjectNode`（对象）

### 错误 6：安全审计 — 密钥泄露
- **现象**：审计发现 `application.properties` 中 DeepSeek API key (`sk-...`) 和 Supabase 服务密钥 (`sb_secret_...`) 作为默认值硬编码；`render.yaml` 暴露 Supabase URL
- **风险**：任何人克隆仓库即可调用 DeepSeek API（产生费用）并读写 Supabase 数据库
- **解决**：(1) 移除所有真实默认值，改为 `${ENV_VAR}`；(2) `render.yaml` 敏感变量设 `sync: false`；(3) 删除 `.git` 重建仓库，彻底清除历史；(4) `.gitignore` 新增 `erros/` 和 `.claude/`

### 错误 7：Spring 启动崩溃 — 无法解析占位符
- **现象**：清除默认值后，应用启动报 `Could not resolve placeholder 'SUPABASE_URL'`
- **原因**：`${SUPABASE_URL}` 没有默认值时，Spring 要求环境变量必须存在
- **解决**：使用明显的假占位符 —— `${SUPABASE_URL:http://localhost:54321}`、`${SUPABASE_API_KEY:your-supabase-service-role-key}`、`${DEEPSEEK_API_KEY:your-deepseek-api-key}`。不泄露真实密钥，但让 Spring 能正常启动

### 错误 8：前端响应式布局问题
- **现象**：股票详情模块在平板和手机上显示不佳
- **解决**：图表高度改用 CSS `clamp()` + 外层 `div.chart-wrap`；`.cards-row` 改用 `repeat(auto-fit, minmax(240px, 1fr))`；增加 1024px / 768px / 480px 四档断点；标题字号 `clamp()` 动态缩放
