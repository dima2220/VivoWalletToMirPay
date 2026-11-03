package com.vivotomirpay.stub;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Исполнитель действий: единая точка, через которую работают и перехватчик
 * двойного нажатия (SwipeEntryActivity), и настраиваемое меню (MenuActivity).
 *
 * Поддерживаемые значения:
 *   - "special:torch"            — переключение фонарика;
 *   - "special:camera"           — запуск камеры (работает и с заблокированного экрана);
 *   - "special:circle_to_search" — Circle to Search через VoiceInteractionService (как MiCTS);
 *   - "special:vpn"              — переключение VPN Happ / incy (см. VpnController);
 *   - "special:menu"             — открытие настраиваемого меню действий (MenuActivity);
 *   - "app:<packageName>" или "<packageName>" — запуск установленного приложения.
 */
public final class ActionRunner {

    private static final int OMNI_ENTRY_POINT_APP_LAUNCH = 1;

    private ActionRunner() {
    }

    /** Нормализует id действия: спец-значения как есть, пусто -> null. */
    public static String normalize(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Выполняет действие и возвращает статус для журнала. Никогда не бросает исключений.
     */
    public static String runAction(Context context, String actionId, Intent sourceIntent) {
        String id = normalize(actionId);
        if (id == null) {
            PrefsHelper.saveTriggerLog(context, sourceIntent, null,
                    context.getString(R.string.log_reason_not_set));
            return context.getString(R.string.log_reason_not_set);
        }

        try {
            if (id.startsWith(PrefsHelper.SPECIAL_PREFIX)) {
                return runSpecial(context, id, sourceIntent);
            }
            // Пункты меню хранятся как "app:<packageName>" — префикс нужно снять,
            // иначе launchApp ищет пакет с буквально таким именем и не находит его.
            if (id.startsWith(PrefsHelper.APP_PREFIX)) {
                String pkg = PrefsHelper.appPackageOf(id);
                if (pkg == null) {
                    String bad = context.getString(R.string.log_app_not_found, id);
                    PrefsHelper.saveTriggerLog(context, sourceIntent, id, bad);
                    return bad;
                }
                id = pkg;
            }
            return launchApp(context, id, sourceIntent);
        } catch (Exception e) {
            String status = context.getString(R.string.log_action_failed, safeMsg(e));
            PrefsHelper.saveTriggerLog(context, sourceIntent, id, status);
            return status;
        }
    }

    private static String runSpecial(Context context, String id, Intent sourceIntent) {
        switch (id) {
            case PrefsHelper.SPECIAL_TORCH:
                return toggleFlashlight(context, sourceIntent, id);

            case PrefsHelper.SPECIAL_CAMERA:
                return launchCamera(context, sourceIntent, id);

            case PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH:
                return triggerCircleToSearch(context, sourceIntent, id);

            case PrefsHelper.SPECIAL_VPN:
                String clientSetting = PrefsHelper.getVpnClient(context);
                String vpnResult = VpnController.controlVpn(context, clientSetting, "toggle");
                PrefsHelper.saveTriggerLog(context, sourceIntent, id, vpnResult);
                return vpnResult;

            case PrefsHelper.SPECIAL_MENU:
                try {
                    Intent menu = new Intent(context, MenuActivity.class);
                    menu.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    context.startActivity(menu);
                    String ok = context.getString(R.string.log_menu_opened);
                    PrefsHelper.saveTriggerLog(context, sourceIntent, id, ok);
                    return ok;
                } catch (Exception e) {
                    String fail = context.getString(R.string.log_menu_failed, safeMsg(e));
                    PrefsHelper.saveTriggerLog(context, sourceIntent, id, fail);
                    return fail;
                }

            default:
                String unknown = context.getString(R.string.log_unknown_special, id);
                PrefsHelper.saveTriggerLog(context, sourceIntent, id, unknown);
                return unknown;
        }
    }

    // ------------------------------------------------------------------
    // Фонарик
    // ------------------------------------------------------------------

    public static String toggleFlashlight(Context context, Intent sourceIntent, String targetPkg) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            String msg = context.getString(R.string.log_torch_api);
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
            return msg;
        }
        CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) {
            String msg = context.getString(R.string.log_torch_no_manager);
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
            return msg;
        }
        try {
            String[] ids = cm.getCameraIdList();
            if (ids == null || ids.length == 0) {
                String msg = context.getString(R.string.log_torch_no_flash);
                PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
                return msg;
            }
            boolean currentlyOn = PrefsHelper.isTorchOn(context);
            boolean next = !currentlyOn;
            cm.setTorchMode(ids[0], next);
            PrefsHelper.setTorchOn(context, next);
            String msg = context.getString(next
                    ? R.string.log_torch_on : R.string.log_torch_off);
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
            return msg;
        } catch (Exception e) {
            String msg = context.getString(R.string.log_torch_error, safeMsg(e));
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
            return msg;
        }
    }

    // ------------------------------------------------------------------
    // Камера
    // ------------------------------------------------------------------

    /**
     * Запуск камеры. Сначала пробуем защищённый интент
     * INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE — именно он предназначен для запуска
     * камеры поверх экрана блокировки (так её запускает штатный ярлык на локскрине):
     * камеры, зарегистрированные как «secure camera» (Google Camera, стоковые),
     * открываются с атрибутом showWhenLocked. Если защищённый интент не резолвится —
     * обычный STILL_IMAGE_CAMERA.
     */
    public static String launchCamera(Context context, Intent sourceIntent, String targetPkg) {
        try {
            PackageManager pm = context.getPackageManager();
            boolean secure = true;
            Intent camera = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
            if (camera.resolveActivity(pm) == null) {
                camera = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                secure = false;
            }
            if (camera.resolveActivity(pm) == null) {
                String msg = context.getString(R.string.log_camera_not_found);
                PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
                return msg;
            }
            camera.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            context.startActivity(camera);
            String ok = context.getString(secure
                    ? R.string.log_camera_ok_secure : R.string.log_camera_ok);
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, ok);
            return ok;
        } catch (Exception e) {
            String msg = context.getString(R.string.log_camera_error, safeMsg(e));
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
            return msg;
        }
    }

    // ------------------------------------------------------------------
    // Circle to Search (механизм MiCTS)
    // ------------------------------------------------------------------

    /**
     * Логика та же, что в проекте parallelcc/MiCTS:
     *   1) Bundle: invocation_time_ms, omni.entry_point = 1, micts_trigger = true;
     *   2) IBinder "voiceinteraction" через ServiceManager.getService();
     *   3) IVoiceInteractionManagerService.showSessionFromSession() через reflection
     *      + org.lsposed.hiddenapibypass (4 параметра на Android 14+, 3 — на старых).
     *   Не требует root, accessibility и разрешений.
     */
    public static String triggerCircleToSearch(Context context, Intent sourceIntent, String targetPkg) {
        try {
            try {
                org.lsposed.hiddenapibypass.HiddenApiBypass.setHiddenApiExemptions(
                        "Landroid/os/ServiceManager",
                        "Lcom/android/internal/app/IVoiceInteractionManagerService",
                        "Lcom/android/internal/app/IVoiceInteractionManagerService$Stub"
                );
            } catch (Throwable ignored) {
                // HiddenApiBypass.invoke() всё равно умеет дёргать @hide-методы.
            }

            android.os.Bundle bundle = new android.os.Bundle();
            bundle.putLong("invocation_time_ms", SystemClock.elapsedRealtime());
            bundle.putInt("omni.entry_point", OMNI_ENTRY_POINT_APP_LAUNCH);
            bundle.putBoolean("micts_trigger", true);

            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getService = serviceManagerClass.getMethod("getService", String.class);
            android.os.IBinder visBinder = (android.os.IBinder)
                    getService.invoke(null, "voiceinteraction");
            if (visBinder == null) {
                String msg = context.getString(R.string.log_cts_no_service);
                PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
                return msg;
            }

            Class<?> iVimsClass = Class.forName(
                    "com.android.internal.app.IVoiceInteractionManagerService");
            Class<?> stubClass = Class.forName(
                    "com.android.internal.app.IVoiceInteractionManagerService$Stub");
            Method asInterface = stubClass.getMethod("asInterface", android.os.IBinder.class);
            Object vims = asInterface.invoke(null, visBinder);
            if (vims == null) {
                String msg = context.getString(R.string.log_cts_no_interface);
                PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
                return msg;
            }

            // Android 14+: showSessionFromSession(token, bundle, sourceFlags, invocationContext);
            // старые версии: showSessionFromSession(token, bundle, sourceFlags).
            // В HiddenApiBypass только varargs-версия invoke() — параметры как в MiCTS.
            Object result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                result = org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(
                        iVimsClass, vims, "showSessionFromSession",
                        null, bundle, 7, "hyperOS_home");
            } else {
                result = org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(
                        iVimsClass, vims, "showSessionFromSession",
                        null, bundle, 7);
            }

            boolean success = (result instanceof Boolean) && (Boolean) result;
            String assistant = getCurrentDefaultAssistant(context);
            String msg = success
                    ? context.getString(R.string.log_cts_ok, assistant)
                    : context.getString(R.string.log_cts_false, assistant);
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
            return msg;
        } catch (Exception e) {
            String msg = context.getString(R.string.log_cts_error, safeMsg(e));
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, msg);
            return msg;
        }
    }

    private static String getCurrentDefaultAssistant(Context context) {
        try {
            String assistant = Settings.Secure.getString(
                    context.getContentResolver(), "assistant");
            if (assistant == null || assistant.isEmpty()) {
                return context.getString(R.string.assistant_none);
            }
            String pkg = assistant.contains("/") ? assistant.split("/", 2)[0] : assistant;
            String label = getAppLabel(context, pkg);
            if (label == null || label.equals(pkg)) {
                return assistant;
            }
            return label + " (" + pkg + ")";
        } catch (Exception e) {
            return context.getString(R.string.assistant_read_error);
        }
    }

    private static String getAppLabel(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return pkg;
        try {
            PackageManager pm = context.getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (Exception ignored) {
        }
        return pkg;
    }

    // ------------------------------------------------------------------
    // Запуск приложения
    // ------------------------------------------------------------------

    public static String launchApp(Context context, String targetPkg, Intent sourceIntent) {
        // Защита от прямых вызовов с id в формате меню ("app:<packageName>").
        String stripped = PrefsHelper.appPackageOf(targetPkg);
        if (stripped != null) targetPkg = stripped;

        if (getSelfPackage(context).equals(targetPkg)) {
            String reason = context.getString(R.string.log_reason_self);
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, reason);
            return reason;
        }

        PackageManager pm = context.getPackageManager();

        // Если выбран Vivo Wallet — запускаем сразу его NFC-ключ (NewSwipeActivity).
        if ("com.vivo.wallet".equals(targetPkg)) {
            try {
                Intent walletKeyIntent = new Intent(SwipeEntryActivity.ACTION_VIVO_SWIPE);
                walletKeyIntent.addCategory(Intent.CATEGORY_DEFAULT);
                walletKeyIntent.setComponent(new ComponentName(
                        "com.vivo.wallet",
                        "com.vivo.pay.swing.activity.NewSwipeActivity"
                ));
                walletKeyIntent.putExtra("lb_from", "2");
                walletKeyIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                );
                context.startActivity(walletKeyIntent);
                String ok = context.getString(R.string.log_wallet_ok);
                PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, ok);
                return ok;
            } catch (Exception e) {
                // Не выходим: пробуем обычные способы запуска.
            }
        }

        // 1) Приоритетный путь: getLaunchIntentForPackage.
        try {
            Intent launchIntent = pm.getLaunchIntentForPackage(targetPkg);
            if (launchIntent != null) {
                launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                );
                context.startActivity(launchIntent);
                String ok = context.getString(R.string.log_app_ok, getAppLabel(context, targetPkg));
                PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, ok);
                return ok;
            }
        } catch (Exception e) {
            // Переходим к fallback.
        }

        // 2) Fallback: поиск MAIN/LAUNCHER Activity внутри пакета.
        try {
            Intent mainQuery = new Intent(Intent.ACTION_MAIN);
            mainQuery.addCategory(Intent.CATEGORY_LAUNCHER);
            mainQuery.setPackage(targetPkg);
            List<ResolveInfo> matches = pm.queryIntentActivities(mainQuery, 0);
            ResolveInfo chosen = pickLauncherActivity(matches);
            if (chosen != null && chosen.activityInfo != null) {
                Intent explicit = new Intent(Intent.ACTION_MAIN);
                explicit.addCategory(Intent.CATEGORY_LAUNCHER);
                explicit.setComponent(new ComponentName(
                        chosen.activityInfo.packageName, chosen.activityInfo.name));
                explicit.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                );
                context.startActivity(explicit);
                String ok = context.getString(R.string.log_app_ok, getAppLabel(context, targetPkg));
                PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, ok);
                return ok;
            }

            Intent mainOnly = new Intent(Intent.ACTION_MAIN);
            mainOnly.setPackage(targetPkg);
            List<ResolveInfo> mainOnlyMatches = pm.queryIntentActivities(mainOnly, 0);
            if (mainOnlyMatches != null && !mainOnlyMatches.isEmpty()) {
                ResolveInfo ri = mainOnlyMatches.get(0);
                if (ri.activityInfo != null) {
                    Intent explicit = new Intent(Intent.ACTION_MAIN);
                    explicit.setComponent(new ComponentName(
                            ri.activityInfo.packageName, ri.activityInfo.name));
                    explicit.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    );
                    context.startActivity(explicit);
                    String ok = context.getString(R.string.log_app_ok, getAppLabel(context, targetPkg));
                    PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, ok);
                    return ok;
                }
            }
        } catch (Exception e) {
            String fail = context.getString(R.string.log_app_error, safeMsg(e));
            PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, fail);
            return fail;
        }

        String notFound = context.getString(R.string.log_app_not_found, targetPkg);
        PrefsHelper.saveTriggerLog(context, sourceIntent, targetPkg, notFound);
        return notFound;
    }

    private static ResolveInfo pickLauncherActivity(List<ResolveInfo> matches) {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        for (ResolveInfo ri : matches) {
            if (ri == null || ri.activityInfo == null) continue;
            if (ri.filter != null && ri.filter.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                return ri;
            }
        }
        return matches.get(0);
    }

    public static String getSelfPackage(Context context) {
        return context.getPackageName();
    }

    public static String safeMsg(Throwable t) {
        if (t == null) return "";
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }
}
