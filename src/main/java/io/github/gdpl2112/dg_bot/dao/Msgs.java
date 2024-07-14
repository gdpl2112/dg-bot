package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * @author github.kloping
 */
@Data
public class Msgs {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String botId;
    private String name;
    private String subjectId;
    private String senderName;
    private String senderId;
    private String msg;
    private String imageUrl = "";
    private Long time;
    private String type;
}
