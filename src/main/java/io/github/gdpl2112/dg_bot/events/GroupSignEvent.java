package io.github.gdpl2112.dg_bot.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author github kloping
 * @date 2025/7/30-10:01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupSignEvent {
    private Long gid;
    private Long selfId;
    private Boolean ok;
}
