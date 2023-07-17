package io.github.gdpl2112.dg_bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.github.kloping.MySpringTool.h1.impl.component.PackageScannerImpl;
import io.github.kloping.MySpringTool.interfaces.Logger;
import io.github.kloping.MySpringTool.interfaces.component.PackageScanner;
import io.github.kloping.clasz.ClassUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author github-kloping
 * @date 2023-07-17
 */
@SpringBootApplication
@EnableAsync
@CrossOrigin
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
public class Main implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    Logger logger;

    @Override
    public void run(String... args) throws Exception {
        logger.info("start auto create need tables;");
        PackageScanner scanner = new PackageScannerImpl(true);
        for (Class<?> dclass :
                scanner.scan(Main.class, Main.class.getClassLoader(), "io.github.gdpl2112.dg_bot.dao")) {
            String sql = CreateTable.createTable(dclass);
            try {
                int state = jdbcTemplate.update(sql);
                if (state > 0) System.out.println(sql);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error(sql);
            }
        }
        logger.info("tables create finished");
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
        }

        private static String createTable(Class<?> cla) throws IOException {
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
                if (ClassUtils.isStatic(f)) continue;
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
                    }
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
