package com.vivotomirpay.stub;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PrefsHelper {

    private static final String PREFS_NAME = "vivo_swipe_stub_prefs";
    private static final String KEY_TARGET_PACKAGE = "target_package";
    private static final String KEY_DOUBLE_PRESS_PACKAGE = "double_press_package";
    private static final String KEY_VIBRATE = "vibrate_on_trigger";
    private static final String KEY_LAST_TRIGGER = "last_trigger_log";
    private static final String KEY_TORCH_ON = "torch_on";

    /** Ограничения длины лога, чтобы любой злоумышленник, отправивший огромный Intent в
     *  экспортированную Activity, не смог раздуть SharedPreferences и TextView в UI. */
    private static final int LOG_MAX_TOTAL = 1000;
    private static final int LOG_MAX_EXTRA_VALUE = 100;

    public static final String DEFAULT_TARGET_PACKAGE = "ru.nspk.mirpay";

    // Спец-значения для пресетов, которые не являются packageName, а переключают
    // системные функции устройства напрямую из SwipeEntryActivity.
    public static final String SPECIAL_PREFIX = "special:";
    public static final String SPECIAL_TORCH = "special:torch";
    public static final String SPECIAL_CIRCLE_TO_SEARCH = "special:circle_to_search";

    public static String getTargetPackage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String pkg = prefs.getString(KEY_TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE);
        if (pkg == null || pkg.trim().isEmpty()) {
            return DEFAULT_TARGET_PACKAGE;
        }
        return pkg.trim();
    }

    public static void setTargetPackage(Context context, String pkg) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_TARGET_PACKAGE, pkg != null ? pkg.trim() : DEFAULT_TARGET_PACKAGE).apply();
    }

    /** Пакет/спец-значение для двойного нажатия (пресеты Circle to Search / Gemini). */
    public static String getDoublePressPackage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DOUBLE_PRESS_PACKAGE, ""); // пусто = выключено
    }

    public static void setDoublePressPackage(Context context, String pkg) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DOUBLE_PRESS_PACKAGE, pkg != null ? pkg.trim() : "").apply();
    }

    /** Признак того, что фонарик сейчас включён. Используется как fallback, если
     *  CameraManager.TorchCallback по какой-то причине недоступен. */
    public static boolean isTorchOn(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_TORCH_ON, false);
    }

    public static void setTorchOn(Context context, boolean on) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_TORCH_ON, on).apply();
    }

    public static boolean isVibrateEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_VIBRATE, false);
    }

    public static void setVibrateEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_VIBRATE, enabled).apply();
    }

    public static String getLastTriggerLog(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_TRIGGER, "Пока не зафиксировано вызовов NFC_BUS_SWINGSPLASH");
    }

    public static void saveTriggerLog(Context context, Intent intent, String launchedTarget, String status) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            StringBuilder sb = new StringBuilder();
            sb.append("Время: ").append(sdf.format(new Date())).append("\n");
            if (intent != null) {
                sb.append("Action: ").append(safe(intent.getAction(), LOG_MAX_EXTRA_VALUE)).append("\n");
                Bundle extras = intent.getExtras();
                if (extras != null && !extras.isEmpty()) {
                    sb.append("Extras: ");
                    for (String key : extras.keySet()) {
                        // Ограничиваем длину значения каждого extra, чтобы большое содержимое
                        // от произвольного отправителя не раздувало SharedPreferences.
                        Object val = extras.get(key);
                        sb.append(key).append("=").append(safe(String.valueOf(val), LOG_MAX_EXTRA_VALUE)).append("; ");
                    }
                    sb.append("\n");
                }
            }
            sb.append("Целевой пакет: ").append(safe(launchedTarget, LOG_MAX_EXTRA_VALUE)).append("\n");
            sb.append("Статус: ").append(safe(status, LOG_MAX_EXTRA_VALUE));

            String result = sb.toString();
            if (result.length() > LOG_MAX_TOTAL) {
                result = result.substring(0, LOG_MAX_TOTAL - 3) + "...";
            }

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_LAST_TRIGGER, result).apply();
        } catch (Exception ignored) {
        }
    }

    /** Ограничивает длину строки до maxChars. Если null — заменяет на пустую строку. */
    private static String safe(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars - 3) + "...";
    }
}
