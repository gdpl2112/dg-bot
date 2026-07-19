package io.github.gdpl2112.dg_bot.dao;

import lombok.Data;

/**
 *
 *
 * @author github kloping
 * @since 2025/9/18-14:31
 */
@Data
public class Statistics {
    public static final String GROUP = "GROUP";
    public static final String PRIVATE = "PRIVATE";

    private Long count = 0L;
    private String account;
    private String type = PRIVATE;
}
