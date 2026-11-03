package com.vivotomirpay.stub;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

/**
 * Хранилище настроек заглушки (SharedPreferences).
 *
 * Ключи совместимы с предыдущими версиями: выбор цели (target_package), флаг
 * вибрации (vibrate_on_trigger) и состояние фонарика (torch_on) читаются так же,
 * как в версии 1.3.x, так что обновление не сбрасывает настройки пользователя.
 *
 * Новое в 2.0:
 *  - lockscreen_enabled / lockscreen_action — отдельное действие на заблокированном экране;
 *  - вибрация: длительность и паттерн (single / double / long);
 *  - vpn_client — какой VPN-клиент переключает действие special:vpn;
 *  - menu_items — JSON-массив элементов настраиваемого меню действий.
 */
public class PrefsHelper {

    private static final String PREFS_NAME = "vivo_swipe_stub_prefs";
    private static final String KEY_TARGET_PACKAGE = "target_package";
    private static final String KEY_DOUBLE_PRESS_PACKAGE = "double_press_package";
    private static final String KEY_VIBRATE = "vibrate_on_trigger";
    private static final String KEY_LAST_TRIGGER = "last_trigger_log";
    private static final String KEY_TORCH_ON = "torch_on";

    // --- Экран блокировки ---------------------------------------------------
    private static final String KEY_LOCKSCREEN_ENABLED = "lockscreen_enabled";
    private static final String KEY_LOCKSCREEN_ACTION = "lockscreen_action";

    // --- Вибрация -----------------------------------------------------------
    private static final String KEY_VIBRATE_DURATION = "vibrate_duration_ms";
    private static final String KEY_VIBRATE_PATTERN = "vibrate_pattern"; // single|double|long

    // --- VPN ----------------------------------------------------------------
    private static final String KEY_VPN_CLIENT = "vpn_client"; // auto|happ|incy

    // --- Меню действий -------------------------------------------------------
    private static final String KEY_MENU_ITEMS = "menu_items_json";

    /** Ограничения длины лога, чтобы любой злоумышленник, отправивший огромный Intent в
     *  экспортированную Activity, не смог раздуть SharedPreferences и UI заглушки. */
    private static final int LOG_MAX_TOTAL = 1000;
    private static final int LOG_MAX_EXTRA_VALUE = 100;

    // Спец-значения для пресетов, которые не являются packageName, а переключают
    // системные функции устройства напрямую.
    public static final String SPECIAL_PREFIX = "special:";
    public static final String SPECIAL_TORCH = "special:torch";
    public static final String SPECIAL_CAMERA = "special:camera";
    public static final String SPECIAL_CIRCLE_TO_SEARCH = "special:circle_to_search";
    public static final String SPECIAL_VPN = "special:vpn";
    public static final String SPECIAL_MENU = "special:menu";

    /** Префикс id пункта меню-приложения: "app:<packageName>". */
    public static final String APP_PREFIX = "app:";
    /** Максимальное число пунктов настраиваемого меню (MenuActivity показывает не больше). */
    public static final int MAX_MENU_ITEMS = 16;

    public static final String DEFAULT_TARGET_PACKAGE = "ru.nspk.mirpay";
    public static final String DEFAULT_LOCKSCREEN_ACTION = SPECIAL_TORCH;
    public static final int DEFAULT_VIBRATE_DURATION_MS = 40;

    // ---------------------------------------------------------------------
    // Целевое действие двойного нажатия
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Экран блокировки
    // ---------------------------------------------------------------------

    /** Включено ли отдельное действие на заблокированном экране. По умолчанию включено. */
    public static boolean isLockscreenEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_LOCKSCREEN_ENABLED, true);
    }

    public static void setLockscreenEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_LOCKSCREEN_ENABLED, enabled).apply();
    }

    /** Действие, выполняемое при заблокированном экране (спец-значение или пакет). */
    public static String getLockscreenAction(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String action = prefs.getString(KEY_LOCKSCREEN_ACTION, DEFAULT_LOCKSCREEN_ACTION);
        if (action == null || action.trim().isEmpty()) {
            return DEFAULT_LOCKSCREEN_ACTION;
        }
        return action.trim();
    }

    public static void setLockscreenAction(Context context, String action) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOCKSCREEN_ACTION, action != null ? action.trim() : DEFAULT_LOCKSCREEN_ACTION).apply();
    }

    // ---------------------------------------------------------------------
    // Вибрация
    // ---------------------------------------------------------------------

    public static boolean isVibrateEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_VIBRATE, true);
    }

    public static void setVibrateEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_VIBRATE, enabled).apply();
    }

    /** Длительность одного импульса вибрации в мс (20..500). */
    public static int getVibrateDurationMs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int v = prefs.getInt(KEY_VIBRATE_DURATION, DEFAULT_VIBRATE_DURATION_MS);
        if (v < 20) v = 20;
        if (v > 500) v = 500;
        return v;
    }

    public static void setVibrateDurationMs(Context context, int durationMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_VIBRATE_DURATION, durationMs).apply();
    }

    /** Паттерн вибрации: "single" | "double" | "long". */
    public static String getVibratePattern(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String p = prefs.getString(KEY_VIBRATE_PATTERN, "single");
        if (!"single".equals(p) && !"double".equals(p) && !"long".equals(p)) {
            return "single";
        }
        return p;
    }

    public static void setVibratePattern(Context context, String pattern) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VIBRATE_PATTERN, pattern).apply();
    }

    // ---------------------------------------------------------------------
    // VPN
    // ---------------------------------------------------------------------

    /** Выбранный VPN-клиент: "auto" | "happ" | "incy". */
    public static String getVpnClient(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String c = prefs.getString(KEY_VPN_CLIENT, "auto");
        if (!"auto".equals(c) && !"happ".equals(c) && !"incy".equals(c)) {
            return "auto";
        }
        return c;
    }

    public static void setVpnClient(Context context, String client) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_VPN_CLIENT, client != null ? client.trim() : "auto").apply();
    }

    // ---------------------------------------------------------------------
    // Меню действий
    // ---------------------------------------------------------------------

    /** Пакет приложения из id пункта меню ("app:<pkg>" -> pkg), иначе null. */
    public static String appPackageOf(String id) {
        if (id == null) return null;
        String t = id.trim();
        if (t.startsWith(APP_PREFIX)) {
            String pkg = t.substring(APP_PREFIX.length()).trim();
            return pkg.isEmpty() ? null : pkg;
        }
        return null;
    }

    /** Элементы меню — JSON-массив строк: "special:*" или "app:<packageName>". */
    public static String getMenuItemsJson(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_MENU_ITEMS, null);
    }

    public static void setMenuItemsJson(Context context, String json) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MENU_ITEMS, json).apply();
    }

    /** Нормализованный список элементов меню (с дефолтом при первом запуске). */
    public static JSONArray getMenuItems(Context context) {
        try {
            String raw = getMenuItemsJson(context);
            if (raw != null && !raw.trim().isEmpty()) {
                return new JSONArray(raw);
            }
        } catch (Exception ignored) {
        }
        // Дефолтное меню первого запуска: фонарик, камера, VPN, Circle to Search.
        JSONArray arr = new JSONArray();
        arr.put(SPECIAL_TORCH);
        arr.put(SPECIAL_CAMERA);
        arr.put(SPECIAL_VPN);
        arr.put(SPECIAL_CIRCLE_TO_SEARCH);
        return arr;
    }

    public static void saveMenuItems(Context context, JSONArray items) {
        setMenuItemsJson(context, items.toString());
    }

    // ---------------------------------------------------------------------
    // Фонарик
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Журнал
    // ---------------------------------------------------------------------

    public static String getLastTriggerLog(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_TRIGGER, "");
    }

    public static void clearTriggerLog(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_LAST_TRIGGER).apply();
    }

    public static void saveTriggerLog(Context context, android.content.Intent intent,
                                      String launchedTarget, String status) {
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            StringBuilder sb = new StringBuilder();
            sb.append("Time: ").append(sdf.format(new java.util.Date())).append("\n");
            if (intent != null) {
                sb.append("Action: ").append(safe(intent.getAction(), LOG_MAX_EXTRA_VALUE)).append("\n");
                android.os.Bundle extras = intent.getExtras();
                if (extras != null && !extras.isEmpty()) {
                    sb.append("Extras: ");
                    for (String key : extras.keySet()) {
                        Object val = extras.get(key);
                        sb.append(key).append("=").append(safe(String.valueOf(val), LOG_MAX_EXTRA_VALUE)).append("; ");
                    }
                    sb.append("\n");
                }
            }
            sb.append("Target: ").append(safe(launchedTarget, LOG_MAX_EXTRA_VALUE)).append("\n");
            sb.append("Status: ").append(safe(status, LOG_MAX_EXTRA_VALUE));

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
