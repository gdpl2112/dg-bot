package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * @author github.kloping
 */
@Data
public class CronMessage {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String qid;
    private String desc;
    private String cron;
    private String targetId;
    private String msg;
}
