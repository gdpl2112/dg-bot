package io.github.gdpl2112.dg_bot.service.v11s;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.mapper.V11ConfMapper;
import io.github.gdpl2112.dg_bot.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import net.mamoe.mirai.Bot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 *
 * @author github kloping
 * @since 2025/9/10-22:29
 */
@Slf4j
@Service
public class V11QzoneService {

    @Autowired
    V11ConfMapper mapper;

    @Autowired
    MiraiComponent component;

    @Autowired
    V11AutoLikeService likeService;

    @Scheduled(cron = "20 06 00 * * ? ")
    public void walksAll0() {
        qzoneWalksAll(false);
    }

    @Scheduled(cron = "20 25 12 * * ? ")
    public void walksAll1() {
        qzoneWalksAll(true);
    }

    /**
     *
     * @param k 是否点赞
     */
    private void qzoneWalksAll(boolean k) {
        component.logger.info("空间访问!启动");
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                if (bot instanceof RemoteBot) {
                    startQzoneWalkNow(bot.getId(), (RemoteBot) bot, k);
                }
            }
        }
    }

    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void startQzoneWalkNow(long id, RemoteBot bot, boolean like) {
        V11Conf v11Conf = likeService.getV11Conf(String.valueOf(id));
        List<Long> zoneWalksIds = v11Conf.getZoneWalksIds();
        if (zoneWalksIds.isEmpty()) return;
        Map<String, String> cookiesMap = getCookiesMap(bot);
        for (Long zoneWalksId : zoneWalksIds) {
            component.logger.info("空间访问：b" + id + " u" + zoneWalksId);
            if (zoneWalksId == null) continue;
            try {
                String walkUrl = "https://kloping.top/api/qzone/walk" + getParmsStart(String.valueOf(id), cookiesMap)
                        + "&qq=" + zoneWalksId;
                ResponseEntity<String> entity = template.getForEntity(walkUrl, String.class);
                if (entity.getStatusCode().is2xxSuccessful()) {
                    log.info("空间访问成功：b{} walk u{}..继续:{}", id, zoneWalksId, like);
                    if (like) {
                        String url0 = "https://api.s01s.cn/API/xiadan/?" +
                                "xh=1&uin=" + zoneWalksId +
                                "&qq=" + id +
                                "&skey=" + cookiesMap.get("skey") +
                                "&p_skey=" + cookiesMap.get("p_skey");
                        String dataR1 = template.getForObject(url0, String.class);
                        String fid = JSONObject.parseObject(dataR1).getString("id");
                        String ctime = JSONObject.parseObject(dataR1).getString("time");
                        // 解析为 时间戳
                        // 解析为LocalDateTime（无时区信息）
                        LocalDateTime localDateTime = LocalDateTime.parse(ctime, formatter);
                        // 转换为系统默认时区的时间戳
                        long timestamp = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                        ctime = ctime.substring(0, 10);
                        String unlikeUrl = "https://kloping.top/api/qzone/unlike" + getParmsStart(String.valueOf(id), cookiesMap)
                                + "&fid=" + fid + "&qq=" + zoneWalksId + "&ctime=" + ctime;
                        ResponseEntity<String> entity1 = template.getForEntity(unlikeUrl, String.class);
                        if (entity1.getStatusCode().is2xxSuccessful()) {
                            log.info("取消点赞b{} u{}..完成..继续", id, zoneWalksId);
                            //dolike
                            String likeUrl = "https://kloping.top/api/qzone/dolike" + getParmsStart(String.valueOf(id), cookiesMap)
                                    + "&fid=" + fid + "&qq=" + zoneWalksId + "&ctime=" + ctime;
                            ResponseEntity<String> entity2 = template.getForEntity(likeUrl, String.class);
                            if (entity2.getStatusCode().is2xxSuccessful()) {
                                log.info("点赞成功：b{} u{}..完成", id, zoneWalksId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("空间访问异常", e);
                reportService.report(String.valueOf(id), "空间访问异常,访问ID:" + zoneWalksId);
            }
        }
    }

    public static final String getParmsStart(String id, Map<String, String> cookieMap) {
        StringBuilder sb = new StringBuilder("?RK=");
        sb.append(cookieMap.get("RK"));
        sb.append("&skey=").append(cookieMap.get("skey"));
        sb.append("&pt4_token=").append(cookieMap.get("pt4_token"));
        sb.append("&p_skey=").append(cookieMap.get("p_skey"));
        sb.append("&uin=").append(id);
        return sb.toString();
    }

    @Autowired
    RestTemplate template;
    //以下是 自动评论/点赞功能

    private LambdaQueryWrapper<V11Conf> queryWrapper = new LambdaQueryWrapper<>();

    {
        queryWrapper.ge(V11Conf::getZoneEvl, 1);
    }

    Map<String, Integer> evlMap = new HashMap<>();

    @Scheduled(cron = "12 */1 * * * ? ")
    public void autoComment() {
        List<V11Conf> v11Confs = mapper.selectList(queryWrapper);
        v11Confs.forEach(v -> {
            Integer evl = evlMap.get(v.getQid());
            if (evl == null) evl = 1;
            evlMap.put(v.getQid(), evl + 1);
            if (evl % v.getZoneEvl() == 0) {
                Bot bot = Bot.getInstanceOrNull(Long.parseLong(v.getQid()));
                if (bot != null && bot.isOnline()) {
                    if (bot instanceof RemoteBot) {
                        MiraiComponent.EXECUTOR_SERVICE.submit(() -> {
                            startCommentNow(bot.getId(), (RemoteBot) bot);
                        });
                    }
                }
            }
        });

    }

    private void startCommentNow(long id, RemoteBot bot) {
        V11Conf v11Conf = likeService.getV11Conf(String.valueOf(id));
        String comment = v11Conf.getZoneComment();
        Boolean autoZoneLike = v11Conf.getAutoZoneLike();
        if ((comment == null || comment.trim().isEmpty()) && !autoZoneLike) return;
        Map<String, String> cookiesMap = getCookiesMap(bot);
        String uin = String.valueOf(id);
        try {
            component.logger.info("空间评论/点赞：start-b" + uin);
            String allUrl = "https://kloping.top/api/qzone/all" + getParmsStart(String.valueOf(id), cookiesMap);
            ResponseEntity<String> entity = template.getForEntity(allUrl, String.class);
            if (entity.getStatusCode().is2xxSuccessful()) {
                JSONArray all = JSONArray.parseArray(entity.getBody());
                for (int i = 0; i < all.size(); i++) {
                    JSONObject data = all.getJSONObject(i);
                    String qq = data.getString("uin");
                    if (autoZoneLike) {
                        JSONArray array = data.getJSONArray("likes");
                        if (!array.contains(uin)) {
                            String likeUrl = "https://kloping.top/api/qzone/dolike" + getParmsStart(String.valueOf(id), cookiesMap)
                                    + "&fid=" + data.getString("tid")
                                    + "&qq=" + qq
                                    + "&ctime=" + data.getString("abstime");
                            ResponseEntity<String> entity1 = template.getForEntity(likeUrl, String.class);
                            if (entity1.getStatusCode().is2xxSuccessful()) {
                                log.info("点赞成功：b{} u{}..完成", id, qq);
                            }
                        }
                    }
                    if (comment != null && !comment.isEmpty()) {
                        JSONArray array = data.getJSONArray("comments");
                        Boolean doit = true;
                        for (Object o : array) {
                            JSONObject o1 = (JSONObject) o;
                            if (o1.getString("uin").equals(uin)) {
                                doit = false;
                            }
                        }
                        if (doit) {
                            String commentUrl = "https://kloping.top/api/qzone/comment" + getParmsStart(String.valueOf(id), cookiesMap)
                                    + "&fid=" + data.getString("tid")
                                    + "&qq=" + qq
                                    + "&text=" + comment;
                            ResponseEntity<String> entity1 = template.getForEntity(commentUrl, String.class);
                            if (entity1.getStatusCode().is2xxSuccessful()) {
                                log.info("评论成功：b{} u{}..完成", id, qq);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("空间评论异常", e);
            reportService.report(String.valueOf(id), "空间评论异常");
        }
        component.logger.info("空间评论/点赞：end-b" + uin);
    }

    @Autowired
    ReportService reportService;

    private static Map<String, String> getCookiesMap(RemoteBot bot) {
        String dataR0 = bot.executeAction("get_cookies", "{\"domain\": \"qzone.qq.com\"}");
        JSONObject data = JSONObject.parseObject(dataR0);
        data = data.getJSONObject("data");
        String cookies = data.getString("cookies");
        Map<String, String> cookiesMap = new HashMap<>();
        for (String s : cookies.split(" ")) {
            String[] split = s.split("=");
            String v0 = split[1];
            if (v0.endsWith(";"))
                v0 = v0.substring(0, v0.length() - 1);
            cookiesMap.put(split[0], v0);
        }
        return cookiesMap;
    }
}
