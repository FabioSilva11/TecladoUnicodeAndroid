package com.luisunicode.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ClipboardStore {

    private static final String PREFS_NAME = "gboard_unicode_prefs";
    private static final String KEY_CLIP_ITEMS = "clipboard_items_v2";
    private static final String KEY_CLIP_ENABLED = "clipboard_enabled_v2";
    private static final int MAX_UNLOCKED_ITEMS = 16;

    public static final class ClipEntry {
        public final String text;
        public boolean locked;

        public ClipEntry(String text, boolean locked) {
            this.text = text;
            this.locked = locked;
        }
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_CLIP_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CLIP_ENABLED, enabled)
                .apply();
    }

    public static List<ClipEntry> loadEntries(Context context) {
        List<ClipEntry> result = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CLIP_ITEMS, "");
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String t = obj.optString("t", "");
                boolean locked = obj.optBoolean("l", false);
                if (!t.isEmpty()) {
                    result.add(new ClipEntry(t, locked));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void saveEntries(Context context, List<ClipEntry> entries) {
        try {
            List<ClipEntry> ordered = new ArrayList<>();
            for (ClipEntry e : entries) {
                if (e.locked) ordered.add(e);
            }
            int unlockedCount = 0;
            for (ClipEntry e : entries) {
                if (!e.locked && unlockedCount < MAX_UNLOCKED_ITEMS) {
                    ordered.add(e);
                    unlockedCount++;
                }
            }

            JSONArray arr = new JSONArray();
            for (ClipEntry e : ordered) {
                JSONObject obj = new JSONObject();
                obj.put("t", e.text);
                obj.put("l", e.locked);
                arr.put(obj);
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CLIP_ITEMS, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static void addCopiedText(Context context, String rawText) {
        if (!isEnabled(context) || rawText == null) return;
        String text = rawText.trim();
        if (text.isEmpty()) return;

        List<ClipEntry> list = loadEntries(context);
        for (int i = 0; i < list.size(); i++) {
            ClipEntry existing = list.get(i);
            if (existing.text.equals(text)) {
                if (existing.locked) {
                    return;
                }
                list.remove(i);
                break;
            }
        }
        int insertPos = 0;
        while (insertPos < list.size() && list.get(insertPos).locked) {
            insertPos++;
        }
        list.add(insertPos, new ClipEntry(text, false));
        saveEntries(context, list);
    }

    public static void toggleLock(Context context, String text) {
        if (text == null) return;
        List<ClipEntry> list = loadEntries(context);
        for (ClipEntry e : list) {
            if (e.text.equals(text)) {
                e.locked = !e.locked;
                break;
            }
        }
        saveEntries(context, list);
    }

    /**
     * Deletes an entry ONLY if it is NOT locked by padlock.
     */
    public static boolean deleteEntry(Context context, String text) {
        if (text == null) return false;
        List<ClipEntry> list = loadEntries(context);
        for (int i = 0; i < list.size(); i++) {
            ClipEntry e = list.get(i);
            if (e.text.equals(text)) {
                if (e.locked) {
                    return false;
                }
                list.remove(i);
                saveEntries(context, list);
                return true;
            }
        }
        return false;
    }

    /**
     * Deletes all unlocked entries while keeping locked entries safe.
     */
    public static int clearUnlocked(Context context) {
        List<ClipEntry> list = loadEntries(context);
        List<ClipEntry> kept = new ArrayList<>();
        int removed = 0;
        for (ClipEntry e : list) {
            if (e.locked) {
                kept.add(e);
            } else {
                removed++;
            }
        }
        saveEntries(context, kept);
        return removed;
    }
}
