# DG-Bot 自部署指南

本文档面向**自行部署**的场景，列出必须由部署者自行填写/准备的配置项，并给出可执行的落地步骤。
文中结论均来自当前代码（`src/main/java` + `src/main/resources` + `dg-bot-front`），而非 README 的概述性描述。

---

## 一、部署拓扑

```
QQ 客户端（协议端，如 NapCat / LLOneBot / OpenShamrock）
        │  反向 WS（协议端主动连入）或 正向 WS（DG-Bot 主动连出）
        ▼
DG-Bot 后端（Spring Boot，默认 :34740，SQLite：data.db）
        ▲
        │  /api/** 与 /bot/login 反向代理
Nginx（TLS 终止、静态资源、SPA 路由）
        ▲
        ▼
dg-bot-front（Vue3，构建产物 dist/）
```

四个交付物缺一不可：**JDK 运行时 + 后端 jar + 前端静态产物 + OneBot 协议端**。
后端不含任何前端静态资源（`src/main/resources` 仅 3 个文件），因此前端必须由 Nginx 单独托管。

---

## 二、必须自行配置的清单（速查）

| # | 配置项 | 位置 | 是否必填 | 说明 |
|---|--------|------|----------|------|
| 1 | `super.qid` | `application-run.yaml` | **必填** | 超级管理员 QQ 号，赋予 `admin` 权限；不填会退化为内置默认 `3474006766` |
| 2 | `manage.key` | 同上 | **必填** | `/api/manage/**` 的访问密钥（请求头 `X-Manage-Key`），默认值为示例值 `change-me-manage-secret-key`，**必须修改** |
| 3 | `manage.bid` | 同上 | 建议填 | 群管理记录数据来源的 bot QQ 号，对应文件 `{bid}-manage.db`；默认继承 `super.qid` |
| 4 | `report.point-gid` | 同上 | **必填** | 上报指令触发群号；`DefaultService` 中以 `@Value("${report.point-gid}")` **无默认值**注入，缺失会导致启动失败 |
| 5 | `report.bid` / `report.gid` | 同上 | 可空 | 每日 00:10 错误日志上报的 bot 与群；置空/`null` 即关闭上报 |
| 6 | `server.port` | 同上 | 建议确认 | 后端端口，默认 `34740`，需与 Nginx 反代、前端 `vite.config.ts` 代理一致 |
| 7 | `spring.datasource.url` | 同上 | 可默认 | `jdbc:sqlite:data.db` 为**相对路径**，故必须在应用目录启动 |
| 8 | `super` 的登录密码 | `data.db` 的 `auth_m` 表 | **必填** | 首次部署无自动初始化账号，需手工插入或调用管理接口设置，见第五节 |
| 9 | OneBot 连接参数 | 后台"连接配置"（表 `conn_config`） | **必填** | `qid / ip / port / type / token / heart` 六字段，见第六节 |
| 10 | 域名与 TLS 证书 | Nginx | 生产必填 | 后端本身不处理 HTTPS |
| 11 | `wzry.api` | `application-run.yaml` | 可选 | 王者荣耀战绩查询服务地址，默认指向作者服务；不开启该功能可忽略 |
| 12 | AI 模型参数 | 后台"AI 配置"（表 `ai_conf`） | 可选 | `apiKey / baseUrl / modelId` 按登录用户维度存库，不写在 yaml 里 |
| 13 | MCP 联网搜索 token | `application-run.yaml`（`top.kloping.ai.mcp`） | 可选 | 默认注释掉；仅 AI"联网"能力需要（阿里百炼等） |
| 14 | 运行目录 | 部署机 | **必填** | `data.db`、`{bid}-manage.db`、`logs/`、`bots/` 均相对工作目录生成 |

> 判定依据：`SecurityConfig`、`ManageController`、`DefaultService` 中对 `manage.key`、`manage.bid`、`report.point-gid` 的注入均**未提供默认值**，Spring 上下文启动即失败；其余项在 `application.yml` 中带有作者侧的默认值（超管号、上报群、王者接口），**必须覆盖**，否则会连到作者的账号/群。

---

## 三、环境准备

| 依赖 | 版本要求 | 依据 |
|------|----------|------|
| JDK | **21**（不是 11） | `pom.xml` 中 `java.version / maven.compiler.* = 21`，父 POM 为 Spring Boot 4.0.5 |
| Maven | 3.6+（仅源码编译需要） | 使用 releases 包时可跳过 |
| Node.js | 18+（前端构建） | `dg-bot-front` 为 Vite + Vue3 |
| 操作系统 | Linux / Windows 均可 | SQLite 单文件，无外部数据库 |
| 建议资源 | 2 核 2G 起，磁盘随消息量增长 | 消息表 3 小时清理 + 定期 `VACUUM` |

启动参数注意两点（代码中已有约定）：

- **文件编码必须 UTF-8**：`-Dfile.encoding=UTF-8`，否则中文配置与日志乱码。
- **时区必须 GMT+8**：`pom.xml` 中固定 `user.timezone=GMT+8`，Cron 定时与签到判定依赖本地时间，容器部署需挂载 `TZ=Asia/Shanghai`。

---

## 四、后端配置

### 4.1 配置文件命名规则

后端通过 `--spring.profiles.active=run` 加载 `application-run.yaml`，它会在 `application.yml` 之上**覆盖**同名键。
因此只需要写"差异项"，不要复制整份 `application.yml`。

### 4.2 最小可用配置（`application-run.yaml`）

与 jar 包（或工作目录）同级存放：

```yaml
super:
  qid: 123456789          # 你的超管 QQ 号（必填，替换掉作者默认值）

server:
  port: 34740             # 与 Nginx / 前端代理保持一致

manage:
  bid: 123456789          # 数据来源 bot 号，通常等于 super.qid；生成 {bid}-manage.db
  key: 请替换为随机长字符串 # 必改，示例值不可用于生产

report:
  point-gid: 100000000    # 必填（无默认值），上报指令 #1 在该群生效
  bid: null               # 不需要每日上报则置 null
  gid: null

spring:
  datasource:
    url: jdbc:sqlite:data.db
```

### 4.3 完整可调项（按需启用）

```yaml
# 连接看门狗：解决协议端反向 WS 长时间无消息的静默假死
watchdog:
  enabled: true
  idle-timeout-seconds: 7200      # 空闲超过该秒数判定假死
  check-interval-seconds: 30      # 巡检间隔
  reconnect-timeout-seconds: 180  # 重连保护超时
  night-pause-start-hour: 23      # 该时段内不做假死判定
  night-pause-end-hour: 7

# 王者荣耀战绩（可选功能）
wzry:
  api: http://your-host:8080 #https://wv.kloping.top

# AI 联网检索的 MCP 服务（默认注释，不启用则不填）
top.kloping.ai.mcp:
  servers:
    WebSearch:
      server: https://dashscope.aliyuncs.com
      endpoint: /api/v1/mcps/WebSearch/sse
      token: sk-xxxx          # 自备，阿里百炼控制台获取
      heartbeat: 25
```

### 4.4 不建议改动的项

- `spring.ai.openai.api-key: placeholder`：仅用于满足 OpenAI 自动装配，真正的 Key 在数据库 `ai_conf` 表中按用户存储，**不要**把真实 Key 写进 yaml。
- `logging.*`：如需调整级别可改 `logging.level.root`，日志固定写入 `./logs/slf4j.log`（10MB 滚动、保留 7 份）。

---

## 五、首次启动与管理员初始化（关键步骤）

### 5.1 启动

```bash
# 方式一：releases 完整 jar
mkdir -p /opt/dg-bot && cd /opt/dg-bot
# 将 dg-bot-*.jar 与 application-run.yaml 放入当前目录
java -Dfile.encoding=UTF-8 -jar dg-bot-2.0.jar --spring.profiles.active=run
```

```bat
:: Windows
java -Dfile.encoding=UTF-8 -jar dg-bot-2.0.jar --spring.profiles.active=run
```

首次启动会自动建表（`DatabaseInitializer` 遍历 14 个 DAO 生成 `create table`，并做字段补齐迁移），无需手工导入 DDL。

> 仓库内自带的 `run.sh` / `run.bat` 走的是**开发模式**（`-classpath ./src/main.jar;./target/dependency/*`），依赖本地构建产物，且带 `git pull`，**不要**用于正式部署。

### 5.2 管理员账号的正确初始化方式

系统**没有**内置默认账号，登录链路是：`/bot/login` → 查 `auth_m` 表比对 `auth` 字段 → 签发 2 小时内存令牌（单点登录）。
而 `AuthM` 记录的正规生成时机是"该 QQ 号对应的 bot 上线时自动写入随机 UUID 密码"（`MiraiComponent#onBotOnline`），但**添加连接配置本身又需要登录**，形成先有鸡先有蛋。

因此首次部署必须手工写入一条记录：

```bash
# 1. 先启动一次，让表结构生成，然后停止进程
# 2. 生成未来 30 天的毫秒时间戳
EXP=$(python3 -c "import time;print(int((time.time()+30*86400)*1000))")
NOW=$(python3 -c "import time;print(int(time.time()*1000))")

sqlite3 data.db "insert into auth_m(qid,auth,exp,t0) values('123456789','YourStrongPass',$EXP,$NOW);"
```

表结构（与代码一致）：

```sql
create table auth_m (
  qid   VARCHAR(255) NOT NULL primary key,  -- QQ 号
  auth  VARCHAR(255) NOT NULL,              -- 登录密码/授权码（明文比对，NoOpPasswordEncoder）
  exp   BIGINT NOT NULL,                    -- 到期时间戳(ms)，过期会被强制下线
  t0    BIGINT NOT NULL                     -- 最近在线时间戳(ms)
);
```

随后访问：

```
http://<域名或IP>/bot/login?qid=123456789&p=YourStrongPass
```

成功后会写入 `dg-token` Cookie 并 302 跳转到 `/bot`（即前端管理页）。
`super.qid` 对应账号自动获得 `admin` 权限，可访问 `/api/m/**`（用户授权、到期时间修改）；普通账号只有 `user`。

> 安全提醒：`auth` 为明文比对，登录令牌仅存内存、有效期 2 小时，服务重启后需重新登录。请务必通过 Nginx 上 HTTPS，避免密码明文过网。

---

## 六、协议端接入（OneBot）

DG-Bot 通过 Overflow 以 **OneBot v11** 连接协议端，本身**不执行 QQ 登录**（无扫码、无滑块），账号登录完全在协议端完成。

### 6.1 两种连接模式

在后台"连接配置"新增一条记录（对应表 `conn_config`）：

| 字段 | 反向连接 (`reversed`) | 正向连接 (`ws`) |
|------|----------------------|-----------------|
| `qid` | bot 的 QQ 号 | 同左 |
| `type` | 填任意非 `ws` 值（如 `reversed`） | 必须填 `ws` |
| `ip` | 不需要，留空或占位 | **完整地址**，如 `ws://127.0.0.1:8080` |
| `port` | DG-Bot 监听端口，如 `5800` | 协议端端口（`ip` 已含端口时可填同名值） |
| `token` | 与协议端 access_token 一致 | 同左 |
| `heart` | 心跳检查秒数，建议 `30` | 同左 |

判定逻辑：`type.equalsIgnoreCase("ws")` 走正向（DG-Bot 主动连出），其余走反向（DG-Bot 监听端口，协议端连入）。

### 6.2 反向连接（推荐）

协议端（以 NapCat 为例）配置 WebSocket **客户端**：

```
URL:      ws://<DG-Bot 主机>:5800
Token:    与 conn_config.token 一致
格式:     OneBot v11
消息格式: array（对应 overflow.json 中 use_cq_code=false）
```

要点：

- 云服务器安全组/防火墙需放行该监听端口（如 5800）。
- 同一端口只允许一条 bot 连接，多个 bot 必须使用不同端口。
- 若协议端与后端同机，建议绑定内网，仅放行 Nginx 的 443/80。

### 6.3 正向连接

适用于协议端在内网、DG-Bot 在外网的场景：`ip` 直接填 `ws://host:port`，DG-Bot 启动时按 `conn_config` 顺序主动拨号（内部重试 3 次、间隔 7s）。

### 6.4 上线后的自动行为

- 首次上线自动写入 `auth_m`（随机 UUID 密码）、`exp = 当前 + 30 天`，后台需改为自定义密码。
- 到期账号每日 01:01 巡检并强制下线（`BaseComponent#auth`），需用超管账号在 `/api/m/modify` 延长时间。
- 已配置的连接配置会在每次启动时自动重连，无需手工点开。

---

## 七、前端部署

### 7.1 构建

```bash
git clone https://github.com/gdpl2112/dg-bot-front
cd dg-bot-front
npm install
npm run build      # 产物在 dist/
```

`npm run dev` 仅用于调试，其默认监听 **80** 端口，代理 `/api` 与 `/bot/login` 到 `http://127.0.0.1:34740/`。
若后端不在本机，需修改 `vite.config.ts` 中的 `baseUrl`；**该文件是前端唯一需要改的配置**，其余接口均为同源相对路径，无需 `.env`。

### 7.2 Nginx 参考配置

关键点：**`/bot/login`、`/bot/logout` 属于后端，`/bot` 及其子路由属于前端 SPA**，两者必须分开匹配，否则登录会 404。

```nginx
server {
    listen 80;
    server_name bot.example.com;
    # listen 443 ssl http2; ssl_certificate ...; ssl_certificate_key ...;

    root /var/www/dg-bot-front/dist;
    index index.html;

    # 后端接口
    location /api/ {
        proxy_pass http://127.0.0.1:34740;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;   # AI/点歌等长请求
    }

    # 登录/登出：必须精确匹配，交给后端
    location = /bot/login  { proxy_pass http://127.0.0.1:34740; proxy_set_header Host $host; }
    location = /bot/logout { proxy_pass http://127.0.0.1:34740; proxy_set_header Host $host; }

    # SPA 路由（/bot /conf /ai-conf /manage ...）
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

注意：

- 仓库内的 `nginx.conf` 是历史版本，包含 `/user`、`/cron-list` 等已迁到 `/api/**` 的旧路径，直接套用会得到 404，按上面配置为准。
- 不要把 `proxy_pass` 写成带 URI 的形式（`proxy_pass http://ip:port/;`），否则会截断 `/api` 前缀。
- 后端 Cookie `dg-token` 为 `HttpOnly` 但未置 `Secure`，启用 HTTPS 后建议在 Nginx 补 `proxy_cookie_path / "/; Secure; HttpOnly"`。

### 7.3 跨域说明

后端未配置任何 CORS（`DgMain` 上的 `@CrossOrigin` 对全局过滤链无效），因此：

- 生产：前端与后端**必须同源**（同一域名，靠 Nginx 分路径），不要拆成两个域名。
- 开发：只能用 `vite.config.ts` 的 proxy，不要直接跨域调后端。

---

## 八、运行时目录与数据文件

所有路径均**相对于进程工作目录**，务必固定工作目录（如 `/opt/dg-bot`）后再启动：

| 路径 | 来源 | 说明 |
|------|------|------|
| `data.db` | 配置 | 主库：授权、连接配置、定时、被动回复、AI 配置、统计等 14 张表 |
| `{bid}-manage.db` | `ManageDbService` | 每个 bot 一份群管理记录（踢人/禁言/审批），按访问自动创建 |
| `logs/slf4j.log` | logback | 应用日志，10MB 滚动、保留 7 份 |
| `bots/<qid>/logs/` | Mirai/Overflow | 单个 bot 的独立日志 |
| `conf/callApi/conf.json` | 可选 | 旧版 CallApi 插件的端口/密码/模板文件；新版模板存 `call_template` 表，可不创建 |
| `overflow.json` | 自动生成 | Overflow 全局配置（WS 地址、token、重试次数），无特殊需求不必手写 |

**备份策略**：`data.db`、`{bid}-manage.db`、`application-run.yaml` 三项即可完整恢复业务；`bots/` 与 `logs/` 可按需保留。
SQLite 备份请用 `sqlite3 data.db ".backup backup.db"`（热备），不要直接复制正在写入的文件。

---

## 九、可选能力的额外配置

| 能力 | 需要的配置 | 生效位置 |
|------|-----------|----------|
| AI 助手 | OpenAI 兼容的 `apiKey / baseUrl / modelId / 温度 / 人设` | 后台"AI 配置"，按 bot 账号存 `ai_conf` 表，改完即时清缓存生效 |
| AI 联网 | `top.kloping.ai.mcp.servers.WebSearch.token` 及对应 AI 配置中的 `network=true` | yaml + 后台 |
| 王者战绩 | `wzry.api` 指向可用的查询服务 | yaml |
| 点歌 | 无需 Key，但依赖公网接口（QQ音乐/酷狗/抖音聚合、`https://kloping.top/` 自建音乐服务） | 代码常量，不可配置；内网无外网出口时该功能不可用 |
| 短视频解析 | 依赖公网解析接口与抖音资源域名 | 同上 |
| 群管理记录页 | `manage.bid` + `manage.key`，访问 `/manage` 页并携带密钥 | yaml |
| 主动上报 | `report.point-gid`（必填）、`report.bid/gid`（可空） | yaml |

---

## 十、运维要点

### 10.1 systemd 示例

```ini
[Unit]
Description=DG-Bot
After=network-online.target

[Service]
Type=simple
WorkingDirectory=/opt/dg-bot
Environment="TZ=Asia/Shanghai"
ExecStart=/usr/bin/java -Dfile.encoding=UTF-8 -Xms512m -Xmx1g -jar /opt/dg-bot/dg-bot-2.0.jar --spring.profiles.active=run
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

停止时 `@PreDestroy` 会主动断开全部连接、关闭反向 WS 监听端口，属于优雅退出，建议 `TimeoutStopSec=30`。

### 10.2 升级

1. 停服 → 备份 `data.db` 与 `application-run.yaml`；
2. 替换 jar（配置文件与数据库留在原目录）；
3. 启动，`DatabaseInitializer` 会自动补齐新增字段，无需手工 ALTER。

### 10.3 源码编译

```bash
mvn clean package -DskipTests      # 产出 target/dg-bot-2.0.jar
```

`pom.xml` 已配置阿里云与 Maven Central 双仓库，内网环境需自备私服或离线仓库（仓库内 `plugin-libraries/` 只是插件依赖缓存，不能替代 Maven 本地库）。

---

## 十一、部署自检清单

- [ ] JDK 21 就位，`java -version` 输出 21.x
- [ ] `application-run.yaml` 中 `super.qid`、`manage.key`、`report.point-gid` 已填写且非作者默认值
- [ ] `auth_m` 已有超管记录，`/bot/login?qid=..&p=..` 能 302 跳转
- [ ] `data.db` 已在工作目录生成 14 张表
- [ ] 协议端已连上，后台 bot 列表显示"在线"
- [ ] `conn_config` 的 `type / ip / port / token / heart` 与协议端完全一致
- [ ] 前端 `dist/` 可访问，`/api/**` 与 `/bot/login` 反代正常，SPA 刷新不 404
- [ ] 安全组放行：Web 端口 + 反向 WS 端口（后者仅对协议端开放）
- [ ] HTTPS 已启用，`manage.key` 为随机长串
- [ ] 备份任务覆盖 `*.db`

### 常见故障对照

| 现象 | 根因 | 处理 |
|------|------|------|
| 启动报 `Could not resolve placeholder 'manage.key'` / `'report.point-gid'` | 必填项缺失（这两项无默认值） | 在 `application-run.yaml` 补齐 |
| 登录返回"请使用正确的登录链接或授权码/密码" | `auth_m` 无记录或密码不符/已过期 | 手工插入或核对 `auth`、`exp` |
| 登录成功但页面 404 | Nginx 把 `/bot` 全部反代给后端 | 按 7.2 精确匹配 `/bot/login` |
| bot 一直"离线" | 反向端口未放行 / token 不一致 / 协议端消息格式非 array | 查 `logs/slf4j.log` 与 `bots/<qid>/logs/` |
| 连接偶发假死 | 协议端长时间无事件 | 保持 `watchdog.enabled=true`，按需调 `idle-timeout-seconds` |
| 中文乱码 | 缺少 `-Dfile.encoding=UTF-8` 或系统时区非 +8 | 补启动参数与 `TZ` |
| 内网部署点歌/短视频报错 | 代码内固定公网接口，无法配置替换 | 提供外网出口或关闭对应可选功能 |
