package io.github.gdpl2112.dg_bot.service.script;

import net.mamoe.mirai.message.data.Image;

import java.util.List;
import java.util.Map;

/**
 * 脚本工具
 * 预设脚本变量为:
 * utils
 *
 * @author github.kloping
 */
public interface ScriptUtils {

    /**
     * get 请求
     *
     * @param url
     * @return
     */
    String requestGet(String url);

    /**
     * post 请求
     *
     * @param url
     * @return
     */
    String requestPost(String url, String data);

    /**
     * 查询image url 通过image id
     *
     * @param imageId
     * @return
     */
    default String queryUrlFromId(String imageId) {
        return Image.queryUrl(Image.fromId(imageId));
    }

    /**
     * 获取变量
     *
     * @param name
     * @return
     */
    Object get(String name);

    /**
     * 设置变量
     *
     * @param name
     * @param value
     * @return
     */
    Object set(String name, Object value);

    /**
     * 清除当前账号的所有变量
     *
     * @return
     */
    Integer clear();

    /**
     * 删除指定变量
     *
     * @param name
     * @return
     */
    Object del(String name);

    /**
     * 列出当前bot所有变量
     *
     * @return
     */
    List<Map.Entry<String, Object>> list();
}
