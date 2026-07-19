package io.github.gdpl2112.dg_bot.controllers;

import io.github.gdpl2112.dg_bot.dao.AiConf;
import io.github.gdpl2112.dg_bot.mapper.AiConfMapper;
import io.github.gdpl2112.dg_bot.service.optionals.AIAssistantOptional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * AI助手配置管理接口
 */
@RestController
@PreAuthorize("hasAuthority('user')")
@RequestMapping("/api/ai-conf")
public class AiConfController {

    @Autowired
    private AiConfMapper aiConfMapper;

    /**
     * 获取当前登录用户的AI配置；首次查询时自动创建默认配置。
     * 出于安全考虑，apiKey仅返回脱敏预览（首4位+末4位），前端不可获取完整密钥。
     */
    @GetMapping("/config")
    public AiConf getConfig(@AuthenticationPrincipal UserDetails userDetails) {
        String qid = userDetails.getUsername();
        AiConf config = aiConfMapper.selectById(qid);
        if (config == null) {
            config = new AiConf();
            config.setQid(qid);
            aiConfMapper.insert(config);
        }
        // 对apiKey进行脱敏处理，防止明文泄露
        maskApiKey(config);
        return config;
    }

    /**
     * 将 apiKey 脱敏：仅保留前后各4位，中间用 *** 替代；
     * 若长度不足以脱敏则显示 ****
     */
    private void maskApiKey(AiConf config) {
        String key = config.getApiKey();
        if (key == null || key.isEmpty()) {
            return;
        }
        if (key.length() <= 8) {
            config.setApiKey("****");
        } else {
            config.setApiKey(key.substring(0, 4) + "***" + key.substring(key.length() - 4));
        }
    }

    /**
     * 更新当前登录用户的AI配置
     */
    @PostMapping("/update")
    public Object update(@AuthenticationPrincipal UserDetails userDetails, @RequestBody AiConf aiConf) {
        String qid = userDetails.getUsername();
        AiConf existing = aiConfMapper.selectById(qid);
        if (existing == null) {
            existing = new AiConf();
            existing.setQid(qid);
            aiConfMapper.insert(existing);
        }

        if (aiConf.getOpen() != null) {
            existing.setOpen(aiConf.getOpen());
        }
        if (aiConf.getPrefix() != null) {
            existing.setPrefix(aiConf.getPrefix());
        }
        // 仅当前端传入完整密钥（非脱敏占位符）时才更新，防止将脱敏值写回数据库
        if (aiConf.getApiKey() != null && !aiConf.getApiKey().contains("***")) {
            existing.setApiKey(aiConf.getApiKey());
        }
        if (aiConf.getBaseUrl() != null) {
            existing.setBaseUrl(aiConf.getBaseUrl());
        }
        if (aiConf.getModelId() != null) {
            existing.setModelId(aiConf.getModelId());
        }
        if (aiConf.getTemperature() != null) {
            existing.setTemperature(aiConf.getTemperature());
        }
        if (aiConf.getNetwork() != null) {
            existing.setNetwork(aiConf.getNetwork());
        }
        if (aiConf.getName() != null) {
            existing.setName(aiConf.getName());
        }
        if (aiConf.getTrait() != null) {
            existing.setTrait(aiConf.getTrait());
        }
        if (aiConf.getMaxMessage() != null) {
            existing.setMaxMessage(aiConf.getMaxMessage());
        }
        int result = aiConfMapper.updateById(existing);
        if (result > 0) {
            // 配置更新后清除对应的ChatClient缓存
            AIAssistantOptional.evictChatClient(qid);
            return successResult("更新成功");
        }
        return errorResult("更新失败");
    }

    private Map<String, Object> successResult(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", data);
        return result;
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", message);
        return result;
    }
}
