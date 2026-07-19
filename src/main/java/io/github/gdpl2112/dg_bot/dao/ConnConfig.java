package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 *
 * create on 19:58
 *
 * @author github kloping
 * @since 2025/12/10
 */
@Data
public class ConnConfig {
    @TableId
    private String qid;

    private String ip;
    private Integer port;
    private String type;
    private String token;
    private Integer heart;
}
