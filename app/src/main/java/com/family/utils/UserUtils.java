package com.family.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Random;
import java.util.UUID;

public class UserUtils {
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_USER_UUID = "user_uuid";
    private static final String KEY_USER_NAME = "user_name";

    private static final String[] DEFAULT_NAMES = {"Альбертик", "Джузеппе", "Пипа", "Бобик", "Слон", "Гришка", "ВасяПупкин", "Горпына", "Чиполино", "Тараканище", "Губозакаточный", "Кабан", "Бегемот", "Чикибамбони", "Забияка", "Чирипыжик", "Карлос", "Гонсалес"};

    /**
     * Returns a stable per-install identifier. Generated once on first launch and
     * persisted in SharedPreferences. Does NOT use Google Advertising ID — that
     * value is non-unique under "Limit Ad Tracking" (collapses to all-zeros across
     * devices) and is intended for ad attribution, not user identity.
     */
    public static String resolveUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_USER_UUID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_USER_UUID, id).apply();
        }
        return id;
    }

    public static String generateDefaultName() {
        return DEFAULT_NAMES[new Random().nextInt(DEFAULT_NAMES.length)];
    }

    public static String getSavedName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_NAME, null);
    }

    public static void saveName(Context context, String name) {
        if (name == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_NAME, name).apply();
    }
}
