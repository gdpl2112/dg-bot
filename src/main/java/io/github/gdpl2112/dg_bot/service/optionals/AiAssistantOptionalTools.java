package io.github.gdpl2112.dg_bot.service.optionals;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import io.github.gdpl2112.dg_bot.dto.VoteResponseDTO;
import io.github.gdpl2112.dg_bot.mapper.CronMapper;
import io.github.gdpl2112.dg_bot.service.CronService;
import io.github.gdpl2112.dg_bot.utils.HttpsUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.utils.ExternalResource;
import net.mamoe.mirai.message.data.Image;
import net.mamoe.mirai.message.data.Message;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import top.mrxiaom.overflow.contact.RemoteBot;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
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

    /**
     * 机器人校验结果封装
     * 用于统一返回可用的 RemoteBot 或错误信息
     */
    private record BotResolveResult(RemoteBot remoteBot, String errorMessage) {
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
            return new BotResolveResult(null, "bot ID不能为空");
        }

        Bot bot = Bot.getInstanceOrNull(bid);
        // 机器人不存在时直接返回错误信息
        if (bot == null) {
            return new BotResolveResult(null, "机器人未找到");
        }
        // 机器人离线时不可执行远程操作
        if (!bot.isOnline()) {
            return new BotResolveResult(null, "机器人未在线");
        }
        // 仅支持 RemoteBot 的机器人实例
        if (!(bot instanceof RemoteBot remoteBot)) {
            return new BotResolveResult(null, "当前机器人不支持该操作");
        }
        return new BotResolveResult(remoteBot, null);
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
    @Tool(description = "设置群头衔")
    public String set_group_special_title(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "GroupID") Long groupId,
            @ToolParam(description = "QQID") Long userId,
            @ToolParam(description = "群头衔内容") String title) {
        log.info("set_group_special_title: bid={}, groupId={}, userId={}, title={}", bid, groupId, userId, title);
        if (bid == null || groupId == null || userId == null || title == null) {
            return "参数不能为空";
        }

        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();

        JSONObject payload = new JSONObject();
        payload.put("group_id", groupId);
        payload.put("user_id", userId);
        payload.put("special_title", title);
        return remoteBot.executeAction("set_group_special_title", payload.toJSONString());
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
    @Tool(description = "设置群名片")
    public String set_group_card(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "GroupID") Long groupId,
            @ToolParam(description = "QQID") Long userId,
            @ToolParam(description = "群名片内容") String card) {
        log.info("set_group_card: bid={}, groupId={}, userId={}, card={}", bid, groupId, userId, card);
        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();
        JSONObject payload = new JSONObject();
        payload.put("group_id", groupId);
        payload.put("user_id", userId);
        payload.put("card", card);
        return remoteBot.executeAction("set_group_card", payload.toJSONString());
    }

    // 设置qq头像
    //{
    //  "file": "base64://..."
    //}
    @Tool(description = "设置QQ头像,为最近发的一个图片")
    public String set_qq_avatar(@ToolParam(description = "bot ID") Long bid) {
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


    @Tool(description = "查看此账号的定时(主动续火)任务列表")
    public String list_cron_tasks(@ToolParam(description = "bot ID") Long bid) {
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

    @Tool(description = "添加定时(主动续火)任务,禁止添加秒分时级的循环任务")
    public String add_cron_task(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "目标ID（如群号加前缀 g123456，私聊加前缀 u123456）") String targetId,
            @ToolParam(description = "Cron 表达式（如 0 0 12 * * ?）") String cron,
            @ToolParam(description = "任务中文描述") String desc,
            @ToolParam(description = "要发送的消息内容") String msg) {
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

    @Tool(description = "删除定时(主动续火)任务")
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
    @Tool(description = "获取名片点赞信息")
    public String get_profile_like(@ToolParam(description = "bot ID") Long bid) {
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
    @Tool(description = "给指定QQ名片点赞")
    public String send_like(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "QQID,可多个用英文逗号分隔") String userIds,
            @ToolParam(description = "点赞次数,一般10次 svip20次") Integer times) {
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
    @Tool(description = "给指定好友发送消息")
    public String send_friend_message(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "好友QQ号") Long friendId,
            @ToolParam(description = "消息内容") String message) {
        log.info("send_friend_message: bid={}, friendId={}, message={}", bid, friendId, message);
        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot == null) return "机器人未找到";
        if (!bot.isOnline()) return "机器人不在线";
        Friend friend = bot.getFriend(friendId);
        if (friend == null) return "好友未找到";
        friend.sendMessage(message);
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
    @Tool(description = "给指定群发送消息")
    public String send_group_message(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "群号") Long groupId,
            @ToolParam(description = "消息内容") String message) {
        log.info("send_group_message: bid={}, groupId={}, message={}", bid, groupId, message);
        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot == null) return "机器人未找到";
        if (!bot.isOnline()) return "机器人不在线";
        Group group = bot.getGroup(groupId);
        if (group == null) return "群未找到";
        group.sendMessage(message);
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
    @Tool(description = "群禁言，时长为0则解除禁言")
    public String set_group_ban(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "群号") Long groupId,
            @ToolParam(description = "成员QQ号") Long userId,
            @ToolParam(description = "禁言时长（秒），0表示解除禁言") Integer duration) {
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
    @Tool(description = "发布群公告")
    public String _send_group_notice(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "群号") Long groupId,
            @ToolParam(description = "公告内容") String content,
            @ToolParam(description = "图片base64（可选，无图片则不传）") String image) {
        log.info("_send_group_notice: bid={}, groupId={}, content={}", bid, groupId, content);
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
    @Tool(description = "获取群荣誉信息(群龙王发言最多,续火,连续发言)")
    public String get_group_honor_info(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "群号") Long groupId,
            @ToolParam(description = "荣誉类型：talkative/performer/legend/strong_newbie/emotion/all") String type) {
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
    @Tool(description = "群打卡签到")
    public String set_group_sign(
            @ToolParam(description = "bot ID") Long bid,
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
    @Tool(description = "获取加入的群列表")
    public String get_group_list(@ToolParam(description = "bot ID") Long bid) {
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
    @Tool(description = "发送指定图片或头像到群聊/私聊，type=group时id为群号(群头像)，type=user时id为QQ号(用户头像)，type=url时id为图片直链，targetId格式：g群号=群聊 u用户号=私信")
    public String send_image(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "图片来源：type=group时为群号，type=user时为QQ号，type=url时为图片直链") String id,
            @ToolParam(description = "类型：group=群头像, user=用户头像, url=图片直链") String type,
            @ToolParam(description = "发送目标，g群号=发到群聊，u用户号=发到私信，例如 g123456 或 u123456") String targetId) {
        log.info("send_image: bid={}, id={}, type={}, targetId={}", bid, id, type, targetId);
        if (bid == null || id == null || type == null || targetId == null) {
            return "参数不能为空";
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
            if (group == null) return "群未找到：" + groupId;
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
    @Tool(description = "搜索并发送音乐点歌卡片(MusicShare)到群聊/私聊。targetId格式：g群号=群聊 u用户号=私信；platform可选 wy=网易云(默认)/qq=QQ音乐/kg=酷狗/dy=抖音；index默认1表示第一首")
    public String send_music_card(
            @ToolParam(description = "bot ID") Long bid,
            @ToolParam(description = "发送目标，g群号=发到群聊，u用户号=发到私信，例如 g123456 或 u123456") String targetId,
            @ToolParam(description = "歌曲名称（建议附带歌手名以提高匹配准确度）") String songName,
            @ToolParam(description = "音乐平台：wy=网易云(默认), qq=QQ音乐, kg=酷狗, dy=抖音") String platform,
            @ToolParam(description = "选择搜索结果中的第几首，从1开始，默认为1") Integer index) {
        log.info("send_music_card: bid={}, targetId={}, songName={}, platform={}, index={}",
                bid, targetId, songName, platform, index);
        if (bid == null || targetId == null || songName == null || songName.trim().isEmpty()) {
            return "参数不能为空";
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
            if (group == null) return "群未找到：" + groupId;
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
    @Tool(description = "获取指定群内指定成员的信息")
    public String get_group_member_info(
            @ToolParam(description = "bot ID") Long bid,
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

}
