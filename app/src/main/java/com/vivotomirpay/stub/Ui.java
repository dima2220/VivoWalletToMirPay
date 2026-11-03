package com.vivotomirpay.stub;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;

/**
 * Токены цветов и примитивы Material 3 для программного UI.
 *
 * Динамические цвета Material You: на Android 12+ (API 31) системные палитры
 * android.R.color.system_accent1_* / system_neutral1_* / system_neutral2_* /
 * system_accent3_* резолвятся в рантайме и сортируются по фактической светлоте,
 * поэтому результат корректен независимо от направления тонов в конкретной
 * прошивке. Светлота подбирается по перцентилям:primary в светлой теме —
 * средне-тёмный тон, в тёмной — светлый, и т.д.
 *
 * На Android 11 и старше (и при любом сбое резолва) используется базовая
 * статичная палитра Material 3 (light/dark по системной теме через uiMode).
 */
public class Ui {

    // --- Токены --------------------------------------------------------------
    public static final int SURFACE = 0;             // фон окна
    public static final int CARD = 1;                // карточки (surfaceContainer)
    public static final int SURFACE_HIGH = 2;        // чипы, тональные кнопки
    public static final int ON_SURFACE = 3;          // основной текст
    public static final int ON_SURFACE_VARIANT = 4;  // вторичный текст
    public static final int OUTLINE = 5;             // рамки, разделители
    public static final int PRIMARY = 6;             // акцент (заливка кнопок)
    public static final int ON_PRIMARY = 7;          // текст на акценте
    public static final int PRIMARY_CONTAINER = 8;   // контейнерный акцент (пилюли)
    public static final int ON_PRIMARY_CONTAINER = 9;
    public static final int SUCCESS_CONTAINER = 10;  // зелёная плашка «всё хорошо»
    public static final int ON_SUCCESS_CONTAINER = 11;

    private final Context ctx;
    private final boolean night;
    private final int[] palette;
    private final boolean dynamic;

    public Ui(Context context) {
        this.ctx = context;
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        this.night = mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int[] dyn = Build.VERSION.SDK_INT >= 31 ? resolveDynamicPalette() : null;
        if (dyn != null) {
            palette = dyn;
            dynamic = true;
        } else {
            palette = night ? staticDark() : staticLight();
            dynamic = false;
        }
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public int c(int token) {
        return palette[token];
    }

    /** Прозрачный вариант цвета (alpha 0..255). */
    public int alpha(int token, int alpha) {
        return (alpha << 24) | (palette[token] & 0x00FFFFFF);
    }

    public int argb(int argbColor) {
        return argbColor;
    }

    // --------------------------------------------------------------------------
    // Динамическая палитра Material You
    // --------------------------------------------------------------------------

    private static final int[] TONES = {
            0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100,
            200, 300, 400, 500, 600, 700, 800, 900, 1000
    };

    /** Считывает семейство тонов и сортирует его от тёмного к светлому по светлоте. */
    private int[] luminanceSorted(String family) {
        Resources res = ctx.getResources();
        String pkg = "android";
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        for (int tone : TONES) {
            int id = res.getIdentifier(family + "_" + tone, "color", pkg);
            if (id == 0) continue;
            try {
                colors.add(res.getColor(id, null));
            } catch (Exception ignored) {
            }
        }
        if (colors.size() < 5) return null;
        // Сортировка по светлоте (YIQ-яркость): индекс 0 — самый тёмный.
        java.util.Collections.sort(colors, (a, b) ->
                Float.compare(luma(a), luma(b)));
        int[] arr = new int[colors.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = colors.get(i);
        return arr;
    }

    private static float luma(int color) {
        float r = Color.red(color) / 255f;
        float g = Color.green(color) / 255f;
        float b = Color.blue(color) / 255f;
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    private static int tone(int[] sorted, float fraction) {
        int idx = Math.round(fraction * (sorted.length - 1));
        if (idx < 0) idx = 0;
        if (idx > sorted.length - 1) idx = sorted.length - 1;
        return sorted[idx];
    }

    private int[] resolveDynamicPalette() {
        try {
            int[] accent1 = luminanceSorted("system_accent1");
            int[] neutral1 = luminanceSorted("system_neutral1");
            int[] neutral2 = luminanceSorted("system_neutral2");
            int[] accent3 = luminanceSorted("system_accent3");
            if (accent1 == null || neutral1 == null || neutral2 == null) return null;

            int[] p = new int[12];
            if (!night) {
                // Светлая тема.
                p[PRIMARY] = tone(accent1, 0.38f);
                p[ON_PRIMARY] = tone(accent1, 0.97f);
                p[PRIMARY_CONTAINER] = tone(accent1, 0.88f);
                p[ON_PRIMARY_CONTAINER] = tone(accent1, 0.10f);
                p[SURFACE] = tone(neutral1, 0.99f);
                p[CARD] = tone(neutral2, 0.92f);
                p[SURFACE_HIGH] = tone(neutral2, 0.84f);
                p[ON_SURFACE] = tone(neutral1, 0.05f);
                p[ON_SURFACE_VARIANT] = tone(neutral2, 0.30f);
                p[OUTLINE] = tone(neutral2, 0.55f);
                if (accent3 != null) {
                    p[SUCCESS_CONTAINER] = tone(accent3, 0.86f);
                    p[ON_SUCCESS_CONTAINER] = tone(accent3, 0.15f);
                } else {
                    p[SUCCESS_CONTAINER] = 0xFFB7F0C7;
                    p[ON_SUCCESS_CONTAINER] = 0xFF0B3D1D;
                }
            } else {
                // Тёмная тема.
                p[PRIMARY] = tone(accent1, 0.72f);
                p[ON_PRIMARY] = tone(accent1, 0.15f);
                p[PRIMARY_CONTAINER] = tone(accent1, 0.30f);
                p[ON_PRIMARY_CONTAINER] = tone(accent1, 0.85f);
                p[SURFACE] = tone(neutral1, 0.03f);
                p[CARD] = tone(neutral1, 0.10f);
                p[SURFACE_HIGH] = tone(neutral1, 0.17f);
                p[ON_SURFACE] = tone(neutral1, 0.94f);
                p[ON_SURFACE_VARIANT] = tone(neutral2, 0.72f);
                p[OUTLINE] = tone(neutral2, 0.48f);
                if (accent3 != null) {
                    p[SUCCESS_CONTAINER] = tone(accent3, 0.22f);
                    p[ON_SUCCESS_CONTAINER] = tone(accent3, 0.85f);
                } else {
                    p[SUCCESS_CONTAINER] = 0xFF1E4B2C;
                    p[ON_SUCCESS_CONTAINER] = 0xFFA6F2D1;
                }
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    // --------------------------------------------------------------------------
    // Статичные палитры Material 3 (baseline)
    // --------------------------------------------------------------------------

    private static int[] staticLight() {
        return new int[]{
                0xFFFEF7FF, // SURFACE
                0xFFF3EDF7, // CARD
                0xFFE7E0EC, // SURFACE_HIGH
                0xFF1D1B20, // ON_SURFACE
                0xFF49454F, // ON_SURFACE_VARIANT
                0xFF79747E, // OUTLINE
                0xFF6750A3, // PRIMARY
                0xFFFFFFFF, // ON_PRIMARY
                0xFFEADDFF, // PRIMARY_CONTAINER
                0xFF21005D, // ON_PRIMARY_CONTAINER
                0xFFB7F0C7, // SUCCESS_CONTAINER
                0xFF0B3D1D  // ON_SUCCESS_CONTAINER
        };
    }

    private static int[] staticDark() {
        return new int[]{
                0xFF141218, // SURFACE
                0xFF211F26, // CARD
                0xFF2B2930, // SURFACE_HIGH
                0xFFE6E0E9, // ON_SURFACE
                0xFFCAC4D0, // ON_SURFACE_VARIANT
                0xFF938F99, // OUTLINE
                0xFFD0BCFF, // PRIMARY
                0xFF381E72, // ON_PRIMARY
                0xFF4F378B, // PRIMARY_CONTAINER
                0xFFEADDFF, // ON_PRIMARY_CONTAINER
                0xFF1E4B2C, // SUCCESS_CONTAINER
                0xFFA6F2D1  // ON_SUCCESS_CONTAINER
        };
    }

    // --------------------------------------------------------------------------
    // Примитивы вьюх
    // --------------------------------------------------------------------------

    public Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    public Typeface regular() {
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }

    public GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    public GradientDrawable roundedStroke(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = rounded(color, radiusDp);
        d.setStroke((int) dp(strokeDp), strokeColor);
        return d;
    }

    public RippleDrawable rippleOnRounded(int baseColor, float radiusDp) {
        GradientDrawable content = rounded(baseColor, radiusDp);
        GradientDrawable mask = rounded(0xFFFFFFFF, radiusDp);
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(alpha(ON_SURFACE, 0x28)),
                content, mask);
    }

    public float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }
}
