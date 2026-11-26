package com.luisunicode.keyboard;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FreeTranslator {

    public static final class Lang {
        public final String code;
        public final String name;

        public Lang(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    public static final Lang[] LANGUAGES = {
            new Lang("pt", "Português"),
            new Lang("en", "Inglês"),
            new Lang("es", "Espanhol"),
            new Lang("fr", "Francês"),
            new Lang("it", "Italiano"),
            new Lang("de", "Alemão"),
            new Lang("ja", "Japonês")
    };

    public interface Callback {
        void onResult(String sourceText, String translatedText, boolean success);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile int requestSeq = 0;

    public static void translateAsync(String text, String sourceLang, String targetLang, Callback callback) {
        if (text == null || text.trim().isEmpty()) {
            if (callback != null) {
                callback.onResult("", "", true);
            }
            return;
        }
        final int seq = ++requestSeq;
        final String cleanText = text.trim();

        EXECUTOR.execute(() -> {
            if (seq != requestSeq) return;

            String result = tryGoogleGtx(cleanText, sourceLang, targetLang);
            if (result == null) {
                result = tryMyMemory(cleanText, sourceLang, targetLang);
            }

            final String finalRes = result;
            MAIN_HANDLER.post(() -> {
                if (seq == requestSeq && callback != null) {
                    if (finalRes != null && !finalRes.isEmpty()) {
                        callback.onResult(cleanText, finalRes, true);
                    } else {
                        callback.onResult(cleanText, cleanText, false);
                    }
                }
            });
        });
    }

    private static String tryGoogleGtx(String text, String sl, String tl) {
        HttpURLConnection conn = null;
        try {
            String q = URLEncoder.encode(text, "UTF-8");
            String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
                    + sl + "&tl=" + tl + "&dt=t&q=" + q;
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() != 200) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONArray outer = new JSONArray(sb.toString());
            JSONArray sentences = outer.getJSONArray(0);
            StringBuilder translated = new StringBuilder();
            for (int i = 0; i < sentences.length(); i++) {
                JSONArray seg = sentences.getJSONArray(i);
                if (seg.length() > 0 && !seg.isNull(0)) {
                    translated.append(seg.getString(0));
                }
            }
            String out = translated.toString().trim();
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String tryMyMemory(String text, String sl, String tl) {
        HttpURLConnection conn = null;
        try {
            String q = URLEncoder.encode(text, "UTF-8");
            String urlStr = "https://api.mymemory.translated.net/get?q=" + q + "&langpair=" + sl + "|" + tl;
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);

            if (conn.getResponseCode() != 200) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject obj = new JSONObject(sb.toString());
            JSONObject data = obj.optJSONObject("responseData");
            if (data != null) {
                String t = data.optString("translatedText", "").trim();
                return t.isEmpty() ? null : t;
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
