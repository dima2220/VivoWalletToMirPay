package com.vivotomirpay.stub;

import android.app.Activity;
import android.graphics.Color;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Экран «Настройка меню»: список пунктов меню действий с изменением порядка
 * и удалением, плюс два явных способа добавления — «Добавить действие…»
 * (фонарик, камера, VPN, Circle to Search) и «Добавить приложение…»
 * (список установленных приложений с поиском).
 *
 * Изменения сохраняются сразу; экран обновляется на месте, без пересоздания.
 */
public class MenuEditorActivity extends Activity {

    private Ui ui;
    private LinearLayout list;
    private TextView emptyView;
    private AlertDialog currentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new Ui(this);
        buildUi();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ui.c(Ui.SURFACE));

        // --- Верхняя панель ----------------------------------------------------
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) ui.dp(16);
        topBar.setPadding(pad, (int) ui.dp(14), pad, (int) ui.dp(8));

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_action_back);
        back.setColorFilter(ui.c(Ui.ON_SURFACE), android.graphics.PorterDuff.Mode.SRC_IN);
        back.setClickable(true);
        back.setFocusable(true);
        back.setBackground(ui.rippleOnRounded(Color.TRANSPARENT, 20));
        int bp = (int) ui.dp(8);
        back.setPadding(bp, bp, bp, bp);
        back.setOnClickListener(v -> finish());
        topBar.addView(back, new LinearLayout.LayoutParams((int) ui.dp(40), (int) ui.dp(40)));

        TextView title = new TextView(this);
        title.setText(getString(R.string.editor_menu_title));
        title.setTextColor(ui.c(Ui.ON_SURFACE));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(ui.medium());
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = (int) ui.dp(6);
        topBar.addView(title, titleLp);

        root.addView(topBar);

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.editor_hint));
        hint.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        hint.setLineSpacing(0, 1.15f);
        hint.setPadding(pad, 0, pad, (int) ui.dp(10));
        root.addView(hint);

        // --- Список пунктов ------------------------------------------------------
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        emptyView = new TextView(this);
        emptyView.setText(getString(R.string.editor_empty));
        emptyView.setTextColor(ui.c(Ui.OUTLINE));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        emptyView.setGravity(Gravity.CENTER);
        int ep = (int) ui.dp(20);
        emptyView.setPadding(ep, ep, ep, ep);
        emptyView.setVisibility(View.GONE);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout listWrap = new LinearLayout(this);
        listWrap.setOrientation(LinearLayout.VERTICAL);
        listWrap.setPadding(pad, 0, pad, 0);
        listWrap.addView(list);
        listWrap.addView(emptyView);
        scroller.addView(listWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(scroller);

        // --- Нижние кнопки добавления ---------------------------------------------
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        int bPad = (int) ui.dp(14);
        bottom.setPadding(bPad, (int) ui.dp(10), bPad, (int) ui.dp(14));
        bottom.setBackground(ui.rounded(ui.alpha(Ui.SURFACE_HIGH, 0x55), 0));

        android.widget.Button addAction = buttonTonal(getString(R.string.editor_add_action));
        addAction.setOnClickListener(v -> showSpecialsDialog());
        LinearLayout.LayoutParams add1Lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        add1Lp.rightMargin = (int) ui.dp(8);
        bottom.addView(addAction, add1Lp);

        android.widget.Button addApp = buttonPrimary(getString(R.string.editor_add_app));
        addApp.setOnClickListener(v -> showAppPickerDialog());
        bottom.addView(addApp, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        rebuildList();
    }

    // =========================================================================
    // Список
    // =========================================================================

    private void rebuildList() {
        list.removeAllViews();
        JSONArray arr = PrefsHelper.getMenuItems(this);
        int n = arr.length();
        emptyView.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        // Индексы стрелок/крестика — позиции в JSON, а не в отрисованном списке,
        // иначе при пропущенных (пустых) элементах операции задевают соседей.
        for (int i = 0; i < n; i++) {
            final String id = arr.optString(i, "").trim();
            if (id.isEmpty()) continue;
            final int jsonIndex = i;
            list.addView(makeRow(id, jsonIndex, n));
        }
    }

    private LinearLayout makeRow(final String id, final int jsonIndex, final int total) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(ui.rippleOnRounded(ui.alpha(Ui.SURFACE_HIGH, 0x66), 16));
        row.setPadding((int) ui.dp(12), 0, (int) ui.dp(6), 0);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) ui.dp(54));
        rowLp.bottomMargin = (int) ui.dp(4);
        row.setLayoutParams(rowLp);

        boolean isSpecial = id.startsWith(PrefsHelper.SPECIAL_PREFIX);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (!isSpecial) {
            Drawable appIcon = appIconDrawable(
                    id.startsWith(PrefsHelper.APP_PREFIX) ? id.substring(PrefsHelper.APP_PREFIX.length()) : id);
            if (appIcon != null) {
                icon.setImageDrawable(appIcon);
            } else {
                icon.setImageResource(R.drawable.ic_action_app);
                icon.setColorFilter(ui.c(Ui.PRIMARY), android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } else {
            icon.setImageResource(iconFor(id));
            icon.setColorFilter(ui.c(Ui.ON_PRIMARY_CONTAINER), android.graphics.PorterDuff.Mode.SRC_IN);
        }
        row.addView(icon, new LinearLayout.LayoutParams((int) ui.dp(24), (int) ui.dp(24)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(this);
        label.setText(displayNameFor(id));
        label.setTextColor(ui.c(Ui.ON_SURFACE));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        label.setSingleLine(true);
        col.addView(label);
        TextView sub = new TextView(this);
        sub.setText(isSpecial ? id : (id.startsWith(PrefsHelper.APP_PREFIX) ? id.substring(PrefsHelper.APP_PREFIX.length()) : id));
        sub.setTextColor(ui.c(Ui.ON_SURFACE_VARIANT));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        sub.setSingleLine(true);
        col.addView(sub);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colLp.leftMargin = (int) ui.dp(12);
        row.addView(col, colLp);

        row.addView(smallIconButton(R.drawable.ic_action_up, () -> move(jsonIndex, -1), jsonIndex > 0));
        row.addView(smallIconButton(R.drawable.ic_action_down, () -> move(jsonIndex, 1), jsonIndex < total - 1));
        row.addView(smallIconButton(R.drawable.ic_action_delete, () -> removeAt(jsonIndex), true));
        return row;
    }

    private View smallIconButton(int iconRes, Runnable onClick, boolean enabled) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(enabled ? ui.c(Ui.ON_SURFACE_VARIANT) : ui.alpha(Ui.OUTLINE, 0x50),
                android.graphics.PorterDuff.Mode.SRC_IN);
        iv.setClickable(enabled);
        iv.setFocusable(enabled);
        if (enabled) {
            iv.setBackground(ui.rippleOnRounded(Color.TRANSPARENT, 20));
            iv.setOnClickListener(v -> onClick.run());
        }
        int size = (int) ui.dp(36);
        int pad = (int) ui.dp(8);
        iv.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = (int) ui.dp(2);
        iv.setLayoutParams(lp);
        return iv;
    }

    // =========================================================================
    // Мутации списка (сразу сохраняются)
    // =========================================================================

    private void mutate(MenuMutation mutation) {
        try {
            PrefsHelper.saveMenuItems(this, mutation.apply(PrefsHelper.getMenuItems(this)));
        } catch (Exception ignored) {
        }
        rebuildList();
    }

    private interface MenuMutation {
        JSONArray apply(JSONArray source);
    }

    private void move(final int index, final int delta) {
        mutate(src -> {
            int target = index + delta;
            if (target < 0 || target >= src.length()) return src;
            JSONArray next = new JSONArray();
            for (int j = 0; j < src.length(); j++) {
                if (j == index) next.put(src.opt(target));
                else if (j == target) next.put(src.opt(index));
                else next.put(src.opt(j));
            }
            return next;
        });
    }

    private void removeAt(final int index) {
        mutate(src -> {
            JSONArray next = new JSONArray();
            for (int j = 0; j < src.length(); j++) {
                if (j != index) next.put(src.opt(j));
            }
            return next;
        });
    }

    private void append(String id) {
        String clean = id == null ? "" : id.trim();
        if (clean.isEmpty()) return;
        mutate(src -> {
            // Дубликаты не добавляем: двойной тап по строке пикера или повторный
            // выбор того же приложения не должны плодить одинаковые пункты.
            for (int j = 0; j < src.length(); j++) {
                if (clean.equals(src.optString(j, ""))) {
                    runOnUiThread(() -> android.widget.Toast.makeText(this,
                            getString(R.string.editor_already_in_menu),
                            android.widget.Toast.LENGTH_SHORT).show());
                    return src;
                }
            }
            if (src.length() >= PrefsHelper.MAX_MENU_ITEMS) {
                runOnUiThread(() -> android.widget.Toast.makeText(this,
                        getString(R.string.editor_menu_full),
                        android.widget.Toast.LENGTH_SHORT).show());
                return src;
            }
            JSONArray next = new JSONArray();
            for (int j = 0; j < src.length(); j++) next.put(src.opt(j));
            next.put(clean);
            return next;
        });
    }

    // =========================================================================
    // Добавление: действия
    // =========================================================================

    private void showSpecialsDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = (int) ui.dp(16);
        content.setPadding(p, (int) ui.dp(14), p, (int) ui.dp(6));

        TextView header = new TextView(this);
        header.setText(getString(R.string.editor_add_action));
        header.setTextColor(ui.c(Ui.ON_SURFACE));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.setTypeface(ui.medium());
        header.setPadding((int) ui.dp(2), 0, 0, (int) ui.dp(8));
        content.addView(header);

        String[][] specials = {
                {PrefsHelper.SPECIAL_TORCH, getString(R.string.name_torch)},
                {PrefsHelper.SPECIAL_CAMERA, getString(R.string.name_camera)},
                {PrefsHelper.SPECIAL_VPN, getString(R.string.name_vpn)},
                {PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH, getString(R.string.name_cts)},
        };
        for (String[] sp : specials) {
            content.addView(makePickRow(sp[0], sp[1], null));
        }

        currentDialog = new AlertDialog.Builder(this)
                .setView(content)
                .setNegativeButton(R.string.btn_close, null)
                .setOnDismissListener(d -> currentDialog = null)
                .create();
        currentDialog.show();
        applyDialogBackground(currentDialog);
    }

    /** Округлый фон диалога в цветах Material You — вместо системных рамок. */
    private void applyDialogBackground(AlertDialog dialog) {
        try {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(ui.rounded(ui.c(Ui.CARD), 22));
            }
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // Добавление: приложения (с поиском)
    // =========================================================================

    private static class AppEntry {
        final String label;
        final String packageName;
        final Drawable icon;

        AppEntry(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    private void showAppPickerDialog() {
        final List<AppEntry> apps = installedApps();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = (int) ui.dp(16);
        content.setPadding(p, (int) ui.dp(14), p, (int) ui.dp(6));

        TextView header = new TextView(this);
        header.setText(getString(R.string.editor_add_app));
        header.setTextColor(ui.c(Ui.ON_SURFACE));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        header.setTypeface(ui.medium());
        header.setPadding((int) ui.dp(2), 0, 0, (int) ui.dp(8));
        content.addView(header);

        EditText search = new EditText(this);
        search.setHint(getString(R.string.picker_search_hint));
        search.setTextColor(ui.c(Ui.ON_SURFACE));
        search.setHintTextColor(ui.c(Ui.OUTLINE));
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setBackground(ui.roundedStroke(ui.alpha(Ui.SURFACE_HIGH, 0x66), 14,
                ui.c(Ui.OUTLINE), 1));
        int sp = (int) ui.dp(12);
        search.setPadding(sp, sp, sp, sp);
        content.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);

        Runnable[] updateList = new Runnable[1];

        updateList[0] = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
            appList.removeAllViews();
            int shown = 0;
            for (AppEntry e : apps) {
                boolean match = q.isEmpty()
                        || e.label.toLowerCase(Locale.getDefault()).contains(q)
                        || e.packageName.toLowerCase(Locale.getDefault()).contains(q);
                if (!match || shown >= 10) continue;
                shown++;
                appList.addView(makePickRow(PrefsHelper.APP_PREFIX + e.packageName, e.label, e.icon));
            }
            if (shown == 0) {
                TextView none = new TextView(this);
                none.setText(getString(R.string.picker_no_apps));
                none.setTextColor(ui.c(Ui.OUTLINE));
                none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                int np = (int) ui.dp(14);
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
                updateList[0].run();
            }
        });

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(appList);
        LinearLayout.LayoutParams scrollerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) ui.dp(320));
        scrollerLp.topMargin = (int) ui.dp(8);
        content.addView(scroller, scrollerLp);

        currentDialog = new AlertDialog.Builder(this)
                .setView(content)
                .setNegativeButton(R.string.btn_close, null)
                .setOnDismissListener(d -> currentDialog = null)
                .create();
        currentDialog.show();
        applyDialogBackground(currentDialog);
        updateList[0].run();
    }

    /** Строка выбора (действие или приложение) — тап добавляет пункт в меню. */
    private LinearLayout makePickRow(final String id, String label, Drawable icon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(ui.rippleOnRounded(ui.alpha(Ui.SURFACE_HIGH, 0x66), 14));
        int rp = (int) ui.dp(10);
        row.setPadding(rp, rp, rp, rp);

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (icon != null) {
            iv.setImageDrawable(icon);
        } else {
            iv.setImageResource(iconFor(id));
            iv.setColorFilter(ui.c(Ui.PRIMARY), android.graphics.PorterDuff.Mode.SRC_IN);
        }
        row.addView(iv, new LinearLayout.LayoutParams((int) ui.dp(26), (int) ui.dp(26)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ui.c(Ui.ON_SURFACE));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setSingleLine(true);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvLp.leftMargin = (int) ui.dp(12);
        row.addView(tv, tvLp);

        row.setOnClickListener(v -> {
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
            }
            append(id);
        });

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = (int) ui.dp(2);
        row.setLayoutParams(rowLp);
        return row;
    }

    // =========================================================================
    // Вспомогательные
    // =========================================================================

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
            // У одного пакета бывает несколько launcher-активностей и activity-alias
            // (тематические иконки и т.п.) — без дедупликации приложение
            // показывается в пикере несколько раз.
            if (!seen.add(ri.activityInfo.packageName)) continue;
            Drawable icon = null;
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

    private Drawable appIconDrawable(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private String displayNameFor(String id) {
        switch (id) {
            case PrefsHelper.SPECIAL_TORCH:
                return getString(R.string.name_torch);
            case PrefsHelper.SPECIAL_CAMERA:
                return getString(R.string.name_camera);
            case PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH:
                return getString(R.string.name_cts);
            case PrefsHelper.SPECIAL_VPN:
                String client = VpnController.resolveClientPackage(this,
                        PrefsHelper.getVpnClient(this));
                String clientName = client == null ? getString(R.string.vpn_name_generic)
                        : VpnController.clientLabel(client);
                return getString(R.string.name_vpn_short, clientName);
            case PrefsHelper.SPECIAL_MENU:
                return getString(R.string.name_menu);
            default:
                String pkg = PrefsHelper.appPackageOf(id);
                if (pkg != null) return appLabel(pkg, pkg);
                return appLabel(id, id);
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

    private android.widget.Button buttonPrimary(String text) {
        android.widget.Button b = new android.widget.Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(ui.c(Ui.ON_PRIMARY));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        b.setTypeface(ui.medium());
        b.setBackground(ui.rounded(ui.c(Ui.PRIMARY), 50));
        int p = (int) ui.dp(14);
        b.setPadding(p, (int) ui.dp(11), p, (int) ui.dp(11));
        return b;
    }

    private android.widget.Button buttonTonal(String text) {
        android.widget.Button b = new android.widget.Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(ui.c(Ui.ON_PRIMARY_CONTAINER));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        b.setTypeface(ui.medium());
        b.setBackground(ui.rounded(ui.c(Ui.PRIMARY_CONTAINER), 50));
        int p = (int) ui.dp(14);
        b.setPadding(p, (int) ui.dp(11), p, (int) ui.dp(11));
        return b;
    }
}
