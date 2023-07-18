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

corn 

启动加载 load

主动添加 add

主动删除 delete

bot.html

        ICON
    id:
    name: 

    info1   info2
        |   |
    _____   _____

    manager   cron   reply
    but        but     but



manager.html
    
    ------
    icon id      del
    ______
    icon id      del
    _____
    ...
    add

    group
    _____
    icon id     swi0 swi1
    _____
    icon id     swi0 swi1
    _____
    ....

cron.html
    
    list
    ____
    cron:msg: target      del
    ____
    cron:msg: target      del
    ____
    ....
    add

reply.tml
    
    
