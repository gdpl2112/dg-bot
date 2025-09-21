package io.github.gdpl2112.dg_bot.mapper.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.github.gdpl2112.dg_bot.dao.Statistics;

/**
 *
 *
 * @author github kloping
 * @since 2025/9/18-14:35
 */
public interface IStatisticsService extends IService<Statistics> {

    void statistics(String type, String bid);
}
