package io.github.gdpl2112.dg_bot.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 被点赞事件
 * @author github kloping
 * @since 2025/7/30-09:51
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProfileLikeEvent {
    //自身ID
    private Long selfId;
    //操作者ID
    private Long operatorId;
    //次数
    private Integer times;
}
