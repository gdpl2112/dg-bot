# JS 用户脚本开发文档

本系统允许每个 Bot（按 QQ 号区分）编写一段 JavaScript 脚本，实现自定义功能：
消息回复、事件响应、定时任务、HTTP 请求、数据存储等。

- 相关源码：`ScriptService.java`、`ScriptManager.java`、`ScriptCompile.java`、`BaseScriptUtils.java`
- 简易模板：`js-api.md`

---

## 1. 工作原理

1. 每个 Bot 的脚本代码保存在 `conf` 表的 `code` 字段（主键为 Bot 的 QQ 号 `qid`）。
2. 保存代码后，系统会清除该 Bot 的编译缓存，下次事件触发时重新编译执行。
3. 脚本由 Java 内置的 **Nashorn JavaScript 引擎** 执行，因此脚本里可以调用 Java 类（见 `utils.newObject`、`Java.type`）。
4. 系统通过正则提取脚本中的**函数名**，只有存在对应入口函数时才调用（无该函数则跳过，不报错）。
5. 所有脚本调用都是**异步执行**的，脚本报错不会影响 Bot 正常运行。

### 函数名识别规则

系统用正则匹配以下三种写法，只有匹配到才会认为函数存在：

```js
// 1. 普通函数声明（推荐）
function onMsgEvent(msg, event) { }

// 2. 函数表达式
var onMsgEvent = function (msg, event) { }

// 3. 箭头函数
var onMsgEvent = (msg, event) => { }
```

> 建议统一使用第 1 种 `function 名字(...) { }` 写法。

---

## 2. 快速开始

1. 在后台/接口中保存脚本代码，例如调用：
   ```
   POST /api/code-modify
   code = <你的JS代码>
   ```
2. 确认目标群/好友开启了脚本开关（`group_conf.k0`，即"API/脚本"开关）。若该会话配置了 `k0=false`，消息/事件不会进入脚本。
3. 触发事件验证，日志见 `/api/get-log`，报错见 `/api/get-exception`。

---

## 3. 脚本全局变量

脚本编译时，以下变量会被注入到全局作用域：

| 变量 | 类型 | 说明 |
|------|------|------|
| `utils` | ScriptUtils | 系统提供的工具对象（HTTP、消息序列化、变量、SQL 等） |
| `bot` | Mirai `Bot` | 当前 Bot 实例，可调用 Mirai 接口（发消息、查群/好友等） |
| `log` / `logger` | Logger | 打印日志，可用 `log("xxx")` 或 `log("%s %d", "a", 1)` 格式化 |

示例：

```js
function onMsgEvent(msg, event) {
    log("收到消息: %s", msg);
}
```

日志最多保留最近 30 条，通过 `/api/get-log` 查看。

---

## 4. 事件入口函数

在脚本中定义以下名字的函数，即可接入对应事件。

| 函数名 | 触发时机 | 参数 |
|--------|----------|------|
| `onMsgEvent(msg, event)` | 收到群/好友/陌生人消息 | `msg`：序列化后的消息字符串；`event`：Mirai `MessageEvent` 对象 |
| `onBotEvent(event)` | 收到任意 Bot 事件（消息类、上下线类除外） | `event`：Mirai 事件对象 |
| `onProfileLike(event)` | 自己的 QQ 被点赞 | `event`：`ProfileLikeEvent` |
| `onSendLiked(event)` | 自己给他人点赞完成 | `event`：`SendLikedEvent` |
| `onGroupSign(event)` | 群打卡/签到成功 | `event`：`GroupSignEvent` |

### 4.1 onMsgEvent(msg, event)

- `msg`：消息的序列化字符串，纯文本原样输出，图片/@/表情等会被转成特殊标记（见第 6 节）。
- `event`：`net.mamoe.mirai.event.events.MessageEvent`。
  - 群消息是 `GroupMessageEvent`，好友消息是 `FriendMessageEvent`。
  - 常用方法：
    - `event.getSubject()`：当前会话（群 `Group` 或好友 `Friend`），可 `sendMessage(...)` 回复
    - `event.getSender()`：发送者（群内为 `Member`，好友为 `Friend`）
    - `event.getBot()`：Bot 实例
    - `event.getTime()`：消息时间
- 系统发送消息、接收消息回执等内部事件（`MessagePreSendEvent`/`MessagePostSendEvent`）不会进入该函数。

```js
function onMsgEvent(msg, event) {
    if (msg === "hello") {
        event.getSubject().sendMessage("Hello!");
    }
}
```

### 4.2 onBotEvent(event)

覆盖几乎所有 Mirai Bot 事件（消息事件、`BotOnlineEvent`/`BotOfflineEvent` 被排除），
例如进群、退群、禁言、戳一戳、好友申请等。可用 `event.getClass().getSimpleName()` 判断事件类型，
再调用具体事件的方法。

```js
function onBotEvent(event) {
    var MemberMuteEvent = Java.type("net.mamoe.mirai.event.events.MemberMuteEvent");
    if (event instanceof MemberMuteEvent) {
        log("成员 %s 被禁言 %s 秒", event.getMember().getId(), event.getDurationSeconds());
    }
}
```

完整事件类列表见 `js-api.md` 或 Mirai 文档。

### 4.3 onProfileLike(event) —— 被点赞

| 字段 | 类型 | 说明 |
|------|------|------|
| `selfId` | Long | 自己（Bot）QQ 号 |
| `operatorId` | Long | 点赞的人 |
| `times` | Integer | 次数 |

```js
function onProfileLike(event) {
    log("被 %s 点赞 %s 次", event.getOperatorId(), event.getTimes());
    // 回赞示例
    var Friend = Java.type("net.mamoe.mirai.contact.Friend");
    var f = bot.getFriend(event.getOperatorId());
    if (f != null) f.sendMessage("谢谢点赞~");
}
```

### 4.4 onSendLiked(event) —— 点赞完成

| 字段 | 类型 | 说明 |
|------|------|------|
| `selfId` | Long | 自己（Bot）QQ 号 |
| `operatorId` | Long | 被点赞的人 |
| `times` | Integer | 次数 |
| `ok` | Boolean | 是否成功；失败原因可能是：不是好友、请求失败、点赞上限 |

### 4.5 onGroupSign(event) —— 群签到成功

| 字段 | 类型 | 说明 |
|------|------|------|
| `gid` | Long | 群号 |
| `selfId` | Long | 自己（Bot）QQ 号 |
| `userId` | Long | 签到成员 QQ |
| `ok` | Boolean | 是否成功 |

---

## 5. utils 工具方法

### 5.1 HTTP 请求

| 方法 | 说明 |
|------|------|
| `utils.requestGet(url)` | GET 请求，返回响应字符串 |
| `utils.requestPost(url, data)` | POST 请求，`data` 为请求体，返回响应字符串 |

```js
function onMsgEvent(msg, event) {
    if (msg === "天气") {
        var res = utils.requestGet("https://example.com/api/weather?city=shanghai");
        event.getSubject().sendMessage("天气：" + res);
    }
}
```

### 5.2 消息序列化

| 方法 | 说明 |
|------|------|
| `utils.serialize(chain)` | 把 Mirai `MessageChain` 转成系统字符串格式 |
| `utils.deSerialize(str)` | 把系统字符串格式转回 `MessageChain`，可直接 `sendMessage` |
| `utils.queryUrlFromId(imageId)` | 通过图片 ID 查询图片 URL |

```js
// 构造"@某人 + 文字"并发送
function onMsgEvent(msg, event) {
    var s = "回复成功 <at:" + event.getSender().getId() + ">";
    event.getSubject().sendMessage(utils.deSerialize(s));
}
```

### 5.3 内存变量（按 Bot 账号隔离）

脚本变量保存在内存中，同一 Bot 的所有脚本共享，重启后清空。

| 方法 | 说明 |
|------|------|
| `utils.set(name, value)` | 设置变量，返回旧值 |
| `utils.get(name)` | 获取变量，不存在返回 null |
| `utils.del(name)` | 删除变量，返回被删的值 |
| `utils.clear()` | 清空当前 Bot 所有变量，返回清除个数 |
| `utils.list()` | 列出所有变量（键值对列表） |

```js
function onMsgEvent(msg, event) {
    if (msg === "打卡") {
        var n = utils.get("signCount");
        n = (n == null ? 0 : n) + 1;
        utils.set("signCount", n);
        event.getSubject().sendMessage("今日已打卡 " + n + " 次");
    }
}
```

### 5.4 创建 Java 对象

| 方法 | 说明 |
|------|------|
| `utils.newObject(className, ...args)` | 按全类名创建 Java 对象，如 `utils.newObject("java.util.HashMap")` |

```js
var map = utils.newObject("java.util.HashMap");
map.put("k", "v");
```

### 5.5 数据库（每个 Bot 独立 SQLite 库）

每个 Bot 有自己独立的 SQLite 数据库文件 `user-<botQQ号>-db.db`，可自由建表读写。

| 方法 | 说明 |
|------|------|
| `utils.executeSql(sql)` | 执行任意一句 SQL（建表/增删改），返回 `Boolean` |
| `utils.executeSelectList(sql)` | 查询，返回对象列表（每行一个 JSON 对象）；无结果返回 null |
| `utils.executeSelectOne(sql)` | 查询，只返回第一行对象；无结果返回 null |

```js
// 先建表（可在脚本里做幂等处理）
utils.executeSql("CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY AUTOINCREMENT, content TEXT)");

function onMsgEvent(msg, event) {
    if (msg.indexOf("记笔记 ") === 0) {
        var content = msg.substring(4);
        utils.executeSql("INSERT INTO notes(content) VALUES('" + content + "')");
        event.getSubject().sendMessage("已记录");
    }
    if (msg === "查笔记") {
        var list = utils.executeSelectList("SELECT * FROM notes");
        if (list == null) {
            event.getSubject().sendMessage("暂无笔记");
        } else {
            event.getSubject().sendMessage(JSON.stringify(list));
        }
    }
}
```

---

## 6. 消息序列化格式

`onMsgEvent` 的 `msg` 与 `utils.serialize` / `utils.deSerialize` 使用的格式：

| 内容 | 格式 |
|------|------|
| 纯文本 | 原样输出（含 `<`、`>` 的文本会被转义为 `\<`、`\>`） |
| 图片 | `<pic:图片ID>` |
| @某人 | `<at:QQ号>` |
| 表情 | `<face:表情ID>` |
| 语音 | `<audio:...>` |
| 音乐 | `<music:...>` |
| Mirai 表情 | `[mirai:face:数字]` |
| Mirai 图片 | `[mirai:image:图片ID]` |

因此可以用字符串拼接构造带图片、@ 的回复：

```js
// 发送"@XX 请看图" + 一张图片
var s = "<at:123456> 请看图 <pic:图片ID>";
event.getSubject().sendMessage(utils.deSerialize(s));
```

---

## 7. 定时任务调用脚本函数

定时任务配置（`cron_message` 表）中：

- `cron`：cron 表达式
- `targetId`：目标。以 `FUNCTION` 或 `function` 结尾时，表示**调用脚本函数**
- `msg`：当 `targetId` 以 `FUNCTION` 结尾时，`msg` 就是要调用的**函数名**；否则 `msg` 为要发送的消息内容

```text
cron       : 0 0 9 * * ?
targetId   : 123456789FUNCTION   # 调用脚本函数
msg        : dailyReport         # 脚本里定义的函数名
```

脚本中定义：

```js
function dailyReport() {
    var group = bot.getGroup(群号);
    if (group != null) group.sendMessage("每日报告：今日一切正常");
}
```

> 定时任务执行时会检查 Bot 是否在线，掉线会直接上报失败，不执行脚本。

---

## 8. 日志与报错

| 接口 | 说明 |
|------|------|
| `GET /api/get-log` | 查看脚本日志（`log(...)` 输出），最多 30 行 |
| `GET /api/get-exception` | 查看最近一次脚本报错；无报错返回"未发现报错" |

脚本初始化（编译）失败也会记录到报错接口，例如语法错误会提示"初始化JS脚本时报错"。

---

## 9. 完整示例

```js
// 一个综合示例：关键词回复 + 计数 + @回复 + 定时函数
var GroupMessageEvent = Java.type("net.mamoe.mirai.event.events.GroupMessageEvent");
var FriendMessageEvent = Java.type("net.mamoe.mirai.event.events.FriendMessageEvent");

function onMsgEvent(msg, event) {
    // 回复消息
    if (msg === "ping") {
        event.getSubject().sendMessage("pong");
        return;
    }

    // @回复
    if (msg === "hi") {
        var s = "<at:" + event.getSender().getId() + "> 你好呀";
        event.getSubject().sendMessage(utils.deSerialize(s));
        return;
    }

    // 计数
    if (msg === "次数") {
        var n = utils.get("count");
        n = (n == null ? 0 : n) + 1;
        utils.set("count", n);
        event.getSubject().sendMessage("这是第 " + n + " 次触发");
    }
}

function onProfileLike(event) {
    log("被 %s 点赞 %s 次", event.getOperatorId(), event.getTimes());
}

// 每天 9 点执行（配合 targetId 以 FUNCTION 结尾的定时任务）
function dailyReport() {
    var group = bot.getGroup(123456789);
    if (group != null) group.sendMessage("早安，新的一天！");
}
```

---

## 10. 注意事项

1. **函数必须能被正则识别**：使用 `function 名字()` 声明，避免用 `let`/`const` 箭头函数（当前仅识别 `var x = () =>` 写法）。
2. **事件是异步的**：脚本在后台线程执行，不要依赖事件之间的执行顺序。
3. **开关**：会话的 `k0` 开关关闭时，该会话的消息不会进入脚本。
4. **变量在内存中**：重启后丢失；需要持久化的数据请用 SQLite（第 5.5 节）。
5. **每个 Bot 相互隔离**：变量、数据库均按 Bot QQ 号隔离。
6. **脚本报错不影响 Bot**：报错会记录到 `/api/get-exception`。
7. **不要在脚本里做耗时过长的操作**：避免阻塞 Bot 的异步任务线程池。
