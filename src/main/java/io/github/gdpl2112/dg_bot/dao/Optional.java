package io.github.gdpl2112.dg_bot.dao;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Optional {
    private String qid;
    //功能名 bean名
    private String opt;
    //目标ID 格式: "g" + 群号 或 "f" + 好友号 或 "*" 表示全部
    private String tid;
    //是否开启
    private Boolean open;
}
