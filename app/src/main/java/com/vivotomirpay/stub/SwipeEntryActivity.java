package com.vivotomirpay.stub;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import java.util.List;

/**
 * Прозрачная Activity-заглушка, которая перехватывает системный вызов Vivo
 * при двойном нажатии кнопки питания:
 *   action = "vivo.intent.action.NFC_BUS_SWINGSPLASH"
 *   category = "android.intent.category.DEFAULT"
 *   extra "lb_from" = "2"
 *
 * Сразу перенаправляет пользователя в Mir Pay (или другое выбранное приложение)
 * и мгновенно закрывается без визуальной задержки.
 */
public class SwipeEntryActivity extends Activity {

    public static final String ACTION_VIVO_SWIPE = "vivo.intent.action.NFC_BUS_SWINGSPLASH";

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
            PrefsHelper.saveTriggerLog(
                    this,
                    incomingIntent,
                    targetPkg,
                    "Целевое приложение не найдено -> открыты настройки заглушки"
            );
            Toast.makeText(
                    this,
                    "Приложение " + targetPkg + " не установлено. Откройте настройки заглушки.",
                    Toast.LENGTH_SHORT
            ).show();

            Intent configIntent = new Intent(this, MainActivity.class);
            configIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(configIntent);
        }

        finish();
        overridePendingTransition(0, 0);
    }

    private boolean launchTargetApplication(String targetPkg, Intent incomingIntent) {
        if (targetPkg == null || targetPkg.isEmpty() || getPackageName().equals(targetPkg)) {
            return false;
        }

        PackageManager pm = getPackageManager();
        try {
            // Если выбран Vivo Wallet — запускаем сразу его NFC-ключ (NewSwipeActivity)
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
                    PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg, "Успешно запущен ключ Vivo Wallet (NewSwipeActivity)");
                    return true;
                } catch (Exception ignored) {
                }
            }

            // 1. Стандартный Launcher Intent целевого приложения (например, ru.nspk.mirpay)
            Intent launchIntent = pm.getLaunchIntentForPackage(targetPkg);
            if (launchIntent != null) {
                launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                );
                startActivity(launchIntent);
                PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg, "Успешно запущено через getLaunchIntentForPackage");
                return true;
            }

            // 2. Поиск любой экспортированной MAIN Activity внутри пакета
            Intent mainQuery = new Intent(Intent.ACTION_MAIN);
            mainQuery.setPackage(targetPkg);
            List<ResolveInfo> matches = pm.queryIntentActivities(mainQuery, 0);
            if (matches != null && !matches.isEmpty()) {
                ResolveInfo ri = matches.get(0);
                if (ri.activityInfo != null) {
                    Intent explicit = new Intent(Intent.ACTION_MAIN);
                    explicit.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
                    explicit.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    );
                    startActivity(explicit);
                    PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg, "Успешно запущено через явный ComponentName");
                    return true;
                }
            }
        } catch (Exception e) {
            PrefsHelper.saveTriggerLog(this, incomingIntent, targetPkg, "Ошибка запуска: " + e.getMessage());
        }
        return false;
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
}
