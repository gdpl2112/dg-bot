package io.github.gdpl2112.dg_bot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author github-kloping
 * @date 2023-07-20
 */
public class Utils {
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

    public static final Random RANDOM = new Random();

    public static <T> T getRandT(List<T> ts) {
        return ts.get(RANDOM.nextInt(ts.size()));
    }

    public static boolean contains(Map<Long, List<String>> adding, Long bid, String tid) {
        if (adding.containsKey(bid)) {
            return adding.get(bid.longValue()).contains(tid);
        }
        return false;
    }
}
