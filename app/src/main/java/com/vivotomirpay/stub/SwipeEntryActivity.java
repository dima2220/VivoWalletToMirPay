package com.vivotomirpay.stub;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/**
 * Прозрачная Activity-заглушка, которая перехватывает системный вызов Vivo
 * при двойном нажатии кнопки питания:
 *   action = "vivo.intent.action.NFC_BUS_SWINGSPLASH"
 *   category = "android.intent.category.DEFAULT"
 *   extra "lb_from" = "2"
 *
 * Сразу перенаправляет пользователя в Mir Pay (или другое выбранное приложение /
 * спец-функцию) и мгновенно закрывается без визуальной задержки.
 *
 * Пресет Circle to Search использует тот же механизм, что и MiCTS
 * (parallelcc/MiCTS): прямой вызов скрытого системного API
 * IVoiceInteractionManagerService.showSessionFromSession() через reflection
 * + библиотеку org.lsposed.hiddenapibypass. НЕ требует root, НЕ требует
 * accessibility-сервиса, НЕ требует никаких специальных разрешений.
 */
public class SwipeEntryActivity extends Activity {

    public static final String ACTION_VIVO_SWIPE = "vivo.intent.action.NFC_BUS_SWINGSPLASH";

    private static final int OMNI_ENTRY_POINT_APP_LAUNCH = 1;

    private CameraManager mCameraManager;
    private String mLastCameraId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        handleSwipeIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        overridePendingTransition(0, 0);
        handleSwipeIntent(intent);
    }

    private void handleSwipeIntent(Intent incomingIntent) {
        String targetPkg = PrefsHelper.getTargetPackage(this);

        if (PrefsHelper.isVibrateEnabled(this)) {
            triggerShortVibration();
        }

        boolean launched = launchTargetApplication(targetPkg, incomingIntent);
        if (!launched) {
            Intent configIntent = new Intent(this, MainActivity.class);
            configIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(configIntent);
        }

        finish();
        overridePendingTransition(0, 0);
    }

    private boolean launchTargetApplication(String targetPkg, Intent incomingIntent) {
        if (targetPkg == null || targetPkg.isEmpty() || getPackageName().equals(targetPkg)) {
            String reason = targetPkg == null || targetPkg.isEmpty()
                    ? "Целевой пакет не задан"
                    : "Целевой пакет указывает на само приложение-заглушку";
            PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg, reason);
            return false;
        }

        // =========================================================================
        // 0) Спец-значения (пресеты системных функций, не packageName)
        // =========================================================================
        if (targetPkg.startsWith(PrefsHelper.SPECIAL_PREFIX)) {
            try {
                if (PrefsHelper.SPECIAL_TORCH.equals(targetPkg)) {
                    return toggleFlashlight(incomingIntent, targetPkg);
                }
                if (PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH.equals(targetPkg)) {
                    return triggerCircleToSearch(incomingIntent, targetPkg);
                }
                // Неизвестное спец-значение
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Неизвестное спец-значение: " + targetPkg);
                return false;
            } catch (Exception e) {
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Ошибка спец-действия: " + safeMsg(e));
                return false;
            }
        }

        PackageManager pm = getPackageManager();

        // Если выбран Vivo Wallet — запускаем сразу его NFC-ключ (NewSwipeActivity).
        if ("com.vivo.wallet".equals(targetPkg)) {
            try {
                Intent walletKeyIntent = new Intent(ACTION_VIVO_SWIPE);
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
                startActivity(walletKeyIntent);
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Успешно запущен ключ Vivo Wallet (NewSwipeActivity)");
                return true;
            } catch (Exception e) {
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Vivo Wallet NewSwipeActivity не запустился: " + safeMsg(e)
                                + " — переходим к стандартному пути запуска");
                // Не выходим: пробуем обычные способы.
            }
        }

        // 1) Приоритетный путь: getLaunchIntentForPackage
        try {
            Intent launchIntent = pm.getLaunchIntentForPackage(targetPkg);
            if (launchIntent != null) {
                launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                );
                startActivity(launchIntent);
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Успешно запущено через getLaunchIntentForPackage");
                return true;
            }
        } catch (Exception e) {
            PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                    "getLaunchIntentForPackage упал: " + safeMsg(e));
        }

        // 2) Fallback: поиск MAIN/LAUNCHER Activity внутри пакета.
        try {
            Intent mainQuery = new Intent(Intent.ACTION_MAIN);
            mainQuery.addCategory(Intent.CATEGORY_LAUNCHER);
            mainQuery.setPackage(targetPkg);
            List<ResolveInfo> matches = pm.queryIntentActivities(mainQuery, 0);
            ResolveInfo chosen = pickLauncherActivity(matches, pm);
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
                startActivity(explicit);
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Успешно запущено через явный ComponentName (LAUNCHER-Activity)");
                return true;
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
                    startActivity(explicit);
                    PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                            "Успешно запущено через явный ComponentName (первая MAIN-Activity)");
                    return true;
                }
            }
        } catch (Exception e) {
            PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                    "Ошибка запуска: " + safeMsg(e));
            return false;
        }

        PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                "Целевое приложение не найдено / не запускается");
        return false;
    }

    private ResolveInfo pickLauncherActivity(List<ResolveInfo> matches, PackageManager pm) {
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

    // =========================================================================
    // Пресет Circle to Search.
    //
    // Логика та же, что в проекте parallelcc/MiCTS:
    // https://github.com/parallelcc/MiCTS/blob/main/app/src/main/java/com/parallelc/micts/ui/activity/MainActivity.kt
    //
    // Шаги:
    //   1) Строим Bundle с ключами:
    //        - invocation_time_ms  = SystemClock.elapsedRealtime()
    //        - omni.entry_point    = 1 (entry point = «открытие приложения-триггера»)
    //        - micts_trigger       = true (маркер для Xposed-хука, но и без него работает)
    //   2) Получаем системный IBinder "voiceinteraction" через ServiceManager.getService().
    //   3) Через reflection получаем IVoiceInteractionManagerService.Stub.asInterface(binder)
    //      и вызываем метод showSessionFromSession(IBinder, Bundle, int, String)
    //      (на Android 14+ 4 параметра, на старых — 3).
    //   4) Система воспринимает вызов как жест Circle to Search и открывает
    //      ассистента по умолчанию (Google или Gemini, если выбран в Google-приложении).
    //
    //   НЕ требует root, НЕ требует accessibility, НЕ требует никаких разрешений.
    // =========================================================================

    private boolean triggerCircleToSearch(Intent incomingIntent, String targetPkg) {
        try {
            // Глобально снимаем блокировку @hide для нужных классов через
            // VMRuntime.setHiddenApiExemptions(). На некоторых ROM (включая
            // Vivo OriginOS) эти методы могут быть в dark greylist — тогда plain
            // reflection падает с NoSuchMethodException. Вызов обёрнут в try/catch.
            try {
                org.lsposed.hiddenapibypass.HiddenApiBypass.setHiddenApiExemptions(
                        "Landroid/os/ServiceManager",
                        "Lcom/android/internal/app/IVoiceInteractionManagerService",
                        "Lcom/android/internal/app/IVoiceInteractionManagerService$Stub"
                );
            } catch (Throwable ignored) {
                // HiddenApiBypass.invoke() всё равно умеет дёргать @hide-методы через unsafe.
            }

            Bundle bundle = new Bundle();
            bundle.putLong("invocation_time_ms", SystemClock.elapsedRealtime());
            bundle.putInt("omni.entry_point", OMNI_ENTRY_POINT_APP_LAUNCH);
            bundle.putBoolean("micts_trigger", true);

            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getService = serviceManagerClass.getMethod("getService", String.class);
            IBinder visBinder = (IBinder) getService.invoke(null, "voiceinteraction");
            if (visBinder == null) {
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Circle to Search: сервис voiceinteraction недоступен (visBinder == null)");
                return false;
            }

            Class<?> iVimsClass = Class.forName(
                    "com.android.internal.app.IVoiceInteractionManagerService");
            Class<?> stubClass = Class.forName(
                    "com.android.internal.app.IVoiceInteractionManagerService$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            Object vims = asInterface.invoke(null, visBinder);
            if (vims == null) {
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Circle to Search: IVoiceInteractionManagerService.asInterface вернул null");
                return false;
            }

            // На Android 14+ (UPSIDE_DOWN_CAKE / API 34) метод имеет 4 параметра:
            //   showSessionFromSession(IBinder token, Bundle bundle, int sourceFlags, String invocationContext)
            // На старых версиях — 3 параметра:
            //   showSessionFromSession(IBinder token, Bundle bundle, int sourceFlags)
            //
            // Используем org.lsposed.hiddenapibypass, чтобы обойти @hide-ограничение на Android P+.
            //
            // ВАЖНО: в HiddenApiBypass есть только ОДНА версия invoke() — varargs
            // (Object... args), без отдельной типизированной версии с Class<?>[].
            // Если передать Class<?>[] первым аргументом, Java воспримет его как
            // первый элемент varargs-массива (т.к. массив — это Object), и длина
            // args не совпадёт с числом параметров метода — NoSuchMethodException.
            // Поэтому передаём РОВНО тот же набор, что в MiCTS:
            //   invoke(iVimsClass, vims, "showSessionFromSession", null, bundle, 7, "hyperOS_home")
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

            boolean success = (result instanceof Boolean) ? (Boolean) result : false;
            String currentAssistant = getCurrentDefaultAssistant();
            if (success) {
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Circle to Search: showSessionFromSession() вернул true, источник = 7. "
                                + "Текущий ассистент по умолчанию: " + currentAssistant);
                return true;
            } else {
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                        "Circle to Search: showSessionFromSession() вернул FALSE. "
                                + "Проверьте, что Google-приложение установлено и ассистент "
                                + "выбран по умолчанию. Текущий ассистент: " + currentAssistant);
                return false;
            }
        } catch (Exception e) {
            PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                    "Circle to Search не сработал: " + safeMsg(e));
            return false;
        }
    }

    // =========================================================================
    // Спец-функция: фонарик
    // =========================================================================

    private boolean toggleFlashlight(Intent incomingIntent, String targetPkg) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                    "Фонарик не поддерживается на API < 23");
            return false;
        }
        if (mCameraManager == null) {
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        }
        if (mCameraManager == null) {
            PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                    "CameraManager недоступен");
            return false;
        }
        try {
            String cameraId = mLastCameraId;
            if (cameraId == null) {
                String[] ids = mCameraManager.getCameraIdList();
                if (ids == null || ids.length == 0) {
                    PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                            "Не найдено ни одной камеры с вспышкой");
                    return false;
                }
                cameraId = ids[0];
                mLastCameraId = cameraId;
            }
            boolean currentlyOn = PrefsHelper.isTorchOn(this);
            boolean next = !currentlyOn;
            mCameraManager.setTorchMode(cameraId, next);
            PrefsHelper.setTorchOn(this, next);
            PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                    "Фонарик " + (next ? "ВКЛЮЧЁН" : "выключен"));
            return true;
        } catch (Exception e) {
            PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg,
                    "Ошибка фонарика: " + safeMsg(e));
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private void triggerShortVibration() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(35);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Читает текущего ассистента по умолчанию из Settings.Secure.ASSISTANT.
     *  Возвращает строку вида «Google (com.google.android.googlequicksearchbox)»
     *  или «не задан», если ассистент не выбран. */
    private String getCurrentDefaultAssistant() {
        try {
            String assistant = Settings.Secure.getString(
                    getContentResolver(), "assistant");
            if (assistant == null || assistant.isEmpty()) {
                return "не задан";
            }
            String pkg = assistant;
            if (assistant.contains("/")) {
                pkg = assistant.split("/", 2)[0];
            }
            String label = getAppLabel(pkg);
            if (label == null || label.equals(pkg)) {
                return assistant;
            }
            return label + " (" + pkg + ")";
        } catch (Exception e) {
            return "ошибка чтения: " + safeMsg(e);
        }
    }

    private String getAppLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return pkg;
        try {
            PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (Exception ignored) {
        }
        return pkg;
    }

    private static String safeMsg(Throwable t) {
        if (t == null) return "";
        String msg = t.getMessage();
        return msg != null ? msg : t.getClass().getSimpleName();
    }
}
