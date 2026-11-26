package com.luisunicode.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class EmojiRepository {

    public static final int CAT_RECENT = -1;
    public static final int CAT_COUNT = 9;

    public static final String[] CATEGORY_NAMES = {
            "Carinhas e emoções",
            "Pessoas e corpo",
            "Animais e natureza",
            "Comida e bebida",
            "Viagens e lugares",
            "Atividades e esportes",
            "Objetos",
            "Símbolos",
            "Bandeiras"
    };

    public static final String[] CATEGORY_ICONS = {
            "😀", "👋", "🐾", "🍔", "🚗", "⚽", "💡", "❤️", "🇧🇷"
    };

    private static final String PREFS_NAME = "gboard_unicode_prefs";
    private static final String KEY_RECENT_EMOJIS = "recent_emojis_v1";

    public static final class EmojiItem {
        public final int category;
        public final String emoji;
        public final String searchNormalized;

        EmojiItem(int category, String emoji, String searchNormalized) {
            this.category = category;
            this.emoji = emoji;
            this.searchNormalized = searchNormalized;
        }
    }

    private static volatile List<EmojiItem> allItems = Collections.emptyList();
    private static volatile List<String>[] byCategory = buildSeedCategories();
    private static volatile boolean loadingStarted = false;

    @SuppressWarnings("unchecked")
    private static List<String>[] buildSeedCategories() {
        List<String>[] seed = new List[CAT_COUNT];
        seed[0] = Arrays.asList("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🥰", "😍", "🤩", "😘", "😎", "🤔", "😢", "😭", "😡", "🥳");
        seed[1] = Arrays.asList("👋", "🤚", "✋", "👌", "✌️", "🤞", "👍", "👎", "👏", "🙌", "🤝", "🙏", "💪", "🫶", "👀", "🧠");
        seed[2] = Arrays.asList("🐶", "🐱", "🐭", "🐰", "🦊", "🐻", "🐼", "🦁", "🐮", "🐷", "🐸", "🐵", "🦋", "🌸", "🌹", "🌻", "🌲", "☀️", "🌙", "⭐", "🔥");
        seed[3] = Arrays.asList("🍎", "🍌", "🍉", "🍓", "🍕", "🍔", "🍟", "🌭", "🍿", "🍰", "🎂", "🍫", "☕", "🍺", "🥤");
        seed[4] = Arrays.asList("🚗", "🚕", "🚌", "🏎️", "🚓", "🚑", "🚲", "✈️", "🚀", "🏠", "🏢", "🏖️", "🗺️");
        seed[5] = Arrays.asList("⚽", "🏀", "🏐", "🎾", "🎱", "🏆", "🥇", "🎮", "🎲", "🎯", "🎸", "🎧");
        seed[6] = Arrays.asList("📱", "💻", "⌚", "📷", "💡", "🔦", "💰", "💎", "🔧", "🔑", "🔒", "🎁", "📌", "✏️");
        seed[7] = Arrays.asList("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "💯", "✨", "✅", "❌", "⚠️", "➕", "➖", "➡️", "⬅️");
        seed[8] = Arrays.asList("🇧🇷", "🇵🇹", "🇺🇸", "🇪🇸", "🇫🇷", "🇮🇹", "🇩🇪", "🇬🇧", "🇯🇵", "🇦🇷", "🏳️", "🏴");
        return seed;
    }

    public static void ensureLoaded(Context context) {
        if (loadingStarted || context == null) {
            return;
        }
        loadingStarted = true;
        final Context appCtx = context.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                @SuppressWarnings("unchecked")
                List<String>[] cats = new List[CAT_COUNT];
                for (int i = 0; i < CAT_COUNT; i++) {
                    cats[i] = new ArrayList<>(220);
                }
                List<EmojiItem> items = new ArrayList<>(1920);

                try (InputStream is = appCtx.getAssets().open("emojis_pt.txt");
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        int t1 = line.indexOf('\t');
                        if (t1 <= 0) continue;
                        int t2 = line.indexOf('\t', t1 + 1);
                        int cat = Integer.parseInt(line.substring(0, t1));
                        String em = (t2 > t1) ? line.substring(t1 + 1, t2) : line.substring(t1 + 1);
                        String kw = (t2 > t1 && t2 + 1 < line.length()) ? line.substring(t2 + 1) : "";
                        if (cat >= 0 && cat < CAT_COUNT && !em.isEmpty()) {
                            cats[cat].add(em);
                            String normKw = PortugueseSuggestions.stripAccents(kw).toLowerCase(Locale.ROOT);
                            items.add(new EmojiItem(cat, em, normKw));
                        }
                    }
                }
                if (!items.isEmpty()) {
                    byCategory = cats;
                    allItems = items;
                }
            } catch (Exception ignored) {
            }
        }, "EmojiAssetLoader");
        t.setDaemon(true);
        t.start();
    }

    public static List<String> getCategoryEmojis(Context context, int category) {
        if (category == CAT_RECENT) {
            List<String> rec = getRecentEmojis(context);
            if (!rec.isEmpty()) {
                return rec;
            }
            return byCategory[0];
        }
        if (category >= 0 && category < CAT_COUNT) {
            return byCategory[category];
        }
        return byCategory[0];
    }

    public static List<String> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return byCategory[0];
        }
        String q = PortugueseSuggestions.stripAccents(query.trim()).toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>(80);
        for (EmojiItem item : allItems) {
            if (item.searchNormalized.contains(q)) {
                results.add(item.emoji);
                if (results.size() >= 120) {
                    break;
                }
            }
        }
        return results;
    }

    public static List<String> getRecentEmojis(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_RECENT_EMOJIS, "");
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        String[] split = raw.split("\t");
        List<String> list = new ArrayList<>(split.length);
        for (String s : split) {
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    public static void recordRecentEmoji(Context context, String emoji) {
        if (emoji == null || emoji.isEmpty()) return;
        List<String> list = new ArrayList<>(getRecentEmojis(context));
        list.remove(emoji);
        list.add(0, emoji);
        while (list.size() > 36) {
            list.remove(list.size() - 1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('\t');
            sb.append(list.get(i));
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECENT_EMOJIS, sb.toString())
                .apply();
    }
}
