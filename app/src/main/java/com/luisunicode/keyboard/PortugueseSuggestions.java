package com.luisunicode.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PortugueseSuggestions {

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final int MAX_TYPO_SEARCH_RANK = 20000;
    private static final int MAX_STRICT_DIST2_RANK = 10000;
    private static final int MAX_WORD_LEN_BUCKET = 25;

    private static final String PREFS_NAME = "gboard_unicode_prefs";
    private static final String KEY_PERSONAL_WORDS = "personal_dict_words_v1";

    private static final Set<String> AMBIGUOUS_KEEP_UNACCENTED = new HashSet<>(Arrays.asList(
            "e", "a", "o", "as", "os", "de", "da", "do", "das", "dos",
            "em", "no", "na", "nos", "nas", "por", "para", "pelo", "pela",
            "pelos", "pelas", "esta", "este", "estas", "estes", "esse", "essa",
            "esses", "essas", "como", "mais", "mas", "tem", "vem", "pode",
            "podem", "sabia", "pais", "que", "se", "ou", "ao", "aos", "um",
            "uma", "vou", "vai", "foi", "era", "ver", "vir", "ter", "ser", "ir"
    ));

    private static final Map<String, String> COMMON_TYPOS = new HashMap<>();

    static {
        COMMON_TYPOS.put("refrerencia", "referência");
        COMMON_TYPOS.put("referencia", "referência");
        COMMON_TYPOS.put("trasferencia", "transferência");
        COMMON_TYPOS.put("transferencia", "transferência");
        COMMON_TYPOS.put("inpessa", "impeça");
        COMMON_TYPOS.put("inpedir", "impedir");
        COMMON_TYPOS.put("inportante", "importante");
        COMMON_TYPOS.put("tanbem", "também");
        COMMON_TYPOS.put("tabem", "também");
        COMMON_TYPOS.put("tbm", "também");
        COMMON_TYPOS.put("tb", "também");
        COMMON_TYPOS.put("vc", "você");
        COMMON_TYPOS.put("vcs", "vocês");
        COMMON_TYPOS.put("pq", "porque");
        COMMON_TYPOS.put("corecao", "correção");
        COMMON_TYPOS.put("disponovel", "disponível");
        COMMON_TYPOS.put("instalr", "instalar");
        COMMON_TYPOS.put("eses", "esses");
        COMMON_TYPOS.put("esas", "essas");
        COMMON_TYPOS.put("asim", "assim");
        COMMON_TYPOS.put("mensages", "mensagens");
        COMMON_TYPOS.put("emoge", "emoji");
        COMMON_TYPOS.put("emogis", "emojis");
        COMMON_TYPOS.put("qero", "quero");
        COMMON_TYPOS.put("obg", "obrigado");
        COMMON_TYPOS.put("ctz", "certeza");
        COMMON_TYPOS.put("msg", "mensagem");
        COMMON_TYPOS.put("hj", "hoje");
        COMMON_TYPOS.put("agr", "agora");
        COMMON_TYPOS.put("blz", "beleza");
        COMMON_TYPOS.put("vlw", "valeu");
    }

    private static volatile DictData dictData = buildSeedDict();
    private static volatile Set<String> personalWords = new HashSet<>();
    private static volatile boolean loadingStarted = false;

    private static final class DictData {
        final String[] words;
        final String[] norms;
        final Map<String, Integer> exactToRank;
        final Map<String, Integer> normToBestRank;
        final int[][] bucketsByLen;

        DictData(List<String> rawWords) {
            int n = rawWords.size();
            words = new String[n];
            norms = new String[n];
            exactToRank = new HashMap<>(n * 4 / 3 + 16);
            normToBestRank = new HashMap<>(n * 4 / 3 + 16);

            @SuppressWarnings("unchecked")
            List<Integer>[] tempBuckets = new List[MAX_WORD_LEN_BUCKET + 1];
            for (int i = 0; i <= MAX_WORD_LEN_BUCKET; i++) {
                tempBuckets[i] = new ArrayList<>();
            }

            for (int i = 0; i < n; i++) {
                String w = rawWords.get(i);
                String norm = stripAccents(w).toLowerCase(Locale.ROOT);
                words[i] = w;
                norms[i] = norm;

                if (!exactToRank.containsKey(w)) {
                    exactToRank.put(w, i);
                }
                if (!normToBestRank.containsKey(norm)) {
                    normToBestRank.put(norm, i);
                }
                int len = norm.length();
                if (i < MAX_TYPO_SEARCH_RANK && len >= 2 && len <= MAX_WORD_LEN_BUCKET) {
                    tempBuckets[len].add(i);
                }
            }

            bucketsByLen = new int[MAX_WORD_LEN_BUCKET + 1][];
            for (int len = 0; len <= MAX_WORD_LEN_BUCKET; len++) {
                List<Integer> list = tempBuckets[len];
                int[] arr = new int[list.size()];
                for (int k = 0; k < list.size(); k++) {
                    arr[k] = list.get(k);
                }
                bucketsByLen[len] = arr;
            }
        }
    }

    private static DictData buildSeedDict() {
        List<String> seed = Arrays.asList(
                "é", "à", "que", "não", "de", "você", "vocês", "está", "estão", "já", "só",
                "até", "também", "então", "são", "muito", "mais", "mas", "como", "para",
                "obrigado", "obrigada", "olá", "amanhã", "alguém", "ninguém", "porém", "além",
                "atualização", "correção", "referência", "transferência", "opção", "opções",
                "disponível", "possível", "informação", "configuração", "configurações",
                "número", "código", "português", "coração", "impedir", "impeça", "assim",
                "esses", "essas", "instalar", "teclado", "palavra", "palavras", "acento",
                "mensagem", "mensagens", "emoji", "emojis"
        );
        return new DictData(seed);
    }

    public static void ensureLoaded(Context context) {
        if (context != null) {
            loadPersonalWords(context);
        }
        if (loadingStarted || context == null) {
            return;
        }
        loadingStarted = true;
        final Context appCtx = context.getApplicationContext();
        Thread loader = new Thread(() -> {
            try {
                List<String> loaded = new ArrayList<>(28500);
                try (InputStream is = appCtx.getAssets().open("pt_br_words.txt");
                     BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String w = line.trim();
                        if (!w.isEmpty()) {
                            loaded.add(w);
                        }
                    }
                }
                // Ensure essential technical/everyday words are always present
                for (String extra : new String[]{"referência", "transferência", "impeça", "emoji", "emojis"}) {
                    if (!loaded.contains(extra)) {
                        loaded.add(Math.min(1500, loaded.size()), extra);
                    }
                }
                if (!loaded.isEmpty()) {
                    dictData = new DictData(loaded);
                }
            } catch (Exception ignored) {
            }
        }, "PtBrDictLoader");
        loader.setDaemon(true);
        loader.start();
    }

    public static List<String> getPersonalWords(Context context) {
        loadPersonalWords(context);
        List<String> out = new ArrayList<>(personalWords);
        java.util.Collections.sort(out);
        return out;
    }

    public static void addPersonalWord(Context context, String word) {
        if (word == null) return;
        String clean = word.trim();
        if (clean.isEmpty()) return;
        loadPersonalWords(context);
        personalWords.add(clean);
        savePersonalWords(context);
    }

    public static void removePersonalWord(Context context, String word) {
        if (word == null) return;
        loadPersonalWords(context);
        personalWords.remove(word);
        savePersonalWords(context);
    }

    private static void loadPersonalWords(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_PERSONAL_WORDS, "");
        Set<String> set = new HashSet<>();
        if (raw != null && !raw.isEmpty()) {
            for (String p : raw.split("\t")) {
                if (!p.trim().isEmpty()) set.add(p.trim());
            }
        }
        personalWords = set;
    }

    private static void savePersonalWords(Context context) {
        StringBuilder sb = new StringBuilder();
        for (String w : personalWords) {
            if (sb.length() > 0) sb.append('\t');
            sb.append(w);
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PERSONAL_WORDS, sb.toString())
                .apply();
    }

    public static String stripAccents(String input) {
        if (input == null) return "";
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Applies Portuguese phonetic pre-fixes (e.g. "np" -> "mp", "nb" -> "mb") before dictionary lookup.
     */
    private static String applyPhoneticFixes(String norm) {
        String s = norm;
        if (s.contains("np")) s = s.replace("np", "mp");
        if (s.contains("nb")) s = s.replace("nb", "mb");
        if (s.endsWith("ns") && s.length() > 4) {
            // keep valid plurals like "mensagens"
        } else if (s.endsWith("ges") && s.length() >= 6) {
            s = s.substring(0, s.length() - 3) + "gens";
        }
        return s;
    }

    public static boolean isKnownValidWord(String word) {
        if (word == null || word.isEmpty()) return true;
        String lower = word.toLowerCase(PT_BR);
        for (String pw : personalWords) {
            if (pw.equalsIgnoreCase(word)) return true;
        }
        return dictData.exactToRank.containsKey(lower);
    }

    /**
     * Returns automatic replacement when Space / punctuation is pressed (or dynamically in strict Anti-Error mode).
     */
    public static String getAutoCorrection(String typedWord, boolean autoCorrectEnabled, boolean strictAntiErrorMode) {
        if (!autoCorrectEnabled || typedWord == null || typedWord.length() < 2) {
            return null;
        }
        for (String pw : personalWords) {
            if (pw.equalsIgnoreCase(typedWord)) {
                return pw.equals(typedWord) ? null : pw;
            }
        }

        DictData data = dictData;
        String lower = typedWord.toLowerCase(PT_BR);
        String norm = stripAccents(lower);

        // 1. Explicit high-frequency typo table
        String explicit = COMMON_TYPOS.get(norm);
        if (explicit != null) {
            return matchCase(typedWord, explicit);
        }

        // 2. Check if already exact valid dictionary word
        Integer exactRank = data.exactToRank.get(lower);
        if (exactRank != null) {
            Integer bestNormRank = data.normToBestRank.get(norm);
            if (bestNormRank != null && bestNormRank < exactRank && !AMBIGUOUS_KEEP_UNACCENTED.contains(lower)) {
                String bestAccented = data.words[bestNormRank];
                if (!bestAccented.equals(lower)) {
                    return matchCase(typedWord, bestAccented);
                }
            }
            return null;
        }

        // 3. Phonetic pre-normalization (e.g. "inportante" -> "importante", "mensages" -> "mensagens")
        String phonetic = applyPhoneticFixes(norm);
        Integer phoneticRank = data.normToBestRank.get(phonetic);
        if (phoneticRank != null) {
            String bestWord = data.words[phoneticRank];
            if (!bestWord.equals(lower) && (!AMBIGUOUS_KEEP_UNACCENTED.contains(lower) || !phonetic.equals(norm))) {
                return matchCase(typedWord, bestWord);
            }
            return null;
        }

        // 4. Productive Portuguese suffix accentuation (-cao -> -ção, -coes -> -ções, -encia -> -ência)
        if (phonetic.length() >= 5 && phonetic.endsWith("cao")) {
            return matchCase(typedWord, phonetic.substring(0, phonetic.length() - 3) + "ção");
        }
        if (phonetic.length() >= 6 && phonetic.endsWith("coes")) {
            return matchCase(typedWord, phonetic.substring(0, phonetic.length() - 4) + "ções");
        }
        if (phonetic.length() >= 6 && (phonetic.endsWith("ssao") || phonetic.endsWith("nsao"))) {
            return matchCase(typedWord, phonetic.substring(0, phonetic.length() - 3) + "são");
        }
        if (phonetic.length() >= 7 && phonetic.endsWith("encia")) {
            return matchCase(typedWord, phonetic.substring(0, phonetic.length() - 5) + "ência");
        }

        // 5. Edit distance 1 search (e.g. "refrerencia" -> "referencia" -> "referência"!)
        if (phonetic.length() >= 3) {
            int bestIdx1 = findBestTypoCandidate(data, phonetic, !strictAntiErrorMode);
            if (bestIdx1 >= 0) {
                return matchCase(typedWord, data.words[bestIdx1]);
            }
        }

        // 6. Strict Anti-Error Mode: Edit distance 2 search for tougher misspellings (e.g. "refrerensia", "trasferensia")
        if (strictAntiErrorMode && phonetic.length() >= 5) {
            int bestIdx2 = findBestDist2Candidate(data, phonetic);
            if (bestIdx2 >= 0) {
                return matchCase(typedWord, data.words[bestIdx2]);
            }
        }

        return null;
    }

    /**
     * Mid-word live correction used ONLY when Strict Anti-Error Guard is enabled:
     * Immediately fixes impossible Portuguese bigrams "np" -> "mp" and "nb" -> "mb" as the user types!
     */
    public static String getImmediateMidWordFix(String typedWord) {
        if (typedWord == null || typedWord.length() < 3) return null;
        String lower = typedWord.toLowerCase(PT_BR);
        if (lower.endsWith("np")) {
            return typedWord.substring(0, typedWord.length() - 2)
                    + (Character.isUpperCase(typedWord.charAt(typedWord.length() - 2)) ? "M" : "m")
                    + typedWord.charAt(typedWord.length() - 1);
        }
        if (lower.endsWith("nb")) {
            return typedWord.substring(0, typedWord.length() - 2)
                    + (Character.isUpperCase(typedWord.charAt(typedWord.length() - 2)) ? "M" : "m")
                    + typedWord.charAt(typedWord.length() - 1);
        }
        return null;
    }

    public static List<String> getSuggestions(String typedWord, boolean strictAntiError) {
        List<String> out = new ArrayList<>(3);
        if (typedWord == null || typedWord.isEmpty()) {
            return out;
        }

        DictData data = dictData;
        String lower = typedWord.toLowerCase(PT_BR);
        String norm = stripAccents(lower);
        List<String> candidates = new ArrayList<>(4);

        if (norm.equals("e") || norm.equals("eh")) {
            addUnique(candidates, matchCase(typedWord, "é"), typedWord);
        } else if (norm.equals("a")) {
            addUnique(candidates, matchCase(typedWord, "à"), typedWord);
        } else if (norm.equals("esta")) {
            addUnique(candidates, matchCase(typedWord, "está"), typedWord);
        } else if (norm.equals("nos")) {
            addUnique(candidates, matchCase(typedWord, "nós"), typedWord);
        } else if (norm.equals("da")) {
            addUnique(candidates, matchCase(typedWord, "dá"), typedWord);
        } else if (norm.equals("pais")) {
            addUnique(candidates, matchCase(typedWord, "país"), typedWord);
        }

        for (String pw : personalWords) {
            if (stripAccents(pw).toLowerCase(Locale.ROOT).startsWith(norm)) {
                addUnique(candidates, pw, typedWord);
            }
        }

        String auto = getAutoCorrection(typedWord, true, strictAntiError);
        if (auto != null) {
            addUnique(candidates, auto, typedWord);
        }

        Integer normRank = data.normToBestRank.get(norm);
        if (normRank != null) {
            addUnique(candidates, matchCase(typedWord, data.words[normRank]), typedWord);
        }

        int scanLimit = Math.min(data.words.length, 18000);
        for (int i = 0; i < scanLimit && candidates.size() < 3; i++) {
            if (data.norms[i].length() > norm.length() && data.norms[i].startsWith(norm)) {
                addUnique(candidates, matchCase(typedWord, data.words[i]), typedWord);
            }
        }

        if (candidates.isEmpty() && norm.length() >= 3) {
            int typoIdx = findBestTypoCandidate(data, applyPhoneticFixes(norm), false);
            if (typoIdx >= 0) {
                addUnique(candidates, matchCase(typedWord, data.words[typoIdx]), typedWord);
            } else if (norm.length() >= 5) {
                int d2 = findBestDist2Candidate(data, applyPhoneticFixes(norm));
                if (d2 >= 0) {
                    addUnique(candidates, matchCase(typedWord, data.words[d2]), typedWord);
                }
            }
        }

        String center = !candidates.isEmpty() ? candidates.get(0) : typedWord;
        String left = typedWord;
        String right = candidates.size() >= 2 ? candidates.get(1) : "";

        out.add(left);
        out.add(center);
        out.add(right);
        return out;
    }

    private static void addUnique(List<String> list, String candidate, String typedWord) {
        if (candidate != null && !candidate.isEmpty() && !candidate.equals(typedWord) && !list.contains(candidate)) {
            list.add(candidate);
        }
    }

    private static int findBestTypoCandidate(DictData data, String norm, boolean conservativeRankLimit) {
        int len = norm.length();
        if (len < 3 || len >= MAX_WORD_LEN_BUCKET) {
            return -1;
        }

        int bestIdx = -1;
        int bestScore = Integer.MAX_VALUE;
        char firstChar = norm.charAt(0);
        int maxRank = conservativeRankLimit ? 10500 : MAX_TYPO_SEARCH_RANK;

        int minLen = Math.max(2, len - 1);
        int maxLen = Math.min(MAX_WORD_LEN_BUCKET, len + 1);

        for (int bLen = minLen; bLen <= maxLen; bLen++) {
            int[] bucket = data.bucketsByLen[bLen];
            if (bucket == null) continue;

            for (int idx : bucket) {
                if (idx > maxRank) break;
                String cand = data.norms[idx];
                int penalty = computeEdit1Penalty(norm, cand);
                if (penalty < 0) continue;

                if (cand.charAt(0) != firstChar) {
                    if (conservativeRankLimit) continue;
                    penalty += 2500;
                }
                int score = idx + penalty;
                if (score < bestScore) {
                    bestScore = score;
                    bestIdx = idx;
                }
            }
        }
        return bestIdx;
    }

    private static int findBestDist2Candidate(DictData data, String norm) {
        int len = norm.length();
        if (len < 5 || len >= MAX_WORD_LEN_BUCKET - 1) {
            return -1;
        }
        char firstChar = norm.charAt(0);
        int bestIdx = -1;
        int bestScore = Integer.MAX_VALUE;

        int minLen = Math.max(4, len - 2);
        int maxLen = Math.min(MAX_WORD_LEN_BUCKET, len + 2);

        for (int bLen = minLen; bLen <= maxLen; bLen++) {
            int[] bucket = data.bucketsByLen[bLen];
            if (bucket == null) continue;

            for (int idx : bucket) {
                if (idx > MAX_STRICT_DIST2_RANK) break;
                String cand = data.norms[idx];
                if (cand.charAt(0) != firstChar) continue;
                if (isLevenshteinAtMost2(norm, cand)) {
                    int score = idx + Math.abs(len - cand.length()) * 400;
                    if (score < bestScore) {
                        bestScore = score;
                        bestIdx = idx;
                    }
                }
            }
        }
        return bestIdx;
    }

    private static boolean isLevenshteinAtMost2(String a, String b) {
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > 2) return false;

        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;

        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                int val = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                curr[j] = val;
                if (val < rowMin) rowMin = val;
            }
            if (rowMin > 2) return false;
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[lb] <= 2;
    }

    private static int computeEdit1Penalty(String s1, String s2) {
        int n1 = s1.length();
        int n2 = s2.length();
        int diff = n1 - n2;
        if (diff < -1 || diff > 1) return -1;

        if (diff == 0) {
            int firstMismatch = -1;
            for (int i = 0; i < n1; i++) {
                if (s1.charAt(i) != s2.charAt(i)) {
                    firstMismatch = i;
                    break;
                }
            }
            if (firstMismatch < 0) return -1;

            if (firstMismatch + 1 < n1
                    && s1.charAt(firstMismatch) == s2.charAt(firstMismatch + 1)
                    && s1.charAt(firstMismatch + 1) == s2.charAt(firstMismatch)) {
                for (int k = firstMismatch + 2; k < n1; k++) {
                    if (s1.charAt(k) != s2.charAt(k)) return -1;
                }
                return 200;
            }

            for (int k = firstMismatch + 1; k < n1; k++) {
                if (s1.charAt(k) != s2.charAt(k)) return -1;
            }
            char c1 = s1.charAt(firstMismatch);
            char c2 = s2.charAt(firstMismatch);
            return areCommonSubstitutions(c1, c2) ? 280 : 1400;
        }

        String shorter = (n1 < n2) ? s1 : s2;
        String longer = (n1 < n2) ? s2 : s1;
        int ns = shorter.length();
        int i = 0;
        int j = 0;
        char extraChar = 0;
        int extraPos = -1;
        while (i < ns && j < ns + 1) {
            if (shorter.charAt(i) == longer.charAt(j)) {
                i++;
                j++;
            } else {
                if (extraPos >= 0) return -1;
                extraChar = longer.charAt(j);
                extraPos = j;
                j++;
            }
        }
        if (extraPos < 0) {
            extraChar = longer.charAt(ns);
            extraPos = ns;
        }
        boolean repeatedNearby = (extraPos > 0 && longer.charAt(extraPos - 1) == extraChar)
                || (extraPos + 1 < longer.length() && longer.charAt(extraPos + 1) == extraChar)
                || (extraPos > 1 && longer.charAt(extraPos - 2) == extraChar)
                || (extraPos + 2 < longer.length() && longer.charAt(extraPos + 2) == extraChar);
        return repeatedNearby ? 180 : 650;
    }

    private static boolean areCommonSubstitutions(char a, char b) {
        if ((a == 'n' && b == 'm') || (a == 'm' && b == 'n')) return true;
        if ((a == 's' && (b == 'z' || b == 'c' || b == 'x')) || (b == 's' && (a == 'z' || a == 'c' || a == 'x'))) return true;
        if ((a == 'c' && b == 'z') || (a == 'z' && b == 'c')) return true;
        if ((a == 'g' && b == 'j') || (a == 'j' && b == 'g')) return true;
        if ((a == 'u' && (b == 'o' || b == 'l')) || (b == 'u' && (a == 'o' || a == 'l'))) return true;
        if ((a == 'e' && b == 'i') || (a == 'i' && b == 'e')) return true;

        String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        for (String row : rows) {
            int ia = row.indexOf(a);
            int ib = row.indexOf(b);
            if (ia >= 0 && ib >= 0 && Math.abs(ia - ib) == 1) return true;
        }
        return false;
    }

    private static String matchCase(String source, String target) {
        if (source == null || source.isEmpty() || target == null || target.isEmpty()) {
            return target;
        }
        boolean allUpper = source.length() > 1 && source.equals(source.toUpperCase(PT_BR));
        if (allUpper) {
            return target.toUpperCase(PT_BR);
        }
        if (Character.isUpperCase(source.charAt(0))) {
            return target.substring(0, 1).toUpperCase(PT_BR) + target.substring(1);
        }
        return target;
    }
}
