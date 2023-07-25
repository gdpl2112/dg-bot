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

禁止使用  exit()

```javascript

if (msg.startsWith("解析快手图集")) {
    if (context.getType() === "group") {
        var reg = /(https?|http|ftp|file):\/\/[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]/g;
        var urls = msg.match(reg)
        if (urls !== null) {
            context.send("正在解析...\n请稍等")
            var u0 = encodeURI(urls[0]);
            var arr = JSON.parse(context.requestGet("http://kloping.top/api/search/parseImgs?url=" + u0 + "&type=ks"))
            var builder = context.forwardBuilder();
            for (var i = 0; i < arr.length; i++) {
                var e = arr[i];
                builder.add(context.getBot().getId(), "AI", context.uploadImage(e))
            }
            context.send(builder.build())
        } else {
            context.send("未发现链接")
        }
    } else {
        context.send("命令仅适用群聊")
    }
}

//====================解析结束

if (msg.startsWith("酷狗点歌")) {
    var json = context.requestGet("http://kloping.top/api/search/song?keyword=" + msg.substring(4) + "&type=kugou");
    var jo = JSON.parse(json)
    if (jo.data.length > 0) {
        var sd = jo.data[0]
        context.send(context.createMusicShare("KugouMusic", sd.media_name, sd.author_name, "http://kloping.top", sd.imgUrl, sd.songUrl))
    } else {
        context.send("获取失败")
    }
}
//==============kugou点歌结束


if (msg.startsWith("网易点歌")) {
    var json = context.requestGet("http://kloping.top/api/search/song?keyword=" + msg.substring(4) + "&type=wy");
    var jo = JSON.parse(json)
    if (jo.data.length > 0) {
        var sd = jo.data[0]
        context.send(context.createMusicShare("NeteaseCloudMusic", sd.media_name, sd.author_name, "http://kloping.top", sd.imgUrl, sd.songUrl))
    } else {
        context.send("获取失败")
    }
}
//============网易点歌结束



```