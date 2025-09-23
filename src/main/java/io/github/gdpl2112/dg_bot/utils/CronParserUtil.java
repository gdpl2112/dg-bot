package io.github.gdpl2112.dg_bot.utils;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cron表达式解析工具类
 * 功能：Cron表达式验证、中文描述解析、下次执行时间计算
 * 支持中文描述格式：每天XX点XX分、每周XX点XX分、每月XX号XX点XX分、每小时、每分钟、每年X月X日X点X分
 */
public class CronParserUtil {

    private static final CronDefinition cronDefinition;
    private static final CronParser parser;
    private static final CronDescriptor descriptor;

    static {
        // 定义使用的Cron类型（Quartz，支持7个字段包括秒和年）
        cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
        parser = new CronParser(cronDefinition);
        // 获取中文描述器
        descriptor = CronDescriptor.instance(Locale.CHINA);
    }

    /**
     * 验证Cron表达式是否有效
     *
     * @param cronExpression Cron表达式字符串
     * @return 有效返回true，否则返回false
     */
    public static boolean isValidCronExpression(String cronExpression) {
        try {
            parser.parse(cronExpression);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 将Cron表达式解析为中文描述
     *
     * @param cronExpression Cron表达式字符串
     * @return 中文描述字符串
     * @throws IllegalArgumentException 当表达式无效时抛出异常
     */
    public static String parseToChineseDescription(String cronExpression) throws IllegalArgumentException {
        Cron cron = parser.parse(cronExpression);
        return descriptor.describe(cron);
    }

    /**
     * 获取Cron表达式下一次执行时间
     *
     * @param cronExpression Cron表达式字符串
     * @return 下一次执行时间的ZonedDateTime对象（Optional）
     * @throws IllegalArgumentException 当表达式无效时抛出异常
     */
    public static Optional<ZonedDateTime> getNextExecutionTime(String cronExpression) throws IllegalArgumentException {
        Cron cron = parser.parse(cronExpression);
        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime now = ZonedDateTime.now();
        return executionTime.nextExecution(now);
    }

    /**
     * 将中文描述转换为Cron表达式
     *
     * @param description 中文描述
     * @return 对应的Cron表达式
     * @throws UnsupportedOperationException 对于不支持的描述抛出异常
     */
    public static String convertDescriptionToCron(String description) throws UnsupportedOperationException {
        String lowerCaseDesc = description.toLowerCase().trim();

        if (lowerCaseDesc.contains("每天") && lowerCaseDesc.contains("点") && lowerCaseDesc.contains("分")) {
            // 解析"每天12点30分"格式
            try {
                String[] parts = description.split("每天|点|分");
                if (parts.length >= 3) {
                    String hourStr = parts[1].trim();
                    String minuteStr = parts[2].trim();
                    int hour = Integer.parseInt(hourStr);
                    int minute = Integer.parseInt(minuteStr);
                    if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                        return String.format("6 %d %d * * ?", minute, hour);
                    }
                }
            } catch (NumberFormatException e) {
                throw new UnsupportedOperationException("无法解析描述中的时间数字: " + description);
            }
        } else if (lowerCaseDesc.matches(".*\\d+月\\d+[日号 ]\\d+[点:]\\d+分.*")) {
            // 解析"9月22日20点8分"格式（每年执行）
            Pattern pattern = Pattern.compile("(\\d+)月(\\d+)[日号 ](\\d+)[点:](\\d+)分");
            Matcher matcher = pattern.matcher(lowerCaseDesc);
            if (matcher.find()) {
                try {
                    int month = Integer.parseInt(matcher.group(1));
                    int day = Integer.parseInt(matcher.group(2));
                    int hour = Integer.parseInt(matcher.group(3));
                    int minute = Integer.parseInt(matcher.group(4));

                    // 验证范围
                    if (month < 1 || month > 12) {
                        throw new UnsupportedOperationException("月份必须在1到12之间: " + description);
                    }
                    ValidValueExpression(description, day, hour, minute);

                    return String.format("6 %d %d %d %d ?", minute, hour, day, month);
                } catch (NumberFormatException e) {
                    throw new UnsupportedOperationException("无法解析描述中的数字: " + description);
                }
            }
        } else if (lowerCaseDesc.contains("每周") && lowerCaseDesc.contains("点") && lowerCaseDesc.contains("分")) {
            // 解析"每周一周二13点10分"格式
            try {
                // 提取星期部分和时间部分
                Pattern weeklyPattern = Pattern.compile("每周([^点]+)(\\d{1,2})[点:](\\d{1,2})分");
                Matcher weeklyMatcher = weeklyPattern.matcher(lowerCaseDesc);

                if (weeklyMatcher.find()) {
                    String daysPart = weeklyMatcher.group(1).trim();
                    int hour = Integer.parseInt(weeklyMatcher.group(2).trim());
                    int minute = Integer.parseInt(weeklyMatcher.group(3).trim());

                    // 验证时间范围
                    if (hour < 0 || hour > 23) {
                        throw new UnsupportedOperationException("小时必须在0到23之间: " + description);
                    }
                    if (minute < 0 || minute > 59) {
                        throw new UnsupportedOperationException("分钟必须在0到59之间: " + description);
                    }

                    // 解析星期部分
                    List<String> dayNumbers = new ArrayList<>();
                    Pattern dayPattern = Pattern.compile("[一二三四五六日天]");
                    Matcher dayMatcher = dayPattern.matcher(daysPart);

                    while (dayMatcher.find()) {
                        String dayChar = dayMatcher.group();
                        String dayS = convertChineseDayToNumber(dayChar);
                        if (dayS != null) {
                            dayNumbers.add(dayS);
                        }
                    }

                    if (dayNumbers.isEmpty()) {
                        throw new UnsupportedOperationException("未找到有效的星期: " + description);
                    }

                    // 构建星期部分的Cron表达式
                    StringBuilder daysCron = new StringBuilder();
                    for (int i = 0; i < dayNumbers.size(); i++) {
                        if (i > 0) {
                            daysCron.append(",");
                        }
                        daysCron.append(dayNumbers.get(i));
                    }

                    return String.format("6 %d %d ? * %s", minute, hour, daysCron.toString());
                }
            } catch (Exception e) {
                throw new UnsupportedOperationException("无法解析周计划描述: " + description);
            }
        } else if (lowerCaseDesc.contains("每月") && (lowerCaseDesc.contains("号") || lowerCaseDesc.contains("日")) && lowerCaseDesc.contains("点")) {
            // 解析"每月15号13点"格式
            try {
                Pattern monthlyPattern = Pattern.compile("每月(\\d+)[号日](\\d+)[点:](\\d+)?分?");
                Matcher monthlyMatcher = monthlyPattern.matcher(lowerCaseDesc);

                if (monthlyMatcher.find()) {
                    int day = Integer.parseInt(monthlyMatcher.group(1).trim());
                    int hour = Integer.parseInt(monthlyMatcher.group(2).trim());
                    int minute = 0;

                    // 如果有分钟部分
                    if (monthlyMatcher.group(3) != null) {
                        minute = Integer.parseInt(monthlyMatcher.group(3).trim());
                    }

                    // 验证范围
                    ValidValueExpression(description, day, hour, minute);

                    return String.format("6 %d %d %d * ?", minute, hour, day);
                }
            } catch (NumberFormatException e) {
                throw new UnsupportedOperationException("无法解析描述中的数字: " + description);
            }
        }
        throw new UnsupportedOperationException("暂不支持此描述的反向解析: " + description);
    }

    private static void ValidValueExpression(String description, int day, int hour, int minute) {
        if (day < 1 || day > 31) {
            throw new UnsupportedOperationException("日期必须在1到31之间: " + description);
        }
        if (hour < 0 || hour > 23) {
            throw new UnsupportedOperationException("小时必须在0到23之间: " + description);
        }
        if (minute < 0 || minute > 59) {
            throw new UnsupportedOperationException("分钟必须在0到59之间: " + description);
        }
    }

    /**
     * 将中文星期转换为数字（1-7，1表示星期一，7表示星期日）
     */
    private static String convertChineseDayToNumber(String dayStr) {
        switch (dayStr) {
            case "一":
                return "MON";
            case "二":
                return "TUE";
            case "三":
                return "WED";
            case "四":
                return "THU";
            case "五":
                return "FRI";
            case "六":
                return "SAT";
            case "日":
            case "天":
            case "七":
                return "SUN";
            default:
                return null;
        }
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        // 测试用例
        String[] testCases = {
                "每天12点30分",
                "每周一周二13点10分",
                "每周一、三、五8点30分",
                "每周星期二和星期四10点15分",
                "每月15号13点",
                "每月15号13点45分",
                "每小时",
                "每分钟",
                "9月22日20点8分",
                "9月22号22点37分",
                "12月25日10点30分"
        };

        System.out.println("=== 中文描述解析为Cron表达式 ===");

        for (String desc : testCases) {
            try {
                String cronExpr = convertDescriptionToCron(desc);
                String description = parseToChineseDescription(cronExpr);
                System.out.println("描述: " + desc);
                System.out.println("Cron表达式: " + cronExpr);
                System.out.println("解析描述: " + description);

                Optional<ZonedDateTime> nextTime = getNextExecutionTime(cronExpr);
                if (nextTime.isPresent()) {
                    System.out.println("下一次执行时间: " + nextTime.get());
                }
                System.out.println("-----");
            } catch (Exception e) {
                System.out.println("错误处理描述: " + desc + ", 错误: " + e.getMessage());
                System.out.println("-----");
            }
        }
    }
}
