package io.github.gdpl2112.dg_bot.service.script;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;
import net.mamoe.mirai.message.data.*;

/**
 * script 交互对象
 * 预设脚本环境变量: context
 *
 * @author github.kloping
 */
public interface ScriptContext {
    /**
     * 获取bot
     *
     * @return
     */
    Bot getBot();

    /**
     * 发送字符串
     *
     * @param str
     */
    void send(String str);

    /**
     * 发送至所在环境
     *
     * @param message
     */
    void send(Message message);

    /**
     * 获得一个 Builder
     *
     * @return
     */
    MessageChainBuilder builder();

    /**
     * 获得一个 转发行
     *
     * @return
     */
    ForwardMessageBuilder forwardBuilder();

    /**
     * 创建音乐分享消息
     *
     * @param kind
     * @param title
     * @param summer
     * @param jumUrl
     * @param picUrl
     * @param url
     * @return
     */
    MusicShare createMusicShare(String kind, String title, String summer, String jumUrl, String picUrl, String url);

    /**
     * 发送者ID
     *
     * @return
     */
    User getSender();

    /**
     * 发送环境id 一般为 群id
     *
     * @return
     */
    Contact getSubject();

    /**
     * 上传图片
     *
     * @param url
     * @return
     */
    Image uploadImage(String url);

    /**
     * 构建 文本
     *
     * @param text
     * @return
     */
    PlainText newPlainText(String text);

    /**
     * 所处环境
     * <table>
     *   <thead>
     *     <tr>
     *       <th colspan="2">type: 对应事件</th>
     *     </tr>
     *   </thead>
     *   <tbody>
     *     <tr>
     *       <td>group</td>
     *       <td>群消息</td>
     *     </tr>
     *     <tr>
     *       <td>friend</td>
     *       <td>好友消息</td>
     *     </tr>
     *   </tbody>
     * </table>
     * <h1>注: 以上两种type context 以此文档展示</h1>
     * <h1> 以下type context 仅能获取type 并添加 新变量 event对象 以实际对象结构为主</h1>
     * <h5 tabindex="-1" dir="auto"><a id="user-content-成员列表变更" class="anchor" aria-hidden="true"
     *                                     href="#成员列表变更">
     *         <svg class="octicon octicon-link" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true">
     *             <path d="m7.775 3.275 1.25-1.25a3.5 3.5 0 1 1 4.95 4.95l-2.5 2.5a3.5 3.5 0 0 1-4.95 0 .751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018 1.998 1.998 0 0 0 2.83 0l2.5-2.5a2.002 2.002 0 0 0-2.83-2.83l-1.25 1.25a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042Zm-4.69 9.64a1.998 1.998 0 0 0 2.83 0l1.25-1.25a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042l-1.25 1.25a3.5 3.5 0 1 1-4.95-4.95l2.5-2.5a3.5 3.5 0 0 1 4.95 0 .751.751 0 0 1-.018 1.042.751.751 0 0 1-1.042.018 1.998 1.998 0 0 0-2.83 0l-2.5 2.5a1.998 1.998 0 0 0 0 2.83Z"></path>
     *         </svg>
     *     </a>成员列表变更
     *     </h5>
     *     <ul dir="auto">
     *         <li>
     *             <p dir="auto">成员已经加入群: MemberJoinEvent</p>
     *             <ul dir="auto">
     *                 <li>成员被邀请加入群: Invite</li>
     *                 <li>成员主动加入群: Active</li>
     *             </ul>
     *         </li>
     *         <li>
     *             <p dir="auto">成员已经离开群: MemberLeaveEvent</p>
     *             <ul dir="auto">
     *                 <li>成员被踢出群: Kick</li>
     *                 <li>成员主动离开群: Quit</li>
     *             </ul>
     *         </li>
     *         <li>
     *             <p dir="auto">一个账号请求加入群: MemberJoinRequestEvent</p>
     *         </li>
     *         <li>
     *             <p dir="auto">机器人被邀请加入群: BotInvitedJoinGroupRequestEvent</p>
     *         </li>
     *     </ul>
     *     <h5 tabindex="-1" dir="auto"><a id="user-content-名片和头衔" class="anchor" aria-hidden="true" href="#名片和头衔">
     *         <svg class="octicon octicon-link" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true">
     *             <path d="m7.775 3.275 1.25-1.25a3.5 3.5 0 1 1 4.95 4.95l-2.5 2.5a3.5 3.5 0 0 1-4.95 0 .751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018 1.998 1.998 0 0 0 2.83 0l2.5-2.5a2.002 2.002 0 0 0-2.83-2.83l-1.25 1.25a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042Zm-4.69 9.64a1.998 1.998 0 0 0 2.83 0l1.25-1.25a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042l-1.25 1.25a3.5 3.5 0 1 1-4.95-4.95l2.5-2.5a3.5 3.5 0 0 1 4.95 0 .751.751 0 0 1-.018 1.042.751.751 0 0 1-1.042.018 1.998 1.998 0 0 0-2.83 0l-2.5 2.5a1.998 1.998 0 0 0 0 2.83Z"></path>
     *         </svg>
     *     </a>名片和头衔
     *     </h5>
     *     <ul dir="auto">
     *         <li>成员群名片改动: MemberCardChangeEvent</li>
     *         <li>成员群特殊头衔改动: MemberSpecialTitleChangeEvent</li>
     *         <li>成员群荣誉改变: MemberHonorChangeEvent</li>
     *     </ul>
     *     <h5 tabindex="-1" dir="auto"><a id="user-content-成员权限" class="anchor" aria-hidden="true" href="#成员权限">
     *         <svg class="octicon octicon-link" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true">
     *             <path d="m7.775 3.275 1.25-1.25a3.5 3.5 0 1 1 4.95 4.95l-2.5 2.5a3.5 3.5 0 0 1-4.95 0 .751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018 1.998 1.998 0 0 0 2.83 0l2.5-2.5a2.002 2.002 0 0 0-2.83-2.83l-1.25 1.25a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042Zm-4.69 9.64a1.998 1.998 0 0 0 2.83 0l1.25-1.25a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042l-1.25 1.25a3.5 3.5 0 1 1-4.95-4.95l2.5-2.5a3.5 3.5 0 0 1 4.95 0 .751.751 0 0 1-.018 1.042.751.751 0 0 1-1.042.018 1.998 1.998 0 0 0-2.83 0l-2.5 2.5a1.998 1.998 0 0 0 0 2.83Z"></path>
     *         </svg>
     *     </a>成员权限
     *     </h5>
     *     <ul dir="auto">
     *         <li>成员权限改变: MemberPermissionChangeEvent</li>
     *     </ul>
     *     <h5 tabindex="-1" dir="auto"><a id="user-content-动作" class="anchor" aria-hidden="true" href="#动作">
     *         <svg class="octicon octicon-link" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true">
     *             <path d="m7.775 3.275 1.25-1.25a3.5 3.5 0 1 1 4.95 4.95l-2.5 2.5a3.5 3.5 0 0 1-4.95 0 .751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018 1.998 1.998 0 0 0 2.83 0l2.5-2.5a2.002 2.002 0 0 0-2.83-2.83l-1.25 1.25a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042Zm-4.69 9.64a1.998 1.998 0 0 0 2.83 0l1.25-1.25a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042l-1.25 1.25a3.5 3.5 0 1 1-4.95-4.95l2.5-2.5a3.5 3.5 0 0 1 4.95 0 .751.751 0 0 1-.018 1.042.751.751 0 0 1-1.042.018 1.998 1.998 0 0 0-2.83 0l-2.5 2.5a1.998 1.998 0 0 0 0 2.83Z"></path>
     *         </svg>
     *     </a>动作
     *     </h5>
     *     <ul dir="auto">
     *         <li>群成员被禁言: MemberMuteEvent</li>
     *         <li>群成员被取消禁言: MemberUnmuteEvent</li>
     *     </ul>
     *     <h3 tabindex="-1" dir="auto"><a id="user-content-好友" class="anchor" aria-hidden="true" href="#好友">
     *         <svg class="octicon octicon-link" viewBox="0 0 16 16" version="1.1" width="16" height="16" aria-hidden="true">
     *             <path d="m7.775 3.275 1.25-1.25a3.5 3.5 0 1 1 4.95 4.95l-2.5 2.5a3.5 3.5 0 0 1-4.95 0 .751.751 0 0 1 .018-1.042.751.751 0 0 1 1.042-.018 1.998 1.998 0 0 0 2.83 0l2.5-2.5a2.002 2.002 0 0 0-2.83-2.83l-1.25 1.25a.751.751 0 0 1-1.042-.018.751.751 0 0 1-.018-1.042Zm-4.69 9.64a1.998 1.998 0 0 0 2.83 0l1.25-1.25a.751.751 0 0 1 1.042.018.751.751 0 0 1 .018 1.042l-1.25 1.25a3.5 3.5 0 1 1-4.95-4.95l2.5-2.5a3.5 3.5 0 0 1 4.95 0 .751.751 0 0 1-.018 1.042.751.751 0 0 1-1.042.018 1.998 1.998 0 0 0-2.83 0l-2.5 2.5a1.998 1.998 0 0 0 0 2.83Z"></path>
     *         </svg>
     *     </a>好友
     *     </h3>
     *     <ul dir="auto">
     *         <li>好友昵称改变: FriendRemarkChangeEvent</li>
     *         <li>成功添加了一个新好友: FriendAddEvent</li>
     *         <li>好友已被删除: FriendDeleteEvent</li>
     *         <li>一个账号请求添加机器人为好友: NewFriendRequestEvent</li>
     *         <li>好友头像改变: FriendAvatarChangedEvent</li>
     *         <li>好友昵称改变: FriendNickChangedEvent</li>
     *         <li>好友输入状态改变: FriendInputStatusChangedEvent</li>
     *     </ul>
     * @return
     */
    String getType();
}
