package io.github.gdpl2112.dg_bot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author github kloping
 * @date 2025/6/2-22:08
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class BotInfo {
    private Long id;
    private String avatar;
    private String nick;
    private Boolean online;
}
