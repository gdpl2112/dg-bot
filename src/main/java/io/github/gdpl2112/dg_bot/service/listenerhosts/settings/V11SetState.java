package io.github.gdpl2112.dg_bot.service.listenerhosts.settings;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.service.v11s.V11AutoLikeService;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.contact.User;

import java.util.List;

/**
 *
 * create on 08:42
 *
 * @author github kloping
 * @since 2025/9/23
 */
public class V11SetState implements BotState {
    private final Bot bot;
    private final V11AutoLikeService service;

    public V11SetState(Bot bot, V11AutoLikeService service) {
        this.bot = bot;
        this.service = service;
    }

    @Override
    public String getName() {
        return "V11设置";
    }

    private V11Conf v11Conf = null;

    @Override
    public String getWelcomeMessage() {
        v11Conf = service.getV11Conf(String.valueOf(bot.getId()));
        StringBuilder sb = new StringBuilder("输入序号设置对应选项");
        sb.append("\n 0. 退出");
        sb.append("\n 1. 自动回赞今日: ").append(v11Conf.getAutoLike() ? "是✅" : "否❌");
        sb.append("\n 2. 自动回赞昨日: ").append(v11Conf.getAutoLikeYesterday() ? "是✅" : "否❌");
        sb.append("\n 3. 需要满赞才回赞: ").append(v11Conf.getNeedMaxLike() ? "是✅" : "否❌");
        sb.append("\n 4. 自动空间点赞: ").append(v11Conf.getAutoZoneLike() ? "是✅" : "否❌");
        sb.append("\n 5. 空间赞轮询: ").append(v11Conf.getZoneEvl()).append(" 分钟/次");
        sb.append("\n 6. 点赞黑名单: ").append(v11Conf.getLikeBlackIds().size()).append("个");
        sb.append("\n 7. 点赞白名单: ").append(v11Conf.getLikeWhiteIds().size()).append("个");
        sb.append("\n 8. 自动群打卡: ").append(v11Conf.getSignGroupIds().size()).append("个");
        sb.append("\n 9. 空间自动评论: ").append(v11Conf.getZoneComment().length()).append("字");
        sb.append("\n 10. 空间自动访问: ").append(v11Conf.getZoneWalksIds().size()).append("个");
        return sb.toString();
    }

    private int setState = 0;

    @Override
    public String handleInput(User user, String input, Context context) {
        if (setState > 0) {
            return handleStateInput(input);
        } else {
            return handleMainMenuInput(input);
        }
    }

    private String handleStateInput(String input) {
        switch (setState) {
            case 5:
                return handleZoneEvlInput(input);
            case 6:
                return handleBlackListInput(input);
            case 7:
                return handleWhiteListInput(input);
            case 8:
                return handleSignGroupsInput(input);
            case 9:
                return handleZoneCommentInput(input);
            case 10:
                return handleZoneWalksInput(input);
            default:
                return "异常选项!";
        }
    }

    private String handleMainMenuInput(String input) {
        try {
            Integer i = Integer.parseInt(input);
            switch (i) {
                case 1:
                    toggleAutoLike();
                    return getWelcomeMessage();
                case 2:
                    toggleAutoLikeYesterday();
                    return getWelcomeMessage();
                case 3:
                    toggleNeedMaxLike();
                    return getWelcomeMessage();
                case 4:
                    toggleAutoZoneLike();
                    return getWelcomeMessage();
                case 5:
                    setState = 5;
                    return "请输入空间点赞轮询间隔(分钟) 取值: 1-30" +
                            "\ntips:频率越快 评论(点赞)越及时 反之亦然" +
                            "\n提示,若设置频率过快可能导致被检测有封号风险";
                case 6:
                    setState = 6;
                    return getShowBlacks();
                case 7:
                    setState = 7;
                    return getShowWhites();
                case 8:
                    setState = 8;
                    return getShowSignGroups();
                case 9:
                    setState = 9;
                    return "请输入要自动评论的内容\n输入1表示清空\n当前内容:" + v11Conf.getZoneComment();
                case 10:
                    setState = 10;
                    return getShowQzoneWalks();
                default:
                    return "无效输入!";
            }
        } catch (NumberFormatException e) {
            return "无效输入!";
        }
    }

    private void toggleAutoLike() {
        service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                .set(V11Conf::getAutoLike, !v11Conf.getAutoLike()));
    }

    private void toggleAutoLikeYesterday() {
        service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                .set(V11Conf::getAutoLikeYesterday, !v11Conf.getAutoLikeYesterday()));
    }

    private void toggleNeedMaxLike() {
        service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                .set(V11Conf::getNeedMaxLike, !v11Conf.getNeedMaxLike()));
    }

    private void toggleAutoZoneLike() {
        service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                .set(V11Conf::getAutoZoneLike, !v11Conf.getAutoZoneLike()));
    }

    private String handleZoneEvlInput(String input) {
        try {
            Integer m = Integer.parseInt(input);
            if (m >= 1 && m <= 60) {
                service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                        .set(V11Conf::getZoneEvl, m));
                setState = 0;
                return getWelcomeMessage();
            } else return "取值一般在1-60之间";
        } catch (NumberFormatException e) {
            return "请输入正确的数字!";
        }
    }

    private String handleBlackListInput(String input) {
        try {
            Integer v = Integer.valueOf(input);
            if (v >= 1 && v <= v11Conf.getLikeBlackIds().size() + 1) {
                if (v == 1) {
                    setState = 0;
                    return getWelcomeMessage();
                } else {
                    int n = v - 2;
                    List<Long> likeBlackIds = v11Conf.getLikeBlackIds();
                    likeBlackIds.remove(n);
                    updateBlackList(likeBlackIds);
                }
            }
            return getShowBlacks();
        } catch (NumberFormatException e) {
            // 不是序号，尝试作为QQ号处理
        }

        try {
            Long l = Long.valueOf(input);
            if (!v11Conf.getLikeBlackIds().contains(l)) {
                List<Long> list = v11Conf.getLikeBlackIds();
                list.add(l);
                updateBlackList(list);
            }
            return getShowBlacks();
        } catch (NumberFormatException e) {
            return "无效输入!";
        }
    }

    private String handleWhiteListInput(String input) {
        try {
            Integer v = Integer.valueOf(input);
            if (v >= 1 && v <= v11Conf.getLikeWhiteIds().size() + 1) {
                if (v == 1) {
                    setState = 0;
                    return getWelcomeMessage();
                } else {
                    int n = v - 2;
                    List<Long> likeWhiteIds = v11Conf.getLikeWhiteIds();
                    likeWhiteIds.remove(n);
                    updateWhiteList(likeWhiteIds);
                }
            }
            return getShowWhites();
        } catch (NumberFormatException e) {
            // 不是序号，尝试作为QQ号处理
        }

        try {
            Long l = Long.valueOf(input);
            if (!v11Conf.getLikeWhiteIds().contains(l)) {
                List<Long> list = v11Conf.getLikeWhiteIds();
                list.add(l);
                updateWhiteList(list);
            }
            return getShowWhites();
        } catch (NumberFormatException e) {
            return "无效输入!";
        }
    }

    private String handleZoneWalksInput(String input) {
        try {
            Integer v = Integer.parseInt(input);
            if (v >= 1 && v <= v11Conf.getZoneWalksIds().size() + 1) {
                if (v == 1) {
                    setState = 0;
                    return getWelcomeMessage();
                } else {
                    int n = v - 2;
                    List<Long> zoneWalksIds = v11Conf.getZoneWalksIds();
                    zoneWalksIds.remove(n);
                    updateZoneWalks(zoneWalksIds);
                }
                return getShowQzoneWalks();
            }
        } catch (NumberFormatException e) {
        }
        try {
            Long l = Long.valueOf(input);
            if (!v11Conf.getZoneWalksIds().contains(l)) {
                List<Long> list = v11Conf.getZoneWalksIds();
                list.add(l);
                updateZoneWalks(list);
                return getShowQzoneWalks();
            }
        } catch (NumberFormatException e) {
        }
        return "无效输入!";
    }

    private String handleSignGroupsInput(String input) {
        try {
            Integer v = Integer.valueOf(input);
            if (v >= 1 && v <= v11Conf.getSignGroupIds().size() + 1) {
                if (v == 1) {
                    setState = 0;
                    return getWelcomeMessage();
                } else {
                    int n = v - 2;
                    List<Long> signGroupIds = v11Conf.getSignGroupIds();
                    signGroupIds.remove(n);
                    updateSignGroups(signGroupIds);
                }
            }
        } catch (NumberFormatException e) {
        }
        try {
            Long l = Long.valueOf(input);
            if (!v11Conf.getSignGroupIds().contains(l)) {
                List<Long> list = v11Conf.getSignGroupIds();
                list.add(l);
                updateSignGroups(list);
            }
            return getShowSignGroups();
        } catch (NumberFormatException e) {
        }
        return "无效输入!";
    }


    private String handleZoneCommentInput(String input) {
        if ("1".equalsIgnoreCase(input))
            input = "";
        service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                .set(V11Conf::getZoneComment, input));
        setState = 0;
        return getWelcomeMessage();
    }

    // 自动打卡设置
    private String getShowSignGroups() {
        v11Conf = service.getV11Conf(String.valueOf(bot.getId()));
        StringBuilder sb = new StringBuilder("自动打卡群设置");
        sb.append("\n 1. 退出打卡设置");
        int n = 2;
        if (!v11Conf.getSignGroupIds().isEmpty()) {
            for (Long groupId : v11Conf.getSignGroupIds()) {
                sb.append("\n ").append(n++).append(". ").append(groupId);
            }
        }
        return sb.toString();
    }

    private void updateBlackList(List<Long> blackList) {
        StringBuilder blacks = new StringBuilder();
        blackList.forEach(e -> blacks.append(e).append(","));
        service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                .set(V11Conf::getLikeBlack, blacks.toString()));
    }

    private void updateWhiteList(List<Long> whiteList) {
        StringBuilder whites = new StringBuilder();
        whiteList.forEach(e -> whites.append(e).append(","));
        service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                .set(V11Conf::getLikeWhite, whites.toString()));
    }

    private void updateSignGroups(List<Long> signGroups) {
        StringBuilder groups = new StringBuilder();
        signGroups.forEach(e -> groups.append(e).append(","));
        service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                .set(V11Conf::getSignGroups, groups.toString()));
    }

    private void updateZoneWalks(List<Long> zoneWalks) {
        StringBuilder walks = new StringBuilder();
        zoneWalks.forEach(e -> walks.append(e).append(","));
        service.mapper.update(null, new LambdaUpdateWrapper<V11Conf>().eq(V11Conf::getQid, bot.getId())
                .set(V11Conf::getZoneWalks, walks.toString()));
    }

    private String getShowWhites() {
        v11Conf = service.getV11Conf(String.valueOf(bot.getId()));
        StringBuilder sb = new StringBuilder("tips:即是点赞白名单中账号不给你点赞也进行回赞\n选择序号删除对应ID\n输入ID添加");
        sb.append("\n 1. 退出白名单设置");
        int n = 2;
        for (Long l : v11Conf.getLikeWhiteIds()) {
            sb.append("\n ").append(n++).append(". ").append(l);
        }
        return sb.toString();
    }

    private String getShowBlacks() {
        v11Conf = service.getV11Conf(String.valueOf(bot.getId()));
        StringBuilder sb = new StringBuilder("tips:即是点赞黑名单中账号给你点赞也不进行回赞\n选择序号删除对应ID\n输入ID添加");
        sb.append("\n 1. 退出黑名单设置");
        int n = 2;
        for (Long l : v11Conf.getLikeBlackIds()) {
            sb.append("\n ").append(n++).append(". ").append(l);
        }
        return sb.toString();
    }


    private String getShowQzoneWalks() {
        v11Conf = service.getV11Conf(String.valueOf(bot.getId()));
        StringBuilder sb = new StringBuilder("tips:每日访问该名单账号\n选择序号删除对应ID\n输入ID添加");
        sb.append("\n 1. 退出空间访问设置");
        int n = 2;
        for (Long l : v11Conf.getZoneWalksIds()) {
            sb.append("\n ").append(n++).append(". ").append(l);
        }
        return sb.toString();
    }
}
