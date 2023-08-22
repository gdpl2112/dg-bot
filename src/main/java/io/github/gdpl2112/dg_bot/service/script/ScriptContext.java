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
     * 所处环境 <a href="https://github.com/gdpl2112/dg-bot/blob/master/js-api.md"> 说明</a>
     *
     * @return
     */
    String getType();
}
