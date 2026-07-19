package io.github.gdpl2112.dg_bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gdpl2112.dg_bot.dao.Statistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author github.kloping
 */
@Mapper
public interface StatisticsMapper extends BaseMapper<Statistics> {
    @Select("SELECT SUM(count) AS total_count FROM statistics;")
    Integer getTotalCount();

    @Select("SELECT SUM(count) AS total_count FROM statistics WHERE `type` = #{type};")
    Integer getTotalCountByType(@Param("type") String type);

    @Select("SELECT SUM(count) AS total_count FROM statistics WHERE `account` = #{account};")
    Integer getTotalCount(@Param("account") String account);

    @Select("SELECT SUM(count) AS total_count FROM statistics WHERE `account` = #{account} AND `type` = #{type};")
    Integer getTotalCount(@Param("account") String account, @Param("type") String type);
}
