package io.github.gdpl2112.dg_bot.dao;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author github-kloping
 * @date 2023-07-31
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class CallTemplate {
    private String qid;
    public String touch;
    public String url;
    public String out;
    public String outArgs;
    public String err = "调用失败";
}
