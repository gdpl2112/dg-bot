## js 脚本文档

```
每次指定事件会触发执行脚本
脚本执行会预置变量 以辅助完成脚本功能
```

### 一般消息事件friend/group

预置变量`context`对象类型为[ScriptContext](src/main/java/io/github/gdpl2112/dg_bot/service/script/ScriptContext.java)
对象内方法可用 type 为 friend/group

预设变量`utils`对象类型为[ScriptUtils](src/main/java/io/github/gdpl2112/dg_bot/service/script/ScriptUtils.java)

### 其他事件



#### 群成员

##### 成员列表变更

- 成员已经加入群: MemberJoinEvent
    - 成员被邀请加入群: Invite
    - 成员主动加入群: Active

- 成员已经离开群: MemberLeaveEvent
    - 成员被踢出群: Kick
    - 成员主动离开群: Quit

- 一个账号请求加入群: MemberJoinRequestEvent
- 机器人被邀请加入群: BotInvitedJoinGroupRequestEvent

##### 名片和头衔

- 成员群名片改动: MemberCardChangeEvent
- 成员群特殊头衔改动: MemberSpecialTitleChangeEvent
- 成员群荣誉改变: MemberHonorChangeEvent

##### 成员权限

- 成员权限改变: MemberPermissionChangeEvent

##### 动作

- 群成员被禁言: MemberMuteEvent
- 群成员被取消禁言: MemberUnmuteEvent

### 好友

- 好友昵称改变: FriendRemarkChangeEvent
- 成功添加了一个新好友: FriendAddEvent
- 好友已被删除: FriendDeleteEvent
- 一个账号请求添加机器人为好友: NewFriendRequestEvent
- 好友头像改变: FriendAvatarChangedEvent
- 好友昵称改变: FriendNickChangedEvent
- 好友输入状态改变: FriendInputStatusChangedEvent

