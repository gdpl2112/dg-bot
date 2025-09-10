package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author github kloping
 * @date 2025/7/18-15:27
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
    //自动空间点赞
    private Boolean autoZoneLike;
    //空间自动评论
    private String zoneComment;

    public List<Long> getSignGroupIds() {
        String[] split = signGroups.split(",|;|\\s");
        return Arrays.stream(split).map(
                s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (Exception e) {
                        return null;
                    }
                }
        ).collect(Collectors.toList());
    }

    public List<Long> getLikeBlackIds() {
        String[] split = likeBlack.split(",|;|\\s");
        return Arrays.stream(split).map(
                s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (Exception e) {
                        return null;
                    }
                }
        ).collect(Collectors.toList());
    }

    public List<Long> getLikeWhiteIds() {
        String[] split = likeWhite.split(",|;|\\s");
        return Arrays.stream(split).map(
                s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (Exception e) {
                        return null;
                    }
                }
        ).collect(Collectors.toList());
    }


}
