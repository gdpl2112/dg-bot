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
    //是否成功 失败时原因 可能 不是好友 请求失败 或 点赞上限
    private Boolean ok;
}
