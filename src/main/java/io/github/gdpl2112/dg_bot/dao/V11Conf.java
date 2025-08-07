package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.experimental.Accessors;

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
    //自动打卡群id
    private String signGroups;
    //自动空间点赞
    private Boolean autoZoneLike;
    //空间自动评论
    private String zoneComment;
}
