package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * @author github.kloping
 */
@Data
public class Conf {
    @TableId
    public String qid;
    /**
     * 回复冷却
     */
    private Integer cd0 = 1;
    private String retell = "复述";
    private String open0 = "开启回复";
    private String close0 = "关闭回复";
    private String open1 = "开启调用";
    private String close1 = "关闭调用";
    private String add0 = "添加";
    private String cancel0 = "取消";
    private String select0 = "查询";
    //监听发送id
    private String rsid = "";
    //通知地址
    private String nu = "";
    //脚本代码
    private String code = "";
}
