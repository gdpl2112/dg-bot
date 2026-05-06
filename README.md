# DG-Bot — 多 Bot 代挂管理系统

基于 [Mirai](https://github.com/mamoe/mirai) + [Overflow](https://github.com/MrXiaoM/Overflow) 的 QQ 机器人代挂管理平台，支持多 Bot 同时运行、Web 后台管理、自定义脚本扩展。

> 在线体验：[https://dg.kloping.top](https://dg.kloping.top)  
> 前端仓库：[dg-bot-front](https://github.com/gdpl2112/dg-bot-front)

---

## 功能特性

- **多 Bot 管理** — 单实例运行多个 QQ 机器人，统一后台管理
- **定时消息** — 支持 Cron 表达式，主动续火、定时推送
- **关键词回复** — 自定义触发词与回复内容，支持正则匹配
- **API 调用** — 通过指令调用外部 API，灵活扩展（兼容 [MiraiCallApiPlugin](https://github.com/gdpl2112/MiraiCallApiPlugin)）
- **自定义脚本 (JS)** — 编写 JavaScript 脚本处理消息、Bot 事件、点赞/签到等
- **事件监听** — 撤回监听、群签到、资料卡点赞等
- **点歌** — 支持网易云 / QQ / 酷狗 / 抖音多平台点歌
- **Web 后台** — 基于 Spring Security 的权限管理，管理员/用户分离

---

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 21+ |
| Maven | 3.6+ |
| 协议端 | [OpenShamrock](https://github.com/whitechi73/OpenShamrock) 等 OneBot 实现 |

---

## 快速启动

启动前请先安装好 jdk11+,npm,maven(非必须) 系统环境

### 1. 克隆项目

```bash
git clone https://github.com/gdpl2112/dg-bot-front
git clone https://github.com/gdpl2112/dg-bot.git
cd dg-bot
```

### 2. 配置数据库
项目使用 SQLite，无需额外安装数据库。首次启动会自动建表。

### 3. 编译打包

```bash
mvn clean package -DskipTests
```

### 3.5 前端项目启动(必须) 

- 若不启动前端项目无法进行后续操作,前端为 vue3 项目 在拉取前端仓库后
```bash
git clone https://github.com/gdpl2112/dg-bot-front
npm install
npm run dev ## 仅用于调试 
# 生产环境建议 npm run build 之后 配置在nginx运行
```

### 4. 启动
1. 从 [releases](https://github.com/gdpl2112/dg-bot/releases) 下载编译后的完整jar包

2. 在jar同级目录下创建配置文件 application-run.yaml
    ```yaml
    super:
      qid: 3474006766 # 超级管理
    ```
    为qq号 若不配置 则无法进行配置后续链接

3. 使用 ``java -jar ***.jar --spring.profiles.active=run`` 即可运行


启动后访问 `前端页面` 进入管理后台。

### 5. 连接协议端

需要自行搭建qq登录端 后配置链接协议 连接到该项目地址

在后台添加 Bot 时，需填写 OneBot 反向 WebSocket 地址（如 `ws://127.0.0.1:5800`），确保协议端正向连接到 DG-Bot。

---

## 文档

- [用户使用手册](userManual/README.md)
- [API 调用表达式](expression.md)
- [JS 脚本 API](js-api.md)

---

## 项目结构

```
dg-bot/
├── built/          # 消息序列化、脚本编译、API 调用引擎
├── compile/        # 编译信息
├── controllers/    # Web API 控制器
├── dao/            # 数据实体
├── mapper/         # MyBatis-Plus Mapper
├── security/       # Spring Security 认证
├── service/
│   ├── listenerhosts/  # 消息/事件监听器
│   ├── optionals/      # 可选功能（点歌等）
│   └── script/         # JS 脚本引擎
└── resources/      # 配置文件
```

---

## License

MIT