# AI 股票分析面板

基于 AI 大模型的股票行情分析平台，提供实时行情走势、AI 智能分析、自选股管理等一站式功能。

> **在线演示**: [https://forjob00.onrender.com/](https://forjob00.onrender.com/)（Render Free Plan，首次访问需等待约 30 秒冷启动）

## 功能总览

### AI 智能分析
- 调用 DeepSeek 大模型对股票行情进行深度分析
- 输出市场情绪判断（看涨/中性/看跌）
- 评估风险等级并给出分析总结

### 实时行情走势
- 遵循 A 股真实交易时间（北京时间 9:30-11:30、13:00-15:00，周末休市）
- 行情数据当前为模拟生成（可替换为真实 API）
- 交易日开盘期间每 5 秒实时刷新价格走势
- 午休和收盘后自动封盘，保留当天完整走势数据
- 中途打开页面自动从数据库回填已产生的历史走势
- 收盘后自动汇总当天 OHLCV 数据，纳入近 10 日走势图

### 自选股管理
- 手机号注册/登录
- 添加/删除自选股票，一键切换查看

## LLM JSON 输出控制

本项目未使用 LangChain4j 的 `@AiService` 注解或 Tool Calling，而是直接调用 `ChatLanguageModel.chat()` 并通过 **Prompt Engineering** 严格约束大模型输出合法 JSON。核心 Prompt 模板如下：

```
你是一个专业的股票分析师 AI。你的唯一任务是：基于提供的股票行情数据，
生成严格符合指定 JSON 格式的分析结果。

## 输出要求
- 你必须只输出一个 JSON 对象，不能有任何其他文字。
- 不能使用 Markdown 代码块标记（如 ```json 或 ```）。
- 不能添加问候语、免责声明、或任何额外的解释。
- 你的整个响应必须是合法的 JSON，可以被 Java 的 Jackson ObjectMapper 直接解析。

## JSON 格式
{
  "summary": "对股票行情的深度分析总结（200字以内）",
  "sentiment": "Bullish 或 Neutral 或 Bearish（三选一）",
  "riskLevel": "风险评估等级及简短原因（如：高风险，因为...）"
}

## 分析维度
- 根据价格变动、成交量、日内振幅判断短期趋势
- 给出明确的市场情绪判断（Bullish / Neutral / Bearish）
- 结合涨跌幅与波动性评估当前风险等级

## 重要提醒
- 字段名必须严格使用 summary、sentiment、riskLevel（区分大小写）
- sentiment 只能是 "Bullish"、"Neutral"、"Bearish" 三选一
- 不要输出任何 JSON 以外的内容

当前日期：{{current_date}}
```

完整模板文件位于 `src/main/resources/stock-analysis-prompt-template.txt`，`{{current_date}}` 占位符在运行时注入当天北京时间。收到 LLM 原始输出后，代码层面还会做一道兜底处理——正则去除可能残留的 Markdown 代码块标记（``` ），再用 Jackson 解析并校验 `sentiment` 的值。

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端框架** | Spring Boot 3.2 |
| **AI 框架** | LangChain4j 1.0（OpenAI 兼容接口） |
| **AI 模型** | DeepSeek V4 Flash |
| **前端** | Vue 3 + Vite + Element Plus |
| **图表库** | Chart.js |
| **数据库** | Supabase（PostgreSQL） |
| **部署** | Docker + Render |

## API Key 与配置

项目依赖以下外部服务，需要在环境变量中配置对应的 Key：

### 1. DeepSeek API（AI 分析）

在本项目根目录 `src/main/resources/application.properties` 中：

```properties
langchain4j.open-ai.api-key=${DEEPSEEK_API_KEY:your-deepseek-api-key}
```

获取方式：
- 访问 [platform.deepseek.com](https://platform.deepseek.com) 注册并创建 API Key
- 将 Key 设为系统环境变量 `DEEPSEEK_API_KEY`，或在 Render 的 Environment Variables 中添加

### 2. Supabase（数据库）

获取方式：
- 访问 [supabase.com](https://supabase.com) 创建项目
- 在项目 Settings → API 中找到 **Project URL** 和 **service_role key**（注意是 service_role，不是 anon key）
- 设置环境变量：

| 变量名 | 值 |
|--------|-----|
| `SUPABASE_URL` | `https://xxxxx.supabase.co` |
| `SUPABASE_API_KEY` | service_role key（以 `eyJ` 开头） |

### 3. 数据库表结构

在 Supabase SQL Editor 中依次执行以下建表语句：

```sql
-- AI 分析记录表
CREATE TABLE stock_analysis (
  id SERIAL PRIMARY KEY,
  stock_code VARCHAR(20) NOT NULL,
  summary TEXT,
  sentiment VARCHAR(20),
  risk_level VARCHAR(50),
  created_at TIMESTAMP DEFAULT NOW()
);

-- 用户表
CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  phone VARCHAR(20) NOT NULL UNIQUE,
  password_md5 VARCHAR(64) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- 收藏表
CREATE TABLE user_favorites (
  id SERIAL PRIMARY KEY,
  phone VARCHAR(20) NOT NULL,
  stock_code VARCHAR(20) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- 股票历史走势表（近 10 日 + AI 分析快照）
CREATE TABLE stock_history (
  id SERIAL PRIMARY KEY,
  stock_code VARCHAR(20) NOT NULL,
  price_history TEXT,
  last_analysis TEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- 日内走势表（实时行情数据点）
CREATE TABLE stock_intraday (
  id SERIAL PRIMARY KEY,
  stock_code VARCHAR(20) NOT NULL,
  trade_date DATE NOT NULL,
  time VARCHAR(8) NOT NULL,
  price NUMERIC(10,2),
  volume BIGINT,
  created_at TIMESTAMP DEFAULT NOW()
);
```

## 本地运行

### 后端

```bash
# 设置环境变量
export DEEPSEEK_API_KEY=sk-xxxxxxxx
export SUPABASE_URL=https://xxxxx.supabase.co
export SUPABASE_API_KEY=eyJxxxxxxxxx

# 启动
./mvnw spring-boot:run
```

后端默认运行在 `http://localhost:8080`

### 前端

```bash
cd ui
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，开发模式下自动代理 `/api` 请求到后端 8080 端口。

## Render 部署

本项目已包含 Dockerfile，支持一键部署到 Render：

1. 推送代码到 GitHub
2. Render → New Web Service → 选择仓库
3. Runtime 选择 **Docker**
4. 添加环境变量（同上方的 API Key 配置）
5. 点击 Deploy

部署完成后通过 Render 分配的 `https://xxx.onrender.com` 域名即可访问。

## 注意事项

### 股票行情数据为模拟数据

当前版本使用 `MockStockApiService` 生成模拟股票行情，数据完全随机，**不来自真实交易所**。股票价格根据代码的 hashCode 映射到一个固定区间（10 ~ 3000），之后在区间内随机波动。

如需接入真实行情，只需替换 `MockStockApiService` 的实现即可，调用方 `StockAnalysisService` 无需改动。可接入的行情 API 有：
- 新浪财经 API
- 东方财富 API
- 聚宽 / Tushare 等量化数据平台

### 短信验证码为模拟发送

注册时的验证码不会真正发送短信，而是直接**打印在服务器控制台日志中**。本地运行时在 IDEA 终端查看，Render 部署时在 Logs 面板查看。日志格式如下：

```
========================================
  验证码已发送到 13800138000: 123456
========================================
```

线上部署后如需真实短信服务，只需替换 `AuthService.sendCode()` 方法，接入阿里云短信、腾讯云短信等服务商即可。

## 开发日志

详细的开发过程、架构设计决策、每轮指令记录及 Debug 经验请参阅 [WhatDidIDo.md](WhatDidIDo.md)。
