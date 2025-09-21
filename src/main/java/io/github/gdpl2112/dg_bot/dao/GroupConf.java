package io.github.gdpl2112.dg_bot.dao;

import lombok.Data;

/**
 * @author github-kloping
 * @since 2023-07-18
 */
@Data
public class GroupConf {
    private String qid;
    /**
     * pre type id
     */
    private String tid;
    /**
     * 调用API 开关
     */
    private Boolean k0 = true;
    /**
     * 撤回监听
     */
    private Boolean k1 = true;
    /**
     * 被动回复
     */
    private Boolean k2 = true;
    /**
     * 内置功能开关
     */
    private Boolean k3 = true;
}
