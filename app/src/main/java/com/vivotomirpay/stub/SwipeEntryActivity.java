package com.vivotomirpay.stub;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

/**
 * Прозрачная Activity-заглушка, перехватывающая системный вызов Vivo при двойном
 * нажатии кнопки питания:
 *   action = "vivo.intent.action.NFC_BUS_SWINGSPLASH"
 *   category = "android.intent.category.DEFAULT"
 *   extra "lb_from" = "2"
 *
 * Вся логика выбора и запуска действия вынесена в ActionRunner:
 *   - обычное действие (настройка «Действие двойного нажатия»);
 *   - при заблокированном экране — отдельное действие из настройки
 *     «Экран блокировки», если оно включено (по умолчанию — фонарик);
 *     если отключено — то же действие, что и на разблокированном экране.
 *
 * Атрибуты setShowWhenLocked / setTurnScreenOn позволяют заглушке корректно
 * работать над клавиатурой-защитой: фонарик переключается, камера открывается
 * поверх экрана блокировки, меню действий показывается сразу.
 */
public class SwipeEntryActivity extends Activity {

    public static final String ACTION_VIVO_SWIPE = "vivo.intent.action.NFC_BUS_SWINGSPLASH";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        allowOverKeyguard();
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

    /** Показываем прозрачную Activity (и меню) поверх экрана блокировки. */
    private void allowOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private void handleSwipeIntent(Intent incomingIntent) {
        boolean locked = isDeviceLocked();
        String actionId;

        if (locked && PrefsHelper.isLockscreenEnabled(this)) {
            // Отдельное действие для заблокированного экрана — только фонарик или камера.
            actionId = PrefsHelper.getLockscreenAction(this);
            if (!PrefsHelper.SPECIAL_TORCH.equals(actionId)
                    && !PrefsHelper.SPECIAL_CAMERA.equals(actionId)) {
                actionId = PrefsHelper.SPECIAL_TORCH;
            }
        } else {
            // Обычное действие — и на разблокированном экране, и на заблокированном,
            // если отдельное действие для локскрина отключено.
            actionId = PrefsHelper.getTargetPackage(this);
        }

        if (PrefsHelper.isVibrateEnabled(this)) {
            Vibe.trigger(this);
        }

        boolean isCamera = PrefsHelper.SPECIAL_CAMERA.equals(actionId);
        ActionRunner.runAction(this, actionId, incomingIntent);

        if (isCamera) {
            // Камере нужно время, чтобы подняться, пока наша showWhenLocked-активность
            // ещё видна над клавиатурой-защитой — иначе на некоторых прошивках
            // камера остаётся за локскрином. Задерживаем finish на полсекунды.
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 450L);
        } else {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    private boolean isDeviceLocked() {
        try {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            return km != null && km.isKeyguardLocked();
        } catch (Exception e) {
            return false;
        }
    }
}
