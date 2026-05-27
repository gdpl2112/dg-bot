# DG-Bot Node.js — 低内存版

基于 Node.js + TypeScript 重写的 DG-Bot，内存占用从 Java 版的 400-800MB 降至 **50-120MB**。

## 功能对照

| 功能 | Java 版 | Node.js 版 |
|------|---------|------------|
| 多 Bot 管理 (OneBot WS) | ✓ | ✓ |
| 关键词回复 (正则) | ✓ | ✓ |
| 定时消息 (Cron) | ✓ | ✓ |
| API 调用模板 | ✓ | ✓ |
| JS 脚本引擎 | Nashorn | Node.js vm (原生兼容) |
| AI 助手 | Spring AI | OpenAI SDK |
| 点歌 (QQ/酷狗) | ✓ | ✓ |
| Web 后台 API | Spring Boot | Fastify |
| Token 认证 | Spring Security | 自实现 TokenStore |
| SQLite 数据库 | MyBatis-Plus | better-sqlite3 |
| 撤回监听 | ✓ | ✓ |
| 消息统计 | ✓ | ✓ |

## 环境要求

| 依赖 | 版本 |
|------|------|
| Node.js | 18+ |
| npm | 9+ |
| 协议端 | OneBot 11 实现 (如 OpenShamrock) |

## 快速启动

```bash
cd node
npm install
npm run build
npm start
```

### 开发模式

```bash
npm run dev
```

### 配置

在 `node/` 目录下创建 `application.yml`：

```yaml
server:
  port: 34740

super:
  qid: "3474006766"  # 超级管理员QQ号

manage:
  key: "your-secret-key"
  bid: "3474006766"

database:
  path: "./data.db"
```

也可通过 `--profile=run` 参数加载 `application-run.yml`。

### Docker

```bash
docker build -t dg-bot-node ./node
docker run -d -p 34740:34740 -v ./data:/app/data dg-bot-node
```

## 内存对比

| 版本 | 空载内存 | 10 Bot 运行 |
|------|---------|------------|
| Java (Spring Boot) | ~400MB | ~600-800MB |
| **Node.js** | **~30MB** | **~50-120MB** |

## 数据库兼容

Node.js 版直接复用 Java 版的 `data.db` SQLite 文件，无需数据迁移。

## 前端

前端仓库 [dg-bot-front](https://github.com/gdpl2112/dg-bot-front) 无需修改，API 接口保持兼容。
