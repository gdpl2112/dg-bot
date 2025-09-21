package io.github.gdpl2112.dg_bot.service.script;

import io.github.gdpl2112.dg_bot.Utils;
import io.github.gdpl2112.dg_bot.built.ScriptCompile;
import io.github.gdpl2112.dg_bot.service.script.impl.BaseScriptUtils;
import lombok.Getter;
import net.mamoe.mirai.Bot;
import org.springframework.web.client.RestTemplate;

import javax.script.ScriptEngineManager;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author github kloping
 * @since 2025/8/5-22:05
 */
public class ScriptManager {

    public static void clearBidCache(long bid) {
        BID2ENGINE.remove(bid);
//        Map map = BID2F2K.remove(bid);
//        if (map != null) map.clear();
    }

    public static final Map<Long, Map<String, Object>> BID_2_VARIABLES = new HashMap<>();

    public static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();

    public static final Integer MAX_LINE = 30;

    public static final SimpleDateFormat SF_0 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static final Map<Long, ScriptUtils> BID2UTILS = new HashMap<>();
    // bot 的 编译后代码环境
    public static Map<Long, ScriptCompile> BID2ENGINE = new HashMap<>();
    // bot 的 函数缓存
//    public static Map<Long, Map<String, Boolean>> BID2F2K = new HashMap<>();
    //报错缓存
    public static Map<String, ScriptException> exceptionMap = new HashMap<>();
    //bot 打印结果
    public static Map<String, List<String>> PRINT_MAP = new HashMap<>();

    public static final RestTemplate TEMPLATE = new RestTemplate();

    public static ScriptUtils getScriptUtils(long bid) {
        if (BID2UTILS.containsKey(bid)) return BID2UTILS.get(bid);
        else {
            ScriptUtils utils = new BaseScriptUtils(bid, TEMPLATE);
            BID2UTILS.put(bid, utils);
            return utils;
        }
    }

    public static boolean isDefine(ScriptCompile compile, String funName) {
        try {
            return compile.hasFunction(funName);
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized static boolean isDefined(long bid, ScriptCompile engine, String funName) {
//        Map<String, Boolean> map = BID2F2K.get(bid);
//        if (map == null) {
//            BID2F2K.put(bid, map = new HashMap<>());
//        }
//        if (map.containsKey(funName)) return map.get(funName);
//        else {
//            boolean b = isDefine(engine, funName);
//            map.put(funName, b);
//            return b;
//        }
        return isDefine(engine, funName);
    }

    public static void onException(Bot bot, Throwable e) {
        onException(bot.getId(), e);
    }

    public static void onException(long bid, Throwable e) {
        onException(bid, e, "");
    }

    public static void onException(long bid, Throwable e, String msg) {
        e.printStackTrace();
        String err = Utils.getExceptionLine(e);
        err = e + err;
        ScriptException se = new ScriptException(msg + "\n" + err, System.currentTimeMillis(), bid);
        exceptionMap.put(String.valueOf(bid), se);
        System.err.println(String.format("%s Bot 脚本 执行失败", bid));
    }

    @Getter
    public static class ScriptException {
        private String msg;
        private Long time;
        private Long qid;

        public ScriptException(String msg, Long time, Long qid) {
            this.msg = msg;
            this.time = time;
            this.qid = qid;
        }
    }

    public interface Logger {
        void log(String msg);

        void log(String msg, Object... args);
    }
}
