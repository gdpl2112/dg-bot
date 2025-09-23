package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author github kloping
 * @since 2025/7/18-15:27
 */
@Data
@Accessors(chain = true)
public class V11Conf {
    @TableId
    private String qid;
    //自动回赞
    private Boolean autoLike;
    //需要暂满 才回赞 默认false
    private Boolean needMaxLike;
    //隔日回赞
    private Boolean autoLikeYesterday;
    //点赞黑名单
    private String likeBlack;
    //点赞白名单
    private String likeWhite;
    //自动打卡群id
    private String signGroups;
    //轮询频率
    private Integer zoneEvl;
    //自动空间点赞
    private Boolean autoZoneLike;
    //空间自动评论
    private String zoneComment;
    //空间自动访问
    private String zoneWalks;

    public List<Long> getSignGroupIds() {
        String[] split = signGroups.split(",|;|\\s");
        return getLongList(split);
    }

    public List<Long> getLikeBlackIds() {
        String[] split = likeBlack.split(",|;|\\s");
        return getLongList(split);
    }

    public List<Long> getLikeWhiteIds() {
        String[] split = likeWhite.split(",|;|\\s");
        return getLongList(split);
    }

    // 空间自动访问
    public List<Long> getZoneWalksIds() {
        String[] split = zoneWalks.split(",|;|\\s");
        return getLongList(split);
    }

    @NotNull
    private static List<Long> getLongList(String[] split) {
        List<Long> list = new ArrayList<>();
        for (String s : split) {
            try {
                Long l = Long.parseLong(s);
                list.add(l);
            } catch (NumberFormatException e) {
                continue;
            }
        }
        return list;
    }
}
