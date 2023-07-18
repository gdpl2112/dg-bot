package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * @author github-kloping
 * @date 2023-07-17
 */
@Data
public class AuthM {
    @TableId
    private String qid;
    private String auth;
    /**
     * 剩余时间
     */
    private Long exp;
    /**
     * 在线时间戳
     */
    private Long t0;
}
