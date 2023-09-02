package io.github.gdpl2112.dg_bot.service.script;

import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.built.ScriptService;
import io.github.kloping.map.MapUtils;
import net.mamoe.mirai.message.data.MessageChain;
import org.springframework.web.client.RestTemplate;

import java.util.*;

public class BaseScriptUtils implements ScriptUtils {
    private long bid;
    private RestTemplate template;

    public BaseScriptUtils(Long bid, RestTemplate template) {
        this.template = template;
        this.bid = bid;
    }

    @Override
    public String requestGet(String url) {
        return template.getForObject(url, String.class);
    }

    @Override
    public String requestPost(String url, String data) {
        return template.postForObject(url, data, String.class);
    }

    @Override
    public String serialize(MessageChain chain) {
        return DgSerializer.messageChainSerializeToString(chain);
    }

    @Override
    public Object get(String name) {
        return Utils.getValueOrDefault(ScriptService.BID_2_VARIABLES, bid, name, null);
    }

    @Override
    public Object set(String name, Object value) {
        Object ov = Utils.getValueOrDefault(ScriptService.BID_2_VARIABLES, bid, name, null);
        MapUtils.append(ScriptService.BID_2_VARIABLES, bid, name, value, HashMap.class);
        return ov;
    }

    @Override
    public Integer clear() {
        int i = 0;
        Map<String, Object> sizeMap = ScriptService.BID_2_VARIABLES.get(bid);
        if (sizeMap != null) {
            i = sizeMap.size();
            sizeMap.clear();
        }
        return i;
    }

    @Override
    public Object del(String name) {
        Map<String, Object> sizeMap = ScriptService.BID_2_VARIABLES.get(bid);
        if (sizeMap != null) {
            Object oa = sizeMap.get(name);
            sizeMap.remove(name);
            return oa;
        }
        return null;
    }

    @Override
    public List<Map.Entry<String, Object>> list() {
        if (ScriptService.BID_2_VARIABLES.containsKey(bid)) return new LinkedList<>(ScriptService.BID_2_VARIABLES.get(bid).entrySet());
        return new ArrayList<>();
    }
}
