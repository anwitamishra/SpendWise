package com.spendwise.repository;

import org.springframework.jdbc.support.KeyHolder;

import java.util.Map;

public final class KeyHolderUtil {

    private KeyHolderUtil() {}

    public static Long extractId(KeyHolder keyHolder) {
        if (keyHolder == null) {
            return null;
        }

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null) {
            for (Map.Entry<String, Object> entry : keys.entrySet()) {
                if ("id".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number num) {
                    return num.longValue();
                }
            }
        }

        try {
            Number singleKey = keyHolder.getKey();
            return singleKey != null ? singleKey.longValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
