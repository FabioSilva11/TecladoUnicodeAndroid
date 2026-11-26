package com.luisunicode.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

public final class GboardTheme {

    public static final int THEME_DYNAMIC = 0;
    public static final int THEME_SYSTEM_AUTO = 1;
    public static final int THEME_LIGHT_DEFAULT = 2;
    public static final int THEME_DARK_DEFAULT = 3;
    public static final int THEME_OLED_BLACK = 4;
    public static final int THEME_TEAL_LIGHT = 5;
    public static final int THEME_TEAL_DARK = 6;
    public static final int THEME_CRIMSON_RED = 7;
    public static final int THEME_FOREST_GREEN = 8;
    public static final int THEME_PC_GAMER_RGB = 9;

    public static final String PREFS_NAME = "gboard_unicode_prefs";
    private static final String KEY_THEME_ID = "selected_theme_id";
    private static final String KEY_KEY_BORDERS = "key_borders_enabled";
    private static final String KEY_AUTO_CORRECT = "auto_correct_enabled";
    private static final String KEY_STRICT_ANTI_ERROR = "strict_anti_error_enabled";
    private static final String KEY_SHOW_SUGGESTIONS = "show_suggestions_enabled";
    private static final String KEY_AUTO_CAP = "auto_cap_enabled";
    private static final String KEY_DOUBLE_SPACE_DOT = "double_space_dot_enabled";
    private static final String KEY_NUMBER_ROW = "number_row_enabled";
    private static final String KEY_HEIGHT_MODE = "keyboard_height_mode";
    private static final String KEY_HAPTIC = "haptic_press_enabled";
    private static final String KEY_SOUND = "sound_press_enabled";
    private static final String KEY_POPUP = "popup_preview_enabled";
    private static final String KEY_KEYCAP_FONT = "keycap_font_mode";
    private static final String KEY_TYPING_FONT = "typing_unicode_font";

    public static final class ThemeOption {
        public final int id;
        public final String name;
        public final int previewBg;
        public final int previewSpace;
        public final int previewEnter;
        public final boolean splitPreview;
        public final boolean rgbPreview;

        public ThemeOption(int id, String name, int previewBg, int previewSpace, int previewEnter,
                           boolean splitPreview, boolean rgbPreview) {
            this.id = id;
            this.name = name;
            this.previewBg = previewBg;
            this.previewSpace = previewSpace;
            this.previewEnter = previewEnter;
            this.splitPreview = splitPreview;
            this.rgbPreview = rgbPreview;
        }
    }

    public static final ThemeOption[] ALL_THEMES = {
            new ThemeOption(THEME_DYNAMIC, "Cores dinâmicas",
                    Color.parseColor("#EEEDF4"), Color.WHITE, Color.parseColor("#DCE2F9"), false, false),
            new ThemeOption(THEME_PC_GAMER_RGB, "PC Gamer RGB",
                    Color.parseColor("#05060A"), Color.parseColor("#141824"), Color.parseColor("#00E5FF"), false, true),
            new ThemeOption(THEME_SYSTEM_AUTO, "Automático",
                    Color.parseColor("#ECEFF4"), Color.WHITE, Color.parseColor("#A8C7FA"), true, false),
            new ThemeOption(THEME_LIGHT_DEFAULT, "Padrão Claro",
                    Color.parseColor("#ECEFF4"), Color.WHITE, Color.parseColor("#0B57D0"), false, false),
            new ThemeOption(THEME_DARK_DEFAULT, "Escuro padrão",
                    Color.parseColor("#1B1C1F"), Color.parseColor("#2E3036"), Color.parseColor("#A8C7FA"), false, false),
            new ThemeOption(THEME_OLED_BLACK, "Preto OLED",
                    Color.parseColor("#000000"), Color.parseColor("#1E2025"), Color.parseColor("#8AB4F8"), false, false),
            new ThemeOption(THEME_TEAL_LIGHT, "Verde-água",
                    Color.parseColor("#E6F2F1"), Color.WHITE, Color.parseColor("#00796B"), false, false),
            new ThemeOption(THEME_CRIMSON_RED, "Vermelho Rubi",
                    Color.parseColor("#9C1F27"), Color.parseColor("#B52B34"), Color.parseColor("#FFDAD9"), false, false),
            new ThemeOption(THEME_FOREST_GREEN, "Verde Floresta",
                    Color.parseColor("#246B29"), Color.parseColor("#318237"), Color.parseColor("#C8E6C9"), false, false)
    };

    public final int themeId;
    public final boolean isDark;
    public final boolean keyBorders;
    public final boolean isGamerRgb;
    public final int keycapFontMode;

    public final int boardBg;
    public final int keyBg;
    public final int keyPressedBg;
    public final int keyText;
    public final int hintText;

    public final int funcKeyBg;
    public final int funcKeyPressedBg;
    public final int funcKeyActiveBg;
    public final int funcKeyText;
    public final int funcKeyActiveText;

    public final int enterBg;
    public final int enterPressedBg;
    public final int enterIcon;

    public final int toolbarIcon;
    public final int toolbarCircleBg;
    public final int dividerColor;

    public final int popupBg;
    public final int popupHighlightBg;
    public final int popupBorder;
    public final int popupText;

    private GboardTheme(Context context, int themeId, boolean keyBorders) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean validTheme = false;
        for (ThemeOption opt : ALL_THEMES) {
            if (opt.id == themeId) {
                validTheme = true;
                break;
            }
        }
        if (!validTheme) {
            themeId = THEME_DYNAMIC;
        }

        this.themeId = themeId;
        this.keyBorders = keyBorders;
        this.isGamerRgb = (themeId == THEME_PC_GAMER_RGB);
        this.keycapFontMode = isGamerRgb
                ? FontStyler.KEY_FONT_GAMER_MONO
                : prefs.getInt(KEY_KEYCAP_FONT, FontStyler.KEY_FONT_DEFAULT);

        int nightMask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean sysNight = (nightMask == Configuration.UI_MODE_NIGHT_YES);

        if (isGamerRgb) {
            this.isDark = true;
            boardBg = Color.parseColor("#05060A"); // Pure deep black mechanical chassis
            keyBg = Color.parseColor("#12151F");   // Dark matte PBT keycap surface
            keyPressedBg = Color.parseColor("#23293D");
            keyText = Color.parseColor("#F5F8FF");
            hintText = Color.parseColor("#90A4AE");
            funcKeyBg = Color.parseColor("#0D1018");
            funcKeyPressedBg = Color.parseColor("#20273B");
            funcKeyActiveBg = Color.parseColor("#00E5FF");
            funcKeyText = Color.parseColor("#00E5FF");
            funcKeyActiveText = Color.parseColor("#041016");
            enterBg = Color.parseColor("#101420");
            enterPressedBg = Color.parseColor("#242D45");
            enterIcon = Color.parseColor("#00E5FF");

            toolbarIcon = Color.parseColor("#00E5FF");
            toolbarCircleBg = Color.parseColor("#11141E");
            dividerColor = Color.parseColor("#00E5FF");
            popupBg = Color.parseColor("#0B0E17");
            popupHighlightBg = Color.parseColor("#00E5FF");
            popupBorder = Color.parseColor("#00E5FF");
            popupText = Color.WHITE;
            return;
        }

        switch (themeId) {
            case THEME_SYSTEM_AUTO:
                this.isDark = sysNight;
                break;
            case THEME_LIGHT_DEFAULT:
            case THEME_TEAL_LIGHT:
                this.isDark = false;
                break;
            case THEME_DARK_DEFAULT:
            case THEME_OLED_BLACK:
            case THEME_TEAL_DARK:
            case THEME_CRIMSON_RED:
            case THEME_FOREST_GREEN:
                this.isDark = true;
                break;
            case THEME_DYNAMIC:
            default:
                this.isDark = sysNight;
                break;
        }

        if (themeId == THEME_OLED_BLACK) {
            boardBg = Color.parseColor("#000000");
            keyBg = Color.parseColor("#1D1F24");
            keyPressedBg = Color.parseColor("#33363F");
            keyText = Color.parseColor("#ECEDF0");
            hintText = Color.parseColor("#9AA0A6");
            funcKeyBg = Color.parseColor("#282B33");
            funcKeyPressedBg = Color.parseColor("#3D424E");
            funcKeyActiveBg = Color.parseColor("#8AB4F8");
            funcKeyText = Color.parseColor("#ECEDF0");
            funcKeyActiveText = Color.parseColor("#062E6F");
            enterBg = Color.parseColor("#8AB4F8");
            enterPressedBg = Color.parseColor("#669DF6");
            enterIcon = Color.parseColor("#062E6F");
        } else if (themeId == THEME_TEAL_LIGHT) {
            boardBg = Color.parseColor("#E6F2F1");
            keyBg = Color.WHITE;
            keyPressedBg = Color.parseColor("#C7E5E2");
            keyText = Color.parseColor("#142625");
            hintText = Color.parseColor("#547270");
            funcKeyBg = Color.parseColor("#CBE4E1");
            funcKeyPressedBg = Color.parseColor("#B2D6D2");
            funcKeyActiveBg = Color.parseColor("#00796B");
            funcKeyText = Color.parseColor("#142625");
            funcKeyActiveText = Color.WHITE;
            enterBg = Color.parseColor("#00796B");
            enterPressedBg = Color.parseColor("#00695C");
            enterIcon = Color.WHITE;
        } else if (themeId == THEME_TEAL_DARK) {
            boardBg = Color.parseColor("#172426");
            keyBg = Color.parseColor("#26383B");
            keyPressedBg = Color.parseColor("#385054");
            keyText = Color.parseColor("#E0F2F1");
            hintText = Color.parseColor("#90B4B2");
            funcKeyBg = Color.parseColor("#31494D");
            funcKeyPressedBg = Color.parseColor("#446267");
            funcKeyActiveBg = Color.parseColor("#80CBC4");
            funcKeyText = Color.parseColor("#E0F2F1");
            funcKeyActiveText = Color.parseColor("#003731");
            enterBg = Color.parseColor("#80CBC4");
            enterPressedBg = Color.parseColor("#4DB6AC");
            enterIcon = Color.parseColor("#003731");
        } else if (themeId == THEME_CRIMSON_RED) {
            boardBg = Color.parseColor("#8E1C23");
            keyBg = Color.parseColor("#A92931");
            keyPressedBg = Color.parseColor("#C43841");
            keyText = Color.WHITE;
            hintText = Color.parseColor("#FFCDD2");
            funcKeyBg = Color.parseColor("#77151B");
            funcKeyPressedBg = Color.parseColor("#601015");
            funcKeyActiveBg = Color.parseColor("#FFDAD9");
            funcKeyText = Color.WHITE;
            funcKeyActiveText = Color.parseColor("#5F0A10");
            enterBg = Color.parseColor("#FFDAD9");
            enterPressedBg = Color.parseColor("#FFB4B2");
            enterIcon = Color.parseColor("#5F0A10");
        } else if (themeId == THEME_FOREST_GREEN) {
            boardBg = Color.parseColor("#1F5E24");
            keyBg = Color.parseColor("#2E7D32");
            keyPressedBg = Color.parseColor("#3D9442");
            keyText = Color.WHITE;
            hintText = Color.parseColor("#C8E6C9");
            funcKeyBg = Color.parseColor("#184A1C");
            funcKeyPressedBg = Color.parseColor("#123815");
            funcKeyActiveBg = Color.parseColor("#C8E6C9");
            funcKeyText = Color.WHITE;
            funcKeyActiveText = Color.parseColor("#0D3311");
            enterBg = Color.parseColor("#C8E6C9");
            enterPressedBg = Color.parseColor("#A5D6A7");
            enterIcon = Color.parseColor("#0D3311");
        } else if (themeId == THEME_LIGHT_DEFAULT || (themeId == THEME_SYSTEM_AUTO && !isDark)) {
            boardBg = Color.parseColor("#ECEFF4");
            keyBg = Color.WHITE;
            keyPressedBg = Color.parseColor("#D8E0EC");
            keyText = Color.parseColor("#1F1F1F");
            hintText = Color.parseColor("#5F6368");
            funcKeyBg = Color.parseColor("#D5DDE8");
            funcKeyPressedBg = Color.parseColor("#BCC7D6");
            funcKeyActiveBg = Color.parseColor("#C2E7FF");
            funcKeyText = Color.parseColor("#1F1F1F");
            funcKeyActiveText = Color.parseColor("#004A77");
            enterBg = Color.parseColor("#0B57D0");
            enterPressedBg = Color.parseColor("#0842A0");
            enterIcon = Color.WHITE;
        } else if (themeId == THEME_DARK_DEFAULT || (themeId == THEME_SYSTEM_AUTO && isDark)) {
            boardBg = Color.parseColor("#1B1C1F");
            keyBg = Color.parseColor("#2E3036");
            keyPressedBg = Color.parseColor("#44474F");
            keyText = Color.parseColor("#E3E3E8");
            hintText = Color.parseColor("#9AA0A6");
            funcKeyBg = Color.parseColor("#3A3E47");
            funcKeyPressedBg = Color.parseColor("#505561");
            funcKeyActiveBg = Color.parseColor("#A8C7FA");
            funcKeyText = Color.parseColor("#E3E3E8");
            funcKeyActiveText = Color.parseColor("#062E6F");
            enterBg = Color.parseColor("#A8C7FA");
            enterPressedBg = Color.parseColor("#8AB4F8");
            enterIcon = Color.parseColor("#062E6F");
        } else {
            if (isDark) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    boardBg = blend(
                            context.getColor(android.R.color.system_neutral1_900),
                            context.getColor(android.R.color.system_accent2_900),
                            0.25f
                    );
                    keyBg = blend(
                            context.getColor(android.R.color.system_neutral1_800),
                            context.getColor(android.R.color.system_neutral2_800),
                            0.3f
                    );
                    keyPressedBg = context.getColor(android.R.color.system_neutral2_700);
                    keyText = context.getColor(android.R.color.system_neutral1_100);
                    hintText = context.getColor(android.R.color.system_neutral2_300);
                    funcKeyBg = context.getColor(android.R.color.system_accent2_800);
                    funcKeyPressedBg = context.getColor(android.R.color.system_accent2_700);
                    funcKeyActiveBg = context.getColor(android.R.color.system_accent1_200);
                    funcKeyText = context.getColor(android.R.color.system_neutral1_100);
                    funcKeyActiveText = context.getColor(android.R.color.system_accent1_800);
                    enterBg = context.getColor(android.R.color.system_accent1_200);
                    enterPressedBg = context.getColor(android.R.color.system_accent1_300);
                    enterIcon = context.getColor(android.R.color.system_accent1_800);
                } else {
                    boardBg = Color.parseColor("#1B1C1F");
                    keyBg = Color.parseColor("#2E3036");
                    keyPressedBg = Color.parseColor("#44474F");
                    keyText = Color.parseColor("#E3E3E8");
                    hintText = Color.parseColor("#9AA0A6");
                    funcKeyBg = Color.parseColor("#383C48");
                    funcKeyPressedBg = Color.parseColor("#4D5262");
                    funcKeyActiveBg = Color.parseColor("#B4C5FF");
                    funcKeyText = Color.parseColor("#E3E3E8");
                    funcKeyActiveText = Color.parseColor("#0A2E6E");
                    enterBg = Color.parseColor("#B4C5FF");
                    enterPressedBg = Color.parseColor("#94AEFF");
                    enterIcon = Color.parseColor("#0A2E6E");
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    boardBg = blend(
                            context.getColor(android.R.color.system_neutral1_50),
                            context.getColor(android.R.color.system_accent2_50),
                            0.50f
                    );
                    keyBg = Color.WHITE;
                    keyPressedBg = context.getColor(android.R.color.system_accent2_100);
                    keyText = context.getColor(android.R.color.system_neutral1_900);
                    hintText = context.getColor(android.R.color.system_neutral2_600);
                    int dynamicTonal = blend(
                            context.getColor(android.R.color.system_accent1_100),
                            context.getColor(android.R.color.system_accent2_100),
                            0.30f
                    );
                    funcKeyBg = dynamicTonal;
                    funcKeyPressedBg = context.getColor(android.R.color.system_accent1_200);
                    funcKeyActiveBg = context.getColor(android.R.color.system_accent1_200);
                    funcKeyText = context.getColor(android.R.color.system_neutral1_900);
                    funcKeyActiveText = context.getColor(android.R.color.system_neutral1_900);
                    enterBg = dynamicTonal;
                    enterPressedBg = context.getColor(android.R.color.system_accent1_200);
                    enterIcon = context.getColor(android.R.color.system_neutral1_900);
                } else {
                    boardBg = Color.parseColor("#EEEDF4");
                    keyBg = Color.WHITE;
                    keyPressedBg = Color.parseColor("#DCE2F9");
                    keyText = Color.parseColor("#1A1C20");
                    hintText = Color.parseColor("#5F6368");
                    funcKeyBg = Color.parseColor("#DCE2F9");
                    funcKeyPressedBg = Color.parseColor("#C3CEF4");
                    funcKeyActiveBg = Color.parseColor("#C3CEF4");
                    funcKeyText = Color.parseColor("#1A1C20");
                    funcKeyActiveText = Color.parseColor("#1A1C20");
                    enterBg = Color.parseColor("#DCE2F9");
                    enterPressedBg = Color.parseColor("#C3CEF4");
                    enterIcon = Color.parseColor("#1A1C20");
                }
            }
        }

        toolbarIcon = keyText;
        toolbarCircleBg = keyBg;
        dividerColor = isDark ? Color.parseColor("#3E424B") : Color.parseColor("#CBCED8");
        popupBg = isDark ? Color.parseColor("#363942") : Color.WHITE;
        popupHighlightBg = funcKeyActiveBg;
        popupBorder = isDark ? Color.parseColor("#525763") : funcKeyBg;
        popupText = isDark ? Color.WHITE : Color.parseColor("#1A1C20");
    }

    public static GboardTheme fromContext(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int id = prefs.getInt(KEY_THEME_ID, THEME_DYNAMIC);
        boolean borders = prefs.getBoolean(KEY_KEY_BORDERS, true);
        return new GboardTheme(context, id, borders);
    }

    public static void saveSelectedTheme(Context context, int themeId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME_ID, themeId)
                .apply();
    }

    public static boolean isKeyBordersEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_KEY_BORDERS, true);
    }

    public static void saveKeyBorders(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_KEY_BORDERS, enabled)
                .apply();
    }

    public static boolean isAutoCorrectEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_CORRECT, true);
    }

    public static void setAutoCorrectEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_CORRECT, enabled)
                .apply();
    }

    public static boolean isStrictAntiErrorEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_STRICT_ANTI_ERROR, true);
    }

    public static void setStrictAntiErrorEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_STRICT_ANTI_ERROR, enabled)
                .apply();
    }

    public static int getKeycapFontMode(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_KEYCAP_FONT, FontStyler.KEY_FONT_DEFAULT);
    }

    public static void setKeycapFontMode(Context context, int mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_KEYCAP_FONT, mode)
                .apply();
    }

    public static int getTypingFontStyle(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_TYPING_FONT, FontStyler.FONT_NORMAL);
    }

    public static void setTypingFontStyle(Context context, int style) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_TYPING_FONT, style)
                .apply();
    }

    public static boolean isShowSuggestionsEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SHOW_SUGGESTIONS, true);
    }

    public static void setShowSuggestionsEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOW_SUGGESTIONS, enabled)
                .apply();
    }

    public static boolean isAutoCapEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_CAP, true);
    }

    public static void setAutoCapEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_CAP, enabled)
                .apply();
    }

    public static boolean isDoubleSpaceDotEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_DOUBLE_SPACE_DOT, true);
    }

    public static void setDoubleSpaceDotEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DOUBLE_SPACE_DOT, enabled)
                .apply();
    }

    public static boolean isNumberRowEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NUMBER_ROW, false);
    }

    public static void setNumberRowEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NUMBER_ROW, enabled)
                .apply();
    }

    public static int getHeightMode(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_HEIGHT_MODE, 1);
    }

    public static void setHeightMode(Context context, int mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_HEIGHT_MODE, mode)
                .apply();
    }

    public static float getHeightMultiplier(Context context) {
        int m = getHeightMode(context);
        if (m == 0) return 0.88f;
        if (m == 2) return 1.13f;
        return 1.0f;
    }

    public static boolean isHapticEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_HAPTIC, true);
    }

    public static void setHapticEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_HAPTIC, enabled)
                .apply();
    }

    public static boolean isSoundEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SOUND, true);
    }

    public static void setSoundEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SOUND, enabled)
                .apply();
    }

    public static boolean isPopupEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_POPUP, true);
    }

    public static void setPopupEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_POPUP, enabled)
                .apply();
    }

    private static int blend(int c1, int c2, float ratio) {
        float inv = 1f - ratio;
        int r = Math.round(Color.red(c1) * inv + Color.red(c2) * ratio);
        int g = Math.round(Color.green(c1) * inv + Color.green(c2) * ratio);
        int b = Math.round(Color.blue(c1) * inv + Color.blue(c2) * ratio);
        return Color.rgb(r, g, b);
    }
}
