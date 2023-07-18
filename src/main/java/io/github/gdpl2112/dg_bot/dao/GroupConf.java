package io.github.gdpl2112.dg_bot.dao;

import lombok.Data;

/**
 * @author github-kloping
 * @date 2023-07-18
 */
@Data
public class GroupConf {
    private String qid;
    /**
     * pre type id
     */
    private String tid;
    /**
     * 撤回监听
     */
    private Boolean k1;
    /**
     * 被动回复
     */
    private Boolean k2;
}
