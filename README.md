## 代挂系统 多Bot 管理项目

功能预计

- 不支持添加插件 # 节省内存 避免多bot数据混乱
- 定时消息发送 #主动续火
- 简单的关键词回复 #被动续火
- 撤回监听 #功能1
- 更多功能请pr

web 结构

index 介绍 及 开通方式

login 指定qq与token 登录 #scurity auth

manager 管理自己的qq

~~admin 管理员平台~~


自定义脚本

脚本 预设变量 [context](src/main/java/io/github/gdpl2112/dg_bot/service/script/ScriptContext.java)

```javascript

function handleUrl(s) {
    var reg = /(https?|http|ftp|file):\/\/[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]/g;
    s = s.match(reg);
    return (s)
}

if (msg.startsWith("解析快手图集")) {
    if (context.getType() !== "group") {
        context.send("命令仅适用群聊")
        exit(0)
    }
    var urls = handleUrl(msg)
    if (urls === null) {
        context.send("未发现链接")
        exit(0)
    }
    context.send("正在解析...\n请稍等")
    var u0 = encodeURI(urls[0]);
    var url = "http://kloping.top/api/search/parseImgs?url=" + u0 + "&type=ks"
    var json = context.get(url);
    var arr = JSON.parse(json)
    var builder = context.forwardBuilder();
    for (var i = 0; i < arr.length; i++) {
        var e = arr[i];
        builder.append(context.getSender(), context.uploadImage(e))
    }
    context.send(builder.build())
}


```