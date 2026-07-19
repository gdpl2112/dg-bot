package io.github.gdpl2112.dg_bot.controllers;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.gdpl2112.dg_bot.dao.ConnConfig;
import io.github.gdpl2112.dg_bot.mapper.ConnConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static io.github.gdpl2112.dg_bot.MiraiComponent.closeOneBot;
import static io.github.gdpl2112.dg_bot.MiraiComponent.handleOneBot;

/**
 * 连接配置管理接口
 *
 * @author github kloping
 * @since 2025/12/10
 */
@Slf4j
@RestController
@PreAuthorize("hasAuthority('user')")
@RequestMapping("/api/conn-config")
public class ConnConfigController {

    @Autowired
    private ConnConfigMapper connConfigMapper;

    /**
     * 分页获取连接配置列表
     *
     * @param page 当前页码
     * @param size 每页数量
     * @return 分页结果
     */
    @GetMapping("/page")
    public Object page(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size) {
        QueryWrapper<ConnConfig> queryWrapper = new QueryWrapper<>();
        Page<ConnConfig> pageObj = new Page<>(page, size);
        Page<ConnConfig> result = connConfigMapper.selectPage(pageObj, queryWrapper);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("records", result.getRecords());
        resultMap.put("total", result.getTotal());
        resultMap.put("current", result.getCurrent());
        resultMap.put("pages", result.getPages());
        return successResult(resultMap);
    }

    /**
     * 根据ID获取连接配置详情
     *
     * @param id 配置ID
     * @return 配置详情
     */
    @GetMapping("/detail")
    public Object detail(@RequestParam String id) {
        ConnConfig config = connConfigMapper.selectById(id);
        if (config == null) {
            return errorResult("配置不存在");
        }
        return successResult(config);
    }

    /**
     * 添加连接配置
     *
     * @param connConfig 配置信息
     * @return 添加结果
     */
    @PostMapping("/add")
    public Object add(@RequestBody ConnConfig connConfig) {
        int result = connConfigMapper.insert(connConfig);
        if (result > 0) {
            handleOneBot(connConfig);
            return successResult("添加成功");
        } else {
            return errorResult("添加失败");
        }
    }

    /**
     * 更新连接配置
     *
     * @param connConfig 配置信息
     * @return 更新结果
     */
    @PostMapping("/update")
    public Object update(@RequestBody ConnConfig connConfig) {
        ConnConfig existing = connConfigMapper.selectById(connConfig.getQid());
        if (existing == null) {
            return errorResult("配置不存在");
        }
        int result = connConfigMapper.updateById(connConfig);
        if (result > 0) {
            closeOneBot(connConfig);
            handleOneBot(connConfig);
            return successResult("更新成功");
        } else {
            return errorResult("更新失败");
        }
    }

    /**
     * 删除连接配置
     *
     * @param id 配置ID
     * @return 删除结果
     */
    @PostMapping("/delete")
    public Object delete(@RequestParam String id) {
        ConnConfig existing = connConfigMapper.selectById(id);
        if (existing == null) {
            return errorResult("配置不存在");
        }
        int result = connConfigMapper.deleteById(id);
        if (result > 0) {
            closeOneBot(existing);
            return successResult("删除成功");
        } else {
            return errorResult("删除失败");
        }
    }

    /**
     * 构造成功响应结果
     *
     * @param data 数据
     * @return 响应结果
     */
    private Map<String, Object> successResult(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", data);
        return result;
    }

    /**
     * 构造错误响应结果
     *
     * @param message 错误消息
     * @return 响应结果
     */
    private Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", message);
        return result;
    }
}