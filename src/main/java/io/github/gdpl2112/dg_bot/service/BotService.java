package io.github.gdpl2112.dg_bot.service;

/**
 * @author github-kloping
 * @since 2023-07-17
 */
public interface BotService {
    /**
     * 发送
     *
     * @param qid
     * @param targetId
     * @param msg
     */
    void send(String qid, String targetId, String msg);
}
