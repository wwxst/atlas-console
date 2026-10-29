package com.web.project.auth.presence;

import com.web.project.auth.model.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Online means the account used an authenticated API in the last three minutes.
 * The TTL is deliberately independent of the much longer login session TTL.
 * Multiple devices share one account-presence key, so one device logging out
 * cannot incorrectly mark another active device offline.
 */
@Component
@RequiredArgsConstructor
public class AuthPresenceStore {
    public static final Duration ONLINE_WINDOW = Duration.ofMinutes(3);
    private static final String KEY_PREFIX = "auth:presence:";

    private final StringRedisTemplate redisTemplate;

    public void touch(AccountType type, Long accountId) {
        redisTemplate.opsForValue().set(key(type, accountId), "1", ONLINE_WINDOW);
    }

    public boolean isOnline(AccountType type, Long accountId) {
        return "1".equals(redisTemplate.opsForValue().get(key(type, accountId)));
    }

    /** One Redis multi-get per list page instead of one round-trip per row. */
    public Set<Long> onlineIds(AccountType type, List<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }

        List<String> keys = ids.stream().map(id -> key(type, id)).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return Set.of();
        }

        Set<Long> online = new HashSet<>();
        for (int i = 0; i < ids.size() && i < values.size(); i++) {
            if ("1".equals(values.get(i))) {
                online.add(ids.get(i));
            }
        }
        return online;
    }

    private String key(AccountType type, Long accountId) {
        return KEY_PREFIX + type.name() + ":" + accountId;
    }
}
