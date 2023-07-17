package io.github.gdpl2112.dg_bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gdpl2112.dg_bot.dao.CronMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author github.kloping
 */
@Mapper
public interface CronMapper extends BaseMapper<CronMessage> {
}
