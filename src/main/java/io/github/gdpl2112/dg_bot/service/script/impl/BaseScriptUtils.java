package io.github.gdpl2112.dg_bot.service.script.impl;

import com.alibaba.fastjson.JSONObject;
import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.built.DgSerializer;
import io.github.gdpl2112.dg_bot.service.ScriptService;
import io.github.gdpl2112.dg_bot.service.script.ScriptManager;
import io.github.gdpl2112.dg_bot.service.script.ScriptUtils;
import io.github.kloping.map.MapUtils;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.message.data.MessageChain;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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
    public MessageChain deSerialize(String msg) {
        return DgSerializer.stringDeserializeToMessageChain(msg, Bot.getInstanceOrNull(bid));
    }

    @Override
    public Object get(String name) {
        return Utils.getValueOrDefault(ScriptManager.BID_2_VARIABLES, bid, name, null);
    }

    @Override
    public Object set(String name, Object value) {
        Object ov = Utils.getValueOrDefault(ScriptManager.BID_2_VARIABLES, bid, name, null);
        MapUtils.append(ScriptManager.BID_2_VARIABLES, bid, name, value, HashMap.class);
        return ov;
    }

    @Override
    public Integer clear() {
        int i = 0;
        Map<String, Object> sizeMap = ScriptManager.BID_2_VARIABLES.get(bid);
        if (sizeMap != null) {
            i = sizeMap.size();
            sizeMap.clear();
        }
        return i;
    }

    @Override
    public Object del(String name) {
        Map<String, Object> sizeMap = ScriptManager.BID_2_VARIABLES.get(bid);
        if (sizeMap != null) {
            Object oa = sizeMap.get(name);
            sizeMap.remove(name);
            return oa;
        }
        return null;
    }

    @Override
    public List<Map.Entry<String, Object>> list() {
        if (ScriptManager.BID_2_VARIABLES.containsKey(bid))
            return new LinkedList<>(ScriptManager.BID_2_VARIABLES.get(bid).entrySet());
        return new ArrayList<>();
    }

    @Override
    public <T> T newObject(String name, Object... args) {
        try {
            Class cla = Class.forName(name);
            List<Class> list = new ArrayList<>();
            for (Object arg : args) {
                list.add(arg.getClass());
            }
            Constructor constructor = cla.getDeclaredConstructor(list.toArray(new Class[0]));
            return (T) constructor.newInstance(args);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean executeSql(String sql) {
        try {
            Connection connection = DriverManager.getConnection(String.format("jdbc:sqlite:user-%s-db.db", bid));
            Statement statement = connection.createStatement();
            boolean k = statement.execute(sql);
            statement.close();
            connection.close();
            return k;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Object> executeSelectList(String sql) {
        JdbcTemplate template = getJdbcTemplate(bid);
        List<Map<String, Object>> list = template.queryForList(sql);
        if (list.size() == 0) return null;
        else {
            List out = new ArrayList();
            for (Map<String, Object> map : list) {
                JSONObject jo = new JSONObject();
                for (String key : map.keySet()) {
                    jo.put(key, map.get(key));
                }
                out.add(jo);
            }
            return out;
        }
    }

    @Override
    public Object executeSelectOne(String sql) {
        JdbcTemplate template = getJdbcTemplate(bid);
        List<Map<String, Object>> list = template.queryForList(sql);
        if (list.size() >= 0) {
            for (Map<String, Object> map : list) {
                JSONObject jo = new JSONObject();
                for (String key : map.keySet()) {
                    jo.put(key, map.get(key));
                }
                return jo;
            }
        }
        return null;
    }

    public static final Map<Long, JdbcTemplate> templateMap = new HashMap<>();

    @NotNull
    private static JdbcTemplate getJdbcTemplate(long bid) {
        if (templateMap.containsKey(bid)) return templateMap.get(bid);
        DataSource dataSource = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(String.format("jdbc:sqlite:user-%s-db.db", bid));
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return DriverManager.getConnection(String.format("jdbc:sqlite:user-%s-db.db", bid));
            }
        };
        JdbcTemplate template = new JdbcTemplate(dataSource);
        templateMap.put(bid, template);
        return template;
    }
}
