package io.github.gdpl2112.dg_bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gdpl2112.dg_bot.dao.Optional;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author github.kloping
 */
@org.apache.ibatis.annotations.Mapper
public interface OptionalMapper extends BaseMapper<Optional> {
    @Select("SELECT * FROM optional WHERE qid = #{id} AND opt = #{opt}")
    Optional selectByQidAndOpt(@Param("id") String qid,@Param("opt") String opt);
}
