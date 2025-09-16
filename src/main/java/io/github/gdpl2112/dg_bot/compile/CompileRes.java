package io.github.gdpl2112.dg_bot.compile;

import io.github.gdpl2112.dg_bot.DgMain;

import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * @author github kloping
 * @date 2025/9/4-14:17
 */
public class CompileRes {

    public static final String VERSION_DATE = "2025/0916";

    public static boolean isLinux() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("linux")) {
            return true;
        } else {
            return false;
        }
    }

    public static String getCompileTime() {
        try {
            Properties props = new Properties();
            InputStream input = DgMain.class.getResourceAsStream("/build-info.properties");

            if (input != null) {
                props.load(input);
                String timeStr = props.getProperty("build.time");
                // 创建适合的格式化器
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss X");
                // 解析时间（Z表示UTC时区）
                ZonedDateTime utcTime = ZonedDateTime.parse(timeStr, formatter);
                // 转换为东八区时间
                ZonedDateTime beijingTime = utcTime.withZoneSameInstant(ZoneId.of("GMT+8"));
                // 格式化输出
                DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return beijingTime.format(formatter);
            } else {
                return "未找到编译时间信息";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "获取编译时间失败";
        }
    }
}
