package io.github.gdpl2112.dg_bot.service.v11s;

import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.MiraiComponent;
import io.github.gdpl2112.dg_bot.dao.V11Conf;
import io.github.gdpl2112.dg_bot.mapper.V11ConfMapper;
import net.mamoe.mirai.Bot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 *
 * @author github kloping
 * @date 2025/9/10-22:29
 */
@Service
public class V11QzoneService {

    @Autowired
    V11ConfMapper mapper;

    @Autowired
    MiraiComponent component;

    @Autowired
    V11AutoLikeService likeService;

    @Scheduled(cron = "01 01 00 * * ? ")
    public void walksAll() {
        component.log.info("空间访问!启动");
        for (Bot bot : Bot.getInstances()) {
            if (bot != null && bot.isOnline()) {
                if (bot instanceof RemoteBot) {
                    startQzoneWalkNow(bot.getId(), (RemoteBot) bot);
                }
            }
        }
    }

    public void startQzoneWalkNow(long id, RemoteBot bot) {
        V11Conf v11Conf = likeService.getV11Conf(String.valueOf(id));
        String dataR0 = bot.executeAction("get_cookies", "{\"domain\": \"user.qzone.qq.com\"}");
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
        String gtk = data.getString("bkn");
        String cookiesStr = String.format(
                "ptui_loginuin=%s; RK=%s; Loading=Yes; media_p_uin=%s; qz_screen=1536x864; QZ_FE_WEBP_SUPPORT=1; cpu_performance_v8=0; __Q_w_s_hat_seed=1; " +
                        "uin=o0%s; skey=%s; p_uin=o0%s; pt4_token=%s; p_skey=%s;"
                , id, cookiesMap.get("RK"), id
                , id, cookiesMap.get("skey"), id, cookiesMap.get("pt4_token"), cookiesMap.get("p_skey")
        );

        for (Long zoneWalksId : v11Conf.getZoneWalksIds()) {
            String url = "https://user.qzone.qq.com/proxy/domain/g.qzone.qq.com/fcg-bin/cgi_emotion_list.fcg?uin=" +
                    zoneWalksId + "&loginUin=" + id + "&ver=1.0.3&g_tk=" + gtk;
            component.log.info("空间访问：b" + id + " u" + zoneWalksId);
            try {
                Document doc0 = Jsoup.connect(url).ignoreHttpErrors(true).ignoreContentType(true)
                        .header("accept", "*/*")
                        .header("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
                        .header("referer", "https://user.qzone.qq.com/" + zoneWalksId)
                        .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36 Edg/140.0.0.0")
                        .header("Cookie", cookiesStr)
                        .get();
                System.out.println(doc0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
