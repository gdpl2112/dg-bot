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
import net.mamoe.mirai.contact.Friend;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.message.data.Image;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import top.mrxiaom.overflow.contact.RemoteBot;

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
            return new BotResolveResult(null, "BID不能为空");
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
            @ToolParam(description = "BID") Long bid,
            @ToolParam(description = "GroupID") Long groupId,
            @ToolParam(description = "QQID") Long userId,
            @ToolParam(description = "群头衔内容") String title) {
        if (bid == null || groupId == null || userId == null || title == null) {
            return "参数不能为空";
        }

        BotResolveResult botResult = resolveRemoteBot(bid);
        if (!botResult.success()) {
            return botResult.errorMessage();
        }
        RemoteBot remoteBot = botResult.remoteBot();

        String payload = "{\"group_id\":\"" + groupId + "\",\"user_id\":\"" + userId
                + "\",\"special_title\":\"" + title.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        return remoteBot.executeAction("set_group_special_title", payload);
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
            @ToolParam(description = "BID") Long bid,
            @ToolParam(description = "GroupID") Long groupId,
            @ToolParam(description = "QQID") Long userId,
            @ToolParam(description = "群名片内容") String card) {
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
    public String set_qq_avatar(@ToolParam(description = "BID") Long bid) {
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
    public String list_cron_tasks(@ToolParam(description = "BID") Long bid) {
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
            @ToolParam(description = "BID") Long bid,
            @ToolParam(description = "目标ID（如群号加前缀 g123456，私聊加前缀 u123456）") String targetId,
            @ToolParam(description = "Cron 表达式（如 0 0 12 * * ?）") String cron,
            @ToolParam(description = "任务中文描述") String desc,
            @ToolParam(description = "要发送的消息内容") String msg) {
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
    public String get_profile_like(@ToolParam(description = "BID") Long bid) {
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
            @ToolParam(description = "BID") Long bid,
            @ToolParam(description = "QQID,可多个用英文逗号分隔") String userIds,
            @ToolParam(description = "点赞次数,一般10次 svip20次") Integer times) {
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
            @ToolParam(description = "BID") Long bid,
            @ToolParam(description = "好友QQ号") Long friendId,
            @ToolParam(description = "消息内容") String message) {
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
            @ToolParam(description = "BID") Long bid,
            @ToolParam(description = "群号") Long groupId,
            @ToolParam(description = "消息内容") String message) {
        Bot bot = Bot.getInstanceOrNull(bid);
        if (bot == null) return "机器人未找到";
        if (!bot.isOnline()) return "机器人不在线";
        Group group = bot.getGroup(groupId);
        if (group == null) return "群未找到";
        group.sendMessage(message);
        return "消息已发送至群 " + groupId;
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
            @ToolParam(description = "BID") Long bid,
            @ToolParam(description = "群号") Long groupId,
            @ToolParam(description = "成员QQ号") Long userId) {
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
