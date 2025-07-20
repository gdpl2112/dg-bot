package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author github kloping
 * @date 2025/7/20-17:10
 */
@TableName("like_reco")
@Data
public class LikeReco {
    private String bid;
    private String date;
    private String tid;
}
