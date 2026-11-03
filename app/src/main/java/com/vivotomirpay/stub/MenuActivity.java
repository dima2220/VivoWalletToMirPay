package com.vivotomirpay.stub;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Настраиваемое меню действий — открывается по двойному нажатию кнопки питания,
 * если действием выбрано special:menu. В меню можно добавить и действия
 * (фонарик, камера, VPN, Circle to Search), и любые приложения; состав
 * настраивается в отдельном экране «Настройка меню».
 *
 * Меню работает и над экраном блокировки (setShowWhenLocked). Выбор пункта
 * запускает действие через ActionRunner. Панель плавно выезжает снизу
 * (анимация на самой вьюхе, без системных window-анимаций).
 */
public class MenuActivity extends Activity {

    private Ui ui;
    private LinearLayout sheet;
    private View scrim;
    private boolean dismissed = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        allowOverKeyguard();
        overridePendingTransition(0, 0);
        drawBehindSystemBars();
        ui = new Ui(this);
        buildUi();
    }

    /**
     * Затемнение и панель рисуются от края до края — под статус-баром и жест-баром тоже,
     * иначе затемнение выглядит «обрезанным» и панель висит над навигацией.
     */
    private void drawBehindSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        } catch (Exception ignored) {
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.BOTTOM);
        root.setBackgroundColor(Color.TRANSPARENT);

        // Затемнение поверх клавиатуры-защиты / рабочего стола; тап мимо меню закрывает его.
        scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(0x52, 0, 0, 0));
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> animateOut());
        root.addView(scrim, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // =========================================================================
        // Нижняя панель в стиле Material 3 bottom sheet
        // =========================================================================
        sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable sheetBg = new android.graphics.drawable.GradientDrawable();
        sheetBg.setColor(ui.c(Ui.CARD));
        sheetBg.setCornerRadii(new float[]{
                dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0
        });
        sheet.setBackground(sheetBg);
        sheet.setElevation(dp(12));
        final int pad = dp(18);
        final int basePadBottom = dp(16);
        sheet.setPadding(pad, dp(12), pad, basePadBottom);

        // Поднимаем панель на высоту экрана ещё до первой отрисовки — иначе на
        // первый кадр панель мигает в конечной позиции, и выезд выглядит сломанным.
        sheet.setTranslationY(getResources().getDisplayMetrics().heightPixels);

        // Панель не должна упираться в системную навигацию: добавляем нижний inset.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            try {
                int bottom = insets.getSystemWindowInsetBottom();
                sheet.setPadding(pad, dp(12), pad, basePadBottom + Math.max(0, bottom));
            } catch (Exception ignored) {
            }
            return insets;
        });

        // Ручка (drag handle) — примета bottom sheet.
        View handle = new View(this);
        handle.setBackground(ui.rounded(ui.alpha(Ui.OUTLINE, 0x80), 2));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(30), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = dp(10);
        sheet.addView(handle, handleLp);

        TextView title = new TextView(this);
        title.setText(getString(R.string.menu_title));
        title.setTextColor(ui.c(Ui.ON_SURFACE));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(ui.medium());
        sheet.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.menu_subtitle));
        subtitle.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setPadding(0, dp(1), 0, dp(8));
        sheet.addView(subtitle);

        // =========================================================================
        // Пункты меню
        // =========================================================================
        List<String[]> items = resolveMenuItems();
        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.menu_empty));
            empty.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setGravity(Gravity.CENTER);
            int ep = dp(20);
            empty.setPadding(ep, ep, ep, ep);
            list.addView(empty);
        } else {
            for (String[] entry : items) {
                list.addView(makeMenuItemRow(entry[0], entry[1], entry[2]));
            }
        }

        scroller.addView(list);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.55);
        LinearLayout.LayoutParams scrollerLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        sheet.addView(scroller, scrollerLp);

        root.addView(sheet, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        // Плавное выезжание снизу: уточняем высоту после раскладки и анимируем.
        sheet.post(() -> {
            int h = sheet.getHeight();
            if (h == 0) h = dp(320);
            sheet.setTranslationY(h);
            sheet.animate().translationY(0f)
                    .setDuration(260L)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
            scrim.animate().alpha(1f).setDuration(200L).start();
        });
    }

    /** Показываем меню и над заблокированным экраном, включая экран при срабатывании. */
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

    /** Возвращает [id, label, kind] для каждого пункта меню. */
    private List<String[]> resolveMenuItems() {
        List<String[]> result = new ArrayList<>();
        JSONArray arr = PrefsHelper.getMenuItems(this);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < arr.length() && result.size() < PrefsHelper.MAX_MENU_ITEMS; i++) {
            try {
                String id = arr.optString(i, "").trim();
                if (id.isEmpty()) continue;
                if (PrefsHelper.SPECIAL_MENU.equals(id)) continue; // рекурсия запрещена
                if (!seen.add(id)) continue; // дубликаты из старых версий не показываем
                result.add(new String[]{id, labelFor(id), kindFor(id)});
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String kindFor(String id) {
        if (id.startsWith(PrefsHelper.SPECIAL_PREFIX)) return "special";
        return "app";
    }

    private String labelFor(String id) {
        switch (id) {
            case PrefsHelper.SPECIAL_TORCH:
                return getString(R.string.menu_item_torch);
            case PrefsHelper.SPECIAL_CAMERA:
                return getString(R.string.menu_item_camera);
            case PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH:
                return getString(R.string.menu_item_cts);
            case PrefsHelper.SPECIAL_VPN:
                String client = VpnController.resolveClientPackage(this,
                        PrefsHelper.getVpnClient(this));
                String clientName = client == null
                        ? getString(R.string.vpn_name_generic)
                        : VpnController.clientLabel(client);
                return getString(R.string.menu_item_vpn, clientName);
            default:
                String pkg = PrefsHelper.appPackageOf(id);
                if (pkg != null) return appLabel(pkg, pkg);
                return id;
        }
    }

    private LinearLayout makeMenuItemRow(final String id, String label, String kind) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(ui.rippleOnRounded(ui.alpha(Ui.SURFACE_HIGH, 0x99), 16));

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        boolean customIcon = false;
        if ("app".equals(kind)) {
            String pkg = PrefsHelper.appPackageOf(id);
            if (pkg != null) {
                Drawable appIcon = appIconDrawable(pkg);
                if (appIcon != null) {
                    icon.setImageDrawable(appIcon);
                    customIcon = true;
                }
            }
        }
        if (!customIcon) {
            icon.setImageResource(iconFor(id));
            icon.setColorFilter(ui.c(Ui.ON_PRIMARY_CONTAINER), android.graphics.PorterDuff.Mode.SRC_IN);
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconLp.leftMargin = dp(4);
        row.addView(icon, iconLp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ui.c(Ui.ON_SURFACE));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setSingleLine(true);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvLp.leftMargin = dp(14);
        row.addView(tv, tvLp);

        row.setOnClickListener(v -> {
            if (PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH.equals(id)) {
                // CTS снимает весь экран в момент запуска сессии ассистента.
                // Если дёрнуть его сразу — на скриншот попадёт само меню.
                // Поэтому сначала полностью убираем меню с экрана (анимация,
                // finish и снятие окна с WindowManager), и только после этого
                // запускаем Circle to Search. Анимация панели — 220 мс, finish
                // в её конце; 550 мс с запасом покрывает снятие окна даже на
                // медленных прошивках. Контекст — application, т.к. активность
                // к этому моменту уже закрыта.
                animateOut();
                final android.content.Context appCtx = getApplicationContext();
                handler.postDelayed(() -> ActionRunner.runAction(appCtx, id, null), 550L);
            } else {
                // Остальные действия не снимают экран — запускаем сразу,
                // параллельно уводя панель вниз.
                ActionRunner.runAction(MenuActivity.this, id, null);
                animateOut();
            }
        });

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        rowLp.bottomMargin = dp(3);
        row.setLayoutParams(rowLp);
        int hp = dp(12);
        row.setPadding(hp, 0, hp, 0);
        return row;
    }

    private Drawable appIconDrawable(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    /** Плавно уводит панель вниз и закрывает активность (без повторного запуска). */
    private void animateOut() {
        if (dismissed) return;
        dismissed = true;
        scrim.animate().alpha(0f).setDuration(160L).start();
        sheet.animate().translationY(sheet.getHeight())
                .setDuration(220L)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
                .withEndAction(this::finish)
                .start();
        // Страховка: если withEndAction не сработает — закрываемся принудительно.
        handler.postDelayed(() -> {
            if (!isFinishing()) finish();
        }, 500L);
    }

    @Override
    public void onBackPressed() {
        animateOut();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private int iconFor(String id) {
        switch (id) {
            case PrefsHelper.SPECIAL_TORCH:
                return R.drawable.ic_action_torch;
            case PrefsHelper.SPECIAL_CAMERA:
                return R.drawable.ic_action_camera;
            case PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH:
                return R.drawable.ic_action_search;
            case PrefsHelper.SPECIAL_VPN:
                return R.drawable.ic_action_vpn;
            case PrefsHelper.SPECIAL_MENU:
                return R.drawable.ic_action_menu;
            default:
                return R.drawable.ic_action_app;
        }
    }

    private String appLabel(String pkg, String fallback) {
        try {
            CharSequence label = getPackageManager()
                    .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0));
            if (label != null && label.length() > 0) return label.toString();
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
