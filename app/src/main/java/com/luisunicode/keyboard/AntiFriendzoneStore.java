package com.luisunicode.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AntiFriendzoneStore {

    private static final String PREFS_NAME = "gboard_unicode_prefs";
    private static final String KEY_CUSTOM_AFZ = "afz_custom_msgs_v1";

    public static final String[] CATEGORIES = {
            "🔥 Gelo",
            "😏 Flertar",
            "📅 Sair",
            "💬 Assunto",
            "⭐ Minhas"
    };

    private static final String[][] PRESETS = {
            // 0: Quebrar o gelo
            {
                    "Tava tentando pensar numa desculpa boa pra falar com você, mas resolvi ser direto: oi 😏",
                    "Me tira uma dúvida rápida: você é sempre interessante assim ou hoje tá se superando?",
                    "Passei só pra avisar que lembrei de você… o motivo eu conto pessoalmente 👀",
                    "De 0 a 10, qual a chance da gente se dar bem demais e virar assunto?",
                    "Prometo não tomar muito seu tempo hoje… só o suficiente pra você querer conversar mais 😉"
            },
            // 1: Flertar (Anti-Friendzone direto)
            {
                    "Amizade? Não me ofende assim, minhas intenções com você são bem melhores 😉",
                    "Confessa vai: você deu um sorrisinho quando viu minha mensagem aparecer aí.",
                    "A gente tem química demais pra ficar só no 'oi, tudo bem'.",
                    "Se continuar me tratando bem assim vou achar que tá querendo me conquistar.",
                    "Olha que eu não sou tão fácil assim… mas pra você eu abro uma exceção fácil 😌",
                    "Tô começando a achar que você faz charme de propósito só pra me provocar."
            },
            // 2: Chamar pra sair
            {
                    "Sexta ou sábado? Escolhe o dia que o lugar bom já tá garantido.",
                    "Chega de conversar só por tela, quando você tá livre pra gente se ver de perto?",
                    "Bora resolver isso pessoalmente tomando um café ou açaí essa semana?",
                    "Topa sair pra comer algo gostoso hoje à noite? Prometo ótima companhia.",
                    "Me dá uma carona na sua agenda essa semana que eu cuido do resto 😉"
            },
            // 3: Puxar assunto interessante
            {
                    "Me conta uma coisa sobre você que quase ninguém imagina de primeira.",
                    "Qual foi o ponto alto do seu dia hoje até agora?",
                    "Se a gente pudesse viajar agora sem planejar nada, qual seria o destino?",
                    "Qual música não sai da sua cabeça essa semana?",
                    "Time filme em casa com chuva ou sair pra algum lugar novo à noite?"
            }
    };

    public static final class QuickMsg {
        public final String text;
        public boolean locked;
        public final boolean isCustom;

        public QuickMsg(String text, boolean locked, boolean isCustom) {
            this.text = text;
            this.locked = locked;
            this.isCustom = isCustom;
        }
    }

    public static List<QuickMsg> getMessagesForCategory(Context context, int catIndex) {
        if (catIndex == 4) {
            return loadCustomMessages(context);
        }
        int safeIdx = Math.max(0, Math.min(PRESETS.length - 1, catIndex));
        List<QuickMsg> list = new ArrayList<>(PRESETS[safeIdx].length);
        for (String s : PRESETS[safeIdx]) {
            list.add(new QuickMsg(s, true, false));
        }
        return list;
    }

    public static List<QuickMsg> loadCustomMessages(Context context) {
        List<QuickMsg> out = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_CUSTOM_AFZ, null);
        if (raw == null) {
            // Seed 2 unlocked editable examples on first run
            out.add(new QuickMsg("Passando pra desejar uma noite incrível pra minha pessoa favorita da semana ✨", true, true));
            out.add(new QuickMsg("Me avisa quando chegar em casa pra eu ficar tranquilo(a) 😉", false, true));
            saveCustomMessages(context, out);
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String t = o.optString("t", "");
                boolean l = o.optBoolean("l", false);
                if (!t.isEmpty()) out.add(new QuickMsg(t, l, true));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void saveCustomMessages(Context context, List<QuickMsg> list) {
        try {
            JSONArray arr = new JSONArray();
            for (QuickMsg m : list) {
                JSONObject o = new JSONObject();
                o.put("t", m.text);
                o.put("l", m.locked);
                arr.put(o);
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CUSTOM_AFZ, arr.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    public static void addCustomMessage(Context context, String rawText) {
        if (rawText == null) return;
        String t = rawText.trim();
        if (t.isEmpty()) return;
        List<QuickMsg> list = loadCustomMessages(context);
        for (QuickMsg existing : list) {
            if (existing.text.equals(t)) return;
        }
        list.add(0, new QuickMsg(t, false, true));
        saveCustomMessages(context, list);
    }

    public static void toggleCustomLock(Context context, String text) {
        List<QuickMsg> list = loadCustomMessages(context);
        for (QuickMsg m : list) {
            if (m.text.equals(text)) {
                m.locked = !m.locked;
                break;
            }
        }
        saveCustomMessages(context, list);
    }

    public static boolean deleteCustomMessage(Context context, String text) {
        List<QuickMsg> list = loadCustomMessages(context);
        for (int i = 0; i < list.size(); i++) {
            QuickMsg m = list.get(i);
            if (m.text.equals(text)) {
                if (m.locked) return false;
                list.remove(i);
                saveCustomMessages(context, list);
                return true;
            }
        }
        return false;
    }
}
