package com.luisunicode.keyboard;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class KeyboardSettingsActivity extends Activity {

    private GboardTheme theme;
    private LinearLayout contentList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PortugueseSuggestions.ensureLoaded(this);
        rebuildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuildScreen();
    }

    private void rebuildScreen() {
        theme = GboardTheme.fromContext(this);

        // Sync status bar & navigation bar colors with the selected theme (Pure Black in PC Gamer RGB!)
        Window w = getWindow();
        if (w != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            w.setStatusBarColor(theme.boardBg);
            w.setNavigationBarColor(theme.boardBg);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.boardBg);

        // Top App Bar
        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(12), dp(16), dp(16), dp(12));
        appBar.setBackgroundColor(theme.boardBg);

        Button backBtn = new Button(this);
        backBtn.setText("←");
        backBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        backBtn.setTextColor(theme.isGamerRgb ? Color.parseColor("#00E5FF") : theme.keyText);
        backBtn.setBackgroundColor(Color.TRANSPARENT);
        backBtn.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText("Configurações");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f);
        title.setTypeface(FontStyler.resolveKeycapTypeface(theme.keycapFontMode, true));
        title.setTextColor(theme.isGamerRgb ? Color.parseColor("#00E5FF") : theme.keyText);
        title.setPadding(dp(8), 0, 0, 0);

        appBar.addView(backBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        appBar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(appBar);

        if (theme.isGamerRgb) {
            View neonLine = new View(this);
            GradientDrawable rainbow = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.MAGENTA}
            );
            neonLine.setBackground(rainbow);
            root.addView(neonLine, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
        }

        ScrollView scroll = new ScrollView(this);
        contentList = new LinearLayout(this);
        contentList.setOrientation(LinearLayout.VERTICAL);
        contentList.setPadding(dp(16), dp(6), dp(16), dp(32));

        // 1. Direct Theme Selector Strip (1-tap selection!)
        addSectionTitle("Tema do Teclado");
        addThemeChipSelectorRow();

        int curKeyFont = GboardTheme.getKeycapFontMode(this);
        addActionCard("Fonte visual das teclas: " + FontStyler.KEY_FONT_NAMES[curKeyFont],
                "Toque para alternar o estilo da letra desenhada nos botões",
                () -> {
                    int next = (curKeyFont + 1) % FontStyler.KEY_FONT_NAMES.length;
                    GboardTheme.setKeycapFontMode(this, next);
                    rebuildScreen();
                });

        int curTypingFont = GboardTheme.getTypingFontStyle(this);
        addActionCard("Fonte ao digitar: " + FontStyler.FONT_NAMES[curTypingFont],
                "Converte automaticamente as letras enviadas para qualquer aplicativo",
                () -> {
                    int next = (curTypingFont + 1) % FontStyler.FONT_NAMES.length;
                    GboardTheme.setTypingFontStyle(this, next);
                    rebuildScreen();
                });

        // 2. Correções e sugestões
        addSectionTitle("Correções e sugestões");
        addSwitchCard(
                "Modo Anti-Erro Dinâmico (Impedir palavras erradas)",
                "Detecta erros como 'refrerencia' ou 'inpessa' e corrige dinamicamente para 'referência' / 'impeça'",
                GboardTheme.isStrictAntiErrorEnabled(this),
                checked -> GboardTheme.setStrictAntiErrorEnabled(this, checked)
        );
        addSwitchCard(
                "Correção automática",
                "Corrigir ortografia e acentuar palavras ao pressionar espaço ou pontuação",
                GboardTheme.isAutoCorrectEnabled(this),
                checked -> GboardTheme.setAutoCorrectEnabled(this, checked)
        );
        addSwitchCard(
                "Barra de sugestões de palavras",
                "Exibir sugestões acentuadas no topo do teclado durante a digitação",
                GboardTheme.isShowSuggestionsEnabled(this),
                checked -> GboardTheme.setShowSuggestionsEnabled(this, checked)
        );
        addSwitchCard(
                "Capitalização automática",
                "Iniciar primeira palavra de cada frase com letra maiúscula",
                GboardTheme.isAutoCapEnabled(this),
                checked -> GboardTheme.setAutoCapEnabled(this, checked)
        );
        addSwitchCard(
                "Ponto final com espaço duplo",
                "Toque duas vezes na barra de espaço para inserir ponto seguido de espaço",
                GboardTheme.isDoubleSpaceDotEnabled(this),
                checked -> GboardTheme.setDoubleSpaceDotEnabled(this, checked)
        );

        // 3. Preferências
        addSectionTitle("Preferências");
        addSwitchCard(
                "Linha de números fixa",
                "Exibir sempre os números de 1 a 0 acima da fileira QWERTY",
                GboardTheme.isNumberRowEnabled(this),
                checked -> GboardTheme.setNumberRowEnabled(this, checked)
        );

        int hMode = GboardTheme.getHeightMode(this);
        String[] hNames = {"Curto", "Normal (Padrão Gboard)", "Alto"};
        addActionCard("Altura do teclado: " + hNames[Math.max(0, Math.min(2, hMode))],
                "Toque para alternar entre Curto, Normal e Alto",
                () -> {
                    GboardTheme.setHeightMode(this, (hMode + 1) % 3);
                    rebuildScreen();
                });

        addSwitchCard(
                "Bordas das teclas",
                "Exibir o contorno individual de cada tecla",
                GboardTheme.isKeyBordersEnabled(this),
                checked -> {
                    GboardTheme.saveKeyBorders(this, checked);
                    rebuildScreen();
                }
        );
        addSwitchCard(
                "Vibrar ao tocar nas teclas",
                "Resposta tátil ao pressionar teclas",
                GboardTheme.isHapticEnabled(this),
                checked -> GboardTheme.setHapticEnabled(this, checked)
        );
        addSwitchCard(
                "Som ao tocar nas teclas",
                "Reproduzir clique ao digitar (clique mecânico no tema PC Gamer RGB)",
                GboardTheme.isSoundEnabled(this),
                checked -> GboardTheme.setSoundEnabled(this, checked)
        );
        addSwitchCard(
                "Pop-up ao tocar nas teclas",
                "Mostrar ampliação flutuante da letra ao pressionar",
                GboardTheme.isPopupEnabled(this),
                checked -> GboardTheme.setPopupEnabled(this, checked)
        );

        // 4. Área de transferência
        addSectionTitle("Área de transferência");
        addSwitchCard(
                "Salvar textos copiados recentemente",
                "Armazenar textos copiados e permitir fixar com cadeado para impedir exclusão",
                ClipboardStore.isEnabled(this),
                checked -> ClipboardStore.setEnabled(this, checked)
        );
        addActionCard(
                "Limpar itens não protegidos por cadeado",
                "Remove todos os itens recentes mantendo intactos os itens travados com cadeado",
                () -> {
                    int removed = ClipboardStore.clearUnlocked(this);
                    Toast.makeText(this, removed + " itens desbloqueados removidos.", Toast.LENGTH_SHORT).show();
                }
        );

        // 5. Dicionário pessoal
        addSectionTitle("Dicionário pessoal");
        addPersonalDictionaryCard();

        scroll.addView(contentList);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        setContentView(root);
    }

    private void addThemeChipSelectorRow() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(10));

        for (GboardTheme.ThemeOption opt : GboardTheme.ALL_THEMES) {
            boolean sel = (theme.themeId == opt.id);
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(sel ? ("✓ " + opt.name) : opt.name);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            b.setPadding(dp(16), dp(8), dp(16), dp(8));

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));
            if (opt.rgbPreview) {
                bg.setColor(Color.parseColor("#080A12"));
                bg.setStroke(dp(2), Color.parseColor(sel ? "#00E5FF" : "#FF007F"));
                b.setTextColor(Color.parseColor("#00E5FF"));
            } else {
                bg.setColor(sel ? theme.funcKeyActiveBg : theme.keyBg);
                bg.setStroke(dp(sel ? 2 : 1), sel ? Color.parseColor("#0B57D0") : theme.dividerColor);
                b.setTextColor(sel ? theme.funcKeyActiveText : theme.keyText);
            }
            b.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)
            );
            lp.rightMargin = dp(8);
            b.setOnClickListener(v -> {
                GboardTheme.saveSelectedTheme(this, opt.id);
                rebuildScreen();
            });
            row.addView(b, lp);
        }
        hs.addView(row);
        contentList.addView(hs);
    }

    private void addSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tv.setTypeface(FontStyler.resolveKeycapTypeface(theme.keycapFontMode, true));
        tv.setTextColor(theme.isGamerRgb ? Color.parseColor("#00E5FF")
                : (theme.isDark ? Color.parseColor("#A8C7FA") : Color.parseColor("#0B57D0")));
        tv.setPadding(dp(6), dp(16), dp(6), dp(8));
        contentList.addView(tv);
    }

    private LinearLayout createCardShell() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.keyBg);
        bg.setCornerRadius(dp(16));
        if (theme.isGamerRgb) {
            bg.setStroke(dp(1), Color.parseColor("#00E5FF"));
        } else if (!theme.isDark) {
            bg.setStroke(dp(1), Color.parseColor("#E0E3EC"));
        }
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private void addActionCard(String titleText, String subText, Runnable onClick) {
        LinearLayout card = createCardShell();
        card.addView(buildTitleSubColumn(titleText, subText),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.setOnClickListener(v -> onClick.run());
        contentList.addView(card);
    }

    private interface BoolCallback {
        void onChanged(boolean value);
    }

    private void addSwitchCard(String titleText, String subText, boolean initial, BoolCallback cb) {
        LinearLayout card = createCardShell();
        card.addView(buildTitleSubColumn(titleText, subText),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> cb.onChanged(isChecked));
        card.addView(sw);
        card.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        contentList.addView(card);
    }

    private LinearLayout buildTitleSubColumn(String titleText, String subText) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, dp(10), 0);

        TextView t = new TextView(this);
        t.setText(titleText);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        t.setTypeface(FontStyler.resolveKeycapTypeface(theme.keycapFontMode, false));
        t.setTextColor(theme.keyText);

        TextView s = new TextView(this);
        s.setText(subText);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        s.setTextColor(theme.hintText);
        s.setPadding(0, dp(3), 0, 0);

        col.addView(t);
        col.addView(s);
        return col;
    }

    private void addPersonalDictionaryCard() {
        LinearLayout card = createCardShell();
        card.setOrientation(LinearLayout.VERTICAL);

        TextView desc = new TextView(this);
        desc.setText("Adicione gírias, nomes ou termos próprios para nunca serem corrigidos como erro:");
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        desc.setTextColor(theme.hintText);
        card.addView(desc);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(8), 0, dp(8));

        EditText input = new EditText(this);
        input.setHint("Nova palavra...");
        input.setHintTextColor(theme.hintText);
        input.setTextColor(theme.keyText);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);

        Button addBtn = new Button(this);
        addBtn.setText("Adicionar");
        addBtn.setAllCaps(false);
        addBtn.setOnClickListener(v -> {
            String w = input.getText().toString().trim();
            if (!w.isEmpty()) {
                PortugueseSuggestions.addPersonalWord(this, w);
                input.setText("");
                rebuildScreen();
            }
        });

        inputRow.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputRow.addView(addBtn);
        card.addView(inputRow);

        List<String> personal = PortugueseSuggestions.getPersonalWords(this);
        if (!personal.isEmpty()) {
            TextView savedLabel = new TextView(this);
            savedLabel.setText("Palavras salvas (toque para remover): " + String.join(", ", personal));
            savedLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            savedLabel.setTextColor(theme.isGamerRgb ? Color.parseColor("#00E5FF") : Color.parseColor("#0B57D0"));
            savedLabel.setOnClickListener(v -> {
                String last = personal.get(personal.size() - 1);
                PortugueseSuggestions.removePersonalWord(this, last);
                Toast.makeText(this, "Removida: " + last, Toast.LENGTH_SHORT).show();
                rebuildScreen();
            });
            card.addView(savedLabel);
        }

        contentList.addView(card);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                v,
                getResources().getDisplayMetrics()
        ));
    }
}
