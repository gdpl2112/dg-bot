package io.github.gdpl2112.dg_bot.dto;

import com.alibaba.fastjson.JSON;
import lombok.Data;

import java.util.List;

/**
 * 点赞响应数据传输对象 (DTO)
 * 用于解析API返回的JSON数据，提取点赞（favoriteInfo）与被点赞（voteInfo）相关的用户信息
 */
@Data
public class VoteResponseDTO {

    private String status;
    private Integer retcode;
    private String message;
    private String wording;
    private Object echo;
    private String stream;
    private VoteData data;

    /**
     * 根据JSON字符串解析为点赞响应实体
     *
     * @param jsonString API返回的JSON格式字符串 非空
     * @return 解析成功后的点赞响应对象
     */
    public static VoteResponseDTO parseFromJson(String jsonString) {
        // 利用fastjson反序列化提取数据结构
        return JSON.parseObject(jsonString, VoteResponseDTO.class);
    }

    /**
     * 点赞核心数据包装类
     * 区分主动点赞(favoriteInfo)与被点赞(voteInfo)的信息模块
     */
    @Data
    public static class VoteData {
        private String uid;
        private Long time;
        private VoteInfo favoriteInfo;
        private VoteInfo voteInfo;
    }

    /**
     * 点赞统计与用户列表详情
     * 包含总数、今日数及具体的点赞用户集合
     */
    @Data
    public static class VoteInfo {
        private Long total_count;
        private Long last_time;
        private Long last_visit_time;
        private Integer today_count;
        private Integer new_count;
        private Integer new_nearby_count;
        private List<VoteUserInfo> userInfos;
    }

    /**
     * 点赞用户信息实体
     * 封装目标用户的账号(uin)、点赞次数、最近点赞时间以及SVIP状态等核心字段
     */
    @Data
    public static class VoteUserInfo {
        private Long uin;
        private Integer count;
        private Long latestTime;
        private Boolean isSvip;

        // 补充保留JSON中的其他相关字段，以防后续扩展使用
        private String uid;
        private Integer src;
        private Integer giftCount;
        private Integer customId;
        private Integer lastCharged;
        private Integer bAvailableCnt;
        private Integer bTodayVotedCnt;
        private String nick;
        private Integer gender;
        private Integer age;
        private Boolean isFriend;
        private Boolean isvip;
    }

    /**
     * 将当前包含海量原始信息的实体转换为简短数据，仅保留核心字段（uin, count, latestTime, isSvip）
     * 适合用于输出给AI，减少Token消耗
     * @return 简化后的点赞数据组合实体
     */
    public SimpleVoteData toSimplifiedData() {
        SimpleVoteData simpleData = new SimpleVoteData();

        // 提取点赞用户列表（如果存在）
        if (this.data != null && this.data.getFavoriteInfo() != null && this.data.getFavoriteInfo().getUserInfos() != null) {
            List<SimpleVoteUserInfo> favorites = this.data.getFavoriteInfo().getUserInfos().stream().map(info -> {
                SimpleVoteUserInfo simple = new SimpleVoteUserInfo();
                simple.setUin(info.getUin());
                simple.setCount(info.getCount());
                simple.setLatestTime(info.getLatestTime());
                simple.setIsSvip(info.getIsSvip());
                return simple;
            }).collect(java.util.stream.Collectors.toList());
            simpleData.setFavoriteUsers(favorites);
        }

        // 提取被点赞用户列表（如果存在）
        if (this.data != null && this.data.getVoteInfo() != null && this.data.getVoteInfo().getUserInfos() != null) {
            List<SimpleVoteUserInfo> votes = this.data.getVoteInfo().getUserInfos().stream().map(info -> {
                SimpleVoteUserInfo simple = new SimpleVoteUserInfo();
                simple.setUin(info.getUin());
                simple.setCount(info.getCount());
                simple.setLatestTime(info.getLatestTime());
                simple.setIsSvip(info.getIsSvip());
                return simple;
            }).collect(java.util.stream.Collectors.toList());
            simpleData.setVoteUsers(votes);
        }

        return simpleData;
    }

    /**
     * 将当前数据直接转换为仅含精简字段的JSON字符串，方便直接输出给AI
     * @return 简化后的JSON格式字符串
     */
    public String toSimplifiedJson() {
        return JSON.toJSONString(this.toSimplifiedData());
    }

    /**
     * 提取出的精简版点赞数据组合
     * 包含主动点赞和被点赞的用户列表
     */
    @Data
    public static class SimpleVoteData {
        private List<SimpleVoteUserInfo> favoriteUsers;
        private List<SimpleVoteUserInfo> voteUsers;
    }

    /**
     * 精简版的用户信息实体
     * 仅包含提供给AI分析的核心字段，剔除无用字段
     */
    @Data
    public static class SimpleVoteUserInfo {
        private Long uin;
        private Integer count;
        private Long latestTime;
        private Boolean isSvip;
    }
}
