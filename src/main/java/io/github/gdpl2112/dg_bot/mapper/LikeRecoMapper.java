package io.github.gdpl2112.dg_bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.gdpl2112.dg_bot.dao.LikeReco;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author github.kloping
 */
@Mapper
public interface LikeRecoMapper extends BaseMapper<LikeReco> {

    @Select("SELECT * from `like_reco` where `bid`=#{bid} AND `date`=#{date} AND `tid`=#{tid}")
    LikeReco getByDateAndBidAndTid(@Param("bid") Long bid,@Param("date") String date,@Param("tid") String tid);

    @Select("SELECT * from `like_reco` where `bid`=#{bid} AND `date`=#{date} ")
    List<LikeReco> selectListByDateAndBid(String bid, String date);
}
