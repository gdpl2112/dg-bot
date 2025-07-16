package io.github.gdpl2112.dg_bot.service;

import net.mamoe.mirai.event.SimpleListenerHost;
import org.springframework.stereotype.Service;

/**
 * @author github.kloping
 */
@Service
public class AutoLikesService extends SimpleListenerHost {
    public static final String FORMAT_SEND_LIKE = "{\"user_id\": \"%s\",\"times\": %s}";
//
//    private MiraiComponent component;
//
//    public AutoLikesService(MiraiComponent component) {
//        super();
//        this.component = component;
//    }
//
//    private static final SimpleDateFormat SF_DD = new SimpleDateFormat("dd");
//
//    private long upvid = -1L;
//
//    @EventHandler
//    public void onEvent(FriendMessageSyncEvent event) {
//        if (event.getSubject().getId() == event.getSender().getId()) {
//            StringBuilder sb = new StringBuilder();
//            for (SingleMessage singleMessage : event.getMessage()) {
//                if (singleMessage instanceof PlainText) {
//                    PlainText text = (PlainText) singleMessage;
//                    sb.append(text.getContent().trim());
//                }
//            }
//            if ("/自动回赞".equalsIgnoreCase(sb.toString())) {
//                this.run();
//            }
//        }
//    }
//
//    @Scheduled(cron = "0 35 23 * * ? ")
//    public void run() {
//        for (Bot bot : Bot.getInstances()) {
//            if (bot != null) {
//                if (bot instanceof RemoteBot) {
//                    RemoteBot remoteBot = ((RemoteBot) bot);
//                    String data = remoteBot.executeAction("get_profile_like", "{}");
//                    JSONObject jsonObject = JSONObject.parseObject(data);
//
//                    jsonObject = jsonObject.getJSONObject("data");
//
//                    JSONObject voteInfo = jsonObject.getJSONObject("voteInfo");
//                    JSONArray vUserInfos = voteInfo.getJSONArray("userInfos");
//                    JSONObject nvu = vUserInfos.getJSONObject(0);
//                    long cvid = nvu.getLong("uin");
//                    if (upvid == cvid) return;
//                    else upvid = cvid;
//                    JSONObject favoriteInfo = jsonObject.getJSONObject("favoriteInfo");
//                    JSONArray fUserInfos = favoriteInfo.getJSONArray("userInfos");
//
//                    int dayN = DateUtils.getDay();
//
//                    //已点
//                    Map<Long, Integer> f2c = new HashMap<>();
//                    for (Object fUserInfo : fUserInfos) {
//                        JSONObject fUser = (JSONObject) fUserInfo;
//                        Long vid = fUser.getLong("uin");
//                        Integer count = fUser.getInteger("count");
//                        Long date = fUser.getLong("latestTime") * 1000L;
//                        int day = Integer.valueOf(SF_DD.format(new Date(date)).trim());
//                        if (day != dayN) {
//                            break;
//                        } else {
//                            f2c.put(vid, count);
//                        }
//                    }
//
//                    //被点
//                    for (Object vUserInfo : vUserInfos) {
//                        JSONObject vUser = (JSONObject) vUserInfo;
//                        Long vid = vUser.getLong("uin");
//                        Integer count = vUser.getInteger("count");
//                        Long date = vUser.getLong("latestTime") * 1000L;
//                        Integer day = Integer.valueOf(SF_DD.format(new Date(date)).trim());
//                        if (day != dayN) {
//                            return;
//                        } else {
//                            int max = component.VIP_INFO.get(bot.getId()) ? 20 : 10;
//                            if (f2c.containsKey(vid)) {
//                                if (f2c.get(vid) >= max) continue;
//                            }
//                            remoteBot.executeAction("send_like", String.format(FORMAT_SEND_LIKE, vid, max));
//                        }
//                    }
//                }
//            }
//        }
//    }
}
