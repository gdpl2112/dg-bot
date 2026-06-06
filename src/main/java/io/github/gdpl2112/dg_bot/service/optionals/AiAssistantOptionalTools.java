package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.dao.AllMessage;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.dto.VoteResponseDTO;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.gdpl2112.dg_bot.mapper.SaveMapper;
import io.github.gdpl2112.dg_bot.service.CronService;
import io.github.gdpl2112.dg_bot.utils.HttpsUtils;
import io.github.kloping.judge.Judge;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.*;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.Message;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.utils.ExternalResource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * <br/><strong>Created at 12:56<strong/>
 *
 * @author github kloping
 * @since 2026/5/7
 */
@AllArgsConstructor
@Slf4j
public class AiAssistantOptionalTools {
    private final CronMapper cronMapper;
    private final CronService cronService;
    private final SaveMapper saveMapper;

    /**
     * 机器人校验结果封装
     * 用于统一返回可用的 RemoteBot 或错误信息
     */
    private record BotResolveResult(RemoteBot remoteBot, String errorMessage, Bot bot) {
        private boolean success() {
            return remoteBot != null;
        }
    }

    /**
     * 校验机器人是否存在、在线且支持远程操作
     *
     * @param bid 机器人ID
     * @return 校验结果，成功时包含 RemoteBot，失败时包含错误信息
     */
    private static BotResolveResult resolveRemoteBot(Long bid) {
        if (bid == null) {
            return new BotResolveResult(null, "bot ID不能为空", null);
        }

        Bot bot = Bot.getInstanceOrNull(bid);
        // 机器人不存在时直接返回错误信息
        if (bot == null) {
            return new BotResolveResult(null, "机器人未找到", bot);
        }
        // 机器人离线时不可执行远程操作
        if (!bot.isOnline()) {
            return new BotResolveResult(null, "机器人未在线", bot);
        }
        // 仅支持 RemoteBot 的机器人实例
        if (!(bot instanceof RemoteBot remoteBot)) {
            return new BotResolveResult(null, "当前机器人不支持该操作", bot);
        }
        return new BotResolveResult(remoteBot, null, bot);
    }

    /**
     * set_group_special_title
     * {
     * "group_id": "123456",
     * "user_id": "123456789",
     * "special_title": "头衔"
     * }
     *
     * @param bid
     * @param groupId
     * @param userId
     * @param title
     * @return
     */
    @Tool(description = "【群管理】给某个群成员设置专属头衔(群主专属能力)；只改头衔，不改群名片。")
    public String set_group_special_title(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "目标群号") Long groupId,
            @ToolParam(description = "目标成员的QQ号") Long userId,
            @ToolParam(description = "头衔文字内容；传空字符串则清除该成员头衔") String title) {
        log.info("set_group_special_title: bid={}, groupId={}, userId={}, title={}", bid, groupId, userId, title);
        if (bid == null || groupId == null || userId == null || title == null) {
            return "missing params";
        }

        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        Group group = botResult.bot.getGroup(groupId);
        if (group == null) {
            return "group not found";
        }
        NormalMember member = group.get(userId);
        if (member == null) {
            return "member not found";
        }
        try {
            member.setSpecialTitle(title);
            return "ok";
        } catch (Exception e) {
            log.error("set_group_special_title error", e);
            return "failed:" + e.getMessage();
        }
    }

    /**
     * set_group_card
     * {
     * "group_id": "123456",
     * "user_id": "123456789",
     * "card": "新名片"
     * }
     *
     * @param bid
     * @param groupId
     * @param userId
     * @param card
     */
    @Tool(description = "【群管理】修改某个群成员在本群的群名片(群内昵称)；只改群名片，不改头衔。")
    public String set_group_card(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "目标群号") Long groupId,
            @ToolParam(description = "目标成员的QQ号") Long userId,
            @ToolParam(description = "新的群名片(群内昵称)；传空字符串则清除群名片") String card) {
        log.info("set_group_card: bid={}, groupId={}, userId={}, card={}", bid, groupId, userId, card);
        if (bid == null || groupId == null || userId == null || card == null) {
            return "missing params";
        }

        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        Group group = botResult.bot.getGroup(groupId);
        if (group == null) {
            return "group not found";
        }
        NormalMember member = group.get(userId);
        if (member == null) {
            return "member not found";
        }
        try {
            member.setNameCard(card);
            return "ok";
        } catch (Exception e) {
            log.error("set_group_card error", e);
            return "failed:" + e.getMessage();
        }
    }

    // 设置qq头像
    //{
    //  "file": "base64://..."
    //}
    @Tool(description = "【账号设置】将机器人最近收到的一张图片设为机器人自己的QQ头像；需用户先发送一张图片。")
    public String set_qq_avatar(@ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid) {
        log.info("set_qq_avatar: bid={}", bid);
        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();

        Image lastImage = AIAssistantOptional.getLastImage(bid);
        if (lastImage == null) {
            return "未找到最近发送的图片";
        }

        String url = Image.queryUrl(lastImage);
        if (url == null || url.isEmpty()) {
            return "获取图片URL失败";
        }

        try {
            byte[] bytes = HttpsUtils.readAsBytesFromImageUrl(url);
            String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
            JSONObject payload = new JSONObject();
            payload.put("file", "base64://" + base64);
            return remoteBot.executeAction("set_qq_avatar", payload.toJSONString());
        } catch (Exception e) {
            log.error("设置QQ头像失败", e);
            return "设置QQ头像失败: " + e.getMessage();
        }
    }


    @Tool(description = "【定时任务】查询本机器人已配置的全部定时(主动续火/定时发送)任务列表。")
    public String list_cron_tasks(@ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid) {
        log.info("list_cron_tasks: bid={}", bid);
        QueryWrapper<CronMessage> qw = new QueryWrapper<>();
        qw.eq("qid", String.valueOf(bid));
        java.util.List<CronMessage> list = cronMapper.selectList(qw);
        if (list == null || list.isEmpty()) {
            return "当前账号没有定时任务";
        }
        StringBuilder sb = new StringBuilder("定时任务列表：\n");
        for (CronMessage msg : list) {
            sb.append(String.format("ID: %s, 目标: %s, Cron: %s, 描述: %s, 内容: %s\n",
                    msg.getId(), msg.getTargetId(), msg.getCron(), msg.getDesc(), msg.getMsg()));
        }
        return sb.toString();
    }

    @Tool(description = "【定时任务】新增一个按 Cron 周期向指定目标发送消息的定时(主动续火)任务；禁止添加秒/分/小时级的高频循环任务。")
    public String add_cron_task(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "发送目标：群聊用 g+群号(如 g123456)，私聊用 u+QQ号(如 u123456)") String targetId,
            @ToolParam(description = "标准6位 Cron 表达式，如 0 0 12 * * ? 表示每天12点；禁止秒/分/小时级高频触发") String cron,
            @ToolParam(description = "任务用途的简短中文描述") String desc,
            @ToolParam(description = "到点要发送的消息文本") String msg) {
        log.info("add_cron_task: bid={}, targetId={}, cron={}, desc={}, msg={}", bid, targetId, cron, desc, msg);
        CronMessage cronMessage = new CronMessage();
        cronMessage.setQid(String.valueOf(bid));
        cronMessage.setCron(cron);
        cronMessage.setTargetId(targetId);
        cronMessage.setMsg(msg);
        cronMessage.setDesc(desc);

        int state = cronMapper.insert(cronMessage);
        if (state > 0) {
            QueryWrapper<CronMessage> qw = new QueryWrapper<>();
            qw.eq("qid", String.valueOf(bid))
                    .eq("cron", cron)
                    .eq("desc", desc)
                    .eq("target_id", targetId)
                    .eq("msg", msg);
            CronMessage savedMsg = cronMapper.selectOne(qw);
            if (savedMsg != null) {
                cronMessage.setId(savedMsg.getId());
                cronService.appendTask(cronMessage);
                return "添加成功，任务ID：" + savedMsg.getId();
            }
            return "添加成功，但未获取到新任务ID";
        }
        return "添加失败";
    }

    @Tool(description = "【定时任务】按任务ID删除一个已存在的定时任务；任务ID 可先用 list_cron_tasks 查询。")
    public String delete_cron_task(
            @ToolParam(description = "定时任务ID") String id) {
        log.info("delete_cron_task: id={}", id);
        try {
            cronService.del(id);
            return "删除成功，已移除任务ID：" + id;
        } catch (Exception e) {
            log.error("删除定时任务出错", e);
            return "删除失败: " + e.getMessage();
        }
    }

    //get_profile_like
    //{
    //  "user_id": "",
    //  "start": 0,
    //  "count": 10
    //}
    @Tool(description = "【名片点赞】查询本机器人名片收到的点赞(赞我)信息。")
    public String get_profile_like(@ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid) {
        log.info("get_profile_like: bid={}", bid);
        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();
        try {
            String result = remoteBot.executeAction("get_profile_like", "{\n" +
                    "  \"user_id\": \"\",\n" +
                    "  \"start\": 0,\n" +
                    "  \"count\": 10\n" +
                    "}");
            VoteResponseDTO responseDTO = VoteResponseDTO.parseFromJson(result);
            return JSON.toJSONString(responseDTO.toSimplifiedData());
        } catch (Exception e) {
            log.error("获取名片点赞信息出错", e);
            return "获取名片点赞信息出错: " + e.getMessage();
        }
    }

    //send_like
    //{
    //    "user_id": "123456,234567",
    //    "times": 10
    //}
    @Tool(description = "【名片点赞】给一个或多个QQ的名片点赞(赞别人)。")
    public String send_like(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "要点赞的QQ号，可多个用英文逗号分隔，如 123,456") String userIds,
            @ToolParam(description = "点赞次数：普通用户最多10，SVIP最多20") Integer times) {
        log.info("send_like: bid={}, userIds={}, times={}", bid, userIds, times);
        if (userIds == null || userIds.trim().isEmpty()) {
            return "QQID不能为空";
        }

        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();
        String[] userIdArray = userIds.split("[,，]");
        List<String> results = new ArrayList<>();
        for (String userIdText : userIdArray) {
            String trimmedUserId = userIdText.trim();
            if (trimmedUserId.isEmpty()) {
                continue;
            }
            try {
                Long.parseLong(trimmedUserId);
            } catch (NumberFormatException e) {
                results.add("QQID格式错误: " + trimmedUserId);
                continue;
            }
            try {
                String result = remoteBot.executeAction("send_like", "{\n" +
                        "    \"user_id\": \"" + trimmedUserId + "\",\n" +
                        "    \"times\": " + times + "\n" +
                        "}");
                results.add(trimmedUserId + ": " + result);
            } catch (Exception e) {
                log.error("给指定QQ名片点赞出错, userId={}", trimmedUserId, e);
                results.add(trimmedUserId + ": 给指定QQ名片点赞出错: " + e.getMessage());
            }
        }

        if (results.isEmpty()) {
            return "未找到有效的QQID";
        }
        return String.join("\n", results);
    }

    /**
     * 给指定好友发送消息
     *
     * @param bid      机器人ID
     * @param friendId 好友QQ号
     * @param message  消息内容
     * @return 发送结果
     */
    @Tool(description = "【发消息·私聊文本】给某个好友发送纯文本消息；只发文字。发图片用 send_image，发音乐用 send_music_card。")
    public String send_friend_message(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "接收消息的好友QQ号") Long friendId,
            @ToolParam(description = "要发送的纯文本内容") String message) {
        log.info("send_friend_message: bid={}, friendId={}, message={}", bid, friendId, message);
        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot == null) return "机器人未找到";
        if (!bot.isOnline()) return "机器人不在线";
        Friend friend = bot.getFriend(friendId);
        if (friend == null) return "好友未找到";
        MessageChain responseChain = DgSerializer.stringDeserializeToMessageChain(message, bot, friend);
        friend.sendMessage(responseChain);
        return "消息已发送至好友 " + friendId;
    }

    /**
     * 给指定群发送消息
     *
     * @param bid     机器人ID
     * @param groupId 群号
     * @param message 消息内容
     * @return 发送结果
     */
    @Tool(description = "【发消息·群聊文本】向某个群发送纯文本消息；只发文字。发图片用 send_image，发音乐用 send_music_card，发群公告用 send_group_notice。")
    public String send_group_message(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "目标群号") Long groupId,
            @ToolParam(description = "要发送的纯文本内容") String message) {
        log.info("send_group_message: bid={}, groupId={}, message={}", bid, groupId, message);
        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot == null) return "机器人未找到";
        if (!bot.isOnline()) return "机器人不在线";
        Group group = bot.getGroup(groupId);
        if (group == null) return "group not found";
        MessageChain responseChain = DgSerializer.stringDeserializeToMessageChain(message, bot, group);
        group.sendMessage(responseChain);
        return "已发送至 " + groupId;
    }

    /**
     * 群禁言，时长为0则解除禁言
     *
     * @param bid      机器人ID
     * @param groupId  群号
     * @param userId   成员QQ号
     * @param duration 禁言时长（秒），0表示解除禁言
     * @return 操作结果
     */
    @Tool(description = "【群管理】禁言或解除禁言某个群成员；duration>0 为禁言秒数，duration=0 表示解除禁言。")
    public String set_group_ban(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "目标群号") Long groupId,
            @ToolParam(description = "被禁言成员的QQ号") Long userId,
            @ToolParam(description = "禁言时长(秒)，0=解除禁言，最长 2592000(30天)") Integer duration) {
        log.info("set_group_ban: bid={}, groupId={}, userId={}, duration={}", bid, groupId, userId, duration);
        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();
        JSONObject payload = new JSONObject();
        payload.put("group_id", groupId);
        payload.put("user_id", userId);
        payload.put("duration", duration != null ? duration : 0);
        try {
            return remoteBot.executeAction("set_group_ban", payload.toJSONString());
        } catch (Exception e) {
            log.error("群禁言操作出错", e);
            return "群禁言操作出错: " + e.getMessage();
        }
    }

    /**
     * 发布群公告
     *
     * @param bid     机器人ID
     * @param groupId 群号
     * @param content 公告内容
     * @param image   图片（base64格式，可选）
     * @return 操作结果
     */
    @Tool(description = "【群管理】在某个群发布群公告(置顶通告)；区别于普通群消息 send_group_message。")
    public String send_group_notice(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "目标群号") Long groupId,
            @ToolParam(description = "公告正文文本") String content,
            @ToolParam(description = "可选：公告配图的 base64 字符串；无图则不传") String image) {
        log.info("send_group_notice: bid={}, groupId={}, content={}", bid, groupId, content);
        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();
        JSONObject payload = new JSONObject();
        payload.put("group_id", groupId);
        payload.put("content", content);
        // 图片参数可选，非空时才加入
        if (image != null && !image.trim().isEmpty()) {
            payload.put("image", image);
        }
        try {
            return remoteBot.executeAction("_send_group_notice", payload.toJSONString());
        } catch (Exception e) {
            log.error("发布群公告出错", e);
            return "发布群公告出错: " + e.getMessage();
        }
    }

    /**
     * 获取群荣誉信息
     *
     * @param bid     机器人ID
     * @param groupId 群号
     * @param type    荣誉类型（talkative/performer/legend/strong_newbie/emotion/all）
     * @return 群荣誉信息
     */
    @Tool(description = "【群信息】获取某个群的荣誉信息(群龙王/续火榜/连续发言等)。")
    public String get_group_honor_info(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "群号") Long groupId,
            @ToolParam(description = "荣誉类型：talkative=龙王 performer=群聊之火 legend=群聊炽焰 strong_newbie=冒尖小新 emotion=快乐源泉 all=全部(默认)") String type) {
        log.info("get_group_honor_info: bid={}, groupId={}, type={}", bid, groupId, type);
        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();
        JSONObject payload = new JSONObject();
        payload.put("group_id", groupId);
        payload.put("type", type != null ? type : "all");
        try {
            return remoteBot.executeAction("get_group_honor_info", payload.toJSONString());
        } catch (Exception e) {
            log.error("获取群荣誉信息出错", e);
            return "获取群荣誉信息出错: " + e.getMessage();
        }
    }

    /**
     * 群打卡签到
     *
     * @param bid     机器人ID
     * @param groupId 群号
     * @return 打卡结果
     */
    @Tool(description = "【群互动】在某个群执行打卡签到。")
    public String set_group_sign(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "群号") Long groupId) {
        log.info("set_group_sign: bid={}, groupId={}", bid, groupId);
        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();
        JSONObject payload = new JSONObject();
        payload.put("group_id", groupId);
        try {
            String result = remoteBot.executeAction("set_group_sign", payload.toJSONString());
            return "OK";
        } catch (Exception e) {
            log.error("群打卡签到出错", e);
            return "群打卡签到出错: " + e.getMessage();
        }
    }

    /**
     * 获取加入的群列表
     *
     * @param bid 机器人ID
     * @return 群列表信息
     */
    @Tool(description = "【群信息】获取本机器人已加入的群列表。")
    public String get_group_list(@ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid) {
        log.info("get_group_list: bid={}", bid);
        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();
        try {
            return remoteBot.executeAction("get_group_list", "{}");
        } catch (Exception e) {
            log.error("获取群列表出错", e);
            return "获取群列表出错: " + e.getMessage();
        }
    }

    /**
     * 发送指定图片或头像到群聊/私聊
     *
     * @param bid      机器人ID
     * @param id       type=group时为群号，type=user时为QQ号，type=url时为图片直链
     * @param type     图片类型：group=群头像，user=用户头像，url=图片直链
     * @param targetId 发送目标，g群号=群聊，u用户号=私信，例如 g123456 或 u123456
     * @return 发送结果
     */
    @Tool(description = "【发消息·图片】向群聊或私聊发送一张图片，图片来源可为群头像/用户头像/图片直链。")
    public String send_image(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "图片来源：type=group填群号(取群头像)，type=user填QQ号(取该用户头像)，type=url填图片直链URL") String id,
            @ToolParam(description = "图片来源类型，三选一：group=群头像 / user=用户头像 / url=图片直链") String type,
            @ToolParam(description = "发送目标：g+群号=群聊，u+QQ号=私聊，如 g123456 或 u123456") String targetId) {
        log.info("send_image: bid={}, id={}, type={}, targetId={}", bid, id, type, targetId);
        if (bid == null || id == null || type == null || targetId == null) {
            return "missing params";
        }

        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot == null) return "机器人未找到";
        if (!bot.isOnline()) return "机器人不在线";

        // 解析发送目标为具体联系人（群或好友）
        Contact contact;
        String prefix = targetId.substring(0, 1).toLowerCase();
        String idPart = targetId.substring(1);
        if ("g".equals(prefix)) {
            long groupId;
            try {
                groupId = Long.parseLong(idPart);
            } catch (NumberFormatException e) {
                return "targetId格式错误，群聊格式：g群号";
            }
            Group group = bot.getGroup(groupId);
            if (group == null) return "group not found: " + groupId;
            contact = group;
        } else if ("u".equals(prefix)) {
            long userId;
            try {
                userId = Long.parseLong(idPart);
            } catch (NumberFormatException e) {
                return "targetId格式错误，私信格式：u用户号";
            }
            Friend friend = bot.getFriend(userId);
            if (friend == null) return "好友未找到：" + userId;
            contact = friend;
        } else {
            return "targetId格式错误，请使用 g群号 或 u用户号";
        }

        // 根据type构造图片URL
        String url;
        if ("group".equalsIgnoreCase(type)) {
            // 群头像URL
            url = "https://p.qlogo.cn/gh/" + id + "/" + id + "/0";
        } else if ("user".equalsIgnoreCase(type)) {
            // 用户头像URL
            url = "https://q1.qlogo.cn/g?b=qq&nk=" + id + "&s=640";
        } else if ("url".equalsIgnoreCase(type)) {
            // 图片直链直接使用
            url = id;
        } else {
            return "type参数错误，支持：group, user, url";
        }

        try {
            byte[] bytes = HttpsUtils.readAsBytesFromImageUrl(url);
            if (bytes == null || bytes.length == 0) {
                return "图片下载失败：" + url;
            }
            // 上传图片资源并发送
            ExternalResource resource = ExternalResource.create(bytes);
            try {
                Image img = contact.uploadImage(resource);
                contact.sendMessage(img);
            } finally {
                resource.close();
            }
            return "图片已发送至 " + targetId;
        } catch (Exception e) {
            log.error("发送图片失败", e);
            return "发送图片失败: " + e.getMessage();
        }
    }

    /**
     * 搜索并发送音乐点歌卡片(MusicShare)到群聊/私聊
     *
     * @param bid      机器人ID
     * @param targetId 发送目标，g群号=群聊，u用户号=私聊，例如 g123456 或 u123456
     * @param songName 歌曲名称（建议附带歌手名以提高匹配准确度）
     * @param platform 音乐平台：wy=网易云(默认), qq=QQ音乐, kg=酷狗, dy=抖音
     * @param index    选择搜索结果中的第几首，从 1 开始，默认 1
     * @return 发送结果
     */
    @Tool(description = "【发消息·音乐】搜索歌曲并以可点击播放的音乐卡片形式发送到群聊或私聊。")
    public String send_music_card(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "发送目标：g+群号=群聊，u+QQ号=私聊，如 g123456 或 u123456") String targetId,
            @ToolParam(description = "歌曲名称，建议附带歌手名以提高匹配准确度") String songName,
            @ToolParam(description = "音乐平台，可选：wy=网易云(默认) / qq=QQ音乐 / kg=酷狗 / dy=抖音") String platform,
            @ToolParam(description = "取搜索结果第几首，从1开始，默认1") Integer index) {
        log.info("send_music_card: bid={}, targetId={}, songName={}, platform={}, index={}",
                bid, targetId, songName, platform, index);
        if (bid == null || targetId == null || songName == null || songName.trim().isEmpty()) {
            return "missing params";
        }

        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot == null) return "机器人未找到";
        if (!bot.isOnline()) return "机器人不在线";

        // 解析发送目标为具体联系人（群或好友）
        Contact contact;
        String prefix = targetId.substring(0, 1).toLowerCase();
        String idPart = targetId.substring(1);
        if ("g".equals(prefix)) {
            long groupId;
            try {
                groupId = Long.parseLong(idPart);
            } catch (NumberFormatException e) {
                return "targetId格式错误，群聊格式：g群号";
            }
            Group group = bot.getGroup(groupId);
            if (group == null) return "group not found: " + groupId;
            contact = group;
        } else if ("u".equals(prefix)) {
            long userId;
            try {
                userId = Long.parseLong(idPart);
            } catch (NumberFormatException e) {
                return "targetId格式错误，私信格式：u用户号";
            }
            Friend friend = bot.getFriend(userId);
            if (friend == null) return "好友未找到：" + userId;
            contact = friend;
        } else {
            return "targetId格式错误，请使用 g群号 或 u用户号";
        }

        // 平台兜底为网易云，并校验合法值
        String type = (platform == null || platform.trim().isEmpty())
                ? SongPoint.TYPE_WY
                : platform.trim().toLowerCase();
        if (!SongPoint.TYPE_WY.equals(type)
                && !SongPoint.TYPE_QQ.equals(type)
                && !SongPoint.TYPE_KUGOU.equals(type)
                && !SongPoint.TYPE_DY.equals(type)) {
            return "platform参数错误，支持：wy, qq, kg, dy";
        }

        try {
            Message message = SongPoint.pickAsCard(type, songName.trim(), index);
            if (message == null) {
                return "音乐卡片生成失败";
            }
            contact.sendMessage(message);
            return "已发送音乐卡片到 " + targetId;
        } catch (Exception e) {
            log.error("发送音乐卡片失败", e);
            return "发送音乐卡片失败: " + e.getMessage();
        }
    }

    /**
     * 获取指定群内指定成员的信息
     *
     * @param bid     机器人ID
     * @param groupId 群号
     * @param userId  成员QQ号
     * @return 成员信息
     */
    @Tool(description = "【群信息】获取某个群内指定成员的资料(昵称/群名片/角色/等级等)。")
    public String get_group_member_info(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "群号") Long groupId,
            @ToolParam(description = "成员QQ号") Long userId) {
        log.info("get_group_member_info: bid={}, groupId={}, userId={}", bid, groupId, userId);
        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();
        JSONObject payload = new JSONObject();
        payload.put("group_id", groupId);
        payload.put("user_id", userId);
        try {
            return remoteBot.executeAction("get_group_member_info", payload.toJSONString());
        } catch (Exception e) {
            log.error("获取群成员信息出错", e);
            return "获取群成员信息出错: " + e.getMessage();
        }
    }

    /**
     * 检索本bot相关的信息：聊天记录、群名、群成员昵称等
     *
     * @param bid     机器人ID
     * @param keyword 搜索关键词
     * @return 检索结果摘要
     */
    @Tool(description = "【信息检索】在本机器人所能接触到的范围内，搜索与关键词相关的信息：包括近期聊天记录、匹配的群名、群成员昵称等。仅限本bot可见范围。")
    public String search_bot_info(
            @ToolParam(description = "机器人自身的QQ号(bot ID)；必须使用系统提示中的 Robot ID，不要填其他人的QQ号") Long bid,
            @ToolParam(description = "搜索关键词，可以是人名/群名/话题/事件等任意文本") String keyword) {
        log.info("search_bot_info: bid={}, keyword={}", bid, keyword);
        if (bid == null || keyword == null || keyword.trim().isEmpty()) {
            return "missing params";
        }
        keyword = keyword.trim();

        Bot bot = Bot.getInstanceOrNull(bid);

        StringBuilder result = new StringBuilder();
        result.append("【检索结果】关键词：").append(keyword).append("\n\n");

        // ========== 1. 搜索聊天记录 ==========
        try {
            QueryWrapper<AllMessage> qw = new QueryWrapper<>();
            qw.eq("bot_id", bid)
                    .like("content", keyword)
                    .orderByDesc("time")
                    .last("LIMIT 20");
            List<AllMessage> messages = saveMapper.selectList(qw);

            if (messages != null && !messages.isEmpty()) {
                result.append("--- 近期聊天记录（最多20条）---\n");
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
                int count = 0;
                for (AllMessage msg : messages) {
                    if (++count > 20) break;
                    String timeStr = sdf.format(new Date(msg.getTime()));
                    String typeLabel = msg.getType() != null && msg.getType().contains("group") ? "群聊" : "私聊";
                    String contentPreview = extractTextPreview(msg.getContent(), 80, bot);
                    if (Judge.isEmpty(contentPreview)) continue;
                    result.append(String.format("[%s] %s | 发送者:%s | %s(%s)\n  → %s\n",
                            timeStr, typeLabel, msg.getSenderId(), msg.getFromId(), msg.getType(), contentPreview));
                }
                result.append("\n");
            } else {
                result.append("--- 聊天记录 ---\n未找到匹配的聊天记录\n\n");
            }
        } catch (Exception e) {
            log.warn("搜索聊天记录失败", e);
            result.append("--- 聊天记录 ---\n搜索失败: ").append(e.getMessage()).append("\n\n");
        }

        // ========== 2. 搜索群名和群成员昵称 ==========
        if (bot != null && bot.isOnline()) {
            try {
                result.append("--- 群名/群成员匹配 ---\n");
                boolean found = false;
                for (Group group : bot.getGroups()) {
                    // 群名匹配
                    String groupName = group.getName();
                    if (groupName != null && groupName.contains(keyword)) {
                        result.append(String.format("【群名匹配】群 %s(%d)\n", groupName, group.getId()));
                        found = true;
                    }

                    // 群成员昵称/名片匹配（仅检查前100个成员，避免耗时过长）
                    int memberChecked = 0;
                    for (Member member : group.getMembers()) {
                        if (++memberChecked > 100) break;
                        String nick = member.getNick();
                        String nameCard = member.getNameCard();
                        boolean matchNick = nick != null && nick.contains(keyword);
                        boolean matchCard = nameCard != null && nameCard.contains(keyword);
                        if (matchNick || matchCard) {
                            String displayName = (matchCard && !Judge.isEmpty(nameCard))
                                    ? nameCard + "(" + nick + ")"
                                    : nick;
                            result.append(String.format("【成员匹配】群 %s(%d) | QQ:%d | %s\n",
                                    groupName, group.getId(), member.getId(), displayName));
                            found = true;
                        }
                    }
                }
                if (!found) {
                    result.append("未找到匹配的群名或群成员\n");
                }
                result.append("\n");
            } catch (Exception e) {
                log.warn("搜索群信息失败", e);
                result.append("--- 群名/群成员 ---\n搜索失败: ").append(e.getMessage()).append("\n\n");
            }
        } else {
            result.append("--- 群名/群成员 ---\n机器人不在线，无法检索群信息\n\n");
        }

        return result.toString();
    }

    /**
     * 从消息链JSON内容中提取纯文本预览
     */
    private String extractTextPreview(String content, int maxLen, Bot bot) {
        if (content == null || content.isEmpty()) return "";
        try {
            if (bot != null) {
                MessageChain chain = DgSerializer.stringDeserializeToMessageChain(content, bot);
                if (chain != null) {
                    String text = DgSerializer.messageChainSerializeWithTextFirst(chain);
                    if (!Judge.isEmpty(text)) {
                        if (text.length() > maxLen) text = text.substring(0, maxLen) + "…";
                        return text;
                    }
                }
            }
            // fallback: 直接截取原始内容
            return content.length() > maxLen ? content.substring(0, maxLen) + "…" : content;
        } catch (Exception e) {
            return content.length() > maxLen ? content.substring(0, maxLen) + "…" : content;
        }
    }

}
