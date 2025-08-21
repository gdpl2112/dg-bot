package io.github.gdpl2112.dg_bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.github.gdpl2112.dg_bot.built.callapi.Converter;
import io.github.gdpl2112.dg_bot.dao.AuthM;
import io.github.gdpl2112.dg_bot.mapper.AuthMapper;
import io.github.kloping.judge.Judge;
import lombok.Setter;
import net.mamoe.mirai.Bot;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.message.data.PlainText;
import net.mamoe.mirai.message.data.SingleMessage;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;
import oshi.util.Util;
import top.mrxiaom.overflow.contact.RemoteBot;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author github-kloping
 * @date 2023-07-20
 */
public class Utils {

    /**
     * 获取重定向地址
     *
     * @param path
     * @return
     * @throws Exception
     */
    public static String getRedirectUrl(String path) {
        Connection.Response response;
        try {
            response = Jsoup.connect(path).ignoreHttpErrors(true)
                    .followRedirects(false)
                    .ignoreContentType(true).header("Connection", "Keep-Alive")
                    .header("User-Agent", "Apache-HttpClient/4.5.14 (Java/17.0.8.1)")
                    .header("Accept-Encoding", "br,deflate,gzip,x-gzip").method(Connection.Method.HEAD).execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        String url = response.header("Location");
        if (url == null) url = response.header("location");
        if (url == null) url = response.url().toString();
        System.out.println(path + " => redirect to " + url);
        return url;
    }

    public static String getAllStatus(long bid, AuthMapper authMapper) {
        System.gc();
        AuthM authM = authMapper.selectById(String.valueOf(bid));
        StringBuilder sb = new StringBuilder();
        Bot bot = Bot.getInstance(bid);
        if (bot instanceof RemoteBot) {
            RemoteBot remoteBot = ((RemoteBot) bot);
            sb.append("ID: ").append(bot.getBot().getNick()).append("(").append(bid).append(")");
            sb.append("\n协议: ").append(remoteBot.getAppName()).append(" v.").append(remoteBot.getAppVersion());
            sb.append("\n过期: ").append(BaseComponent.SIMPLE_DATE_FORMAT.format(authM.getExp()));
            sb.append("\n上线: ").append(BaseComponent.SIMPLE_DATE_FORMAT.format(authM.getT0()));
        }

        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem os = systemInfo.getOperatingSystem();
        // 获取当前进程 ID
        int pid = os.getProcessId();
        // 获取当前进程对象
        OSProcess currentProcess = os.getProcess(pid);
        // 获取内存占用（常驻内存集大小，即实际物理内存）
        long memoryUsageBytes = currentProcess.getResidentSetSize();
        sb.append("\n程序占用内存: " + formatBytes(memoryUsageBytes));

        GlobalMemory memory = systemInfo.getHardware().getMemory();

        long totalPhysical = memory.getTotal();
        long availablePhysical = memory.getAvailable();
        long usedPhysical = totalPhysical - availablePhysical;

        sb.append("\n物理总占内存: " + (usedPhysical * 100 / totalPhysical) + "%");

        sb.append("\n物理总用内存: " + formatBytes(usedPhysical) + "/" + formatBytes(totalPhysical));

        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        Util.sleep(1000);
        double usage = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        sb.append("\n" + String.format("CPU使用率: %.2f%%", usage));
        sb.append("\nDG版本: v25.0821");
        if (isLinux()) {
            sb.append("\nCPU温度: " + getCpuTemplate());
        }
        return sb.toString();
    }

    public static boolean isLinux() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("linux")) {
            return true;
        } else {
            return false;
        }
    }

    public static String getCpuTemplate() {
        try {
            Process process = Runtime.getRuntime().exec("cat /sys/class/thermal/thermal_zone0/temp");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            double tempCelsius = Double.parseDouble(line) / 1000.0;
            return String.format("%.1f°C", tempCelsius);
        } catch (Exception e) {
            System.err.println("读取失败，需root权限或驱动支持");
            return null;
        }
    }

    // 字节单位转换工具（如 B → GB）
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), unit);
    }

    public static final Random RANDOM = new Random();

    public static final <T, K1, K2> T getValueOrDefault(Map<K1, Map<K2, T>> map, K1 k1, K2 k2, T def) {
        if (map.containsKey(k1)) {
            Map<K2, T> m2 = map.get(k1);
            if (m2.containsKey(k2)) {
                return m2.get(k2);
            } else {
                m2.put(k2, def);
                map.put(k1, m2);
                return def;
            }
        } else {
            Map<K2, T> m2 = new HashMap<>();
            m2.put(k2, def);
            map.put(k1, m2);
            return def;
        }
    }

    public static <T> T getRandT(List<T> ts) {
        return ts.get(RANDOM.nextInt(ts.size()));
    }

    private static final Map<Long, Object> BP_SYNC_MAP = new HashMap<>();

    public static Object getBpSync(Long bid) {
        long key = bid.longValue();
        if (!BP_SYNC_MAP.containsKey(key)) {
            BP_SYNC_MAP.put(key, new Object());
        }
        return BP_SYNC_MAP.get(key);
    }


    public static String getExceptionLine(Throwable e) {
        try {
            Method method = Throwable.class.getDeclaredMethod("getOurStackTrace");
            method.setAccessible(true);
            Object[] objects = (Object[]) method.invoke(e);
            StringBuilder sb = new StringBuilder("\r\n");
            for (Object o : objects) {
                sb.append(" at ").append(o.toString()).append("\r\n\t");
            }
            return sb.toString();
        } catch (Exception e1) {
            return "??";
        }
    }


    /**
     * 判断表达式是否成立
     *
     * @param jude
     * @param json
     * @return
     */
    public static boolean jude(String jude, String json) {
        if (Judge.isEmpty(jude)) return false;
        String[] judes = jude.split("\\|\\||&&");
        Logic logic = null;
        if (judes.length == 2) {
            if (jude.contains("&&")) {
                logic = new Logic(Logic.LIMITER_AND,
                        new Logic(null, null, judes[1])
                        , judes[0]);
            } else if (jude.contains("||")) {
                logic = new Logic(Logic.LIMITER_OR,
                        new Logic(null, null, judes[1])
                        , judes[0]);
            }
        } else {
            logic = new Logic(null, null, judes[0]);
        }
        return logic.getV(json);
    }

    @NotNull
    public static String getLineString(MessageEvent event) {
        StringBuilder line = new StringBuilder();
        for (SingleMessage singleMessage : event.getMessage()) {
            if (singleMessage instanceof PlainText) {
                line.append(((PlainText) singleMessage).getContent().trim());
            }
        }
        return line.toString().trim();
    }

    @Setter
    public static class Logic {
        public static final String LIMITER_AND = "and";
        public static final String LIMITER_OR = "or";
        private String limiter;
        private Boolean v;
        private Logic logic;
        private String exp;

        public Boolean getV(String json) {
            if (logic != null) {
                if (LIMITER_AND.equals(limiter)) {
                    return getLogic(json) && logic.getLogic(json);
                } else if (LIMITER_OR.equals(limiter)) {
                    return getLogic(json) || logic.getLogic(json);
                } else return false;
            } else return getLogic(json);
        }

        public Boolean getLogic(String json) {
            String[] kov = exp.split("[><]|={2}");
            String key = kov[0];
            String value = kov[1];
            String op = exp.substring(key.length(), key.length() + (exp.length() - key.length() - value.length()));
            if (op.equals("==")) {
                Object kv;
                if (key.equals("$all")) kv = json;
                else kv = Converter.getOutEnd(json, key, null);
                if (value.equalsIgnoreCase("NULL")) {
                    if (kv == null || "NULL".equalsIgnoreCase(kv.toString())) return true;
                    else return false;
                } else {
                    return value.equals(kv.toString());
                }
            } else if (op.equals("<")) {
                try {
                    Double dk = Double.parseDouble(value);
                    Double kv = Double.parseDouble(Converter.getOutEnd(json, key, null).toString());
                    return kv < dk;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            } else if (op.equals(">")) {
                try {
                    Double dk = Double.parseDouble(value);
                    Double kv = Double.parseDouble(Converter.getOutEnd(json, key, null).toString());
                    return kv > dk;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
            return false;
        }

        public Logic() {
        }

        public Logic(String limiter, Logic logic, String exp) {
            this.limiter = limiter;
            this.logic = logic;
            this.exp = exp;
        }

    }

    /**
     * Created by lingsf on 2019/11/5.
     * modify by kloping on 2022/2/24
     * modify by kloping on 2023/7/17
     */
    public static class CreateTable {
        public static Map<String, String> javaProperty2SqlColumnMap = new HashMap<>();

        static {
            javaProperty2SqlColumnMap.put("Integer", "INTEGER");
            javaProperty2SqlColumnMap.put("Long", "BIGINT");
            javaProperty2SqlColumnMap.put("long", "BIGINT");
            javaProperty2SqlColumnMap.put("Float", "REAL");
            javaProperty2SqlColumnMap.put("Boolean", "BLOB");
            javaProperty2SqlColumnMap.put("boolean", "BLOB");
            javaProperty2SqlColumnMap.put("String", "VARCHAR(255)");
            javaProperty2SqlColumnMap.put("String", "VARCHAR(255)");
        }

        public static String createTable(Class<?> cla) throws IOException {
            String sName = cla.getSimpleName();
            sName = filterName(sName);
            return createTable(cla, sName);
        }

        public static String filterName(String sName) {
            String firstS = sName.substring(0, 1).toLowerCase();
            sName = firstS + sName.substring(1);
            Matcher matcher = p0.matcher(sName);
            while (matcher.find()) {
                String g0 = matcher.group();
                String g1 = toLow(g0);
                sName = sName.replace(g0, g1);
            }
            return sName;
        }

        public static final Pattern p0 = Pattern.compile("[A-Z]");

        public static String createTable(Class<?> clz, String tableName) throws IOException {
            Field[] fields = null;
            fields = clz.getDeclaredFields();
            String param = null;
            String column = null;
            StringBuilder sb = null;
            sb = new StringBuilder(50);
            sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" ( \r\n");
            boolean firstId = true;
            File file = null;
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                column = f.getName();
                column = filterName(column);
                param = f.getType().getSimpleName();
                sb.append("`");
                sb.append(column);
                String typeName = javaProperty2SqlColumnMap.get(param);
                if (typeName == null) {
                    if (f.getType().isEnum()) {
                        typeName = javaProperty2SqlColumnMap.get("String");
                    } else typeName = "BLOB";
                }
                sb.append("`").append(typeName).append(" NOT NULL ");
                if (f.isAnnotationPresent(TableId.class)) {
                    TableId tableId = f.getDeclaredAnnotation(TableId.class);
                    boolean auto = tableId.type().equals(IdType.AUTO);
                    if (auto) sb.append("primary key autoincrement");
                    else sb.append("primary key");
                }
                sb.append(",\n ");
            }
            String sql = null;
            sql = sb.toString();
            int lastIndex = sql.lastIndexOf(",");
            sql = sql.substring(0, lastIndex) + sql.substring(lastIndex + 1);
            sql = sql + "\n);\n";
            return sql;
        }

        public static String toLow(String s1) {
            switch (s1.charAt(0)) {
                case 'A':
                    return "_a";
                case 'B':
                    return "_b";
                case 'C':
                    return "_c";
                case 'D':
                    return "_d";
                case 'E':
                    return "_e";
                case 'F':
                    return "_f";
                case 'G':
                    return "_g";
                case 'H':
                    return "_h";
                case 'I':
                    return "_i";
                case 'J':
                    return "_j";
                case 'K':
                    return "_k";
                case 'L':
                    return "_l";
                case 'M':
                    return "_m";
                case 'N':
                    return "_n";
                case 'O':
                    return "_o";
                case 'P':
                    return "_p";
                case 'Q':
                    return "_q";
                case 'R':
                    return "_r";
                case 'S':
                    return "_s";
                case 'T':
                    return "_t";
                case 'U':
                    return "_u";
                case 'V':
                    return "_v";
                case 'W':
                    return "_w";
                case 'X':
                    return "_x";
                case 'Y':
                    return "_y";
                case 'Z':
                    return "_z";
                default:
                    return s1;
            }
        }
    }

}
