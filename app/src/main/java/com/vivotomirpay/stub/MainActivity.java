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
import android.text.Editable;
import android.text.TextWatcher;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Главное окно настроек заглушки.
 *
 * Карточки UI:
 *   1. «Какое приложение запускать при двойном нажатии кнопки питания» —
 *      пресеты Mir Pay / СБПэй / Vivo Wallet / Фонарик / Circle to Search,
 *      поле ввода packageName, кнопка «Все приложения…» с динамическим поиском.
 *   2. «Обработчик кнопки питания Vivo» — статусная плашка, кнопки проверки запуска,
 *      вызова системного интента NFC_BUS_SWINGSPLASH, открытия свойств обработчика.
 *   3. «Журнал последнего вызова» — ограниченный по длине diagnostic log.
 *
 * Над каждой группой кнопок есть короткая пояснительная подсказка в стиле
 * Material You, чтобы пользователю было понятно «зачем эта кнопка и что с ней делать».
 */
public class MainActivity extends Activity {

    private TextView powerHandlerChip;
    private TextView statusDetailsView;
    private EditText packageEditText;
    private TextView targetAppStatusView;
    private TextView logTextView;
    private TextView assistantStatusView;
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
        headline.setText("Double Power Key");
        headline.setTextColor(Color.parseColor("#FFE2E2E9"));
        headline.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        headline.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        root.addView(headline);

        TextView subhead = new TextView(this);
        subhead.setText("Переназначение двойного нажатия кнопки питания для смартфонов Vivo / iQOO (OriginOS / FuntouchOS)");
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
        // КАРТОЧКА 1: Целевое приложение / спец-функция
        // =========================================================================
        LinearLayout targetCard = createM3Card();
        targetCard.addView(createM3SectionHeader("Что запускает двойное нажатие", "⚡"));

        targetCard.addView(createHelpText(
                "Выберите, что произойдёт при двойном нажатии кнопки питания. "
                        + "Можно выбрать обычное приложение (Mir Pay, СБПэй и др.) или одну из "
                        + "спец-функций — фонарик или Circle to Search. "
                        + "Circle to Search использует тот же механизм, что и MiCTS: "
                        + "вызов VoiceInteractionService через reflection. Без root, без accessibility."
        ));

        // Быстрые чипы выбора пресета в стиле Android 13
        LinearLayout presetsRow = new LinearLayout(this);
        presetsRow.setOrientation(LinearLayout.HORIZONTAL);
        addM3ChipButton(presetsRow, "Mir Pay", "ru.nspk.mirpay");
        addM3ChipButton(presetsRow, "СБПэй", "ru.nspk.sbpay");
        addM3ChipButton(presetsRow, "Wallet (Ключ)", "com.vivo.wallet");
        LinearLayout.LayoutParams presetsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        presetsLp.bottomMargin = dp(8);
        targetCard.addView(presetsRow, presetsLp);

        // Второй ряд — спец-функции (не packageName, а special:*)
        LinearLayout specialsRow = new LinearLayout(this);
        specialsRow.setOrientation(LinearLayout.HORIZONTAL);
        addM3ChipButton(specialsRow, "Фонарик", PrefsHelper.SPECIAL_TORCH);
        addM3ChipButton(specialsRow, "Circle to Search", PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH);
        LinearLayout.LayoutParams specialsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        specialsLp.bottomMargin = dp(10);
        targetCard.addView(specialsRow, specialsLp);

        packageEditText = new EditText(this);
        packageEditText.setText(PrefsHelper.getTargetPackage(this));
        packageEditText.setTextColor(Color.parseColor("#FFE2E2E9"));
        packageEditText.setHint("Имя пакета (ru.nspk.mirpay) или special:torch / special:circle_to_search");
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

        targetCard.addView(createHelpText(
                "«Сохранить» — записывает выбор. «Все приложения…» — открывает список "
                        + "с динамическим поиском по названию или packageName."
        ), createHelpLp());

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
        rowMarginLp.bottomMargin = dp(6);

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
        handlerCard.addView(createHelpText(
                "Имитирует двойное нажатие питания, не меняя системного обработчика. "
                        + "Удобно проверить, запускается ли выбранное приложение."
        ), createHelpLp());

        LinearLayout.LayoutParams rowMarginLp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowMarginLp2.bottomMargin = dp(6);

        Button triggerSystemSwipeBtn = createM3TonalButton("Вызвать NFC_BUS_SWINGSPLASH (Выбрать «Всегда»)");
        triggerSystemSwipeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerVivoSwipeIntent();
            }
        });
        handlerCard.addView(triggerSystemSwipeBtn, rowMarginLp2);
        handlerCard.addView(createHelpText(
                "Отправляет тот же системный Intent, что Vivo отправляет при двойном нажатии кнопки "
                        + "питания. Появится системное окно выбора — выберите «Double Power Key» и "
                        + "отметьте «Всегда», чтобы заглушка стала постоянным обработчиком."
        ), createHelpLp());

        LinearLayout.LayoutParams rowMarginLp3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowMarginLp3.bottomMargin = dp(6);

        Button clearDefaultBtn = createM3SecondaryButton("Открыть свойства текущего обработчика по умолчанию");
        clearDefaultBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pkgToOpen = currentDefaultSwipePkg != null ? currentDefaultSwipePkg : getPackageName();
                openAppDetails(pkgToOpen);
            }
        });
        handlerCard.addView(clearDefaultBtn, rowMarginLp3);
        handlerCard.addView(createHelpText(
                "Открывает системные настройки приложения, которое сейчас назначено "
                        + "обработчиком двойного нажатия. Там можно сбросить выбор, чтобы "
                        + "снова вызвать окно «Всегда»."
        ), createHelpLp());

        root.addView(handlerCard, createCardMargin());

        // =========================================================================
        // КАРТОЧКА 3: Ассистент по умолчанию
        // =========================================================================
        LinearLayout assistantCard = createM3Card();
        assistantCard.addView(createM3SectionHeader("Ассистент по умолчанию", "🤖"));

        assistantCard.addView(createHelpText(
                "Пресет Circle to Search запускает ассистента, который выбран "
                        + "как цифровой помощник по умолчанию в системе. Если хотите, чтобы "
                        + "Circle to Search открывал Gemini (а не Google), выберите Gemini "
                        + "как ассистента по умолчанию. На Android 13+ Gemini интегрирован в "
                        + "Google-приложение: откройте Google-приложение → профиль → Настройки "
                        + "→ Gemini → включите «Использовать Gemini как ассистента». После этого "
                        + "в статусе ниже появится «Gemini»."
        ));

        final TextView assistantStatus = new TextView(this);
        assistantStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        assistantStatus.setPadding(dp(4), dp(6), dp(4), dp(12));
        assistantCard.addView(assistantStatus);

        Button changeAssistantBtn = createM3PrimaryButton("Изменить ассистента по умолчанию");
        changeAssistantBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDefaultAssistantSettings();
            }
        });
        LinearLayout.LayoutParams changeAssistantLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        changeAssistantLp.bottomMargin = dp(6);
        assistantCard.addView(changeAssistantBtn, changeAssistantLp);

        Button openGoogleAppBtn = createM3SecondaryButton("Открыть Google-приложение (для переключения на Gemini)");
        openGoogleAppBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    PackageManager pm = getPackageManager();
                    Intent launch = pm.getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launch);
                    }
                } catch (Exception ignored) {
                }
            }
        });
        assistantCard.addView(openGoogleAppBtn);

        // Сохраняем ссылку на статус-вью, чтобы refreshAllStates() обновлял его.
        assistantStatusView = assistantStatus;

        root.addView(assistantCard, createCardMargin());

        // =========================================================================
        // КАРТОЧКА 4: Диагностический журнал
        // =========================================================================
        LinearLayout logCard = createM3Card();
        logCard.addView(createM3SectionHeader("Журнал последнего вызова", "📋"));

        logCard.addView(createHelpText(
                "Записывается только последний вызов. Action и значения extras обрезаются "
                        + "до 100 символов, общий лог — до 1000 символов. Это защищает приложение "
                        + "от раздувания SharedPreferences и UI произвольными интентами."
        ), createHelpLp());

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
        if (targetPkg.startsWith(PrefsHelper.SPECIAL_PREFIX)) {
            // Для спец-значений не проверяем установку пакета — просто показываем подсказку.
            targetAppStatusView.setText("⚙ Спец-функция: " + describeSpecial(targetPkg));
            targetAppStatusView.setTextColor(Color.parseColor("#FFA8C7FA"));
        } else if (isPackageInstalled(targetPkg)) {
            String label = getAppLabel(targetPkg, targetPkg);
            targetAppStatusView.setText("✓ Пакет установлен: " + label + " (" + targetPkg + ")");
            targetAppStatusView.setTextColor(Color.parseColor("#FFA6F2D1"));
        } else {
            targetAppStatusView.setText("⚠ Пакет " + targetPkg + " сейчас не установлен на устройстве");
            targetAppStatusView.setTextColor(Color.parseColor("#FFFFB4AB"));
        }

        // Статус ассистента по умолчанию.
        if (assistantStatusView != null) {
            String assistant = getCurrentDefaultAssistant();
            boolean isGemini = assistant != null
                    && assistant.toLowerCase().contains("com.google.android.apps.bard");
            boolean isGoogle = assistant != null
                    && assistant.toLowerCase().contains("com.google.android.googlequicksearchbox");
            if (isGemini) {
                assistantStatusView.setText("✓ Ассистент по умолчанию: " + assistant
                        + ". Circle to Search будет открывать Gemini.");
                assistantStatusView.setTextColor(Color.parseColor("#FFA6F2D1"));
            } else if (isGoogle) {
                assistantStatusView.setText("ℹ Ассистент по умолчанию: " + assistant
                        + ". Circle to Search будет открывать Google. Чтобы открывал Gemini — "
                        + "переключите ассистента кнопкой ниже.");
                assistantStatusView.setTextColor(Color.parseColor("#FFA8C7FA"));
            } else {
                assistantStatusView.setText("⚠ Ассистент по умолчанию: " + assistant
                        + ". Нажмите кнопку ниже и выберите Google или Gemini.");
                assistantStatusView.setTextColor(Color.parseColor("#FFFFB4AB"));
            }
        }

        logTextView.setText(PrefsHelper.getLastTriggerLog(this));
    }

    /** Читает текущего ассистента по умолчанию из Settings.Secure.ASSISTANT.
     *  Возвращает «Google (com.google.android.googlequicksearchbox)» или «не задан». */
    private String getCurrentDefaultAssistant() {
        try {
            String assistant = android.provider.Settings.Secure.getString(
                    getContentResolver(), "assistant");
            if (assistant == null || assistant.isEmpty()) {
                return "не задан";
            }
            String pkg = assistant;
            if (assistant.contains("/")) {
                pkg = assistant.split("/", 2)[0];
            }
            String label = getAppLabel(pkg, pkg);
            if (label == null || label.equals(pkg)) {
                return assistant;
            }
            return label + " (" + pkg + ")";
        } catch (Exception e) {
            return "ошибка чтения: " + e.getMessage();
        }
    }

    /** Открывает системные настройки «Цифровой ассистент по умолчанию».
     *  Пробует несколько intent'ов по убыванию специфичности:
     *    1) android.settings.VOICE_INPUT_SETTINGS — открывает «Голос и активность»
     *    2) android.settings.MANAGE_DEFAULT_APPS_SETTINGS — открывает «Приложения по умолчанию»
     *    3) android.settings.MANAGE_ASSISTANT — Android 14+, недокументированный
     *    4) Настройки приложения Google (если совсем ничего не сработало). */
    private void openDefaultAssistantSettings() {
        String[] actions = new String[]{
                "android.settings.VOICE_INPUT_SETTINGS",
                "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
                "android.settings.MANAGE_ASSISTANT",
        };
        for (String action : actions) {
            try {
                Intent intent = new Intent(action);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Exception ignored) {
                // try next
            }
        }
        // Fallback: открыть настройки приложения Google.
        try {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package",
                    "com.google.android.googlequicksearchbox", null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private String describeSpecial(String pkg) {
        if (PrefsHelper.SPECIAL_TORCH.equals(pkg)) return "Фонарик (вкл/выкл вспышкой)";
        if (PrefsHelper.SPECIAL_CIRCLE_TO_SEARCH.equals(pkg))
            return "Circle to Search (через VoiceInteractionService, как MiCTS)";
        return pkg;
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

    /** Диалог выбора приложения с динамическим поиском по названию или packageName. */
    private void showInstalledAppsPicker() {
        final PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(mainIntent, 0);

        final List<AppEntry> allEntries = new ArrayList<>();
        for (ResolveInfo ri : list) {
            if (ri.activityInfo != null) {
                String pkg = ri.activityInfo.packageName;
                if (!getPackageName().equals(pkg)) {
                    String label = String.valueOf(ri.loadLabel(pm));
                    allEntries.add(new AppEntry(label, pkg));
                }
            }
        }
        Collections.sort(allEntries, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        // Кастомный вид для диалога: EditText для поиска + ListView.
        final EditText searchBox = new EditText(this);
        searchBox.setHint("Поиск: введите название или имя пакета…");
        searchBox.setHintTextColor(Color.parseColor("#FF8E9099"));
        searchBox.setTextColor(Color.parseColor("#FFE2E2E9"));
        searchBox.setSingleLine(true);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        int pad8 = dp(12);
        searchBox.setPadding(pad8, pad8, pad8, pad8);

        final LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        final Runnable[] updateList = new Runnable[1];

        updateList[0] = () -> {
            String q = searchBox.getText().toString().trim().toLowerCase(Locale.getDefault());
            listContainer.removeAllViews();
            for (AppEntry e : allEntries) {
                boolean match = q.isEmpty()
                        || e.label.toLowerCase(Locale.getDefault()).contains(q)
                        || e.packageName.toLowerCase(Locale.getDefault()).contains(q);
                if (!match) continue;
                TextView item = new TextView(this);
                item.setText(e.label + "\n" + e.packageName);
                item.setTextColor(Color.parseColor("#FFE2E2E9"));
                item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                int p = dp(12);
                item.setPadding(p, p, p, p);
                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setColor(Color.parseColor("#FF1D2026"));
                itemBg.setCornerRadius(dp(12));
                item.setBackground(itemBg);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                ilp.bottomMargin = dp(6);
                item.setLayoutParams(ilp);
                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        packageEditText.setText(e.packageName);
                        PrefsHelper.setTargetPackage(MainActivity.this, e.packageName);
                        refreshAllStates();
                        // Диалог закроется через dismiss, который мы повесим ниже —
                        // но мы не храним ссылку на него из этого Runnable, поэтому
                        // закрываем через аккуратный трюк: убираем фокус и шлём BACK.
                        // Проще: обратиться через глобальную переменную dialog.
                        if (currentPickerDialog != null) {
                            currentPickerDialog.dismiss();
                        }
                    }
                });
                listContainer.addView(item);
            }
        };

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateList[0].run();
            }
        });

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.addView(listContainer);

        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        dialogRoot.setPadding(p, p, p, p);
        dialogRoot.addView(searchBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(360)
        );
        scrollerLp.topMargin = dp(8);
        dialogRoot.addView(scroller, scrollerLp);

        currentPickerDialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Выберите приложение")
                .setView(dialogRoot)
                .setNegativeButton("Отмена", null)
                .setOnDismissListener(d -> currentPickerDialog = null)
                .create();
        currentPickerDialog.show();
        updateList[0].run();
    }

    private AlertDialog currentPickerDialog;

    private TextView createHelpText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#FF9C9EA8"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        return tv;
    }

    private LinearLayout.LayoutParams createHelpLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.topMargin = dp(2);
        lp.bottomMargin = dp(12);
        return lp;
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
