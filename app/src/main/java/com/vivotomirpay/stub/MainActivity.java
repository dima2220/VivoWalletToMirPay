package com.vivotomirpay.stub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Главное окно настроек заглушки (функционал первой версии + интерфейс в стиле Android 13 Material You).
 */
public class MainActivity extends Activity {

    private TextView powerHandlerChip;
    private TextView statusDetailsView;
    private EditText packageEditText;
    private TextView targetAppStatusView;
    private TextView logTextView;
    private String currentDefaultSwipePkg = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildAndroid13Ui();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAllStates();
    }

    private void buildAndroid13Ui() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#FF111318"));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        root.setPadding(pad, dp(24), pad, dp(28));

        // Заголовок в стиле Android 13 Settings
        TextView headline = new TextView(this);
        headline.setText("Vivo -> Mir Pay");
        headline.setTextColor(Color.parseColor("#FFE2E2E9"));
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        headline.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        root.addView(headline);

        TextView subhead = new TextView(this);
        subhead.setText("Заглушка двойного нажатия кнопки питания для смартфонов Vivo");
        subhead.setTextColor(Color.parseColor("#FFC4C6D0"));
        subhead.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subhead.setPadding(0, dp(6), 0, dp(16));
        root.addView(subhead);

        // Статусная пилюля Android 13 (Material You Tonal Pill)
        powerHandlerChip = createStatusPill();
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pillLp.bottomMargin = dp(16);
        root.addView(powerHandlerChip, pillLp);

        // =========================================================================
        // КАРТОЧКА 1: Целевое приложение при двойном нажатии кнопки питания
        // =========================================================================
        LinearLayout targetCard = createM3Card();
        targetCard.addView(createM3SectionHeader("Какое приложение запускать", "⚡"));

        TextView defaultAppLabel = new TextView(this);
        defaultAppLabel.setText("Выберите приложение, которое будет мгновенно открываться при двойном нажатии кнопки питания:");
        defaultAppLabel.setTextColor(Color.parseColor("#FFC4C6D0"));
        defaultAppLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        defaultAppLabel.setPadding(0, dp(6), 0, dp(10));
        targetCard.addView(defaultAppLabel);

        // Быстрые чипы выбора приложения в стиле Android 13
        LinearLayout presetsRow = new LinearLayout(this);
        presetsRow.setOrientation(LinearLayout.HORIZONTAL);
        addM3ChipButton(presetsRow, "Mir Pay", "ru.nspk.mirpay");
        addM3ChipButton(presetsRow, "СБПэй", "ru.nspk.sbpay");
        addM3ChipButton(presetsRow, "Wallet (Ключ)", "com.vivo.wallet");
        LinearLayout.LayoutParams presetsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        presetsLp.bottomMargin = dp(10);
        targetCard.addView(presetsRow, presetsLp);

        packageEditText = new EditText(this);
        packageEditText.setText(PrefsHelper.getTargetPackage(this));
        packageEditText.setTextColor(Color.parseColor("#FFE2E2E9"));
        packageEditText.setHint("Имя пакета (например, ru.nspk.mirpay)");
        packageEditText.setHintTextColor(Color.parseColor("#FF8E9099"));
        packageEditText.setSingleLine(true);
        packageEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable editBg = new GradientDrawable();
        editBg.setColor(Color.parseColor("#FF111318"));
        editBg.setCornerRadius(dp(16));
        editBg.setStroke(dp(1), Color.parseColor("#FF44474F"));
        packageEditText.setBackground(editBg);
        int pEdit = dp(14);
        packageEditText.setPadding(pEdit, dp(12), pEdit, dp(12));
        targetCard.addView(packageEditText);

        targetAppStatusView = new TextView(this);
        targetAppStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        targetAppStatusView.setPadding(dp(4), dp(6), dp(4), dp(12));
        targetCard.addView(targetAppStatusView);

        LinearLayout rowSavePick = new LinearLayout(this);
        rowSavePick.setOrientation(LinearLayout.HORIZONTAL);

        Button savePkgBtn = createM3PrimaryButton("Сохранить");
        savePkgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pkg = packageEditText.getText().toString().trim();
                PrefsHelper.setTargetPackage(MainActivity.this, pkg);
                refreshAllStates();
                Toast.makeText(MainActivity.this, "Сохранено: " + pkg, Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half1.rightMargin = dp(8);
        rowSavePick.addView(savePkgBtn, half1);

        Button pickAppBtn = createM3TonalButton("Все приложения…");
        pickAppBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInstalledAppsPicker();
            }
        });
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rowSavePick.addView(pickAppBtn, half2);
        targetCard.addView(rowSavePick);

        CheckBox cbVibrate = new CheckBox(this);
        cbVibrate.setText("Короткая вибрация при двойном нажатии кнопки питания");
        cbVibrate.setTextColor(Color.parseColor("#FFC4C6D0"));
        cbVibrate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cbVibrate.setChecked(PrefsHelper.isVibrateEnabled(this));
        cbVibrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PrefsHelper.setVibrateEnabled(MainActivity.this, isChecked);
            }
        });
        LinearLayout.LayoutParams vibLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        vibLp.topMargin = dp(10);
        targetCard.addView(cbVibrate, vibLp);

        root.addView(targetCard, createCardMargin());

        // =========================================================================
        // КАРТОЧКА 2: Назначение по умолчанию и проверка
        // =========================================================================
        LinearLayout handlerCard = createM3Card();
        handlerCard.addView(createM3SectionHeader("Обработчик кнопки питания Vivo", "🔘"));

        statusDetailsView = new TextView(this);
        statusDetailsView.setTextColor(Color.parseColor("#FFC4C6D0"));
        statusDetailsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusDetailsView.setPadding(0, dp(6), dp(0), dp(14));
        handlerCard.addView(statusDetailsView);

        LinearLayout.LayoutParams rowMarginLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowMarginLp.bottomMargin = dp(10);

        Button testLaunchBtn = createM3PrimaryButton("Проверить запуск выбранного приложения");
        testLaunchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent test = new Intent(MainActivity.this, SwipeEntryActivity.class);
                test.setAction(SwipeEntryActivity.ACTION_VIVO_SWIPE);
                test.putExtra("lb_from", "2");
                startActivity(test);
            }
        });
        handlerCard.addView(testLaunchBtn, rowMarginLp);

        Button triggerSystemSwipeBtn = createM3TonalButton("Вызвать NFC_BUS_SWINGSPLASH (Выбрать «Всегда»)");
        triggerSystemSwipeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerVivoSwipeIntent();
            }
        });
        handlerCard.addView(triggerSystemSwipeBtn, rowMarginLp);

        Button clearDefaultBtn = createM3SecondaryButton("Открыть свойства текущего обработчика по умолчанию");
        clearDefaultBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pkgToOpen = currentDefaultSwipePkg != null ? currentDefaultSwipePkg : getPackageName();
                openAppDetails(pkgToOpen);
            }
        });
        handlerCard.addView(clearDefaultBtn);

        root.addView(handlerCard, createCardMargin());

        // =========================================================================
        // КАРТОЧКА 3: Диагностический журнал
        // =========================================================================
        LinearLayout logCard = createM3Card();
        logCard.addView(createM3SectionHeader("Журнал последнего вызова", "📋"));

        logTextView = new TextView(this);
        logTextView.setTextColor(Color.parseColor("#FFC4C6D0"));
        logTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        logTextView.setTypeface(Typeface.MONOSPACE);
        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(Color.parseColor("#FF111318"));
        logBg.setCornerRadius(dp(16));
        logTextView.setBackground(logBg);
        int pLog = dp(14);
        logTextView.setPadding(pLog, pLog, pLog, pLog);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        logLp.topMargin = dp(8);
        logCard.addView(logTextView, logLp);

        root.addView(logCard, createCardMargin());

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private void refreshAllStates() {
        PackageManager pm = getPackageManager();
        Intent testIntent = new Intent(SwipeEntryActivity.ACTION_VIVO_SWIPE);
        testIntent.addCategory(Intent.CATEGORY_DEFAULT);

        ResolveInfo defaultRi = pm.resolveActivity(testIntent, PackageManager.MATCH_DEFAULT_ONLY);
        List<ResolveInfo> allHandlers = pm.queryIntentActivities(testIntent, PackageManager.MATCH_DEFAULT_ONLY);

        currentDefaultSwipePkg = null;
        boolean ourAppIsDefault = false;

        if (defaultRi != null && defaultRi.activityInfo != null) {
            String defPkg = defaultRi.activityInfo.packageName;
            if (!"android".equals(defPkg) && !defPkg.toLowerCase().contains("resolver")) {
                currentDefaultSwipePkg = defPkg;
                ourAppIsDefault = getPackageName().equals(defPkg);
            }
        }

        if (ourAppIsDefault) {
            styleStatusPill(
                    powerHandlerChip,
                    "✓ Двойное нажатие питания: эта заглушка установлена по умолчанию",
                    "#FF003826",
                    "#FFA6F2D1"
            );
        } else if (currentDefaultSwipePkg != null) {
            styleStatusPill(
                    powerHandlerChip,
                    "⚠ Двойное нажатие питания: сейчас выбрано " + currentDefaultSwipePkg,
                    "#FF412600",
                    "#FFFFDEA6"
            );
        } else {
            styleStatusPill(
                    powerHandlerChip,
                    "ℹ Двойное нажатие питания: приложение по умолчанию ещё не зафиксировано",
                    "#FF1E2A3A",
                    "#FFA8C7FA"
            );
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Зарегистрированные обработчики NFC_BUS_SWINGSPLASH (").append(allHandlers.size()).append("):\n");
        for (ResolveInfo ri : allHandlers) {
            if (ri.activityInfo != null) {
                CharSequence label = ri.loadLabel(pm);
                sb.append("• ").append(label)
                        .append(" (").append(ri.activityInfo.packageName).append(")\n");
            }
        }
        statusDetailsView.setText(sb.toString().trim());

        String targetPkg = PrefsHelper.getTargetPackage(this);
        if (isPackageInstalled(targetPkg)) {
            String label = getAppLabel(targetPkg, targetPkg);
            targetAppStatusView.setText("✓ Пакет установлен: " + label + " (" + targetPkg + ")");
            targetAppStatusView.setTextColor(Color.parseColor("#FFA6F2D1"));
        } else {
            targetAppStatusView.setText("⚠ Пакет " + targetPkg + " сейчас не установлен на устройстве");
            targetAppStatusView.setTextColor(Color.parseColor("#FFFFB4AB"));
        }

        logTextView.setText(PrefsHelper.getLastTriggerLog(this));
    }

    private boolean isPackageInstalled(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getAppLabel(String pkg, String fallback) {
        try {
            PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void triggerVivoSwipeIntent() {
        try {
            Intent intent = new Intent(SwipeEntryActivity.ACTION_VIVO_SWIPE);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra("lb_from", "2");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка вызова: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openAppDetails(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", pkg, null));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть настройки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addM3ChipButton(LinearLayout parent, String title, final String pkg) {
        Button chip = new Button(this);
        chip.setAllCaps(false);
        chip.setText(title);
        chip.setTextColor(Color.parseColor("#FFD3E3FD"));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#FF283040"));
        bg.setCornerRadius(dp(50));
        bg.setStroke(dp(1), Color.parseColor("#FF44526C"));
        chip.setBackground(bg);
        chip.setPadding(dp(8), dp(6), dp(8), dp(6));
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                packageEditText.setText(pkg);
                PrefsHelper.setTargetPackage(MainActivity.this, pkg);
                refreshAllStates();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(6);
        parent.addView(chip, lp);
    }

    private static class AppEntry {
        final String label;
        final String packageName;

        AppEntry(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    private void showInstalledAppsPicker() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(mainIntent, 0);

        final List<AppEntry> entries = new ArrayList<>();
        for (ResolveInfo ri : list) {
            if (ri.activityInfo != null) {
                String pkg = ri.activityInfo.packageName;
                if (!getPackageName().equals(pkg)) {
                    String label = String.valueOf(ri.loadLabel(pm));
                    entries.add(new AppEntry(label, pkg));
                }
            }
        }
        Collections.sort(entries, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        CharSequence[] items = new CharSequence[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            AppEntry e = entries.get(i);
            items[i] = e.label + "\n(" + e.packageName + ")";
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Выберите приложение")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AppEntry chosen = entries.get(which);
                        packageEditText.setText(chosen.packageName);
                        PrefsHelper.setTargetPackage(MainActivity.this, chosen.packageName);
                        refreshAllStates();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private TextView createStatusPill() {
        TextView tv = new TextView(this);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setPadding(dp(16), dp(12), dp(16), dp(12));
        return tv;
    }

    private void styleStatusPill(TextView tv, String text, String bgHex, String fgHex) {
        tv.setText(text);
        tv.setTextColor(Color.parseColor(fgHex));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(dp(20));
        tv.setBackground(bg);
    }

    private LinearLayout createM3Card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#FF1D2026"));
        bg.setCornerRadius(dp(28));
        card.setBackground(bg);
        int p = dp(20);
        card.setPadding(p, p, p, p);
        return card;
    }

    private LinearLayout.LayoutParams createCardMargin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(16);
        return lp;
    }

    private View createM3SectionHeader(String titleText, String iconEmoji) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(6));

        TextView badge = new TextView(this);
        badge.setText(iconEmoji);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#FF283040"));
        badgeBg.setCornerRadius(dp(12));
        badge.setBackground(badgeBg);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        badgeLp.rightMargin = dp(12);
        row.addView(badge, badgeLp);

        TextView tv = new TextView(this);
        tv.setText(titleText);
        tv.setTextColor(Color.parseColor("#FFE2E2E9"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(tv);

        return row;
    }

    private Button createM3PrimaryButton(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(Color.parseColor("#FF062E6F"));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#FFA8C7FA"));
        bg.setCornerRadius(dp(50));
        b.setBackground(bg);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        return b;
    }

    private Button createM3TonalButton(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(Color.parseColor("#FFD3E3FD"));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#FF283040"));
        bg.setCornerRadius(dp(50));
        b.setBackground(bg);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        return b;
    }

    private Button createM3SecondaryButton(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(Color.parseColor("#FFC4C6D0"));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#FF282A31"));
        bg.setCornerRadius(dp(50));
        bg.setStroke(dp(1), Color.parseColor("#FF44474F"));
        b.setBackground(bg);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        return b;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
