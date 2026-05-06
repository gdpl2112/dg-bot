package io.github.gdpl2112.dg_bot.dao;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * AI助手配置实体类
 */
@Data
public class AiConf {
    @TableId
    private String qid;

    /** 是否开启AI功能 */
    private Boolean open = false;

    /** AI唤起前缀 */
    private String prefix = "AI";

    /** API Key */
    private String apiKey = "";

    /** API Base URL */
    private String baseUrl = "https://api.openai.com/v1";

    /** 模型ID */
    private String modelId = "gpt-3.5-turbo";

    /** AI Temperature参数 */
    private Double temperature = 0.7;

    /** 是否允许联网 */
    private Boolean network = false;
}
