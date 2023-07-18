package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * @author github.kloping
 */
@Data
public class Passive {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String qid;
    private String touch;
    private String out;
}
