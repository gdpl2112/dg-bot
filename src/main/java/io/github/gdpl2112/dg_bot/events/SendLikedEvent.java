package io.github.gdpl2112.dg_bot.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点赞完成事件
 * @author github kloping
 * @date 2025/7/30-09:53
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendLikedEvent {
    private Long selfId;
    private Long operatorId;
    private Integer times;
    private Boolean ok;
}
