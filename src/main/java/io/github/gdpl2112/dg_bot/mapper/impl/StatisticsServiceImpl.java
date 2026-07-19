package io.github.gdpl2112.dg_bot.mapper.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.gdpl2112.dg_bot.dao.Statistics;
import io.github.gdpl2112.dg_bot.mapper.StatisticsMapper;
import io.github.gdpl2112.dg_bot.mapper.service.IStatisticsService;
import org.springframework.stereotype.Service;

/**
 *
 *
 * @author github kloping
 * @since 2025/9/18-14:36
 */
@Service
public class StatisticsServiceImpl extends ServiceImpl<StatisticsMapper, Statistics> implements IStatisticsService {

    @Override
    public synchronized void statistics(String type, String bid) {
        Statistics statistics = lambdaQuery()
                .eq(Statistics::getAccount, bid).eq(Statistics::getType, type).select(Statistics::getCount).one();
        Long count = 0L;
        if (statistics == null) {
            statistics = new Statistics();
            statistics.setAccount(bid);
            statistics.setType(type);
            statistics.setCount(count);
            save(statistics);
        } else if (statistics.getCount() != null) {
            count = statistics.getCount();
        }
        count = count + 1;
        lambdaUpdate().eq(Statistics::getAccount, bid)
                .eq(Statistics::getType, type)
                .set(Statistics::getCount, count).update();
    }
}
