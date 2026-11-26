package com.luisunicode.keyboard;

import android.graphics.Typeface;

public final class FontStyler {

    public static final int FONT_NORMAL = 0;
    public static final int FONT_BOLD = 1;
    public static final int FONT_ITALIC = 2;
    public static final int FONT_BOLD_ITALIC = 3;
    public static final int FONT_MONOSPACE = 4;
    public static final int FONT_DOUBLE_STRUCK = 5;
    public static final int FONT_SCRIPT = 6;
    public static final int FONT_SMALL_CAPS = 7;

    public static final String[] FONT_NAMES = {
            "Normal (Padrão)",
            "𝐍𝐞𝐠𝐫𝐢𝐭𝐨",
            "𝐼𝑡𝑎́𝑙𝑖𝑐𝑜",
            "𝑵𝒆𝒈𝒓𝒊𝒕𝒐 𝑰𝒕𝒂́𝒍𝒊𝒄𝒐",
            "𝙼𝚘𝚗𝚘 / 𝙶𝚊𝚖𝚎𝚛",
            "𝔻𝕦𝕡𝕝𝕠 (𝔹𝕝𝕒𝕔𝕜𝕓𝕠𝕒𝕣𝕕)",
            "𝒞𝓊𝓇𝓈𝒾𝓋𝒶 (𝒮𝒸𝓇𝒾𝓅𝓉)",
            "ꜱᴍᴀʟʟ ᴄᴀᴘꜱ"
    };

    public static final int KEY_FONT_DEFAULT = 0;
    public static final int KEY_FONT_GAMER_MONO = 1;
    public static final int KEY_FONT_SERIF = 2;
    public static final int KEY_FONT_CONDENSED = 3;

    public static final String[] KEY_FONT_NAMES = {
            "Padrão Google Sans",
            "Gamer Tech (Mono)",
            "Clássica (Serif)",
            "Compacta (Condensed)"
    };

    private static final String SMALL_CAPS_MAP = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    public static Typeface resolveKeycapTypeface(int keyFontMode, boolean medium) {
        switch (keyFontMode) {
            case KEY_FONT_GAMER_MONO:
                return Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
            case KEY_FONT_SERIF:
                return Typeface.create(Typeface.SERIF, medium ? Typeface.BOLD : Typeface.NORMAL);
            case KEY_FONT_CONDENSED:
                return Typeface.create("sans-serif-condensed", medium ? Typeface.BOLD : Typeface.NORMAL);
            case KEY_FONT_DEFAULT:
            default:
                return Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL);
        }
    }

    /**
     * Transforms plain text into the selected Unicode styled font.
     * Accented characters decompose gracefully (base styled + combining mark) or preserve readability.
     */
    public static String transform(String input, int fontStyle) {
        if (input == null || input.isEmpty() || fontStyle == FONT_NORMAL) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            String mapped = mapChar(c, fontStyle);
            out.append(mapped);
        }
        return out.toString();
    }

    private static String mapChar(char c, int style) {
        if (c >= 'A' && c <= 'Z') {
            int offset = c - 'A';
            switch (style) {
                case FONT_BOLD:
                    return new String(Character.toChars(0x1D400 + offset));
                case FONT_ITALIC:
                    return new String(Character.toChars(0x1D434 + offset));
                case FONT_BOLD_ITALIC:
                    return new String(Character.toChars(0x1D468 + offset));
                case FONT_MONOSPACE:
                    return new String(Character.toChars(0x1D670 + offset));
                case FONT_DOUBLE_STRUCK:
                    if (c == 'C') return "ℂ";
                    if (c == 'H') return "ℍ";
                    if (c == 'N') return "ℕ";
                    if (c == 'P') return "ℙ";
                    if (c == 'Q') return "ℚ";
                    if (c == 'R') return "ℝ";
                    if (c == 'Z') return "ℤ";
                    return new String(Character.toChars(0x1D538 + offset));
                case FONT_SCRIPT:
                    if (c == 'B') return "ℬ";
                    if (c == 'E') return "ℰ";
                    if (c == 'F') return "ℱ";
                    if (c == 'H') return "ℋ";
                    if (c == 'I') return "ℐ";
                    if (c == 'L') return "ℒ";
                    if (c == 'M') return "ℳ";
                    if (c == 'R') return "ℛ";
                    return new String(Character.toChars(0x1D49C + offset));
                case FONT_SMALL_CAPS:
                    return String.valueOf(c);
            }
        } else if (c >= 'a' && c <= 'z') {
            int offset = c - 'a';
            switch (style) {
                case FONT_BOLD:
                    return new String(Character.toChars(0x1D41A + offset));
                case FONT_ITALIC:
                    if (c == 'h') return "ℎ";
                    return new String(Character.toChars(0x1D44E + offset));
                case FONT_BOLD_ITALIC:
                    return new String(Character.toChars(0x1D482 + offset));
                case FONT_MONOSPACE:
                    return new String(Character.toChars(0x1D68A + offset));
                case FONT_DOUBLE_STRUCK:
                    return new String(Character.toChars(0x1D552 + offset));
                case FONT_SCRIPT:
                    if (c == 'e') return "ℯ";
                    if (c == 'g') return "ℊ";
                    if (c == 'o') return "ℴ";
                    return new String(Character.toChars(0x1D4B6 + offset));
                case FONT_SMALL_CAPS:
                    return String.valueOf(SMALL_CAPS_MAP.charAt(offset));
            }
        } else if (c >= '0' && c <= '9') {
            int offset = c - '0';
            switch (style) {
                case FONT_BOLD:
                case FONT_BOLD_ITALIC:
                    return new String(Character.toChars(0x1D7CE + offset));
                case FONT_MONOSPACE:
                    return new String(Character.toChars(0x1D7F6 + offset));
                case FONT_DOUBLE_STRUCK:
                    return new String(Character.toChars(0x1D7D8 + offset));
            }
        }
        return String.valueOf(c);
    }
}
