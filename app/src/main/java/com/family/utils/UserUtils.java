package com.family.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserUtils {
    private static final String[] DEFAULT_NAMES = {"Альбертик", "Джузеппе", "Пипа", "Бобик", "Слон", "Гришка", "ВасяПупкин", "Горпына", "Чиполино", "Тараканище", "Губозакаточный", "Кабан", "Бегемот", "Чикибамбони", "Забияка", "Чирипыжик", "Карлос", "Гонсалес"};

    public interface AdIdCallback {
        void onAdIdReceived(String adId);
    }

    public static void getAdvertisingId(Context context, AdIdCallback callback) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executorService.execute(() -> {
            String adId = null;
            try {
                AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
                adId = adInfo.getId();
            } catch (Exception e) {
                // Unrecoverable error connecting to Google Play services
            }

            // Check SharedPreferences
            String uniqueID = getSavedId(context);
            if (uniqueID == null) {
                // No ID in SharedPreferences
                if (adId == null) {
                    // No AdvertisingId, generate a new UUID
                    uniqueID = generateNewId(context);
                } else {
                    // Use AdvertisingId as the ID
                    uniqueID = adId;
                    saveId(context, uniqueID);
                }
            }
            // Otherwise, use the ID from SharedPreferences

            String finalAdId = uniqueID;
            handler.post(() -> callback.onAdIdReceived(finalAdId));
        });
    }

    public static String generateDefaultName() {
        return DEFAULT_NAMES[new Random().nextInt(DEFAULT_NAMES.length)];
    }

    private static String getSavedId(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        return sharedPrefs.getString("unique_id", null);
    }

    private static void saveId(Context context, String id) {
        SharedPreferences sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString("unique_id", id);
        editor.apply();
    }

    private static String generateNewId(Context context) {
        String uniqueID = UUID.randomUUID().toString();
        saveId(context, uniqueID);
        return uniqueID;
    }
}

