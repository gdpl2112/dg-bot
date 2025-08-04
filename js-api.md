### 更新 v1.1

> time 2025/08/04

##### 基础配置模版

```js
// 基础配置模版

// 所有消息事件入口
// msg string类型
// event java事件对象
// utils 工具
function onMsgEvent(msg, event, utils) {

}

// 所有机器人事件入口
// event java事件对象
// utils 工具
function onBotEvent(event, utils) {

}

// 被点赞事件入口
// event 包含字段 selfId,operatorId,times
// utils 工具
// bot 对象
function onProfileLike(event, utils, bot) {

}

// 点赞发送事件入口
// event 包含事件 selfId,operatorId,times ## Boolean ok; 是否成功 失败时原因 可能 不是好友 请求失败 或 点赞上限
// 其余同上

function onSendLiked(event, utils, bot) {

}

// 群签到事件入口
// event 字段 gid,selfId #Boolean ok;
function onGroupSign(event, utils, bot) {

}
```

- utils 方法列表 见 [ScriptUtils](src/main/java/io/github/gdpl2112/dg_bot/service/script/ScriptUtils.java)
- onMsgEvent 参数 event 对象
  见 [MessageEvent](https://github.com/mamoe/mirai/blob/dev/mirai-core-api/src/commonMain/kotlin/event/events/MessageEvent.kt#L49)
- onBotEvent 中 event 列表

<details>
<summary>事件列表</summary>

```text
AbstractMessageEvent
Achieve (MemberHonorChangeEvent 内)
Active (BotJoinGroupEvent 内)
Active (BotLeaveEvent 内)
Active (BotOfflineEvent 内)
Active (MemberJoinEvent 内)
AutoLoginEvent
BaseGroupMemberInfoChangeEvent
BeforeImageUploadEvent
BeforeShortVideoUploadEvent
BotActiveEvent
BotAvatarChangedEvent
BotEvent
BotGroupPermissionChangeEvent
BotInvitedJoinGroupRequestEvent
BotJoinGroupEvent
BotLeaveEvent
BotMuteEvent
BotNickChangedEvent
BotOfflineEvent
BotOnlineEvent
BotPassiveEvent
BotReloginEvent
BotUnmuteEvent
Deleted (StrangerRelationChangeEvent 内)
Disband (BotLeaveEvent 内)
Dropped (BotOfflineEvent 内)
Failed (ImageUploadEvent 内)
Failed (ShortVideoUploadEvent 内)
Failure (AutoLoginEvent 内)
Force (BotOfflineEvent 内)
FriendAddEvent
FriendAvatarChangedEvent
FriendDeleteEvent
FriendEvent
FriendInfoChangeEvent
FriendInputStatusChangedEvent
FriendNickChangedEvent
FriendRecall (MessageRecallEvent 内)
FriendRemarkChangeEvent
Friended (StrangerRelationChangeEvent 内)
GroupAllowAnonymousChatEvent
GroupAllowConfessTalkEvent
GroupAllowMemberInviteEvent
GroupAwareMessageEvent
GroupEntranceAnnouncementChangeEvent
GroupEvent
GroupMemberEvent
GroupMemberInfoChangeEvent
GroupMuteAllEvent
GroupNameChangeEvent
GroupOperableEvent
GroupRecall (MessageRecallEvent 内)
GroupSettingChangeEvent
GroupTalkativeChangeEvent
ImageUploadEvent
Invite (BotJoinGroupEvent 内)
Invite (MemberJoinEvent 内)
Kick (BotLeaveEvent 内)
Kick (MemberLeaveEvent 内)
Lose (MemberHonorChangeEvent 内)
MemberCardChangeEvent
MemberHonorChangeEvent
MemberJoinEvent
MemberJoinRequestEvent
MemberLeaveEvent
MemberMuteEvent
MemberPermissionChangeEvent
MemberSpecialTitleChangeEvent
MemberUnmuteEvent
MessageEvent
MessagePostSendEvent
MessagePreSendEvent
MessageReactionEvent
MessageRecallEvent
MessageSyncEvent
MsfOffline (BotOfflineEvent 内)
NewFriendRequestEvent
NudgeEvent
OtherClientEvent
OtherClientMessageEvent
OtherClientOfflineEvent
OtherClientOnlineEvent
Quit (MemberLeaveEvent 内)
RequireReconnect (BotOfflineEvent 内)
Retrieve (BotJoinGroupEvent 内)
Retrieve (MemberJoinEvent 内)
ShortVideoUploadEvent
SignEvent
StrangerAddEvent
StrangerEvent
StrangerMessageEvent
StrangerMessagePostSendEvent
StrangerMessagePreSendEvent
StrangerMessageSyncEvent
StrangerRelationChangeEvent
Succeed (ImageUploadEvent 内)
Succeed (ShortVideoUploadEvent 内)
Success (AutoLoginEvent 内)
TempMessageEvent
TempMessagePostSendEvent
TempMessagePreSendEvent
UserEvent
UserMessageEvent
UserMessagePostSendEvent
UserMessagePreSendEvent
```

</details>


#### 判断消息类型 方法

```js
var GroupMessageEvent = Java.type("net.mamoe.mirai.event.events.GroupMessageEvent")
var FriendMessageEvent = Java.type("net.mamoe.mirai.event.events.FriendMessageEvent")


function onMsgEvent(msg, event, utils) {
    if (msg === "test") {
        if (event instanceof GroupMessageEvent) {
            var strCls = Java.type("java.lang.String")
            var sMsg = strCls.format("群消息 测试成功 <at:%s>", event.getSender().getId());
            var m0 = utils.deSerialize(sMsg)
            event.getSubject().sendMessage(m0)
        } else if (event instanceof FriendMessageEvent) {
            event.getSubject().sendMessage("好友消息 测试成功")
        } else {
            event.getSubject().sendMessage(event.class.name + ".消息测试成功")
        }
    }
}
```