package io.github.gdpl2112.dg_bot.security;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存令牌存储，维护用户ID与令牌的映射关系
 * 支持单点登录：同一用户仅保留最新令牌，新登录使旧令牌失效
 * 令牌有效期为2小时
 */
public class TokenStore {

    /** 令牌有效期：2小时（毫秒） */
    private static final long TOKEN_VALIDITY_MS = 2 * 60 * 60 * 1000L;

    /** userId -> TokenEntry */
    private final ConcurrentHashMap<String, TokenEntry> userStore = new ConcurrentHashMap<>();

    /** token -> userId（反向查找，用于过滤器快速定位用户） */
    private final ConcurrentHashMap<String, String> tokenToUser = new ConcurrentHashMap<>();

    /**
     * 为用户创建新令牌，同时使该用户之前的令牌失效（单点）
     *
     * @param userId 用户ID
     * @return 新生成的令牌字符串
     */
    public String createToken(String userId) {
        // 移除旧令牌的反向映射
        TokenEntry old = userStore.get(userId);
        if (old != null) {
            tokenToUser.remove(old.token);
        }
        // 生成新令牌
        String token = UUID.randomUUID().toString().replace("-", "");
        TokenEntry entry = new TokenEntry(token, System.currentTimeMillis());
        userStore.put(userId, entry);
        tokenToUser.put(token, userId);
        return token;
    }

    /**
     * 验证令牌是否有效（存在且未过期）
     *
     * @param token 令牌字符串
     * @return 有效时返回对应的用户ID，无效返回null
     */
    public String validateToken(String token) {
        if (token == null || token.isEmpty()) return null;
        String userId = tokenToUser.get(token);
        if (userId == null) return null;
        TokenEntry entry = userStore.get(userId);
        if (entry == null || !entry.token.equals(token)) return null;
        // 检查是否过期
        if (System.currentTimeMillis() - entry.createdAt > TOKEN_VALIDITY_MS) {
            removeToken(userId);
            return null;
        }
        return userId;
    }

    /**
     * 移除用户令牌（登出时调用）
     *
     * @param userId 用户ID
     */
    public void removeToken(String userId) {
        TokenEntry entry = userStore.remove(userId);
        if (entry != null) {
            tokenToUser.remove(entry.token);
        }
    }

    /**
     * 清理所有过期令牌
     */
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        userStore.entrySet().removeIf(e -> {
            if (now - e.getValue().createdAt > TOKEN_VALIDITY_MS) {
                tokenToUser.remove(e.getValue().token);
                return true;
            }
            return false;
        });
    }

    /**
     * 令牌条目，记录令牌值与创建时间
     */
    private static class TokenEntry {
        final String token;
        final long createdAt;

        TokenEntry(String token, long createdAt) {
            this.token = token;
            this.createdAt = createdAt;
        }
    }
}
