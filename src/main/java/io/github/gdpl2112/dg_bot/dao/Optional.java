package io.github.gdpl2112.dg_bot.dao;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Optional {
    private String qid;
    //功能名 bean名
    private String opt;
    //是否开启
    private Boolean open;
}
