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
    private static final String KEY_VIBRATE = "vibrate_on_trigger";
    private static final String KEY_LAST_TRIGGER = "last_trigger_log";

    public static final String DEFAULT_TARGET_PACKAGE = "ru.nspk.mirpay";

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
                sb.append("Action: ").append(intent.getAction()).append("\n");
                Bundle extras = intent.getExtras();
                if (extras != null && !extras.isEmpty()) {
                    sb.append("Extras: ");
                    for (String key : extras.keySet()) {
                        Object val = extras.get(key);
                        sb.append(key).append("=").append(val).append("; ");
                    }
                    sb.append("\n");
                }
            }
            sb.append("Целевой пакет: ").append(launchedTarget).append("\n");
            sb.append("Статус: ").append(status);

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_LAST_TRIGGER, sb.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
