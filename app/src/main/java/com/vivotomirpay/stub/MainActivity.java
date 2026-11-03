package com.vivotomirpay.stub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Главное окно настроек — Material You (Material 3).
 *
 * Карточки:
 *   1. Действие двойного нажатия (приложения и спец-действия);
 *   2. Экран блокировки — отдельное действие при заблокированном экране (фонарик/камера),
 *      отключается — и локскрин работает как разблокированный;
 *   3. Вибрация — вкл/выкл, длительность импульса, паттерн;
 *   4. VPN — выбор клиента для действия special:vpn (Happ / incy / авто);
 *   5. Меню действий — предпросмотр и настройка состава меню;
 *   6. Обработчик кнопки питания Vivo;
 *   7. Ассистент по умолчанию (для Circle to Search);
 *   8. Журнал последнего срабатывания.
 *
 * Цвета — динамические (Material You) на Android 12+ через Ui, статичные M3 — на старых.
 * Все строки — в ресурсах: values (русский) + values-en (английский).
 */
public class MainActivity extends Activity {

    private Ui ui;

    private TextView statusPill;
    private TextView handlerDetails;
    private TextView mainActionTitle;
    private TextView mainActionSubtitle;
    private ImageView mainActionIcon;
    private TextView lockChipTorch;
    private TextView lockChipCamera;
    private TextView[] patternChipViews;
    private TextView[] vpnChipViews;
    private TextView lockSwitchHint;
    private TextView vibrateDurationLabel;
    private TextView vpnStatusLine;
    private TextView assistantStatusView;
    private TextView logTextView;
    private String currentDefaultSwipePkg = null;

    private AlertDialog currentDialog;

    // Чипы-пресеты действия и их id — для обновления подсветки при смене выбора
    // (иначе после выбора нового действия чип продолжает подсвечивать прошлое).
    private final List<TextView> presetChipViews = new ArrayList<>();
    private final List<String> presetChipIds = new ArrayList<>();

    /** Колбэк выбора действия из пикера. */
    private interface OnActionPicked {
        void onPicked(String actionId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAllStates();
    }

    // =========================================================================
    // Построение UI
    // =========================================================================

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ui.c(Ui.SURFACE));
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) ui.dp(18);
        root.setPadding(pad, (int) ui.dp(22), pad, (int) ui.dp(28));

        // --- Заголовок -----------------------------------------------------
        TextView headline = new TextView(this);
        headline.setText(getString(R.string.header_title));
        headline.setTextColor(ui.c(Ui.ON_SURFACE));
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        headline.setTypeface(ui.medium());
        root.addView(headline);

        TextView subhead = new TextView(this);
        subhead.setText(getString(R.string.header_subtitle));
        subhead.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        subhead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        subhead.setPadding((int) ui.dp(2), (int) ui.dp(4), 0, 0);
        root.addView(subhead);

        // Статус-пилюля обработчика.
        statusPill = new TextView(this);
        statusPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusPill.setTypeface(ui.medium());
        statusPill.setPadding((int) ui.dp(16), (int) ui.dp(11), (int) ui.dp(16), (int) ui.dp(11));
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillLp.topMargin = (int) ui.dp(12);
        pillLp.bottomMargin = (int) ui.dp(12);
        root.addView(statusPill, pillLp);

        // --- Карточка 1: действие двойного нажатия --------------------------
        LinearLayout actionCard = card();
        actionCard.addView(sectionHeader(R.drawable.ic_action_app, getString(R.string.card_action_title)));

        actionCard.addView(targetLabel(getString(R.string.main_action_label)));
        LinearLayout mainRow = targetActionRow();
        mainActionIcon = (ImageView) mainRow.getChildAt(0);
        LinearLayout mainTextCol = (LinearLayout) mainRow.getChildAt(1);
        mainActionTitle = (TextView) mainTextCol.getChildAt(0);
        mainActionSubtitle = (TextView) mainTextCol.getChildAt(1);
        actionCard.addView(mainRow);
        mainRow.setOnClickListener(v -> showActionPicker(
                getString(R.string.picker_title_main),
                PrefsHelper.getTargetPackage(MainActivity.this),
                picked -> {
                    PrefsHelper.setTargetPackage(MainActivity.this, picked);
                    refreshAllStates();
                }));

        actionCard.addView(hint(getString(R.string.card_action_hint)), hintLp());

        actionCard.addView(chipsRow(new String[]{
                getString(R.string.preset_mirpay),
                getString(R.string.preset_sbpay),
                getString(R.string.preset_wallet)
        }, new String[]{
                "ru.nspk.mirpay", "ru.nspk.sbpay", "com.vivo.wallet"
        }, 0));
        actionCard.addView(chipsRow(new String[]{
                getString(R.string.preset_torch),
                getString(R.string.preset_camera),
                getString(R.string.preset_vpn),
                getString(R.string.preset_cts),
                getString(R.string.preset_menu)
        }, new String[]{
                PrefsHelper.SPECIAL_TORCH,
                PrefsHelper.SPECIAL_CAMERA,
                PrefsHelper.SPECIAL_VPN,
                PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH,
                PrefsHelper.SPECIAL_MENU
        }, (int) ui.dp(8)));

        root.addView(actionCard, cardMargin());

        // --- Карточка 2: экран блокировки -----------------------------------
        // На заблокированном экране — только фонарик или камера.
        LinearLayout lockCard = card();
        lockCard.addView(sectionHeader(R.drawable.ic_action_lock, getString(R.string.card_lock_title)));

        Switch lockSwitch = makeSwitch(getString(R.string.switch_lock),
                PrefsHelper.isLockscreenEnabled(this));
        lockSwitch.setOnCheckedChangeListener((b, checked) -> {
            PrefsHelper.setLockscreenEnabled(MainActivity.this, checked);
            refreshAllStates();
        });
        lockCard.addView(lockSwitch, switchLp());

        lockSwitchHint = hint("");
        lockCard.addView(lockSwitchHint, hintLp());

        lockCard.addView(targetLabel(getString(R.string.lock_action_label)));
        lockCard.addView(lockChips());

        lockCard.addView(hint(getString(R.string.card_lock_hint)), hintLp());
        root.addView(lockCard, cardMargin());

        // --- Карточка 3: вибрация -------------------------------------------
        LinearLayout vibCard = card();
        vibCard.addView(sectionHeader(R.drawable.ic_action_vibrate, getString(R.string.card_vibration_title)));

        Switch vibSwitch = makeSwitch(getString(R.string.switch_vibrate),
                PrefsHelper.isVibrateEnabled(this));
        vibSwitch.setOnCheckedChangeListener((b, checked) -> {
            PrefsHelper.setVibrateEnabled(MainActivity.this, checked);
            Vibe.trigger(MainActivity.this);
            refreshAllStates();
        });
        vibCard.addView(vibSwitch, switchLp());

        vibrateDurationLabel = new TextView(this);
        vibrateDurationLabel.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        vibrateDurationLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        vibCard.addView(vibrateDurationLabel);

        SeekBar seek = new SeekBar(this);
        seek.setMax(480); // 20..500 мс
        seek.setProgress(PrefsHelper.getVibrateDurationMs(this) - 20);
        seek.getThumb().setColorFilter(ui.c(Ui.PRIMARY), android.graphics.PorterDuff.Mode.SRC_IN);
        seek.getProgressDrawable().setColorFilter(ui.c(Ui.PRIMARY), android.graphics.PorterDuff.Mode.SRC_IN);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int ms = progress + 20;
                PrefsHelper.setVibrateDurationMs(MainActivity.this, ms);
                updateVibrateDurationLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
            }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                Vibe.trigger(MainActivity.this);
            }
        });
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekLp.topMargin = (int) ui.dp(2);
        vibCard.addView(seek, seekLp);

        TextView patternLabel = new TextView(this);
        patternLabel.setText(getString(R.string.vibrate_pattern_label));
        patternLabel.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        patternLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams patternLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        patternLp.topMargin = (int) ui.dp(10);
        patternLp.bottomMargin = (int) ui.dp(6);
        vibCard.addView(patternLabel, patternLp);

        vibCard.addView(patternChips());
        vibCard.addView(hint(getString(R.string.card_vibration_hint)), hintLp());
        root.addView(vibCard, cardMargin());

        // --- Карточка 4: VPN --------------------------------------------------
        LinearLayout vpnCard = card();
        vpnCard.addView(sectionHeader(R.drawable.ic_action_vpn, getString(R.string.card_vpn_title)));

        vpnCard.addView(vpnClientChips());
        vpnStatusLine = new TextView(this);
        vpnStatusLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        vpnStatusLine.setPadding((int) ui.dp(2), (int) ui.dp(8), (int) ui.dp(2), 0);
        vpnCard.addView(vpnStatusLine);
        vpnCard.addView(hint(getString(R.string.card_vpn_hint)), hintLp());
        root.addView(vpnCard, cardMargin());

        // --- Карточка 5: меню действий ----------------------------------------
        LinearLayout menuCard = card();
        menuCard.addView(sectionHeader(R.drawable.ic_action_menu, getString(R.string.card_menu_title)));
        menuCard.addView(hint(getString(R.string.menu_desc)), hintLp());

        LinearLayout menuBtnRow = new LinearLayout(this);
        menuBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.Button previewBtn = buttonTonal(getString(R.string.btn_menu_preview));
        previewBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, MenuActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
            } catch (Exception ignored) {
            }
        });
        menuBtnRow.addView(previewBtn, halfWeight(true));
        android.widget.Button editBtn = buttonPrimary(getString(R.string.btn_menu_edit));
        editBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, MenuEditorActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
            } catch (Exception ignored) {
            }
        });
        menuBtnRow.addView(editBtn, halfWeight(false));
        menuCard.addView(menuBtnRow);
        root.addView(menuCard, cardMargin());

        // --- Карточка 6: обработчик кнопки питания ----------------------------
        LinearLayout handlerCard = card();
        handlerCard.addView(sectionHeader(R.drawable.ic_action_bolt, getString(R.string.card_handler_title)));

        handlerDetails = new TextView(this);
        handlerDetails.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        handlerDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        handlerDetails.setPadding((int) ui.dp(2), (int) ui.dp(4), (int) ui.dp(2), (int) ui.dp(12));
        handlerCard.addView(handlerDetails);

        android.widget.Button testBtn = buttonPrimary(getString(R.string.btn_test));
        testBtn.setOnClickListener(v -> {
            Intent test = new Intent(MainActivity.this, SwipeEntryActivity.class);
            test.setAction(SwipeEntryActivity.ACTION_VIVO_SWIPE);
            test.putExtra("lb_from", "2");
            startActivity(test);
        });
        handlerCard.addView(testBtn, rowMargin());
        handlerCard.addView(hint(getString(R.string.btn_test_hint)), hintLp());

        android.widget.Button resolveBtn = buttonTonal(getString(R.string.btn_resolve));
        resolveBtn.setOnClickListener(v -> triggerVivoSwipeIntent());
        handlerCard.addView(resolveBtn, rowMargin());
        handlerCard.addView(hint(getString(R.string.btn_resolve_hint)), hintLp());

        android.widget.Button infoBtn = buttonOutline(getString(R.string.btn_appinfo));
        infoBtn.setOnClickListener(v -> {
            String pkg = currentDefaultSwipePkg != null ? currentDefaultSwipePkg : getPackageName();
            openAppDetails(pkg);
        });
        handlerCard.addView(infoBtn, rowMargin());
        handlerCard.addView(hint(getString(R.string.btn_appinfo_hint)), hintLp());
        root.addView(handlerCard, cardMargin());

        // --- Карточка 7: ассистент --------------------------------------------
        LinearLayout assistantCard = card();
        assistantCard.addView(sectionHeader(R.drawable.ic_action_assistant, getString(R.string.card_assistant_title)));
        assistantCard.addView(hint(getString(R.string.assistant_hint)), hintLp());

        assistantStatusView = new TextView(this);
        assistantStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        assistantStatusView.setPadding((int) ui.dp(2), 0, (int) ui.dp(2), (int) ui.dp(12));
        assistantCard.addView(assistantStatusView);

        android.widget.Button assistantBtn = buttonTonal(getString(R.string.btn_assistant));
        assistantBtn.setOnClickListener(v -> openDefaultAssistantSettings());
        assistantCard.addView(assistantBtn, rowMargin());
        root.addView(assistantCard, cardMargin());

        // --- Карточка 8: журнал -------------------------------------------------
        LinearLayout logCard = card();
        logCard.addView(sectionHeader(R.drawable.ic_action_log, getString(R.string.card_log_title)));
        logCard.addView(hint(getString(R.string.card_log_hint)), hintLp());

        logTextView = new TextView(this);
        logTextView.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        logTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        logTextView.setTypeface(Typeface.MONOSPACE);
        logTextView.setBackground(ui.rounded(ui.alpha(Ui.SURFACE_HIGH, 0x66), 16));
        int pLog = (int) ui.dp(14);
        logTextView.setPadding(pLog, pLog, pLog, pLog);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logLp.topMargin = (int) ui.dp(8);
        logCard.addView(logTextView, logLp);

        android.widget.Button clearLogBtn = buttonOutline(getString(R.string.btn_log_clear));
        clearLogBtn.setOnClickListener(v -> {
            PrefsHelper.clearTriggerLog(MainActivity.this);
            refreshAllStates();
        });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clearLp.topMargin = (int) ui.dp(10);
        logCard.addView(clearLogBtn, clearLp);
        root.addView(logCard, cardMargin());

        scroll.addView(root);
        setContentView(scroll);
    }

    // =========================================================================
    // Обновление состояний
    // =========================================================================

    private void refreshAllStates() {
        PackageManager pm = getPackageManager();
        Intent testIntent = new Intent(SwipeEntryActivity.ACTION_VIVO_SWIPE);
        testIntent.addCategory(Intent.CATEGORY_DEFAULT);

        ResolveInfo defaultRi = pm.resolveActivity(testIntent, PackageManager.MATCH_DEFAULT_ONLY);
        List<ResolveInfo> allHandlers =
                pm.queryIntentActivities(testIntent, PackageManager.MATCH_DEFAULT_ONLY);

        currentDefaultSwipePkg = null;
        boolean ourAppIsDefault = false;
        if (defaultRi != null && defaultRi.activityInfo != null) {
            String defPkg = defaultRi.activityInfo.packageName;
            if (!"android".equals(defPkg) && !defPkg.toLowerCase(Locale.ROOT).contains("resolver")) {
                currentDefaultSwipePkg = defPkg;
                ourAppIsDefault = getPackageName().equals(defPkg);
            }
        }

        if (ourAppIsDefault) {
            stylePill(getString(R.string.status_default_ok),
                    ui.c(Ui.SUCCESS_CONTAINER), ui.c(Ui.ON_SUCCESS_CONTAINER));
        } else if (currentDefaultSwipePkg != null) {
            stylePill(getString(R.string.status_default_other, currentDefaultSwipePkg),
                    ui.c(Ui.PRIMARY_CONTAINER), ui.c(Ui.ON_PRIMARY_CONTAINER));
        } else {
            stylePill(getString(R.string.status_default_none),
                    ui.c(Ui.SURFACE_HIGH), ui.c(Ui.ON_SURFACE_VARIANT));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.handler_status_registered, allHandlers.size())).append('\n');
        for (ResolveInfo ri : allHandlers) {
            if (ri.activityInfo != null) {
                sb.append("• ").append(ri.loadLabel(pm))
                        .append(" (").append(ri.activityInfo.packageName).append(")\n");
            }
        }
        handlerDetails.setText(sb.toString().trim());

        refreshMainAction();
        refreshLockCard();
        refreshVpnCard();
        refreshPresetChips();

        if (assistantStatusView != null) {
            String assistant = readDefaultAssistant();
            boolean isGemini = assistant != null
                    && assistant.toLowerCase(Locale.ROOT).contains("com.google.android.apps.bard");
            boolean isGoogle = assistant != null
                    && assistant.toLowerCase(Locale.ROOT).contains("com.google.android.googlequicksearchbox");
            if (isGemini) {
                assistantStatusView.setText(getString(R.string.assistant_ok, assistant));
                assistantStatusView.setTextColor(ui.c(Ui.ON_SUCCESS_CONTAINER));
            } else if (isGoogle) {
                assistantStatusView.setText(getString(R.string.assistant_google, assistant));
                assistantStatusView.setTextColor(ui.c(Ui.PRIMARY));
            } else {
                assistantStatusView.setText(getString(R.string.assistant_warn, assistant));
                assistantStatusView.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
            }
        }

        String log = PrefsHelper.getLastTriggerLog(this);
        logTextView.setText(log == null || log.trim().isEmpty()
                ? getString(R.string.log_empty) : log);
    }

    private void refreshMainAction() {
        String id = PrefsHelper.getTargetPackage(this);
        mainActionTitle.setText(displayNameFor(id));
        mainActionSubtitle.setText(subtitleFor(id));
        mainActionIcon.setImageResource(iconFor(id));
        int tint = id.startsWith(PrefsHelper.SPECIAL_PREFIX)
                ? ui.c(Ui.ON_PRIMARY_CONTAINER) : ui.c(Ui.PRIMARY);
        mainActionIcon.setColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN);
    }

    private void refreshLockCard() {
        boolean enabled = PrefsHelper.isLockscreenEnabled(this);
        lockSwitchHint.setText(enabled
                ? getString(R.string.lock_hint_on)
                : getString(R.string.lock_hint_off));
        refreshLockChips();
        updateVibrateDurationLabel();
    }

    /** На заблокированном экране — только фонарик или камера. */
    private View lockChips() {
        FlowLayout flow = new FlowLayout(this, 6);
        lockChipTorch = selectableChip(getString(R.string.name_torch));
        lockChipCamera = selectableChip(getString(R.string.name_camera));
        lockChipTorch.setOnClickListener(v -> {
            PrefsHelper.setLockscreenAction(MainActivity.this, PrefsHelper.SPECIAL_TORCH);
            refreshLockChips();
        });
        lockChipCamera.setOnClickListener(v -> {
            PrefsHelper.setLockscreenAction(MainActivity.this, PrefsHelper.SPECIAL_CAMERA);
            refreshLockChips();
        });
        flow.addView(lockChipTorch, chipLp());
        flow.addView(lockChipCamera, chipLp());
        return flow;
    }

    private void refreshLockChips() {
        if (lockChipTorch == null || lockChipCamera == null) return;
        // Если в сохранённой настройке осталось что-то кроме фонарика/камеры — считаем фонарик.
        boolean camera = PrefsHelper.SPECIAL_CAMERA.equals(PrefsHelper.getLockscreenAction(this));
        styleSelectableChip(lockChipTorch, !camera);
        styleSelectableChip(lockChipCamera, camera);
    }

    private void updateVibrateDurationLabel() {
        if (vibrateDurationLabel != null) {
            vibrateDurationLabel.setText(getString(R.string.vibrate_duration_label,
                    PrefsHelper.getVibrateDurationMs(this)));
        }
    }

    private void refreshVpnCard() {
        String setting = PrefsHelper.getVpnClient(this);
        String resolved = VpnController.resolveClientPackage(this, setting);
        boolean vpnActive = VpnController.isAnyVpnActive(this);
        String resolvedName = resolved == null
                ? getString(R.string.vpn_none_found)
                : VpnController.clientLabel(resolved);
        vpnStatusLine.setText(getString(
                resolved == null ? R.string.vpn_status_not_installed_short : R.string.vpn_status_installed,
                resolvedName)
                + " · "
                + getString(vpnActive ? R.string.vpn_state_active : R.string.vpn_state_inactive));
        vpnStatusLine.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
    }

    // =========================================================================
    // Диалог выбора действия
    // =========================================================================

    private void showActionPicker(String headerText, String currentId, final OnActionPicked callback) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = (int) ui.dp(16);
        content.setPadding(p, (int) ui.dp(14), p, (int) ui.dp(6));

        // Своий заголовок — компактный, вместо системного «огромного».
        TextView header = new TextView(this);
        header.setText(headerText);
        header.setTextColor(ui.c(Ui.ON_SURFACE));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.setTypeface(ui.medium());
        header.setPadding((int) ui.dp(2), 0, 0, 0);
        content.addView(header);

        // Спец-действия — сетка 2 колонки.
        TextView specialsHeader = dialogSectionHeader(getString(R.string.picker_section_specials));
        content.addView(specialsHeader);

        String[][] specials = {
                {PrefsHelper.SPECIAL_TORCH, getString(R.string.name_torch)},
                {PrefsHelper.SPECIAL_CAMERA, getString(R.string.name_camera)},
                {PrefsHelper.SPECIAL_VPN, getString(R.string.name_vpn)},
                {PrefsHelper.SPECIAL_MENU, getString(R.string.name_menu)},
                {PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH, getString(R.string.name_cts)},
        };
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int rowStart = 0; rowStart < specials.length; rowStart += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int cell = 0; cell < 2; cell++) {
                final String id;
                final String label;
                if (rowStart + cell < specials.length) {
                    id = specials[rowStart + cell][0];
                    label = specials[rowStart + cell][1];
                } else {
                    id = null;
                    label = "";
                }
                boolean selected = id != null && id.equals(currentId);
                LinearLayout cellView = new LinearLayout(this);
                cellView.setOrientation(LinearLayout.VERTICAL);
                cellView.setGravity(Gravity.CENTER);
                cellView.setClickable(id != null);
                if (id != null) {
                    if (selected) {
                        // Текущее действие выделено контейнерным акцентом.
                        cellView.setBackground(ui.roundedStroke(ui.c(Ui.PRIMARY_CONTAINER), 18,
                                ui.c(Ui.PRIMARY), 1.5f));
                    } else {
                        cellView.setBackground(ui.rippleOnRounded(ui.alpha(Ui.SURFACE_HIGH, 0x88), 18));
                    }
                    cellView.setOnClickListener(v -> {
                        dismissDialog();
                        callback.onPicked(id);
                    });
                }
                ImageView iv = new ImageView(this);
                iv.setImageResource(id == null ? R.drawable.ic_action_app : iconFor(id));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setColorFilter(selected
                                ? ui.c(Ui.ON_PRIMARY_CONTAINER)
                                : ui.c(Ui.PRIMARY),
                        android.graphics.PorterDuff.Mode.SRC_IN);
                int ivPad = (int) ui.dp(10);
                iv.setPadding(ivPad, ivPad, ivPad, ivPad);
                LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                        (int) ui.dp(42), (int) ui.dp(42));
                ivLp.topMargin = (int) ui.dp(10);
                cellView.addView(iv, ivLp);
                TextView tv = new TextView(this);
                tv.setText(label);
                tv.setTextColor(selected ? ui.c(Ui.ON_PRIMARY_CONTAINER) : ui.c(Ui.ON_SURFACE));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding((int) ui.dp(4), (int) ui.dp(6), (int) ui.dp(4), (int) ui.dp(12));
                cellView.addView(tv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cellLp.rightMargin = cell == 0 ? (int) ui.dp(6) : 0;
                cellLp.bottomMargin = (int) ui.dp(4);
                row.addView(cellView, cellLp);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        content.addView(grid);

        // Приложения с поиском.
        content.addView(dialogSectionHeader(getString(R.string.picker_section_apps)));
        EditText search = new EditText(this);
        search.setHint(getString(R.string.picker_search_hint));
        search.setTextColor(ui.c(Ui.ON_SURFACE));
        search.setHintTextColor(ui.c(Ui.OUTLINE));
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setBackground(ui.roundedStroke(ui.alpha(Ui.SURFACE_HIGH, 0x66), 14,
                ui.c(Ui.OUTLINE), 1));
        int sp = (int) ui.dp(12);
        search.setPadding(sp, (int) ui.dp(10), sp, (int) ui.dp(10));
        content.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);

        List<AppEntry> apps = installedApps();
        final List<AppEntry> visible = new ArrayList<>(apps);

        Runnable updateApps = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
            appList.removeAllViews();
            int shown = 0;
            for (AppEntry e : visible) {
                boolean match = q.isEmpty()
                        || e.label.toLowerCase(Locale.getDefault()).contains(q)
                        || e.packageName.toLowerCase(Locale.getDefault()).contains(q);
                if (!match || shown >= 12) continue;
                shown++;
                boolean isCurrent = e.packageName.equals(currentId)
                        || (PrefsHelper.APP_PREFIX + e.packageName).equals(currentId);
                LinearLayout item = new LinearLayout(MainActivity.this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                int ip = (int) ui.dp(10);
                item.setPadding(ip, ip, ip, ip);
                if (isCurrent) {
                    item.setBackground(ui.roundedStroke(ui.c(Ui.PRIMARY_CONTAINER), 14,
                            ui.c(Ui.PRIMARY), 1.5f));
                } else {
                    item.setBackground(ui.rippleOnRounded(ui.alpha(Ui.SURFACE_HIGH, 0x66), 14));
                }

                ImageView appIcon = new ImageView(MainActivity.this);
                appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (e.icon != null) {
                    appIcon.setImageDrawable(e.icon);
                } else {
                    appIcon.setImageResource(R.drawable.ic_action_app);
                    appIcon.setColorFilter(ui.c(Ui.PRIMARY), android.graphics.PorterDuff.Mode.SRC_IN);
                }
                item.addView(appIcon, new LinearLayout.LayoutParams((int) ui.dp(26), (int) ui.dp(26)));

                LinearLayout textCol = new LinearLayout(MainActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                TextView name = new TextView(MainActivity.this);
                name.setText(isCurrent
                        ? getString(R.string.picker_current_mark, e.label) : e.label);
                name.setTextColor(ui.c(Ui.ON_SURFACE));
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                name.setSingleLine(true);
                textCol.addView(name);
                TextView pkg = new TextView(MainActivity.this);
                pkg.setText(e.packageName);
                pkg.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
                pkg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
                pkg.setSingleLine(true);
                textCol.addView(pkg);
                LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                colLp.leftMargin = (int) ui.dp(12);
                item.addView(textCol, colLp);

                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ilp.bottomMargin = (int) ui.dp(4);
                item.setLayoutParams(ilp);
                item.setOnClickListener(v -> {
                    dismissDialog();
                    callback.onPicked(e.packageName);
                });
                appList.addView(item);
            }
            if (shown == 0) {
                TextView none = new TextView(MainActivity.this);
                none.setText(getString(R.string.picker_no_apps));
                none.setTextColor(ui.c(Ui.OUTLINE));
                none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                int np = (int) ui.dp(12);
                none.setPadding(np, np, np, np);
                appList.addView(none);
            }
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override public void afterTextChanged(Editable s) {
                updateApps.run();
            }
        });

        ScrollView appScroll = new ScrollView(this);
        appScroll.setFillViewport(false);
        appScroll.setVerticalScrollBarEnabled(false);
        appScroll.addView(appList);
        LinearLayout.LayoutParams appScrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) ui.dp(280));
        appScrollLp.topMargin = (int) ui.dp(8);
        content.addView(appScroll, appScrollLp);

        currentDialog = new AlertDialog.Builder(this)
                .setView(content)
                .setNegativeButton(R.string.btn_close, null)
                .setOnDismissListener(d -> currentDialog = null)
                .create();
        currentDialog.show();
        applyDialogBackground(currentDialog);
        updateApps.run();
    }

    /** Округлый фон диалога в цветах Material You — вместо системных «чужих» рамок. */
    private void applyDialogBackground(AlertDialog dialog) {
        try {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(ui.rounded(ui.c(Ui.CARD), 22));
            }
        } catch (Exception ignored) {
        }
    }

    private static class AppEntry {
        final String label;
        final String packageName;
        final android.graphics.drawable.Drawable icon;

        AppEntry(String label, String packageName, android.graphics.drawable.Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    private List<AppEntry> installedApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(mainIntent, 0);
        List<AppEntry> entries = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null
                    || getPackageName().equals(ri.activityInfo.packageName)) continue;
            // Дедупликация по пакету: у приложения может быть несколько
            // launcher-активностей и activity-alias (тематические иконки).
            if (!seen.add(ri.activityInfo.packageName)) continue;
            android.graphics.drawable.Drawable icon = null;
            try {
                icon = ri.loadIcon(pm);
            } catch (Exception ignored) {
            }
            entries.add(new AppEntry(String.valueOf(ri.loadLabel(pm)),
                    ri.activityInfo.packageName, icon));
        }
        Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return entries;
    }

    // =========================================================================
    // Прочее поведение
    // =========================================================================

    private void triggerVivoSwipeIntent() {
        try {
            Intent intent = new Intent(SwipeEntryActivity.ACTION_VIVO_SWIPE);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra("lb_from", "2");
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private void openAppDetails(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", pkg, null));
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private void openDefaultAssistantSettings() {
        String[] actions = {
                "android.settings.VOICE_INPUT_SETTINGS",
                "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
                "android.settings.MANAGE_ASSISTANT",
        };
        for (String action : actions) {
            try {
                startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", "com.google.android.googlequicksearchbox", null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private String readDefaultAssistant() {
        try {
            String assistant = Settings.Secure.getString(getContentResolver(), "assistant");
            if (assistant == null || assistant.isEmpty()) {
                return getString(R.string.assistant_none);
            }
            String pkg = assistant.contains("/") ? assistant.split("/", 2)[0] : assistant;
            String label = appLabel(pkg);
            if (label == null || label.equals(pkg)) return assistant;
            return label + " (" + pkg + ")";
        } catch (Exception e) {
            return getString(R.string.assistant_read_error);
        }
    }

    private String appLabel(String pkg) {
        try {
            CharSequence label = getPackageManager()
                    .getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0));
            if (label != null && label.length() > 0) return label.toString();
        } catch (Exception ignored) {
        }
        return pkg;
    }

    // =========================================================================
    // Отображение действий
    // =========================================================================

    private String displayNameFor(String id) {
        if (id == null || id.trim().isEmpty()) {
            return getString(R.string.action_none_selected);
        }
        switch (id) {
            case PrefsHelper.SPECIAL_TORCH:
                return getString(R.string.name_torch);
            case PrefsHelper.SPECIAL_CAMERA:
                return getString(R.string.name_camera);
            case PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH:
                return getString(R.string.name_cts);
            case PrefsHelper.SPECIAL_VPN:
                String client = VpnController.resolveClientPackage(this, PrefsHelper.getVpnClient(this));
                String clientName = client == null ? getString(R.string.vpn_name_generic)
                        : VpnController.clientLabel(client);
                return getString(R.string.name_vpn_short, clientName);
            case PrefsHelper.SPECIAL_MENU:
                return getString(R.string.name_menu);
            default:
                String pkg = PrefsHelper.appPackageOf(id);
                if (pkg != null) {
                    return displayNameFor(pkg);
                }
                return appLabel(id);
        }
    }

    private String subtitleFor(String id) {
        if (id == null || id.trim().isEmpty()) {
            return getString(R.string.subtitle_not_set);
        }
        if (id.startsWith(PrefsHelper.SPECIAL_PREFIX)) {
            return id;
        }
        return id;
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

    // =========================================================================
    // Фабрики вьюх (Material 3)
    // =========================================================================

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ui.rounded(ui.c(Ui.CARD), 24));
        int p = (int) ui.dp(20);
        card.setPadding(p, p, p, p);
        return card;
    }

    private LinearLayout.LayoutParams cardMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) ui.dp(14);
        return lp;
    }

    private View sectionHeader(int iconRes, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, (int) ui.dp(12));

        ImageView badge = new ImageView(this);
        badge.setImageResource(iconRes);
        badge.setColorFilter(ui.c(Ui.ON_PRIMARY_CONTAINER), android.graphics.PorterDuff.Mode.SRC_IN);
        badge.setBackground(ui.rounded(ui.c(Ui.PRIMARY_CONTAINER), 12));
        int bp = (int) ui.dp(5);
        badge.setPadding(bp, bp, bp, bp);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams((int) ui.dp(34), (int) ui.dp(34));
        badgeLp.rightMargin = (int) ui.dp(12);
        row.addView(badge, badgeLp);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(ui.c(Ui.ON_SURFACE));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tv.setTypeface(ui.medium());
        row.addView(tv);
        return row;
    }

    private TextView hint(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tv.setLineSpacing(0, 1.15f);
        return tv;
    }

    private LinearLayout.LayoutParams hintLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) ui.dp(8);
        lp.bottomMargin = (int) ui.dp(12);
        return lp;
    }

    private LinearLayout.LayoutParams switchLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) ui.dp(2);
        return lp;
    }

    private LinearLayout.LayoutParams rowMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) ui.dp(6);
        return lp;
    }

    private LinearLayout.LayoutParams halfWeight(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = left ? (int) ui.dp(8) : 0;
        return lp;
    }

    /** Метка над строкой выбора действия. */
    private TextView targetLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setPadding((int) ui.dp(4), 0, 0, (int) ui.dp(6));
        return label;
    }

    /** Строка «текущее действие»: иконка + заголовок + подпись + «Изменить». */
    private LinearLayout targetActionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(ui.rippleOnRounded(ui.alpha(Ui.SURFACE_HIGH, 0x80), 18));
        int rp = (int) ui.dp(14);
        row.setPadding(rp, rp, rp, rp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_action_app);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int) ui.dp(28), (int) ui.dp(28));
        iconLp.leftMargin = (int) ui.dp(2);
        row.addView(icon, iconLp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(ui.medium());
        title.setTextColor(ui.c(Ui.ON_SURFACE));
        title.setSingleLine(true);
        col.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        subtitle.setSingleLine(true);
        col.addView(subtitle);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colLp.leftMargin = (int) ui.dp(14);
        row.addView(col, colLp);

        TextView change = new TextView(this);
        change.setText(getString(R.string.btn_change).toUpperCase(Locale.getDefault()));
        change.setTextColor(ui.c(Ui.PRIMARY));
        change.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        change.setTypeface(ui.medium());
        row.addView(change);

        return row;
    }

    private Switch makeSwitch(String text, boolean checked) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextColor(ui.c(Ui.ON_SURFACE));
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        s.setChecked(checked);
        return s;
    }

    private View chipsRow(String[] titles, String[] ids, int topMarginDp) {
        FlowLayout flow = new FlowLayout(this, 6);
        for (int i = 0; i < titles.length; i++) {
            final String id = ids[i];
            TextView chip = selectableChip(titles[i]);
            presetChipViews.add(chip);
            presetChipIds.add(id);
            chip.setOnClickListener(v -> {
                PrefsHelper.setTargetPackage(MainActivity.this, id);
                refreshAllStates();
            });
            flow.addView(chip, chipLp());
        }
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = (int) ui.dp(topMarginDp);
        wrapper.addView(flow);
        wrapper.setLayoutParams(wlp);
        return wrapper;
    }

    /** Подсветка чипов-пресетов по текущему выбору (иначе чип показывает прошлое действие). */
    private void refreshPresetChips() {
        String current = PrefsHelper.getTargetPackage(this);
        for (int i = 0; i < presetChipViews.size() && i < presetChipIds.size(); i++) {
            styleSelectableChip(presetChipViews.get(i), presetChipIds.get(i).equals(current));
        }
    }

    /** Чип без стиля — стиль наводится styleSelectableChip. */
    private TextView selectableChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        chip.setTypeface(ui.medium());
        chip.setPadding((int) ui.dp(14), (int) ui.dp(8), (int) ui.dp(14), (int) ui.dp(8));
        chip.setClickable(true);
        chip.setFocusable(true);
        return chip;
    }

    /** Стиль чипа: выбран — контейнерный акцент, не выбран — тональная поверхность с рамкой. */
    private void styleSelectableChip(TextView chip, boolean selected) {
        if (selected) {
            chip.setTextColor(ui.c(Ui.ON_PRIMARY_CONTAINER));
            chip.setBackground(ui.roundedStroke(ui.c(Ui.PRIMARY_CONTAINER), 50,
                    ui.c(Ui.PRIMARY), 1.5f));
        } else {
            chip.setTextColor(ui.c(Ui.ON_SURFACE));
            chip.setBackground(ui.roundedStroke(ui.alpha(Ui.SURFACE_HIGH, 0xCC), 50,
                    ui.c(Ui.OUTLINE), 1));
        }
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = (int) ui.dp(6);
        lp.bottomMargin = (int) ui.dp(4);
        return lp;
    }

    /** Паттерны вибрации: три переключаемых чипа. */
    private View patternChips() {
        FlowLayout flow = new FlowLayout(this, 6);
        String[] ids = {"single", "double", "long"};
        String[] titles = {
                getString(R.string.pattern_single),
                getString(R.string.pattern_double),
                getString(R.string.pattern_long)
        };
        patternChipViews = new TextView[ids.length];
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            TextView chip = selectableChip(titles[i]);
            patternChipViews[i] = chip;
            chip.setOnClickListener(v -> {
                PrefsHelper.setVibratePattern(MainActivity.this, id);
                Vibe.trigger(MainActivity.this);
                refreshPatternChips();
            });
            flow.addView(chip, chipLp());
        }
        refreshPatternChips();
        return flow;
    }

    private void refreshPatternChips() {
        if (patternChipViews == null) return;
        String current = PrefsHelper.getVibratePattern(this);
        String[] ids = {"single", "double", "long"};
        for (int i = 0; i < ids.length; i++) {
            styleSelectedChipFilled(patternChipViews[i], ids[i].equals(current));
        }
    }

    /** Выбор VPN-клиента: три переключаемых чипа. */
    private View vpnClientChips() {
        FlowLayout flow = new FlowLayout(this, 6);
        String[] ids = {"auto", "happ", "incy"};
        String[] titles = {
                getString(R.string.vpn_client_auto),
                "Happ",
                "incy"
        };
        vpnChipViews = new TextView[ids.length];
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            TextView chip = selectableChip(titles[i]);
            vpnChipViews[i] = chip;
            chip.setOnClickListener(v -> {
                PrefsHelper.setVpnClient(MainActivity.this, id);
                refreshVpnChips();
                refreshVpnCard();
            });
            flow.addView(chip, chipLp());
        }
        refreshVpnChips();
        return flow;
    }

    private void refreshVpnChips() {
        if (vpnChipViews == null) return;
        String current = PrefsHelper.getVpnClient(this);
        String[] ids = {"auto", "happ", "incy"};
        for (int i = 0; i < ids.length; i++) {
            styleSelectedChipFilled(vpnChipViews[i], ids[i].equals(current));
        }
    }

    /** Выбранный чип в «залитом» стиле (для одиночного выбора среди нескольких). */
    private void styleSelectedChipFilled(TextView chip, boolean selected) {
        if (selected) {
            chip.setTextColor(ui.c(Ui.ON_PRIMARY));
            chip.setBackground(ui.rounded(ui.c(Ui.PRIMARY), 50));
        } else {
            chip.setTextColor(ui.c(Ui.ON_SURFACE));
            chip.setBackground(ui.roundedStroke(ui.alpha(Ui.SURFACE_HIGH, 0xCC), 50,
                    ui.c(Ui.OUTLINE), 1));
        }
    }

    private android.widget.Button buttonPrimary(String text) {
        android.widget.Button b = new android.widget.Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(ui.c(Ui.ON_PRIMARY));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(ui.medium());
        b.setBackground(ui.rounded(ui.c(Ui.PRIMARY), 50));
        styleButton(b);
        return b;
    }

    private android.widget.Button buttonTonal(String text) {
        android.widget.Button b = new android.widget.Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(ui.c(Ui.ON_PRIMARY_CONTAINER));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(ui.medium());
        b.setBackground(ui.rounded(ui.c(Ui.PRIMARY_CONTAINER), 50));
        styleButton(b);
        return b;
    }

    private android.widget.Button buttonOutline(String text) {
        android.widget.Button b = new android.widget.Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(ui.c(Ui.PRIMARY));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        b.setTypeface(ui.medium());
        b.setBackground(ui.roundedStroke(Color.TRANSPARENT, 50, ui.c(Ui.OUTLINE), 1));
        styleButton(b);
        return b;
    }

    /** Единая геометрия кнопок: одинаковая высота и отступы у всех стилей. */
    private void styleButton(android.widget.Button b) {
        b.setStateListAnimator(null);
        b.setMinHeight((int) ui.dp(46));
        b.setMinimumWidth(0);
        int p = (int) ui.dp(16);
        b.setPadding(p, (int) ui.dp(11), p, (int) ui.dp(11));
    }

    private TextView dialogSectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text.toUpperCase(Locale.getDefault()));
        tv.setTextColor(ui.c(Ui.PRIMARY));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(ui.medium());
        int pad = (int) ui.dp(2);
        tv.setPadding(pad, (int) ui.dp(10), pad, (int) ui.dp(8));
        return tv;
    }

    private void stylePill(String text, int bg, int fg) {
        statusPill.setText(text);
        statusPill.setTextColor(fg);
        statusPill.setBackground(ui.rounded(bg, 20));
    }

    private void dismissDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
    }
}
