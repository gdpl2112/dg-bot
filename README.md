## 代挂系统 多Bot 管理项目

功能预计

- 不支持添加插件 # 节省内存 避免多bot数据混乱
- 定时消息发送 #主动续火
- 简单的关键词回复 #被动续火
- 撤回监听 #功能1
- 更多功能请pr

## [用户使用手册](userManual/README.md)

### 内置功能: [API调用](expression.md)

- 功能相同于: [自定义 调用 API 插件](https://github.com/gdpl2112/MiraiCallApiPlugin)

### 自定义脚本

脚本 预设变量 [context](src/main/java/io/github/gdpl2112/dg_bot/service/script/ScriptContext.java)

禁止使用 exit()

### **_[js脚本文档](./js-api.md)_**

[更多脚本](https://github.com/gdpl2112/dg-script)

## 示例

```javascript


if (msg.startsWith("@1234")) {
    if (context.getType() === "group") {
        context.send("你好")
    } else {
        context.send("命令仅适用群聊")
    }
}


```
