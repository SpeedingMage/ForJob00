# What Did I Do — AI 股票分析面板 开发日志

## 一、项目搭建思路

将一个 Spring Boot + Vue 3 框架搭建为 **AI 股票分析面板**。

### 1.1 技术选型

| 层 | 技术 | 选型原因 |
|---|------|------|
| 后端框架 | Spring Boot 3.2.6 + Java 17 | 成熟稳定，生态完善 |
| AI 模型 | DeepSeek v4-flash | 高性价比，OpenAI 兼容接口 |
| AI 集成 | LangChain4j 1.0.0-beta3 (`langchain4j-open-ai-spring-boot-starter`) | 直接使用 `ChatLanguageModel.chat()`，精准控制 prompt 和 JSON 输出 |
| 数据库 | Supabase (PostgreSQL) | 免费额度、自带 REST API、无需自建服务器 |
| 数据库访问 | WebClient (Spring WebFlux) | 通过 Supabase REST API 访问，无需 JDBC 驱动 |
| 前端 | Vue 3 + Element Plus + Chart.js + Vite | 组件化 UI、丰富的图表生态 |
| 部署 | Docker + Render.com | 多阶段构建（Node 编译前端 → Maven 编译后端 → JRE 运行） |

### 1.2 架构

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
  → StockAnalysisController → StockAnalysisService
      ├── MockStockApiService.fetchQuote()       → 模拟股票行情
      ├── buildPrompt() + chatLanguageModel.chat() → 调用 DeepSeek LLM
      ├── parseJsonResponse()                    → 解析 JSON 并校验
      └── getRealtimeData()                      → 实时行情生成 + 持久化
    → SupabaseService (WebClient → Supabase REST API)
      ├── users 表 — findUserByPhone() / insertUser()
      ├── user_favorites 表 — getFavorites() / addFavorite() / removeFavorite()
      ├── stock_analysis 表 — getAnalysis() / saveAnalysis()
      ├── stock_history 表 — getStockHistory() / saveStockHistory() / updatePriceHistory()
      └── stock_intraday 表 — getIntradayPoints() / saveIntradayPoint() / saveIntradayBatch()
```

### 1.3 关键设计决策

- **不用 `@AiService`**：需要严格控制 prompt 模板和 JSON 输出格式，`@AiService` 的自动工具调用和 streaming 不需要，直接用 `ChatLanguageModel.chat()` 更简洁
- **Supabase REST API 而非 JDBC**：项目已有 spring-boot-starter-webflux，无需引入 MyBatis/MySQL 驱动，直接 HTTP 调用
- **Mock 股票数据**：`MockStockApiService` 通过 hashCode 映射模拟价格区间（10 ~ 3000），后续替换真实 API 只需修改此一个类
- **交易时间遵循 A 股**：北京时间 9:30-11:30、13:00-15:00，周末及法定节假日休市
- **实时行情持久化**：开盘期间每个数据点写入 `stock_intraday` 表，中途打开页面从 DB 回填，避免数据丢失
- **AI 分析不自动刷新**：仅在用户手动点击按钮时触发，防止 LLM API 频繁调用产生费用，设有 30 秒冷却
- **Mock 验证码**：注册验证码打印在服务器控制台日志，不接入真实短信服务

---

## 二、开发历程

### 第一轮：初始改造
- 移除原项目所有医疗/聊天/挂号代码
- 新建股票分析功能骨架
- 创建 `MockStockApiService` 模拟股票行情 API

### 第二轮：登录注册 + 自选股
- 手机号注册/登录（密码 MD5），验证码打印在控制台
- 侧边栏自选列表（per-user 存储）
- 股票详情页：AI 分析卡片 + 实时行情折线图
- 历史数据以 JSON 存入 `stock_history` 表

### 第三轮：双折线图 + 大模型切换
- 修复 AI 分析死循环（无限触发 + JSON 双重编码）
- 双折线图：实时行情（5 秒轮询）+ 近 10 日走势
- 从 DashScope 切换到 DeepSeek v4-flash
- 修复按钮点击无反应（MouseEvent 传参）
- 前端响应式适配

### 第四轮：安全审计与发布准备
- 清理 `application.properties` 中硬编码的 API Key
- 删除 `.git` 重建仓库，彻底清除敏感信息历史
- `.gitignore` 新增 `erros/` 和 `.claude/`

### 第五轮：交易时间逻辑 + Render 部署
- AI 分析覆盖写入（存在则更新，不存在则插入）
- 完整 A 股交易时间实现（开盘/午休/收盘/周末）
- 日内走势持久化到 `stock_intraday` 表，支持中途回填
- 收盘后自动汇总当天 OHLCV 并入 10 日历史
- LLM 原始输出写入 `LLMReturn.txt`
- Render Docker 部署（修复 manifest 缺失、时区问题、前缀过滤）
- 撰写 README.md

### 第六轮：数据完整性 + 前端健壮性
- 修复 AI 重新分析时覆盖 `price_history` 的问题（新增 `updateLastAnalysis()` 只更新分析字段）
- 新增收藏股票时自动生成 mock 近 10 日历史（`ensureStockHistory()`）
- 前端防重复点击：三层防护（loading 锁 + 时间戳锁 + UI 禁用）
- 收藏列表去重（`dedupFavorites()`，Set 去重）
- 移动端适配修复（`100dvh`、flex 子元素滚动、iOS 触控滚动）
- 股票代码校验收紧：`^[A-Z]{1,5}$|^\d{5,6}$`（美股 1-5 字母、A 股 6 数字、港股 5 数字）

---

## 三、功能清单

| 功能模块 | 说明 |
|------|------|
| **AI 分析** | DeepSeek LLM 分析股票行情，输出 sentiment / riskLevel / summary |
| **用户系统** | 手机号注册/登录，MD5 密码，验证码 console 输出 |
| **自选股** | 侧边栏增删自选，per-user 隔离 |
| **实时行情** | 遵循 A 股交易时间，开盘 5 秒轮询，数据持久化 DB，中途回填 |
| **10 日走势** | 收盘后自动汇总当日 OHLCV，FIFO 保留近 10 天 |
| **交易时间** | 北京时间 9:30-11:30、13:00-15:00，周末休市，前端状态标签 |
| **LLM 日志** | 每次 AI 分析原始输出写入 `LLMReturn.txt` |
| **API 前缀** | `ApiPrefixFilter` 剥离 `/api` 前缀，兼容前端开发路径 |
| **防重复点击** | 三层防护：loading 布尔锁 + `lastAddTime` 时间戳锁 + CSS `pointer-events: none` UI 禁用 |
| **收藏去重** | 每次操作完成后自动检查收藏列表，Set 去重 |
| **代码校验** | 美股 1-5 大写字母、A 股 6 位数字、港股 5 位数字，正则 `^[A-Z]{1,5}$\|^\d{5,6}$` |
| **移动端优化** | `100dvh` 动态视口、flex 子元素滚动（`min-height: 0`）、iOS 平滑滚动 |

---

## 四、错误与 DEBUG

### 4.1 LLM 与 API
| # | 现象 | 原因 | 解决 |
|---|------|------|------|
| 1 | `ApiException: url error` | DashScope base-url 配置错误 | 移除 base-url，模型切到 DeepSeek |

### 4.2 数据库
| # | 现象 | 原因 | 解决 |
|---|------|------|------|
| 2 | Supabase 404 Not Found | 数据库表未创建 | 提供 CREATE TABLE SQL，在 Supabase SQL Editor 执行 |
| 9 | stock_analysis 写入 400 | `updated_at` 列不存在 | 移除 `updated_at` 字段 |

### 4.3 编译与启动
| # | 现象 | 原因 | 解决 |
|---|------|------|------|
| 3 | `IllegalArgumentException` — 参数名未指定 | Java 编译未保留参数名，Spring 无法推断 `@RequestParam` | `@RequestParam` 显式指定 value；pom.xml 加 `<maven.compiler.parameters>true</maven.compiler.parameters>` |
| 7 | Spring 启动崩溃 — 无法解析占位符 | `${SUPABASE_URL}` 无默认值 | 使用假占位符 `${SUPABASE_URL:http://localhost:54321}` |
| 10 | `no main manifest attribute` | pom.xml 缺少 spring-boot-maven-plugin | 添加插件，配置 `repackage` goal |
| 11 | Render 实时行情不显示 | `stock_intraday` 表未创建；服务器 UTC 时间与北京时间差 8 小时 | 建表；全部改为 `LocalTime.now(ZoneId.of("Asia/Shanghai"))` |

### 4.4 前端
| # | 现象 | 原因 | 解决 |
|---|------|------|------|
| 4 | 按钮点击无反应，`.trim is not a function` | `@click="startAnalyze"` 传入了 MouseEvent | 改为 `@click="startAnalyze()"` |
| 5 | AI 分析每 5 秒无限循环 | (1) `fetchDetail()` 自动调用 `reanalyze()` (2) JSON 双重编码导致 `treeToValue()` 失败 | (1) 移除自动分析触发 (2) `TextNode` 与 `ObjectNode` 双分支处理 |
| 8 | 平板/手机布局不佳 | 固定像素尺寸不适配 | CSS `clamp()` + `grid auto-fit` + 四档媒体查询 |

### 4.5 数据完整性
| # | 现象 | 原因 | 解决 |
|---|------|------|------|
| 12 | AI 重新分析后 10 日历史被覆盖 | `analyzeStock()` 每次都重新 `insertStockHistory()` 写入随机 mock 数据 | 先检查 `stock_history` 是否存在，存在则只调用 `updateLastAnalysis()` 更新分析字段 |
| 13 | 新增收藏股无历史数据 | `addFavorite()` 只插入收藏记录，不创建 `stock_history` | 注入 `StockAnalysisService`，新增时调用 `ensureStockHistory()` 生成 mock 10 日 |

### 4.6 安全
| # | 现象 | 原因 | 解决 |
|---|------|------|------|
| 6 | API Key 硬编码泄露 | `application.properties` 和 `render.yaml` 含真实密钥 | 全部改为 `${ENV_VAR}` 占位符；删除 `.git` 重建；`.gitignore` 补充 |

### 4.7 前端校验
| # | 现象 | 原因 | 解决 |
|---|------|------|------|
| 14 | 股票代码"44"能通过校验 | 原正则 `^[A-Z0-9]{1,10}$` 过于宽松，允许纯数字短串 | 收紧为 `^[A-Z]{1,5}$\|^\d{5,6}$`，区分美股/A股/港股格式 |
