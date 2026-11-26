package com.luisunicode.keyboard;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UnicodeKeyboardService extends InputMethodService implements GboardKeyView.PreviewListener {

    private static final int VIEW_LETTERS = 0;
    private static final int VIEW_SYMBOLS_P1 = 1;
    private static final int VIEW_SYMBOLS_P2 = 2;
    private static final int VIEW_NUMPAD_3X4 = 3;
    private static final int VIEW_EMOJI = 4;
    private static final int VIEW_TOOLS_MENU = 5;
    private static final int VIEW_TEXT_EDIT = 6;
    private static final int VIEW_CLIPBOARD = 7;
    private static final int VIEW_THEMES = 8;
    private static final int VIEW_ANTI_FRIENDZONE = 9;
    private static final int VIEW_FONTS = 10;
    private static final int VIEW_RESIZE = 11;

    private static final int SHIFT_OFF = 0;
    private static final int SHIFT_SINGLE = 1;
    private static final int SHIFT_CAPS_LOCK = 2;

    private static final int ONE_HAND_OFF = 0;
    private static final int ONE_HAND_RIGHT = 1;
    private static final int ONE_HAND_LEFT = 2;

    private static final Locale PT_BR = new Locale("pt", "BR");

    private GboardTheme theme;
    private final Paint chassisRgbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private LinearLayout outerRoot;
    private LinearLayout oneHandWrapper;
    private LinearLayout boardContent;
    private LinearLayout translateBarContainer;
    private LinearLayout headerContainer;
    private LinearLayout bodyContainer;
    private Space bottomInsetSpacer;

    private PopupWindow previewPopup;
    private LinearLayout previewContainer;

    private int currentView = VIEW_LETTERS;
    private int previousTypingView = VIEW_LETTERS;
    private int toolsMenuPage = 0; // 0 = Page 1, 1 = Page 2
    private int shiftState = SHIFT_OFF;
    private long lastShiftTapMs = 0L;
    private long lastSpaceTapMs = 0L;

    private int currentEmojiCategory = 0;
    private boolean emojiEmoticonTab = false;
    private boolean emojiSearchMode = false;
    private final StringBuilder emojiSearchQuery = new StringBuilder();

    private int currentAfzCategory = 0;

    private boolean translateModeActive = false;
    private int translateSourceIdx = 0; // pt
    private int translateTargetIdx = 1; // en
    private final StringBuilder translateBuffer = new StringBuilder();
    private String latestTranslatedPreview = "";

    private boolean searchActionMode = false;
    private boolean selectionMode = false;
    private int oneHandState = ONE_HAND_OFF;

    private String lastRawBeforeAutoCorrect = null;
    private String lastCommittedAutoCorrect = null;
    private String lastDeletedChunk = "";

    private ClipboardManager clipboardManager;
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::syncSystemClipboard;

    private TextToSpeech tts;
    private boolean ttsReady = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable translateDebounceRunnable = this::triggerLiveTranslation;
    private final Runnable rgbWaveRunnable = new Runnable() {
        @Override
        public void run() {
            if (theme != null && theme.isGamerRgb && outerRoot != null && isInputViewShown()) {
                outerRoot.invalidate();
                invalidateKeyViewsRecursive(outerRoot);
                mainHandler.postDelayed(this, 38);
            }
        }
    };

    private final String[] row1Hints = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};

    private final String[][] lowerLetterRows = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"z", "x", "c", "v", "b", "n", "m"}
    };

    private final String[][] symbolsPage1Rows = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "$", "_", "&", "-", "+", "(", ")", "/"},
            {"*", "\"", "'", ":", ";", "!", "?"}
    };

    private final String[][] symbolsPage2Rows = {
            {"~", "`", "|", "•", "√", "π", "÷", "×", "§", "Δ"},
            {"£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"},
            {"%", "©", "®", "™", "✓", "[", "]"}
    };

    private final String[][] emoticonRows = {
            {":-)", ";-)", "^_^", "(^^)"},
            {":-D", ":-P", "8-)", "B-)"},
            {":-(", ":'-(", ":-O", ":-/"},
            {"<3", "\\o/", "o_O", "¯\\_(ツ)_/¯"}
    };

    @Override
    public void onCreate() {
        super.onCreate();
        PortugueseSuggestions.ensureLoaded(this);
        EmojiRepository.ensureLoaded(this);
        try {
            Object svc = getSystemService(Context.CLIPBOARD_SERVICE);
            if (svc instanceof ClipboardManager) {
                clipboardManager = (ClipboardManager) svc;
                clipboardManager.addPrimaryClipChangedListener(clipListener);
            }
        } catch (Exception ignored) {
        }
        try {
            tts = new TextToSpeech(getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int res = tts.setLanguage(PT_BR);
                    ttsReady = (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED);
                }
            });
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(rgbWaveRunnable);
        mainHandler.removeCallbacks(translateDebounceRunnable);
        if (clipboardManager != null) {
            try {
                clipboardManager.removePrimaryClipChangedListener(clipListener);
            } catch (Exception ignored) {
            }
        }
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
        }
        dismissPreview();
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public View onCreateInputView() {
        PortugueseSuggestions.ensureLoaded(this);
        EmojiRepository.ensureLoaded(this);
        theme = GboardTheme.fromContext(this);
        dismissPreview();

        outerRoot = new LinearLayout(this) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (theme != null && theme.isGamerRgb && getWidth() > 0) {
                    float w = getWidth();
                    float hBar = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 2.2f, getResources().getDisplayMetrics()
                    );
                    float shift = ((SystemClock.uptimeMillis() % 2400L) / 2400f) * w;
                    chassisRgbPaint.setShader(new LinearGradient(
                            -w + shift, 0f, w + shift, 0f,
                            new int[]{
                                    Color.parseColor("#FF0055"), Color.parseColor("#FF9900"),
                                    Color.parseColor("#00FF66"), Color.parseColor("#00E5FF"),
                                    Color.parseColor("#7C4DFF"), Color.parseColor("#FF0055"),
                                    Color.parseColor("#FF9900"), Color.parseColor("#00FF66"),
                                    Color.parseColor("#00E5FF")
                            },
                            null, Shader.TileMode.CLAMP
                    ));
                    canvas.drawRect(0f, 0f, w, hBar, chassisRgbPaint);
                    chassisRgbPaint.setShader(null);
                }
            }
        };
        outerRoot.setOrientation(LinearLayout.VERTICAL);
        outerRoot.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        outerRoot.setBackgroundColor(theme.boardBg);

        oneHandWrapper = new LinearLayout(this);
        oneHandWrapper.setOrientation(LinearLayout.HORIZONTAL);
        oneHandWrapper.setGravity(Gravity.CENTER_VERTICAL);
        outerRoot.addView(oneHandWrapper, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        bottomInsetSpacer = new Space(this);
        outerRoot.addView(bottomInsetSpacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        ));

        outerRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            int navBottom = getNavBottomInset(insets);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bottomInsetSpacer.getLayoutParams();
            int desiredHeight = Math.max(navBottom + dp(4), dp(8));
            if (lp.height != desiredHeight) {
                lp.height = desiredHeight;
                bottomInsetSpacer.setLayoutParams(lp);
            }
            return insets;
        });

        applyBackgroundLayers();
        applyNavigationBarStyle();
        rebuildOneHandWrapper();
        syncRgbAnimationLoop();
        return outerRoot;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        PortugueseSuggestions.ensureLoaded(this);
        EmojiRepository.ensureLoaded(this);
        updateEditorActionState(info);
        syncSystemClipboard();
        clearAutoCorrectUndo();
        updateAutoShiftFromCursor();

        GboardTheme latest = GboardTheme.fromContext(this);
        if (theme == null || latest.themeId != theme.themeId || latest.isDark != theme.isDark
                || latest.keyBorders != theme.keyBorders || latest.keycapFontMode != theme.keycapFontMode
                || outerRoot == null) {
            setInputView(onCreateInputView());
        } else {
            applyBackgroundLayers();
            applyNavigationBarStyle();
            outerRoot.requestApplyInsets();
            renderCurrentView();
        }
        syncRgbAnimationLoop();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if (currentView == VIEW_LETTERS && !translateModeActive && !emojiSearchMode) {
            updateAutoShiftFromCursor();
            refreshHeaderBarOnly();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        dismissPreview();
        if (outerRoot != null) {
            setInputView(onCreateInputView());
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        mainHandler.removeCallbacks(rgbWaveRunnable);
        dismissPreview();
        selectionMode = false;
        clearAutoCorrectUndo();
        super.onFinishInputView(finishingInput);
    }

    private void syncRgbAnimationLoop() {
        mainHandler.removeCallbacks(rgbWaveRunnable);
        if (theme != null && theme.isGamerRgb) {
            mainHandler.post(rgbWaveRunnable);
        }
    }

    private void invalidateKeyViewsRecursive(View v) {
        if (v instanceof GboardKeyView) {
            v.invalidate();
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                invalidateKeyViewsRecursive(vg.getChildAt(i));
            }
        }
    }

    private void applyBackgroundLayers() {
        if (outerRoot == null || theme == null) return;
        outerRoot.setBackgroundColor(theme.boardBg);
        outerRoot.invalidate();
    }

    private void applyFullThemeReload() {
        theme = GboardTheme.fromContext(this);
        applyBackgroundLayers();
        applyNavigationBarStyle();
        rebuildOneHandWrapper();
        syncRgbAnimationLoop();
    }

    private void updateEditorActionState(EditorInfo info) {
        if (info == null) {
            searchActionMode = false;
            return;
        }
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noEnter = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        searchActionMode = !noEnter && action == EditorInfo.IME_ACTION_SEARCH;
    }

    private void updateAutoShiftFromCursor() {
        if (shiftState == SHIFT_CAPS_LOCK) return;
        if (!GboardTheme.isAutoCapEnabled(this)) return;

        EditorInfo ei = getCurrentInputEditorInfo();
        InputConnection ic = getCurrentInputConnection();
        if (ei != null && ic != null && ei.inputType != EditorInfo.TYPE_NULL) {
            int caps = ic.getCursorCapsMode(ei.inputType);
            int desired = (caps != 0) ? SHIFT_SINGLE : SHIFT_OFF;
            if (desired != shiftState) {
                shiftState = desired;
                if (currentView == VIEW_LETTERS && bodyContainer != null) {
                    renderCurrentView();
                }
            }
        }
    }

    private void rebuildOneHandWrapper() {
        if (oneHandWrapper == null) return;
        oneHandWrapper.removeAllViews();

        boardContent = new LinearLayout(this);
        boardContent.setOrientation(LinearLayout.VERTICAL);
        boardContent.setPadding(dp(3), dp(2), dp(3), 0);

        translateBarContainer = new LinearLayout(this);
        translateBarContainer.setOrientation(LinearLayout.VERTICAL);
        translateBarContainer.setVisibility(View.GONE);
        boardContent.addView(translateBarContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        headerContainer = new LinearLayout(this);
        headerContainer.setOrientation(LinearLayout.HORIZONTAL);
        headerContainer.setGravity(Gravity.CENTER_VERTICAL);
        boardContent.addView(headerContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        bodyContainer = new LinearLayout(this);
        bodyContainer.setOrientation(LinearLayout.VERTICAL);
        boardContent.addView(bodyContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (oneHandState == ONE_HAND_OFF) {
            oneHandWrapper.addView(boardContent, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        } else if (oneHandState == ONE_HAND_RIGHT) {
            oneHandWrapper.addView(buildOneHandSideControls(true), new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 0.19f
            ));
            oneHandWrapper.addView(boardContent, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.81f
            ));
        } else {
            oneHandWrapper.addView(boardContent, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.81f
            ));
            oneHandWrapper.addView(buildOneHandSideControls(false), new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 0.19f
            ));
        }

        renderCurrentView();
    }

    private LinearLayout buildOneHandSideControls(boolean keyboardOnRight) {
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER);
        side.setPadding(dp(4), dp(36), dp(4), dp(12));

        int btnSize = dp(48);

        GboardKeyView expandBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        expandBtn.setIcon(GboardKeyView.ICON_EXPAND_FULL);
        expandBtn.setInsetsDp(3f, 3f);
        expandBtn.setOnClickListener(v -> {
            oneHandState = ONE_HAND_OFF;
            rebuildOneHandWrapper();
        });

        GboardKeyView flipSideBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        flipSideBtn.setIcon(keyboardOnRight ? GboardKeyView.ICON_CHEVRON_LEFT : GboardKeyView.ICON_CHEVRON_RIGHT);
        flipSideBtn.setInsetsDp(3f, 3f);
        flipSideBtn.setOnClickListener(v -> {
            oneHandState = keyboardOnRight ? ONE_HAND_LEFT : ONE_HAND_RIGHT;
            rebuildOneHandWrapper();
        });

        GboardKeyView switchImeBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        switchImeBtn.setIcon(GboardKeyView.ICON_GLOBE);
        switchImeBtn.setInsetsDp(3f, 3f);
        switchImeBtn.setOnClickListener(v -> showInputMethodSelector());

        side.addView(expandBtn, new LinearLayout.LayoutParams(btnSize, btnSize));
        side.addView(new Space(this), new LinearLayout.LayoutParams(dp(1), dp(16)));
        side.addView(flipSideBtn, new LinearLayout.LayoutParams(btnSize, btnSize));
        side.addView(new Space(this), new LinearLayout.LayoutParams(dp(1), dp(16)));
        side.addView(switchImeBtn, new LinearLayout.LayoutParams(btnSize, btnSize));
        return side;
    }

    private void switchToView(int targetView) {
        if (currentView == VIEW_LETTERS || currentView == VIEW_SYMBOLS_P1
                || currentView == VIEW_SYMBOLS_P2 || currentView == VIEW_NUMPAD_3X4) {
            previousTypingView = currentView;
        }
        currentView = targetView;
        dismissPreview();
        renderCurrentView();
    }

    private void refreshHeaderBarOnly() {
        if (headerContainer == null) return;
        if (translateModeActive) {
            rebuildTranslateBar();
        }
        if (currentView != VIEW_LETTERS && currentView != VIEW_SYMBOLS_P1
                && currentView != VIEW_SYMBOLS_P2 && currentView != VIEW_NUMPAD_3X4
                && currentView != VIEW_TOOLS_MENU) {
            return;
        }
        headerContainer.removeAllViews();
        String word = getActiveWordForSuggestions();
        if (currentView == VIEW_LETTERS && GboardTheme.isShowSuggestionsEnabled(this) && !word.isEmpty()) {
            buildWordSuggestionBar(word);
        } else {
            buildMainGboardToolbar();
        }
    }

    private String getActiveWordForSuggestions() {
        if (translateModeActive) {
            String buf = translateBuffer.toString();
            int end = buf.length();
            int start = end;
            while (start > 0 && Character.isLetter(buf.charAt(start - 1))) {
                start--;
            }
            return buf.substring(start, end);
        }
        return extractCurrentWordBeforeCursor();
    }

    private void renderCurrentView() {
        if (headerContainer == null || bodyContainer == null || translateBarContainer == null) {
            return;
        }
        headerContainer.removeAllViews();
        bodyContainer.removeAllViews();
        bodyContainer.setPadding(0, 0, 0, 0);

        if (translateModeActive && (currentView == VIEW_LETTERS || currentView == VIEW_SYMBOLS_P1
                || currentView == VIEW_SYMBOLS_P2)) {
            translateBarContainer.setVisibility(View.VISIBLE);
            rebuildTranslateBar();
        } else {
            translateBarContainer.setVisibility(View.GONE);
        }

        if (currentView == VIEW_TEXT_EDIT) {
            buildPanelHeader("Edição de texto", null, () -> {
                selectionMode = false;
                switchToView(previousTypingView);
            });
            buildTextEditingBody();
            return;
        }

        if (currentView == VIEW_CLIPBOARD) {
            syncSystemClipboard();
            buildClipboardHeader();
            buildClipboardBody();
            return;
        }

        if (currentView == VIEW_THEMES) {
            GboardKeyView borderToggle = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC_PILL);
            borderToggle.setLabel(theme.keyBorders ? "Bordas: Sim" : "Bordas: Não");
            borderToggle.setTextSizeSp(11.5f);
            borderToggle.setInsetsDp(2f, 6f);
            borderToggle.setOnClickListener(v -> {
                GboardTheme.saveKeyBorders(this, !theme.keyBorders);
                applyFullThemeReload();
            });
            buildPanelHeader("Tema", borderToggle, () -> switchToView(previousTypingView));
            buildThemesBody();
            return;
        }

        if (currentView == VIEW_ANTI_FRIENDZONE) {
            GboardKeyView saveDraftBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC_PILL);
            saveDraftBtn.setLabel("+ Salvar campo");
            saveDraftBtn.setTextSizeSp(11.5f);
            saveDraftBtn.setInsetsDp(2f, 6f);
            saveDraftBtn.setOnClickListener(v -> saveCurrentFieldToAfz());
            buildPanelHeader("Anti-Friendzone", saveDraftBtn, () -> switchToView(previousTypingView));
            buildAntiFriendzoneBody();
            return;
        }

        if (currentView == VIEW_FONTS) {
            int curKeyF = GboardTheme.getKeycapFontMode(this);
            GboardKeyView keyFontBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC_PILL);
            keyFontBtn.setLabel("Teclas: " + (curKeyF + 1) + "/4");
            keyFontBtn.setTextSizeSp(11.5f);
            keyFontBtn.setInsetsDp(2f, 6f);
            keyFontBtn.setOnClickListener(v -> {
                GboardTheme.setKeycapFontMode(this, (curKeyF + 1) % FontStyler.KEY_FONT_NAMES.length);
                applyFullThemeReload();
            });
            buildPanelHeader("Fontes especiais", keyFontBtn, () -> switchToView(VIEW_LETTERS));
            buildFontsBody();
            return;
        }

        if (currentView == VIEW_RESIZE) {
            buildPanelHeader("Redimensionar teclado", null, () -> switchToView(previousTypingView));
            buildResizeBody();
            return;
        }

        if (currentView == VIEW_EMOJI) {
            buildEmojiHeader();
            buildEmojiBody();
            return;
        }

        String word = (currentView == VIEW_LETTERS && GboardTheme.isShowSuggestionsEnabled(this))
                ? getActiveWordForSuggestions() : "";
        if (!word.isEmpty()) {
            buildWordSuggestionBar(word);
        } else {
            buildMainGboardToolbar();
        }

        switch (currentView) {
            case VIEW_SYMBOLS_P1:
                buildSymbolsPageBody(true);
                break;
            case VIEW_SYMBOLS_P2:
                buildSymbolsPageBody(false);
                break;
            case VIEW_NUMPAD_3X4:
                buildNumpad3x4Body();
                break;
            case VIEW_TOOLS_MENU:
                buildToolsMenuBody();
                break;
            case VIEW_LETTERS:
            default:
                buildLettersBody();
                break;
        }
    }

    /**
     * Exact Gboard Top Toolbar Positions:
     * [⊞ Menu]  [💘 Anti-Friendzone]  [📋 Área de transferência]  [⚙️ Configurações]  [🎤 Voz / 🔊 TTS círculo]
     */
    private void buildMainGboardToolbar() {
        headerContainer.setPadding(dp(4), 0, dp(4), 0);

        boolean menuOpen = (currentView == VIEW_TOOLS_MENU);
        GboardKeyView menuBtn = new GboardKeyView(
                this,
                theme,
                menuOpen ? GboardKeyView.STYLE_TONAL_CIRCLE : GboardKeyView.STYLE_TOOLBAR_ICON
        );
        menuBtn.setIcon(menuOpen ? GboardKeyView.ICON_CLOSE : GboardKeyView.ICON_GRID_MENU);
        menuBtn.setInsetsDp(3f, 4f);
        menuBtn.setOnClickListener(v -> {
            if (currentView == VIEW_TOOLS_MENU) {
                switchToView(previousTypingView);
            } else {
                toolsMenuPage = 0;
                switchToView(VIEW_TOOLS_MENU);
            }
        });

        GboardKeyView afzShortcut = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
        afzShortcut.setIcon(GboardKeyView.ICON_HEART_SPARK);
        afzShortcut.setInsetsDp(6f, 5f);
        afzShortcut.setOnClickListener(v -> switchToView(VIEW_ANTI_FRIENDZONE));

        GboardKeyView clipShortcut = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
        clipShortcut.setIcon(GboardKeyView.ICON_CLIPBOARD);
        clipShortcut.setInsetsDp(6f, 5f);
        clipShortcut.setOnClickListener(v -> switchToView(VIEW_CLIPBOARD));

        GboardKeyView settingsShortcut = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
        settingsShortcut.setIcon(GboardKeyView.ICON_SETTINGS);
        settingsShortcut.setInsetsDp(6f, 5f);
        settingsShortcut.setOnClickListener(v -> openSettingsActivity());

        // Right white circle: tap = Read Text Aloud (TTS) or Voice Input; long-press = Hide Keyboard
        GboardKeyView rightVoiceBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        rightVoiceBtn.setIcon(GboardKeyView.ICON_MIC);
        rightVoiceBtn.setInsetsDp(3f, 4f);
        rightVoiceBtn.setOnClickListener(v -> triggerVoiceOrTts());
        rightVoiceBtn.setOnLongClickListener(v -> {
            requestHideSelf(0);
            return true;
        });

        headerContainer.addView(menuBtn, new LinearLayout.LayoutParams(dp(46), dp(46)));
        headerContainer.addView(new Space(this), weightedParams(0.5f, dp(46)));
        headerContainer.addView(afzShortcut, new LinearLayout.LayoutParams(dp(50), dp(46)));
        headerContainer.addView(new Space(this), weightedParams(1f, dp(46)));
        headerContainer.addView(clipShortcut, new LinearLayout.LayoutParams(dp(50), dp(46)));
        headerContainer.addView(new Space(this), weightedParams(1f, dp(46)));
        headerContainer.addView(settingsShortcut, new LinearLayout.LayoutParams(dp(50), dp(46)));
        headerContainer.addView(new Space(this), weightedParams(0.5f, dp(46)));
        headerContainer.addView(rightVoiceBtn, new LinearLayout.LayoutParams(dp(44), dp(46)));
    }

    /**
     * Exact Gboard Translate Bar matching gboard_13_translate.png.
     */
    private void rebuildTranslateBar() {
        translateBarContainer.removeAllViews();
        translateBarContainer.setPadding(dp(4), dp(4), dp(4), dp(2));

        // Row 1: <- | [Source Lang] | <-> | [Target Lang]
        LinearLayout langRow = createRow();
        GboardKeyView closeTransBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        closeTransBtn.setIcon(GboardKeyView.ICON_BACK_ARROW);
        closeTransBtn.setInsetsDp(3f, 3f);
        closeTransBtn.setOnClickListener(v -> exitTranslateMode(true));

        GboardKeyView srcLangPill = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC_PILL);
        srcLangPill.setLabel(FreeTranslator.LANGUAGES[translateSourceIdx].name);
        srcLangPill.setTextSizeSp(13f);
        srcLangPill.setInsetsDp(4f, 4f);
        srcLangPill.setOnClickListener(v -> {
            translateSourceIdx = (translateSourceIdx + 1) % FreeTranslator.LANGUAGES.length;
            if (translateSourceIdx == translateTargetIdx) {
                translateSourceIdx = (translateSourceIdx + 1) % FreeTranslator.LANGUAGES.length;
            }
            rebuildTranslateBar();
            scheduleTranslation();
        });

        GboardKeyView swapBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
        swapBtn.setIcon(GboardKeyView.ICON_SWAP_HORIZ);
        swapBtn.setInsetsDp(4f, 4f);
        swapBtn.setOnClickListener(v -> {
            int tmp = translateSourceIdx;
            translateSourceIdx = translateTargetIdx;
            translateTargetIdx = tmp;
            rebuildTranslateBar();
            scheduleTranslation();
        });

        GboardKeyView dstLangPill = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC_PILL);
        dstLangPill.setLabel(FreeTranslator.LANGUAGES[translateTargetIdx].name);
        dstLangPill.setTextSizeSp(13f);
        dstLangPill.setInsetsDp(4f, 4f);
        dstLangPill.setOnClickListener(v -> {
            translateTargetIdx = (translateTargetIdx + 1) % FreeTranslator.LANGUAGES.length;
            if (translateTargetIdx == translateSourceIdx) {
                translateTargetIdx = (translateTargetIdx + 1) % FreeTranslator.LANGUAGES.length;
            }
            rebuildTranslateBar();
            scheduleTranslation();
        });

        langRow.addView(closeTransBtn, new LinearLayout.LayoutParams(dp(42), dp(40)));
        langRow.addView(new Space(this), new LinearLayout.LayoutParams(dp(8), dp(40)));
        langRow.addView(srcLangPill, weightedParams(1f, dp(40)));
        langRow.addView(swapBtn, new LinearLayout.LayoutParams(dp(42), dp(40)));
        langRow.addView(dstLangPill, weightedParams(1f, dp(40)));
        translateBarContainer.addView(langRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
        ));

        // Row 2: Outlined "Digite aqui para traduzir" box
        LinearLayout boxRow = createRow();
        boxRow.setPadding(dp(4), dp(3), dp(4), dp(2));

        LinearLayout pillBox = createRow();
        pillBox.setPadding(dp(14), dp(6), dp(8), dp(6));
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(theme.keyBg);
        boxBg.setCornerRadius(dp(22));
        boxBg.setStroke(dp(1), theme.isGamerRgb ? Color.parseColor("#00E5FF") : theme.funcKeyActiveBg);
        pillBox.setBackground(boxBg);

        TextView inputDisplay = new TextView(this);
        boolean empty = translateBuffer.length() == 0;
        inputDisplay.setText(empty ? "Digite aqui para traduzir" : translateBuffer.toString());
        inputDisplay.setTextColor(empty ? theme.hintText : theme.keyText);
        inputDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        inputDisplay.setSingleLine(true);
        inputDisplay.setEllipsize(TextUtils.TruncateAt.START);

        pillBox.addView(inputDisplay, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (!empty) {
            GboardKeyView clearBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
            clearBtn.setIcon(GboardKeyView.ICON_CLOSE);
            clearBtn.setInsetsDp(2f, 2f);
            clearBtn.setOnClickListener(v -> {
                translateBuffer.setLength(0);
                latestTranslatedPreview = "";
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.setComposingText("", 1);
                rebuildTranslateBar();
            });
            pillBox.addView(clearBtn, new LinearLayout.LayoutParams(dp(30), dp(30)));
        }

        boxRow.addView(pillBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        translateBarContainer.addView(boxRow);
    }

    private void scheduleTranslation() {
        mainHandler.removeCallbacks(translateDebounceRunnable);
        if (!translateModeActive) return;
        if (translateBuffer.length() == 0) {
            latestTranslatedPreview = "";
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.setComposingText("", 1);
            return;
        }
        mainHandler.postDelayed(translateDebounceRunnable, 360);
    }

    private void triggerLiveTranslation() {
        if (!translateModeActive) return;
        String srcText = translateBuffer.toString().trim();
        if (srcText.isEmpty()) return;

        String sl = FreeTranslator.LANGUAGES[translateSourceIdx].code;
        String tl = FreeTranslator.LANGUAGES[translateTargetIdx].code;

        FreeTranslator.translateAsync(srcText, sl, tl, (source, translated, ok) -> {
            if (!translateModeActive) return;
            latestTranslatedPreview = translated;
            InputConnection ic = getCurrentInputConnection();
            if (ic != null && translated != null) {
                ic.setComposingText(translated, 1);
            }
        });
    }

    private void exitTranslateMode(boolean commitCurrent) {
        mainHandler.removeCallbacks(translateDebounceRunnable);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (commitCurrent && !latestTranslatedPreview.isEmpty()) {
                ic.commitText(latestTranslatedPreview + " ", 1);
            } else {
                ic.finishComposingText();
            }
        }
        translateBuffer.setLength(0);
        latestTranslatedPreview = "";
        translateModeActive = false;
        renderCurrentView();
    }

    private void buildWordSuggestionBar(String currentWord) {
        headerContainer.setPadding(dp(4), 0, dp(4), 0);

        GboardKeyView menuBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
        menuBtn.setIcon(GboardKeyView.ICON_GRID_MENU);
        menuBtn.setInsetsDp(3f, 4f);
        menuBtn.setOnClickListener(v -> switchToView(VIEW_TOOLS_MENU));
        headerContainer.addView(menuBtn, new LinearLayout.LayoutParams(dp(44), dp(46)));

        boolean strictGuard = GboardTheme.isStrictAntiErrorEnabled(this);
        List<String> suggestions = PortugueseSuggestions.getSuggestions(currentWord, strictGuard);
        String leftWord = suggestions.size() > 0 ? suggestions.get(0) : currentWord;
        String centerWord = suggestions.size() > 1 ? suggestions.get(1) : currentWord;
        String rightWord = suggestions.size() > 2 ? suggestions.get(2) : "";

        boolean centerIsFix = !centerWord.equals(currentWord);

        headerContainer.addView(buildSuggestionChip(leftWord, false, currentWord), weightedParams(1f, dp(44)));
        headerContainer.addView(buildVerticalDivider(), new LinearLayout.LayoutParams(dp(1), dp(22)));
        headerContainer.addView(buildSuggestionChip(centerWord, centerIsFix, currentWord), weightedParams(1.2f, dp(44)));
        headerContainer.addView(buildVerticalDivider(), new LinearLayout.LayoutParams(dp(1), dp(22)));
        headerContainer.addView(buildSuggestionChip(rightWord, false, currentWord), weightedParams(1f, dp(44)));
    }

    private View buildSuggestionChip(String suggestion, boolean highlightPrimary, String currentWord) {
        TextView tv = new TextView(this);
        tv.setText(suggestion);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, highlightPrimary ? 15.5f : 14f);
        tv.setTypeface(Typeface.create(highlightPrimary ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        if (highlightPrimary && theme.isGamerRgb) {
            tv.setTextColor(Color.parseColor("#00E5FF"));
        } else {
            tv.setTextColor(highlightPrimary ? theme.keyText : theme.hintText);
        }
        tv.setPadding(dp(6), 0, dp(6), 0);

        if (suggestion != null && !suggestion.isEmpty()) {
            tv.setOnClickListener(v -> applySuggestedWord(currentWord, suggestion));
        }
        return tv;
    }

    private View buildVerticalDivider() {
        View v = new View(this);
        v.setBackgroundColor(theme.dividerColor);
        return v;
    }

    private void buildPanelHeader(String title, View rightActionView, Runnable onBack) {
        headerContainer.setPadding(dp(4), 0, dp(8), 0);

        GboardKeyView backBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        backBtn.setIcon(GboardKeyView.ICON_BACK_ARROW);
        backBtn.setInsetsDp(3f, 4f);
        backBtn.setOnClickListener(v -> onBack.run());

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setTextColor(theme.keyText);
        titleTv.setPadding(dp(10), 0, dp(8), 0);

        headerContainer.addView(backBtn, new LinearLayout.LayoutParams(dp(44), dp(46)));
        headerContainer.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (rightActionView != null) {
            headerContainer.addView(rightActionView, new LinearLayout.LayoutParams(dp(116), dp(42)));
        }
    }

    private int scaledRowHeight(int baseDp) {
        float mult = GboardTheme.getHeightMultiplier(this);
        return dp(Math.round(baseDp * mult));
    }

    private void buildLettersBody() {
        boolean isUpper = (shiftState != SHIFT_OFF);
        boolean showNumRow = GboardTheme.isNumberRowEnabled(this);
        int rowHeight = scaledRowHeight(showNumRow ? 48 : 56);

        if (showNumRow) {
            LinearLayout numRow = createRow();
            for (String d : row1Hints) {
                numRow.addView(createSymbolKey(d, 18.5f), weightedParams(1f, dp(42)));
            }
            bodyContainer.addView(numRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        }

        // Row 1: q..p
        LinearLayout r1 = createRow();
        for (int i = 0; i < lowerLetterRows[0].length; i++) {
            String base = lowerLetterRows[0][i];
            String display = isUpper ? base.toUpperCase(PT_BR) : base;
            GboardKeyView k = createLetterKey(display);
            if (!showNumRow) {
                k.setTopRightHint(row1Hints[i]);
            }
            k.setPopupVariants(getVariantsForLetter(base, isUpper), this::commitLetterText);
            r1.addView(k, weightedParams(1f, rowHeight));
        }
        bodyContainer.addView(r1, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight));

        // Row 2: a..l
        LinearLayout r2 = createRow();
        r2.addView(new Space(this), weightedParams(0.5f, rowHeight));
        for (String base : lowerLetterRows[1]) {
            String display = isUpper ? base.toUpperCase(PT_BR) : base;
            GboardKeyView k = createLetterKey(display);
            String[] variants = getVariantsForLetter(base, isUpper);
            if (variants != null) {
                k.setPopupVariants(variants, this::commitLetterText);
            }
            r2.addView(k, weightedParams(1f, rowHeight));
        }
        r2.addView(new Space(this), weightedParams(0.5f, rowHeight));
        bodyContainer.addView(r2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight));

        // Row 3: Shift + z..m + Backspace
        LinearLayout r3 = createRow();

        GboardKeyView shiftKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        shiftKey.setIcon(shiftState == SHIFT_CAPS_LOCK ? GboardKeyView.ICON_CAPS_LOCK : GboardKeyView.ICON_SHIFT);
        shiftKey.setActiveState(shiftState != SHIFT_OFF);
        shiftKey.setInsetsDp(3f, 4f);
        shiftKey.setOnClickListener(v -> handleShiftTap());
        r3.addView(shiftKey, weightedParams(1.5f, rowHeight));

        for (String base : lowerLetterRows[2]) {
            String display = isUpper ? base.toUpperCase(PT_BR) : base;
            GboardKeyView k = createLetterKey(display);
            String[] variants = getVariantsForLetter(base, isUpper);
            if (variants != null) {
                k.setPopupVariants(variants, this::commitLetterText);
            }
            r3.addView(k, weightedParams(1f, rowHeight));
        }

        r3.addView(createBackspaceKey(), weightedParams(1.5f, rowHeight));
        bodyContainer.addView(r3, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight));

        // Row 4: Exact Gboard bottom row (?123 | , | Emoji | Space | . | Search/Enter)
        int bh = scaledRowHeight(58);
        LinearLayout r4 = createRow();

        GboardKeyView toSymPill = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC_PILL);
        toSymPill.setLabel("?123");
        toSymPill.setTextSizeSp(14.5f);
        toSymPill.setInsetsDp(3f, 4.5f);
        toSymPill.setOnClickListener(v -> switchToView(VIEW_SYMBOLS_P1));

        GboardKeyView commaKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        commaKey.setLabel(",");
        commaKey.setTextSizeSp(21f);
        commaKey.setInsetsDp(3f, 4.5f);
        commaKey.setPopupVariants(new String[]{"-", "'", "\"", ";"}, this::commitPunctuationWithAutoCorrect);
        commaKey.setOnClickListener(v -> commitPunctuationWithAutoCorrect(","));

        GboardKeyView emojiKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        emojiKey.setIcon(GboardKeyView.ICON_EMOJI);
        emojiKey.setInsetsDp(3f, 4.5f);
        emojiKey.setOnClickListener(v -> {
            emojiSearchMode = false;
            switchToView(VIEW_EMOJI);
        });
        emojiKey.setOnLongClickListener(v -> {
            showInputMethodSelector();
            return true;
        });

        GboardKeyView spaceKey = createSpaceKey();

        GboardKeyView dotKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        dotKey.setLabel(".");
        dotKey.setTextSizeSp(21f);
        dotKey.setInsetsDp(3f, 4.5f);
        dotKey.setPopupVariants(new String[]{"?", "!", "-", ":", ";", "/", "@"}, this::commitPunctuationWithAutoCorrect);
        dotKey.setOnClickListener(v -> commitPunctuationWithAutoCorrect("."));

        GboardKeyView actionPill = createActionPill();

        r4.addView(toSymPill, weightedParams(1.5f, bh));
        r4.addView(commaKey, weightedParams(0.95f, bh));
        r4.addView(emojiKey, weightedParams(0.95f, bh));
        r4.addView(spaceKey, weightedParams(4.15f, bh));
        r4.addView(dotKey, weightedParams(0.95f, bh));
        r4.addView(actionPill, weightedParams(1.5f, bh));

        bodyContainer.addView(r4, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, bh));
    }

    private String[] getVariantsForLetter(String lowerLetter, boolean upper) {
        String[] raw;
        switch (lowerLetter) {
            case "a":
                raw = new String[]{"á", "ã", "â", "à", "ª"};
                break;
            case "e":
                raw = new String[]{"é", "ê", "3", "è"};
                break;
            case "i":
                raw = new String[]{"í", "8", "ì", "î"};
                break;
            case "o":
                raw = new String[]{"ó", "õ", "ô", "9", "ò", "º"};
                break;
            case "u":
                raw = new String[]{"ú", "7", "ü", "ù", "û"};
                break;
            case "c":
                raw = new String[]{"ç"};
                break;
            case "n":
                raw = new String[]{"ñ"};
                break;
            case "q":
                return new String[]{"1"};
            case "w":
                return new String[]{"2"};
            case "r":
                return new String[]{"4"};
            case "t":
                return new String[]{"5"};
            case "y":
                return new String[]{"6"};
            case "p":
                return new String[]{"0"};
            default:
                return null;
        }
        if (!upper) return raw;
        String[] up = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String item = raw[i];
            up[i] = (item.equals("ª") || item.equals("º")) ? item : item.toUpperCase(PT_BR);
        }
        return up;
    }

    private void handleShiftTap() {
        long now = SystemClock.uptimeMillis();
        if (shiftState == SHIFT_SINGLE && (now - lastShiftTapMs) < 380L) {
            shiftState = SHIFT_CAPS_LOCK;
        } else if (shiftState == SHIFT_OFF) {
            shiftState = SHIFT_SINGLE;
        } else {
            shiftState = SHIFT_OFF;
        }
        lastShiftTapMs = now;
        renderCurrentView();
    }

    private void buildSymbolsPageBody(boolean page1) {
        String[][] rows = page1 ? symbolsPage1Rows : symbolsPage2Rows;
        int rowHeight = scaledRowHeight(56);

        for (int r = 0; r < 2; r++) {
            LinearLayout row = createRow();
            for (String s : rows[r]) {
                row.addView(createSymbolKey(s, 21f), weightedParams(1f, rowHeight));
            }
            bodyContainer.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight));
        }

        LinearLayout r3 = createRow();
        GboardKeyView subPageBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        subPageBtn.setLabel(page1 ? "=\\<" : "?123");
        subPageBtn.setTextSizeSp(14f);
        subPageBtn.setInsetsDp(3f, 4.5f);
        subPageBtn.setOnClickListener(v -> switchToView(page1 ? VIEW_SYMBOLS_P2 : VIEW_SYMBOLS_P1));
        r3.addView(subPageBtn, weightedParams(1.5f, rowHeight));

        for (String s : rows[2]) {
            r3.addView(createSymbolKey(s, 20.5f), weightedParams(1f, rowHeight));
        }

        r3.addView(createBackspaceKey(), weightedParams(1.5f, rowHeight));
        bodyContainer.addView(r3, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight));

        int bh = scaledRowHeight(58);
        LinearLayout r4 = createRow();

        GboardKeyView abcPill = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC_PILL);
        abcPill.setLabel("ABC");
        abcPill.setTextSizeSp(14.5f);
        abcPill.setInsetsDp(3f, 4.5f);
        abcPill.setOnClickListener(v -> switchToView(VIEW_LETTERS));

        String leftSym = page1 ? "," : "<";
        GboardKeyView leftKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        leftKey.setLabel(leftSym);
        leftKey.setTextSizeSp(21f);
        leftKey.setInsetsDp(3f, 4.5f);
        leftKey.setOnClickListener(v -> commitRawText(leftSym));

        GboardKeyView numpadKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        numpadKey.setStackedLabels("1 2", "3 4");
        numpadKey.setInsetsDp(3f, 4.5f);
        numpadKey.setOnClickListener(v -> switchToView(VIEW_NUMPAD_3X4));

        GboardKeyView spaceKey = createSpaceKey();

        String rightSym = page1 ? "." : ">";
        GboardKeyView rightKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        rightKey.setLabel(rightSym);
        rightKey.setTextSizeSp(21f);
        rightKey.setInsetsDp(3f, 4.5f);
        rightKey.setOnClickListener(v -> commitRawText(rightSym));

        GboardKeyView actionPill = createActionPill();

        r4.addView(abcPill, weightedParams(1.5f, bh));
        r4.addView(leftKey, weightedParams(0.95f, bh));
        r4.addView(numpadKey, weightedParams(0.95f, bh));
        r4.addView(spaceKey, weightedParams(4.15f, bh));
        r4.addView(rightKey, weightedParams(0.95f, bh));
        r4.addView(actionPill, weightedParams(1.5f, bh));
        bodyContainer.addView(r4, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, bh));
    }

    private void buildNumpad3x4Body() {
        int rH = scaledRowHeight(56);
        int gridHeight = rH * 3;
        LinearLayout topSection = createRow();

        LinearLayout opStripWrapper = new LinearLayout(this);
        opStripWrapper.setPadding(dp(3), dp(4), dp(3), dp(4));
        LinearLayout opStrip = new LinearLayout(this);
        opStrip.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable stripBg = new GradientDrawable();
        stripBg.setColor(theme.funcKeyBg);
        stripBg.setCornerRadius(dp(8));
        if (theme.isGamerRgb) {
            stripBg.setStroke(dp(1), Color.parseColor("#00E5FF"));
        }
        opStrip.setBackground(stripBg);

        String[] ops = {"+", "-", "*", "/"};
        for (String op : ops) {
            GboardKeyView opKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FLAT_STRIP);
            opKey.setLabel(op);
            opKey.setTextSizeSp(19f);
            opKey.setInsetsDp(2f, 2f);
            opKey.setOnClickListener(v -> commitRawText(op));
            opStrip.addView(opKey, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        opStripWrapper.addView(opStrip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        ));
        topSection.addView(opStripWrapper, weightedParams(1.45f, gridHeight));

        LinearLayout rightArea = new LinearLayout(this);
        rightArea.setOrientation(LinearLayout.VERTICAL);

        String[][] padDigits = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = createRow();
            for (int c = 0; c < 3; c++) {
                row.addView(createSymbolKey(padDigits[r][c], 25f), weightedParams(2.35f, rH));
            }
            if (r == 0) {
                GboardKeyView pctKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
                pctKey.setLabel("%");
                pctKey.setTextSizeSp(19f);
                pctKey.setInsetsDp(3f, 4.5f);
                pctKey.setOnClickListener(v -> commitRawText("%"));
                row.addView(pctKey, weightedParams(1.5f, rH));
            } else if (r == 1) {
                GboardKeyView spIconKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
                spIconKey.setIcon(GboardKeyView.ICON_SPACE_BAR);
                spIconKey.setInsetsDp(3f, 4.5f);
                spIconKey.setOnClickListener(v -> commitRawText(" "));
                row.addView(spIconKey, weightedParams(1.5f, rH));
            } else {
                row.addView(createBackspaceKey(), weightedParams(1.5f, rH));
            }
            rightArea.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rH));
        }
        topSection.addView(rightArea, weightedParams(8.55f, gridHeight));
        bodyContainer.addView(topSection, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, gridHeight));

        int bh = scaledRowHeight(58);
        LinearLayout r4 = createRow();

        GboardKeyView abcPill = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC_PILL);
        abcPill.setLabel("ABC");
        abcPill.setTextSizeSp(14.5f);
        abcPill.setInsetsDp(3f, 4.5f);
        abcPill.setOnClickListener(v -> switchToView(VIEW_LETTERS));

        GboardKeyView commaKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        commaKey.setLabel(",");
        commaKey.setTextSizeSp(21f);
        commaKey.setInsetsDp(3f, 4.5f);
        commaKey.setOnClickListener(v -> commitRawText(","));

        GboardKeyView symKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        symKey.setLabel("!?#");
        symKey.setTextSizeSp(15.5f);
        symKey.setInsetsDp(3f, 4.5f);
        symKey.setOnClickListener(v -> switchToView(VIEW_SYMBOLS_P1));

        GboardKeyView zeroKey = createSymbolKey("0", 25f);
        GboardKeyView eqKey = createSymbolKey("=", 21f);

        GboardKeyView dotKey = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        dotKey.setLabel(".");
        dotKey.setTextSizeSp(21f);
        dotKey.setInsetsDp(3f, 4.5f);
        dotKey.setOnClickListener(v -> commitRawText("."));

        GboardKeyView actionPill = createActionPill();

        r4.addView(abcPill, weightedParams(1.45f, bh));
        r4.addView(commaKey, weightedParams(0.90f, bh));
        r4.addView(symKey, weightedParams(1.30f, bh));
        r4.addView(zeroKey, weightedParams(2.55f, bh));
        r4.addView(eqKey, weightedParams(1.35f, bh));
        r4.addView(dotKey, weightedParams(0.95f, bh));
        r4.addView(actionPill, weightedParams(1.50f, bh));
        bodyContainer.addView(r4, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, bh));
    }

    /**
     * Categorized 1,898-Emoji Panel + Portuguese Search + Clássicos Emoticons matching gboard_06 & gboard_07.
     */
    private void buildEmojiHeader() {
        headerContainer.setPadding(dp(4), 0, dp(4), 0);

        GboardKeyView backBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        backBtn.setIcon(GboardKeyView.ICON_BACK_ARROW);
        backBtn.setInsetsDp(3f, 4f);
        backBtn.setOnClickListener(v -> {
            if (emojiSearchMode) {
                emojiSearchMode = false;
                emojiSearchQuery.setLength(0);
                renderCurrentView();
            } else {
                switchToView(VIEW_LETTERS);
            }
        });
        headerContainer.addView(backBtn, new LinearLayout.LayoutParams(dp(42), dp(44)));

        // Search pill matching gboard_06_emoji.png
        GboardKeyView searchPill = new GboardKeyView(
                this,
                theme,
                emojiSearchMode ? GboardKeyView.STYLE_FUNC_PILL : GboardKeyView.STYLE_TOOLBAR_CIRCLE
        );
        searchPill.setLabel(emojiSearchMode
                ? (emojiSearchQuery.length() == 0 ? "Digite..." : emojiSearchQuery.toString())
                : "Pesquisar");
        searchPill.setTextSizeSp(12.5f);
        searchPill.setInsetsDp(3f, 5f);
        searchPill.setOnClickListener(v -> {
            emojiSearchMode = !emojiSearchMode;
            emojiEmoticonTab = false;
            if (!emojiSearchMode) emojiSearchQuery.setLength(0);
            renderCurrentView();
        });
        headerContainer.addView(searchPill, new LinearLayout.LayoutParams(dp(96), dp(44)));

        if (emojiEmoticonTab) {
            TextView emTitle = new TextView(this);
            emTitle.setText("Emoticons clássicos");
            emTitle.setTextColor(theme.keyText);
            emTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            emTitle.setPadding(dp(10), 0, 0, 0);
            headerContainer.addView(emTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            return;
        }

        // Scrollable 10-Category Icon Strip (🕒 Recentes + 9 Official Categories)
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout catStrip = createRow();

        // Recent tab (-1)
        GboardKeyView recentTab = new GboardKeyView(
                this,
                theme,
                (!emojiSearchMode && currentEmojiCategory == EmojiRepository.CAT_RECENT)
                        ? GboardKeyView.STYLE_TONAL_CIRCLE : GboardKeyView.STYLE_TOOLBAR_ICON
        );
        recentTab.setIcon(GboardKeyView.ICON_CLOCK);
        recentTab.setInsetsDp(3f, 4f);
        recentTab.setOnClickListener(v -> {
            emojiSearchMode = false;
            currentEmojiCategory = EmojiRepository.CAT_RECENT;
            renderCurrentView();
        });
        catStrip.addView(recentTab, new LinearLayout.LayoutParams(dp(40), dp(42)));

        for (int c = 0; c < EmojiRepository.CAT_COUNT; c++) {
            final int catIdx = c;
            boolean sel = (!emojiSearchMode && currentEmojiCategory == catIdx);
            GboardKeyView tab = new GboardKeyView(
                    this,
                    theme,
                    sel ? GboardKeyView.STYLE_TONAL_CIRCLE : GboardKeyView.STYLE_TOOLBAR_ICON
            );
            tab.setLabel(EmojiRepository.CATEGORY_ICONS[c]);
            tab.setTextSizeSp(17f);
            tab.setInsetsDp(2f, 4f);
            tab.setOnClickListener(v -> {
                emojiSearchMode = false;
                currentEmojiCategory = catIdx;
                renderCurrentView();
            });
            catStrip.addView(tab, new LinearLayout.LayoutParams(dp(40), dp(42)));
        }

        hScroll.addView(catStrip);
        headerContainer.addView(hScroll, new LinearLayout.LayoutParams(0, dp(44), 1f));
    }

    private void buildEmojiBody() {
        if (emojiEmoticonTab) {
            int rowH = dp(42);
            for (String[] rItems : emoticonRows) {
                LinearLayout row = createRow();
                for (String item : rItems) {
                    GboardKeyView k = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
                    k.setLabel(item);
                    k.setTextSizeSp(15f);
                    k.setInsetsDp(2f, 2f);
                    k.setOnClickListener(v -> commitRawText(item));
                    row.addView(k, weightedParams(1f, rowH));
                }
                bodyContainer.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH));
            }
            bodyContainer.addView(buildEmojiBottomBar(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
            return;
        }

        List<String> emojis = emojiSearchMode
                ? EmojiRepository.search(emojiSearchQuery.toString())
                : EmojiRepository.getCategoryEmojis(this, currentEmojiCategory);

        String catTitle = emojiSearchMode
                ? ("Resultados (" + emojis.size() + ")")
                : (currentEmojiCategory == EmojiRepository.CAT_RECENT
                ? "Emojis recentes"
                : EmojiRepository.CATEGORY_NAMES[currentEmojiCategory]);

        TextView catLabel = new TextView(this);
        catLabel.setText(catTitle);
        catLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        catLabel.setTextColor(theme.hintText);
        catLabel.setPadding(dp(12), dp(2), dp(12), dp(2));
        bodyContainer.addView(catLabel);

        int listH = emojiSearchMode ? dp(78) : dp(152);
        ListView gridList = new ListView(this);
        gridList.setDivider(null);
        gridList.setDividerHeight(0);
        gridList.setSelector(android.R.color.transparent);
        gridList.setAdapter(new EmojiRowAdapter(emojis, 8));
        bodyContainer.addView(gridList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                listH
        ));

        if (emojiSearchMode) {
            // Compact mini QWERTY search keyboard right below emoji results!
            int miniH = dp(34);
            for (String[] rowLetters : lowerLetterRows) {
                LinearLayout r = createRow();
                for (String ch : rowLetters) {
                    GboardKeyView k = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
                    k.setLabel(ch);
                    k.setTextSizeSp(14.5f);
                    k.setInsetsDp(1.8f, 2f);
                    k.setOnClickListener(v -> {
                        emojiSearchQuery.append(ch);
                        renderCurrentView();
                    });
                    r.addView(k, weightedParams(1f, miniH));
                }
                if (rowLetters.length == 7) {
                    GboardKeyView bs = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
                    bs.setIcon(GboardKeyView.ICON_BACKSPACE);
                    bs.setInsetsDp(2f, 2f);
                    bs.setOnClickListener(v -> {
                        if (emojiSearchQuery.length() > 0) {
                            emojiSearchQuery.deleteCharAt(emojiSearchQuery.length() - 1);
                            renderCurrentView();
                        }
                    });
                    r.addView(bs, weightedParams(1.6f, miniH));
                }
                bodyContainer.addView(r, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, miniH));
            }
            return;
        }

        bodyContainer.addView(buildEmojiBottomBar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ));
    }

    private LinearLayout buildEmojiBottomBar() {
        int bh = dp(52);
        LinearLayout bBar = createRow();

        GboardKeyView abcBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
        abcBtn.setLabel("ABC");
        abcBtn.setTextSizeSp(14.5f);
        abcBtn.setOnClickListener(v -> switchToView(VIEW_LETTERS));

        GboardKeyView emojiTabBtn = new GboardKeyView(this, theme,
                !emojiEmoticonTab ? GboardKeyView.STYLE_FUNC_PILL : GboardKeyView.STYLE_TOOLBAR_ICON);
        emojiTabBtn.setIcon(GboardKeyView.ICON_EMOJI);
        emojiTabBtn.setActiveState(!emojiEmoticonTab);
        emojiTabBtn.setInsetsDp(6f, 6f);
        emojiTabBtn.setOnClickListener(v -> {
            emojiEmoticonTab = false;
            renderCurrentView();
        });

        GboardKeyView emoticonTabBtn = new GboardKeyView(this, theme,
                emojiEmoticonTab ? GboardKeyView.STYLE_FUNC_PILL : GboardKeyView.STYLE_TOOLBAR_ICON);
        emoticonTabBtn.setLabel(":-)");
        emoticonTabBtn.setTextSizeSp(14.5f);
        emoticonTabBtn.setActiveState(emojiEmoticonTab);
        emoticonTabBtn.setInsetsDp(6f, 6f);
        emoticonTabBtn.setOnClickListener(v -> {
            emojiEmoticonTab = true;
            emojiSearchMode = false;
            renderCurrentView();
        });

        GboardKeyView bsBtn = createBackspaceKey();

        bBar.addView(abcBtn, weightedParams(1.4f, bh));
        bBar.addView(emojiTabBtn, weightedParams(1.8f, bh));
        bBar.addView(emoticonTabBtn, weightedParams(1.8f, bh));
        bBar.addView(new Space(this), weightedParams(1.6f, bh));
        bBar.addView(bsBtn, weightedParams(1.4f, bh));
        return bBar;
    }

    private final class EmojiRowAdapter extends BaseAdapter {
        private final List<String> items;
        private final int cols;

        EmojiRowAdapter(List<String> items, int cols) {
            this.items = items != null ? items : Collections.emptyList();
            this.cols = cols;
        }

        @Override
        public int getCount() {
            return (items.size() + cols - 1) / cols;
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = createRow();
                row.setLayoutParams(new ListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
                ));
                for (int c = 0; c < cols; c++) {
                    TextView cell = new TextView(UnicodeKeyboardService.this);
                    cell.setGravity(Gravity.CENTER);
                    cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
                    cell.setTextColor(Color.WHITE);
                    row.addView(cell, weightedParams(1f, dp(42)));
                }
            }

            int baseIdx = position * cols;
            for (int c = 0; c < cols; c++) {
                TextView cell = (TextView) row.getChildAt(c);
                int idx = baseIdx + c;
                if (idx < items.size()) {
                    final String em = items.get(idx);
                    cell.setText(em);
                    cell.setVisibility(View.VISIBLE);
                    cell.setOnClickListener(v -> {
                        EmojiRepository.recordRecentEmoji(UnicodeKeyboardService.this, em);
                        commitRawText(em);
                    });
                } else {
                    cell.setText("");
                    cell.setVisibility(View.INVISIBLE);
                    cell.setOnClickListener(null);
                }
            }
            return row;
        }
    }

    /**
     * Exact 2-Page Gboard Tools Menu matching gboard_08_tools_menu.png & gboard_09_tools_menu_page2.png!
     */
    private void buildToolsMenuBody() {
        bodyContainer.setPadding(dp(10), dp(4), dp(10), dp(4));
        int rowH = dp(58);

        if (toolsMenuPage == 0) {
            // Page 1:
            // Row 1: Área de transferência | Traduzir
            LinearLayout r1 = createRow();
            r1.addView(createToolCard(GboardKeyView.ICON_CLIPBOARD, "Área de transferência",
                    () -> switchToView(VIEW_CLIPBOARD)), weightedParams(1f, rowH));
            r1.addView(createToolCard(GboardKeyView.ICON_TRANSLATE, "Traduzir",
                    () -> {
                        translateModeActive = true;
                        switchToView(VIEW_LETTERS);
                    }), weightedParams(1f, rowH));
            bodyContainer.addView(r1, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH));

            // Row 2: Emoji | Anti-Friendzone
            LinearLayout r2 = createRow();
            r2.addView(createToolCard(GboardKeyView.ICON_EMOJI, "Emoji",
                    () -> switchToView(VIEW_EMOJI)), weightedParams(1f, rowH));
            r2.addView(createToolCard(GboardKeyView.ICON_HEART_SPARK, "Anti-Friendzone",
                    () -> switchToView(VIEW_ANTI_FRIENDZONE)), weightedParams(1f, rowH));
            bodyContainer.addView(r2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH));

            // Row 3: Fontes (Aa) | Redimensionar
            LinearLayout r3 = createRow();
            r3.addView(createToolCard(GboardKeyView.ICON_FONTS, "Fontes especiais",
                    () -> switchToView(VIEW_FONTS)), weightedParams(1f, rowH));
            r3.addView(createToolCard(GboardKeyView.ICON_RESIZE, "Redimensionar",
                    () -> switchToView(VIEW_RESIZE)), weightedParams(1f, rowH));
            bodyContainer.addView(r3, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH));
        } else {
            // Page 2:
            boolean strictOn = GboardTheme.isStrictAntiErrorEnabled(this);

            // Row 1: Texto p/ Fala (TTS) | Anti-Erro Dinâmico
            LinearLayout r1 = createRow();
            r1.addView(createToolCard(GboardKeyView.ICON_SPEAKER_TTS, "Texto p/ fala",
                    this::speakCurrentTextAloud), weightedParams(1f, rowH));
            r1.addView(createToolCard(GboardKeyView.ICON_SPELLCHECK,
                    strictOn ? "Anti-Erro: Ativo" : "Anti-Erro: Inativo",
                    () -> {
                        GboardTheme.setStrictAntiErrorEnabled(this, !strictOn);
                        renderCurrentView();
                    }), weightedParams(1f, rowH));
            bodyContainer.addView(r1, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH));

            // Row 2: Tema | Uma mão só
            LinearLayout r2 = createRow();
            r2.addView(createToolCard(GboardKeyView.ICON_PALETTE, "Tema (PC Gamer RGB)",
                    () -> switchToView(VIEW_THEMES)), weightedParams(1f, rowH));
            r2.addView(createToolCard(GboardKeyView.ICON_ONE_HANDED,
                    oneHandState == ONE_HAND_OFF ? "Uma mão só" : "Tela cheia",
                    () -> {
                        oneHandState = (oneHandState == ONE_HAND_OFF) ? ONE_HAND_RIGHT : ONE_HAND_OFF;
                        currentView = previousTypingView;
                        rebuildOneHandWrapper();
                    }), weightedParams(1f, rowH));
            bodyContainer.addView(r2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH));

            // Row 3: Edição de texto | Configurações
            LinearLayout r3 = createRow();
            r3.addView(createToolCard(GboardKeyView.ICON_TEXT_EDIT, "Edição de texto",
                    () -> switchToView(VIEW_TEXT_EDIT)), weightedParams(1f, rowH));
            r3.addView(createToolCard(GboardKeyView.ICON_SETTINGS, "Configurações",
                    this::openSettingsActivity), weightedParams(1f, rowH));
            bodyContainer.addView(r3, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH));
        }

        // Bottom 2-page indicator bar (• ◦) matching gboard_08 & gboard_09
        LinearLayout pagerBar = createRow();
        pagerBar.setGravity(Gravity.CENTER);

        GboardKeyView p1Btn = new GboardKeyView(this, theme,
                toolsMenuPage == 0 ? GboardKeyView.STYLE_FUNC_PILL : GboardKeyView.STYLE_TOOLBAR_ICON);
        p1Btn.setLabel("● Página 1");
        p1Btn.setTextSizeSp(12f);
        p1Btn.setInsetsDp(4f, 5f);
        p1Btn.setOnClickListener(v -> {
            toolsMenuPage = 0;
            renderCurrentView();
        });

        GboardKeyView p2Btn = new GboardKeyView(this, theme,
                toolsMenuPage == 1 ? GboardKeyView.STYLE_FUNC_PILL : GboardKeyView.STYLE_TOOLBAR_ICON);
        p2Btn.setLabel("● Página 2");
        p2Btn.setTextSizeSp(12f);
        p2Btn.setInsetsDp(4f, 5f);
        p2Btn.setOnClickListener(v -> {
            toolsMenuPage = 1;
            renderCurrentView();
        });

        pagerBar.addView(p1Btn, new LinearLayout.LayoutParams(dp(110), dp(42)));
        pagerBar.addView(new Space(this), new LinearLayout.LayoutParams(dp(10), dp(42)));
        pagerBar.addView(p2Btn, new LinearLayout.LayoutParams(dp(110), dp(42)));
        bodyContainer.addView(pagerBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
    }

    private GboardKeyView createToolCard(int icon, String title, Runnable action) {
        GboardKeyView card = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOL_CARD);
        card.setIcon(icon);
        card.setLabel(title);
        card.setInsetsDp(4.5f, 4.5f);
        card.setOnClickListener(v -> action.run());
        return card;
    }

    /**
     * Clipboard with Padlock Protection (🔒 Fixar/Impedir exclusão) and Delete (🗑️) matching gboard_14_clipboard.png!
     */
    private void buildClipboardHeader() {
        headerContainer.setPadding(dp(4), 0, dp(6), 0);

        GboardKeyView backBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        backBtn.setIcon(GboardKeyView.ICON_BACK_ARROW);
        backBtn.setInsetsDp(3f, 4f);
        backBtn.setOnClickListener(v -> switchToView(previousTypingView));

        TextView titleTv = new TextView(this);
        titleTv.setText("Área de transferência");
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setTextColor(theme.keyText);
        titleTv.setPadding(dp(8), 0, dp(4), 0);

        boolean enabled = ClipboardStore.isEnabled(this);
        GboardKeyView toggleBtn = new GboardKeyView(this, theme,
                enabled ? GboardKeyView.STYLE_FUNC_PILL : GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        toggleBtn.setLabel(enabled ? "Ativa" : "Pausada");
        toggleBtn.setTextSizeSp(11.5f);
        toggleBtn.setInsetsDp(2f, 6f);
        toggleBtn.setOnClickListener(v -> {
            ClipboardStore.setEnabled(this, !enabled);
            renderCurrentView();
        });

        GboardKeyView clearUnlockedBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_CIRCLE);
        clearUnlockedBtn.setIcon(GboardKeyView.ICON_TRASH);
        clearUnlockedBtn.setInsetsDp(3f, 4f);
        clearUnlockedBtn.setOnClickListener(v -> {
            int removed = ClipboardStore.clearUnlocked(this);
            Toast.makeText(this,
                    removed > 0
                            ? (removed + " itens apagados (itens com cadeado foram mantidos)")
                            : "Nenhum item desbloqueado para apagar",
                    Toast.LENGTH_SHORT).show();
            renderCurrentView();
        });

        headerContainer.addView(backBtn, new LinearLayout.LayoutParams(dp(42), dp(44)));
        headerContainer.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerContainer.addView(toggleBtn, new LinearLayout.LayoutParams(dp(74), dp(42)));
        headerContainer.addView(clearUnlockedBtn, new LinearLayout.LayoutParams(dp(42), dp(44)));
    }

    private void buildClipboardBody() {
        List<ClipboardStore.ClipEntry> entries = ClipboardStore.loadEntries(this);
        if (entries.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("Copie qualquer texto para vê-lo aqui.\nUse o cadeado para impedir a exclusão.");
            emptyTv.setTextColor(theme.hintText);
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            emptyTv.setGravity(Gravity.CENTER);
            bodyContainer.addView(emptyTv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(220)
            ));
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(2), dp(8), dp(8));

        boolean hasLocked = false;
        boolean hasUnlocked = false;
        for (ClipboardStore.ClipEntry e : entries) {
            if (e.locked) hasLocked = true;
            else hasUnlocked = true;
        }

        if (hasLocked) {
            list.addView(buildClipSectionTitle("Fixados (Protegidos com cadeado)"));
            for (ClipboardStore.ClipEntry e : entries) {
                if (e.locked) list.addView(buildInteractiveClipRow(e));
            }
        }
        if (hasUnlocked) {
            list.addView(buildClipSectionTitle("Recentes"));
            for (ClipboardStore.ClipEntry e : entries) {
                if (!e.locked) list.addView(buildInteractiveClipRow(e));
            }
        }

        scroll.addView(list);
        bodyContainer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(224)
        ));
    }

    private TextView buildClipSectionTitle(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setTextColor(theme.hintText);
        tv.setPadding(dp(6), dp(4), dp(6), dp(2));
        return tv;
    }

    private View buildInteractiveClipRow(ClipboardStore.ClipEntry entry) {
        LinearLayout outer = new LinearLayout(this);
        outer.setPadding(dp(2), dp(3), dp(2), dp(3));

        LinearLayout card = createRow();
        card.setPadding(dp(12), dp(6), dp(6), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.keyBg);
        bg.setCornerRadius(dp(12));
        if (entry.locked) {
            bg.setStroke(dp(1), theme.isGamerRgb ? Color.parseColor("#FF00AA") : theme.funcKeyActiveBg);
        } else if (theme.isGamerRgb) {
            bg.setStroke(dp(1), Color.parseColor("#00E5FF"));
        }
        card.setBackground(bg);

        TextView tv = new TextView(this);
        tv.setText(entry.text);
        tv.setTextColor(theme.keyText);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setOnClickListener(v -> commitRawText(entry.text));

        GboardKeyView lockBtn = new GboardKeyView(this, theme,
                entry.locked ? GboardKeyView.STYLE_FUNC : GboardKeyView.STYLE_TOOLBAR_ICON);
        lockBtn.setIcon(entry.locked ? GboardKeyView.ICON_LOCK_CLOSED : GboardKeyView.ICON_LOCK_OPEN);
        lockBtn.setActiveState(entry.locked);
        lockBtn.setInsetsDp(2f, 2f);
        lockBtn.setOnClickListener(v -> {
            ClipboardStore.toggleLock(this, entry.text);
            renderCurrentView();
        });

        GboardKeyView deleteBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
        deleteBtn.setIcon(GboardKeyView.ICON_TRASH);
        deleteBtn.setInsetsDp(2f, 2f);
        deleteBtn.setOnClickListener(v -> {
            if (entry.locked) {
                Toast.makeText(this, "Item protegido por cadeado! Destrave o cadeado para excluir.", Toast.LENGTH_SHORT).show();
            } else {
                ClipboardStore.deleteEntry(this, entry.text);
                renderCurrentView();
            }
        });

        card.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(lockBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));
        card.addView(deleteBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));

        outer.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return outer;
    }

    /**
     * Anti-Friendzone Quick Messages Panel (5 categories + custom lock/delete!).
     */
    private void buildAntiFriendzoneBody() {
        // Category chips bar
        HorizontalScrollView catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout catRow = createRow();
        catRow.setPadding(dp(6), dp(2), dp(6), dp(2));

        for (int i = 0; i < AntiFriendzoneStore.CATEGORIES.length; i++) {
            final int catIdx = i;
            boolean sel = (currentAfzCategory == catIdx);
            GboardKeyView chip = new GboardKeyView(this, theme,
                    sel ? GboardKeyView.STYLE_FUNC_PILL : GboardKeyView.STYLE_TOOLBAR_CIRCLE);
            chip.setLabel(AntiFriendzoneStore.CATEGORIES[i]);
            chip.setTextSizeSp(12f);
            chip.setActiveState(sel);
            chip.setInsetsDp(3f, 3f);
            chip.setOnClickListener(v -> {
                currentAfzCategory = catIdx;
                renderCurrentView();
            });
            catRow.addView(chip, new LinearLayout.LayoutParams(dp(96), dp(36)));
        }
        catScroll.addView(catRow);
        bodyContainer.addView(catScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
        ));

        List<AntiFriendzoneStore.QuickMsg> msgs = AntiFriendzoneStore.getMessagesForCategory(this, currentAfzCategory);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(2), dp(8), dp(8));

        for (AntiFriendzoneStore.QuickMsg m : msgs) {
            LinearLayout outer = new LinearLayout(this);
            outer.setPadding(dp(2), dp(3), dp(2), dp(3));

            LinearLayout card = createRow();
            card.setPadding(dp(12), dp(8), dp(8), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(theme.keyBg);
            bg.setCornerRadius(dp(12));
            if (theme.isGamerRgb) {
                bg.setStroke(dp(1), m.locked ? Color.parseColor("#FF00AA") : Color.parseColor("#00E5FF"));
            }
            card.setBackground(bg);

            TextView tv = new TextView(this);
            tv.setText(m.text);
            tv.setTextColor(theme.keyText);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            tv.setOnClickListener(v -> commitRawText(m.text));
            card.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (m.isCustom) {
                GboardKeyView lockBtn = new GboardKeyView(this, theme,
                        m.locked ? GboardKeyView.STYLE_FUNC : GboardKeyView.STYLE_TOOLBAR_ICON);
                lockBtn.setIcon(m.locked ? GboardKeyView.ICON_LOCK_CLOSED : GboardKeyView.ICON_LOCK_OPEN);
                lockBtn.setActiveState(m.locked);
                lockBtn.setInsetsDp(2f, 2f);
                lockBtn.setOnClickListener(v -> {
                    AntiFriendzoneStore.toggleCustomLock(this, m.text);
                    renderCurrentView();
                });

                GboardKeyView delBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_TOOLBAR_ICON);
                delBtn.setIcon(GboardKeyView.ICON_TRASH);
                delBtn.setInsetsDp(2f, 2f);
                delBtn.setOnClickListener(v -> {
                    if (!AntiFriendzoneStore.deleteCustomMessage(this, m.text)) {
                        Toast.makeText(this, "Frase protegida com cadeado!", Toast.LENGTH_SHORT).show();
                    } else {
                        renderCurrentView();
                    }
                });

                card.addView(lockBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));
                card.addView(delBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));
            }

            outer.addView(card, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            list.addView(outer);
        }

        scroll.addView(list);
        bodyContainer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(184)
        ));
    }

    private void saveCurrentFieldToAfz() {
        String full = extractFullFieldText();
        if (full.isEmpty()) {
            Toast.makeText(this, "Digite uma frase no campo primeiro para salvá-la aqui!", Toast.LENGTH_SHORT).show();
            return;
        }
        AntiFriendzoneStore.addCustomMessage(this, full);
        currentAfzCategory = 4; // Switch to "⭐ Minhas"
        Toast.makeText(this, "Frase salva em ⭐ Minhas!", Toast.LENGTH_SHORT).show();
        renderCurrentView();
    }

    /**
     * Fonts Panel (8 Unicode typing styles + Keycap typeface).
     */
    private void buildFontsBody() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(8), dp(4), dp(8), dp(8));

        int curStyle = GboardTheme.getTypingFontStyle(this);
        int rowH = dp(52);

        for (int i = 0; i < FontStyler.FONT_NAMES.length; i += 2) {
            LinearLayout row = createRow();
            for (int c = 0; c < 2; c++) {
                int idx = i + c;
                if (idx < FontStyler.FONT_NAMES.length) {
                    final int fontIdx = idx;
                    boolean active = (curStyle == fontIdx);
                    GboardKeyView btn = new GboardKeyView(this, theme,
                            active ? GboardKeyView.STYLE_FUNC_PILL : GboardKeyView.STYLE_CHAR);
                    btn.setLabel(FontStyler.FONT_NAMES[fontIdx]);
                    btn.setTextSizeSp(13.5f);
                    btn.setActiveState(active);
                    btn.setInsetsDp(4f, 4f);
                    btn.setOnClickListener(v -> {
                        GboardTheme.setTypingFontStyle(this, fontIdx);
                        switchToView(VIEW_LETTERS);
                    });
                    row.addView(btn, weightedParams(1f, rowH));
                } else {
                    row.addView(new Space(this), weightedParams(1f, rowH));
                }
            }
            grid.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH));
        }

        scroll.addView(grid);
        bodyContainer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(224)
        ));
    }

    /**
     * Resize Mode matching gboard_16_resize.png.
     */
    private void buildResizeBody() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));

        int curH = GboardTheme.getHeightMode(this);
        String[] labels = {"Altura: Curto", "Altura: Normal", "Altura: Alto"};

        LinearLayout hRow = createRow();
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            boolean sel = (curH == mode);
            GboardKeyView b = new GboardKeyView(this, theme,
                    sel ? GboardKeyView.STYLE_FUNC_PILL : GboardKeyView.STYLE_CHAR);
            b.setLabel(labels[i]);
            b.setTextSizeSp(12.5f);
            b.setActiveState(sel);
            b.setInsetsDp(4f, 6f);
            b.setOnClickListener(v -> {
                GboardTheme.setHeightMode(this, mode);
                renderCurrentView();
            });
            hRow.addView(b, weightedParams(1f, dp(52)));
        }
        box.addView(hRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        box.addView(new Space(this), new LinearLayout.LayoutParams(dp(1), dp(20)));

        LinearLayout actionRow = createRow();
        GboardKeyView resetBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC_PILL);
        resetBtn.setLabel("↺ Redefinir");
        resetBtn.setTextSizeSp(13.5f);
        resetBtn.setInsetsDp(6f, 6f);
        resetBtn.setOnClickListener(v -> {
            GboardTheme.setHeightMode(this, 1);
            renderCurrentView();
        });

        GboardKeyView doneBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_ENTER_PILL);
        doneBtn.setLabel("✓ Concluído");
        doneBtn.setTextSizeSp(13.5f);
        doneBtn.setInsetsDp(6f, 6f);
        doneBtn.setOnClickListener(v -> switchToView(previousTypingView));

        actionRow.addView(resetBtn, weightedParams(1f, dp(52)));
        actionRow.addView(doneBtn, weightedParams(1f, dp(52)));
        box.addView(actionRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        bodyContainer.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(224)
        ));
    }

    /**
     * Themes Body with PC Gamer RGB + Gboard swatches!
     */
    private void buildThemesBody() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(8), dp(4), dp(8), dp(8));

        int rowH = dp(74);
        GboardTheme.ThemeOption[] all = GboardTheme.ALL_THEMES;
        for (int i = 0; i < all.length; i += 3) {
            LinearLayout row = createRow();
            for (int c = 0; c < 3; c++) {
                int idx = i + c;
                if (idx < all.length) {
                    final GboardTheme.ThemeOption opt = all[idx];
                    boolean selected = (theme.themeId == opt.id);
                    View swatch = new ThemeSwatchView(this, opt, selected, theme.keyText, () -> {
                        GboardTheme.saveSelectedTheme(this, opt.id);
                        applyFullThemeReload();
                    });
                    row.addView(swatch, weightedParams(1f, rowH));
                } else {
                    row.addView(new Space(this), weightedParams(1f, rowH));
                }
            }
            grid.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH));
        }

        scroll.addView(grid);
        bodyContainer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(224)
        ));
    }

    private void openSettingsActivity() {
        try {
            Intent intent = new Intent(this, KeyboardSettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private void buildTextEditingBody() {
        int topGridH = dp(170);
        int cellH = topGridH / 3;
        LinearLayout grid = createRow();

        GboardKeyView leftArrow = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        leftArrow.setIcon(GboardKeyView.ICON_CHEVRON_LEFT);
        leftArrow.setRepeatable(true);
        leftArrow.setInsetsDp(3.5f, 4f);
        leftArrow.setOnClickListener(v -> sendCursorKey(KeyEvent.KEYCODE_DPAD_LEFT));
        grid.addView(leftArrow, weightedParams(1f, topGridH));

        LinearLayout col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);

        GboardKeyView upArrow = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        upArrow.setIcon(GboardKeyView.ICON_CHEVRON_UP);
        upArrow.setRepeatable(true);
        upArrow.setInsetsDp(3.5f, 4f);
        upArrow.setOnClickListener(v -> sendCursorKey(KeyEvent.KEYCODE_DPAD_UP));

        GboardKeyView selectBtn = new GboardKeyView(this, theme,
                selectionMode ? GboardKeyView.STYLE_FUNC : GboardKeyView.STYLE_CHAR);
        selectBtn.setLabel("Selecionar");
        selectBtn.setTextSizeSp(13.5f);
        selectBtn.setActiveState(selectionMode);
        selectBtn.setInsetsDp(3.5f, 4f);
        selectBtn.setOnClickListener(v -> {
            selectionMode = !selectionMode;
            renderCurrentView();
        });

        GboardKeyView downArrow = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        downArrow.setIcon(GboardKeyView.ICON_CHEVRON_DOWN);
        downArrow.setRepeatable(true);
        downArrow.setInsetsDp(3.5f, 4f);
        downArrow.setOnClickListener(v -> sendCursorKey(KeyEvent.KEYCODE_DPAD_DOWN));

        col2.addView(upArrow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        col2.addView(selectBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        col2.addView(downArrow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        grid.addView(col2, weightedParams(1f, topGridH));

        GboardKeyView rightArrow = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        rightArrow.setIcon(GboardKeyView.ICON_CHEVRON_RIGHT);
        rightArrow.setRepeatable(true);
        rightArrow.setInsetsDp(3.5f, 4f);
        rightArrow.setOnClickListener(v -> sendCursorKey(KeyEvent.KEYCODE_DPAD_RIGHT));
        grid.addView(rightArrow, weightedParams(1f, topGridH));

        LinearLayout col4 = new LinearLayout(this);
        col4.setOrientation(LinearLayout.VERTICAL);

        GboardKeyView selectAllBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        selectAllBtn.setLabel("Selecionar tudo");
        selectAllBtn.setTextSizeSp(12f);
        selectAllBtn.setInsetsDp(3.5f, 4f);
        selectAllBtn.setOnClickListener(v -> performContextAction(android.R.id.selectAll));

        GboardKeyView copyBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        copyBtn.setLabel("Copiar");
        copyBtn.setTextSizeSp(13.5f);
        copyBtn.setInsetsDp(3.5f, 4f);
        copyBtn.setOnClickListener(v -> {
            performContextAction(android.R.id.copy);
            selectionMode = false;
            syncSystemClipboard();
            renderCurrentView();
        });

        GboardKeyView pasteBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        pasteBtn.setLabel("Colar");
        pasteBtn.setTextSizeSp(13.5f);
        pasteBtn.setInsetsDp(3.5f, 4f);
        pasteBtn.setOnClickListener(v -> performContextAction(android.R.id.paste));

        col4.addView(selectAllBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellH));
        col4.addView(copyBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellH));
        col4.addView(pasteBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellH));
        grid.addView(col4, weightedParams(1f, topGridH));

        bodyContainer.addView(grid, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, topGridH));

        int bh = dp(56);
        LinearLayout bRow = createRow();

        GboardKeyView homeBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        homeBtn.setIcon(GboardKeyView.ICON_JUMP_START);
        homeBtn.setInsetsDp(3.5f, 4.5f);
        homeBtn.setOnClickListener(v -> sendCursorKey(KeyEvent.KEYCODE_MOVE_HOME));

        GboardKeyView endBtn = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        endBtn.setIcon(GboardKeyView.ICON_JUMP_END);
        endBtn.setInsetsDp(3.5f, 4.5f);
        endBtn.setOnClickListener(v -> sendCursorKey(KeyEvent.KEYCODE_MOVE_END));

        GboardKeyView bsBtn = createBackspaceKey();

        bRow.addView(homeBtn, weightedParams(1.5f, bh));
        bRow.addView(endBtn, weightedParams(1.5f, bh));
        bRow.addView(bsBtn, weightedParams(1.0f, bh));
        bodyContainer.addView(bRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, bh));
    }

    private void syncSystemClipboard() {
        try {
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) return;
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).coerceToText(this);
                if (text != null) {
                    ClipboardStore.addCopiedText(this, text.toString());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void triggerVoiceOrTts() {
        // If there is text in the input box, speak it aloud (Texto para fala)!
        // Otherwise try switching to system Voice Input IME (Voz para texto)!
        String fieldText = extractFullFieldText();
        if (!fieldText.isEmpty()) {
            speakCurrentTextAloud();
            return;
        }
        try {
            Object svc = getSystemService(INPUT_METHOD_SERVICE);
            if (svc instanceof InputMethodManager) {
                InputMethodManager imm = (InputMethodManager) svc;
                List<InputMethodInfo> list = imm.getEnabledInputMethodList();
                for (InputMethodInfo info : list) {
                    String id = info.getId().toLowerCase(Locale.ROOT);
                    if (id.contains("voice") || id.contains("googletts") || id.contains("asr")) {
                        switchInputMethod(info.getId());
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        speakCurrentTextAloud();
    }

    private void speakCurrentTextAloud() {
        if (tts == null || !ttsReady) {
            Toast.makeText(this, "Motor de Texto para Fala inicializando...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tts.isSpeaking()) {
            tts.stop();
            return;
        }
        String text = extractFullFieldText();
        if (text.isEmpty()) {
            List<ClipboardStore.ClipEntry> clips = ClipboardStore.loadEntries(this);
            if (!clips.isEmpty()) {
                text = clips.get(0).text;
            }
        }
        if (text.isEmpty()) {
            Toast.makeText(this, "Digite ou selecione um texto para ouvir em voz alta!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kb_tts_utterance");
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
        Toast.makeText(this, "Lendo texto em voz alta...", Toast.LENGTH_SHORT).show();
    }

    private String extractFullFieldText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) {
            return sel.toString().trim();
        }
        CharSequence before = ic.getTextBeforeCursor(500, 0);
        CharSequence after = ic.getTextAfterCursor(500, 0);
        StringBuilder sb = new StringBuilder();
        if (before != null) sb.append(before);
        if (after != null) sb.append(after);
        return sb.toString().trim();
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private GboardKeyView createLetterKey(String symbol) {
        GboardKeyView key = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        key.setLabel(symbol);
        key.setTextSizeSp(22.5f);
        key.setInsetsDp(2.8f, 4.2f);
        key.setPreviewListener(this);
        key.setOnClickListener(v -> commitLetterText(((GboardKeyView) v).getLabel()));
        return key;
    }

    private GboardKeyView createSymbolKey(String symbol, float sp) {
        GboardKeyView key = new GboardKeyView(this, theme, GboardKeyView.STYLE_CHAR);
        key.setLabel(symbol);
        key.setTextSizeSp(sp);
        key.setInsetsDp(2.8f, 4.2f);
        key.setPreviewListener(this);
        key.setOnClickListener(v -> commitRawText(((GboardKeyView) v).getLabel()));
        return key;
    }

    private GboardKeyView createBackspaceKey() {
        GboardKeyView bs = new GboardKeyView(this, theme, GboardKeyView.STYLE_FUNC);
        bs.setIcon(GboardKeyView.ICON_BACKSPACE);
        bs.setRepeatable(true);
        bs.setInsetsDp(3f, 4.2f);
        bs.setOnClickListener(v -> deleteOneCharacter());
        return bs;
    }

    private GboardKeyView createSpaceKey() {
        GboardKeyView space = new GboardKeyView(this, theme, GboardKeyView.STYLE_SPACE);
        int fontStyle = GboardTheme.getTypingFontStyle(this);
        space.setLabel(fontStyle == FontStyler.FONT_NORMAL ? "" : FontStyler.FONT_NAMES[fontStyle]);
        space.setTextSizeSp(11.5f);
        space.setInsetsDp(3f, 4.5f);
        space.setOnClickListener(v -> handleSpaceTap());
        space.setOnLongClickListener(v -> {
            showInputMethodSelector();
            return true;
        });
        return space;
    }

    private GboardKeyView createActionPill() {
        GboardKeyView pill = new GboardKeyView(this, theme, GboardKeyView.STYLE_ENTER_PILL);
        pill.setIcon(searchActionMode ? GboardKeyView.ICON_SEARCH : GboardKeyView.ICON_ENTER);
        pill.setInsetsDp(3f, 4.5f);
        pill.setOnClickListener(v -> performEnterAction());
        return pill;
    }

    private LinearLayout.LayoutParams weightedParams(float weight, int height) {
        return new LinearLayout.LayoutParams(0, height, weight);
    }

    private String extractCurrentWordBeforeCursor() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence before = ic.getTextBeforeCursor(36, 0);
        if (before == null || before.length() == 0) return "";
        int end = before.length();
        int start = end;
        while (start > 0 && Character.isLetter(before.charAt(start - 1))) {
            start--;
        }
        return (start == end) ? "" : before.subSequence(start, end).toString();
    }

    private void applySuggestedWord(String currentWord, String chosenWord) {
        if (chosenWord == null || chosenWord.isEmpty()) return;

        if (translateModeActive) {
            if (currentWord != null && !currentWord.isEmpty() && translateBuffer.length() >= currentWord.length()) {
                translateBuffer.setLength(translateBuffer.length() - currentWord.length());
            }
            translateBuffer.append(chosenWord).append(' ');
            rebuildTranslateBar();
            scheduleTranslation();
            refreshHeaderBarOnly();
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        int fontStyle = GboardTheme.getTypingFontStyle(this);
        String finalWord = FontStyler.transform(chosenWord, fontStyle);

        ic.beginBatchEdit();
        if (currentWord != null && !currentWord.isEmpty()) {
            ic.deleteSurroundingText(currentWord.length(), 0);
        }
        ic.commitText(finalWord + " ", 1);
        ic.endBatchEdit();

        clearAutoCorrectUndo();
        if (shiftState == SHIFT_SINGLE) {
            shiftState = SHIFT_OFF;
            renderCurrentView();
        } else {
            refreshHeaderBarOnly();
        }
    }

    private void handleSpaceTap() {
        if (translateModeActive) {
            String w = getActiveWordForSuggestions();
            boolean autoOn = GboardTheme.isAutoCorrectEnabled(this);
            boolean strictOn = GboardTheme.isStrictAntiErrorEnabled(this);
            if (!w.isEmpty() && autoOn) {
                String fix = PortugueseSuggestions.getAutoCorrection(w, true, strictOn);
                if (fix != null && !fix.equals(w)) {
                    translateBuffer.setLength(translateBuffer.length() - w.length());
                    translateBuffer.append(fix);
                }
            }
            translateBuffer.append(' ');
            rebuildTranslateBar();
            scheduleTranslation();
            refreshHeaderBarOnly();
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        long now = SystemClock.uptimeMillis();
        int fontStyle = GboardTheme.getTypingFontStyle(this);

        // Double-space period ("Ponto final com espaço duplo")
        if (GboardTheme.isDoubleSpaceDotEnabled(this) && (now - lastSpaceTapMs) < 320L) {
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (before != null && before.length() == 2
                    && Character.isLetterOrDigit(before.charAt(0)) && before.charAt(1) == ' ') {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(1, 0);
                ic.commitText(". ", 1);
                ic.endBatchEdit();
                lastSpaceTapMs = 0L;
                updateAutoShiftFromCursor();
                refreshHeaderBarOnly();
                return;
            }
        }
        lastSpaceTapMs = now;

        String word = (fontStyle == FontStyler.FONT_NORMAL) ? extractCurrentWordBeforeCursor() : "";
        boolean autoCorrectOn = GboardTheme.isAutoCorrectEnabled(this);
        boolean strictOn = GboardTheme.isStrictAntiErrorEnabled(this);

        if (!word.isEmpty() && (autoCorrectOn || strictOn)) {
            String replacement = PortugueseSuggestions.getAutoCorrection(word, true, strictOn);
            if (replacement != null && !replacement.equals(word)) {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(word.length(), 0);
                ic.commitText(replacement + " ", 1);
                ic.endBatchEdit();

                lastRawBeforeAutoCorrect = word;
                lastCommittedAutoCorrect = replacement + " ";
                updateAutoShiftFromCursor();
                refreshHeaderBarOnly();
                return;
            }
        }

        clearAutoCorrectUndo();
        ic.commitText(" ", 1);
        updateAutoShiftFromCursor();
        refreshHeaderBarOnly();
    }

    private void commitPunctuationWithAutoCorrect(String punct) {
        if (translateModeActive) {
            translateBuffer.append(punct);
            rebuildTranslateBar();
            scheduleTranslation();
            refreshHeaderBarOnly();
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        int fontStyle = GboardTheme.getTypingFontStyle(this);
        String word = (fontStyle == FontStyler.FONT_NORMAL) ? extractCurrentWordBeforeCursor() : "";
        boolean autoOn = GboardTheme.isAutoCorrectEnabled(this);
        boolean strictOn = GboardTheme.isStrictAntiErrorEnabled(this);

        if (!word.isEmpty() && (autoOn || strictOn)) {
            String replacement = PortugueseSuggestions.getAutoCorrection(word, true, strictOn);
            if (replacement != null && !replacement.equals(word)) {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(word.length(), 0);
                ic.commitText(replacement + punct, 1);
                ic.endBatchEdit();
                clearAutoCorrectUndo();
                updateAutoShiftFromCursor();
                refreshHeaderBarOnly();
                return;
            }
        }

        clearAutoCorrectUndo();
        ic.commitText(punct, 1);
        updateAutoShiftFromCursor();
        refreshHeaderBarOnly();
    }

    private void commitLetterText(String text) {
        if (translateModeActive) {
            translateBuffer.append(text);
            if (GboardTheme.isStrictAntiErrorEnabled(this)) {
                String w = getActiveWordForSuggestions();
                String midFix = PortugueseSuggestions.getImmediateMidWordFix(w);
                if (midFix != null) {
                    translateBuffer.setLength(translateBuffer.length() - w.length());
                    translateBuffer.append(midFix);
                }
            }
            if (shiftState == SHIFT_SINGLE) {
                shiftState = SHIFT_OFF;
                renderCurrentView();
            } else {
                rebuildTranslateBar();
                refreshHeaderBarOnly();
            }
            scheduleTranslation();
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            int fontStyle = GboardTheme.getTypingFontStyle(this);
            String styled = FontStyler.transform(text, fontStyle);
            ic.commitText(styled, 1);
            selectionMode = false;
            clearAutoCorrectUndo();

            // Dynamic mid-word Anti-Error Guard (e.g. fixes impossible "np" -> "mp", "nb" -> "mb" live!)
            if (fontStyle == FontStyler.FONT_NORMAL && GboardTheme.isStrictAntiErrorEnabled(this)) {
                String curWord = extractCurrentWordBeforeCursor();
                String midFix = PortugueseSuggestions.getImmediateMidWordFix(curWord);
                if (midFix != null && !midFix.equals(curWord)) {
                    ic.beginBatchEdit();
                    ic.deleteSurroundingText(curWord.length(), 0);
                    ic.commitText(midFix, 1);
                    ic.endBatchEdit();
                }
            }
        }
        if (shiftState == SHIFT_SINGLE) {
            shiftState = SHIFT_OFF;
            renderCurrentView();
        } else {
            refreshHeaderBarOnly();
        }
    }

    private void commitRawText(String text) {
        if (translateModeActive) {
            translateBuffer.append(text);
            rebuildTranslateBar();
            scheduleTranslation();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            selectionMode = false;
            clearAutoCorrectUndo();
        }
        refreshHeaderBarOnly();
    }

    private void deleteOneCharacter() {
        if (translateModeActive) {
            if (translateBuffer.length() > 0) {
                translateBuffer.deleteCharAt(translateBuffer.length() - 1);
                rebuildTranslateBar();
                scheduleTranslation();
                refreshHeaderBarOnly();
            }
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        // Instant undo of last spacebar auto-correction (unless strict Anti-Error wants user to explicitly keep editing)
        if (lastCommittedAutoCorrect != null && lastRawBeforeAutoCorrect != null) {
            CharSequence before = ic.getTextBeforeCursor(lastCommittedAutoCorrect.length(), 0);
            if (before != null && before.toString().equals(lastCommittedAutoCorrect)) {
                ic.beginBatchEdit();
                ic.deleteSurroundingText(lastCommittedAutoCorrect.length(), 0);
                ic.commitText(lastRawBeforeAutoCorrect, 1);
                ic.endBatchEdit();
                clearAutoCorrectUndo();
                refreshHeaderBarOnly();
                return;
            }
            clearAutoCorrectUndo();
        }

        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            lastDeletedChunk = selected.toString();
            ic.commitText("", 1);
        } else {
            CharSequence before = ic.getTextBeforeCursor(2, 0);
            if (before != null && before.length() == 2 && Character.isSurrogatePair(before.charAt(0), before.charAt(1))) {
                lastDeletedChunk = before.toString();
                ic.deleteSurroundingText(2, 0);
            } else if (before != null && before.length() >= 1) {
                lastDeletedChunk = String.valueOf(before.charAt(before.length() - 1));
                ic.deleteSurroundingText(1, 0);
            }
        }
        selectionMode = false;
        updateAutoShiftFromCursor();
        refreshHeaderBarOnly();
    }

    private void clearAutoCorrectUndo() {
        lastRawBeforeAutoCorrect = null;
        lastCommittedAutoCorrect = null;
    }

    @Override
    public void onShowPreview(GboardKeyView anchor, String text) {
        showPopupItems(anchor, new String[]{text}, -1);
    }

    @Override
    public void onShowVariants(GboardKeyView anchor, String[] variants, int selectedIndex) {
        showPopupItems(anchor, variants, selectedIndex);
    }

    private void showPopupItems(GboardKeyView anchor, String[] items, int highlightedIdx) {
        try {
            if (anchor.getWindowToken() == null || items == null || items.length == 0) return;

            if (previewPopup == null) {
                previewContainer = new LinearLayout(this);
                previewContainer.setOrientation(LinearLayout.HORIZONTAL);
                previewContainer.setGravity(Gravity.CENTER);

                previewPopup = new PopupWindow(previewContainer, dp(52), dp(56), false);
                previewPopup.setTouchable(false);
                previewPopup.setClippingEnabled(false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    previewPopup.setElevation(dp(5));
                }
            }

            previewContainer.removeAllViews();
            GradientDrawable bubble = new GradientDrawable();
            bubble.setShape(GradientDrawable.RECTANGLE);
            bubble.setCornerRadius(dp(12));
            bubble.setColor(theme.popupBg);
            bubble.setStroke(dp(1), theme.popupBorder);
            previewContainer.setBackground(bubble);
            previewContainer.setPadding(dp(4), dp(4), dp(4), dp(4));

            int cellW = (items.length == 1) ? dp(44) : dp(36);
            int cellH = dp(46);

            for (int i = 0; i < items.length; i++) {
                TextView tv = new TextView(this);
                tv.setText(items[i]);
                tv.setGravity(Gravity.CENTER);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, items.length == 1 ? 25f : 20f);
                tv.setTextColor((i == highlightedIdx && theme.isGamerRgb) ? Color.BLACK : theme.popupText);
                if (i == highlightedIdx) {
                    GradientDrawable hi = new GradientDrawable();
                    hi.setCornerRadius(dp(8));
                    hi.setColor(theme.popupHighlightBg);
                    tv.setBackground(hi);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                }
                previewContainer.addView(tv, new LinearLayout.LayoutParams(cellW, cellH));
            }

            int totalW = cellW * items.length + dp(8);
            int totalH = cellH + dp(8);

            int[] loc = new int[2];
            anchor.getLocationInWindow(loc);
            int screenW = getResources().getDisplayMetrics().widthPixels;

            int xOff = (anchor.getWidth() - totalW) / 2;
            if (highlightedIdx >= 0 && items.length > 1) {
                xOff = (anchor.getWidth() - cellW) / 2 - dp(4);
            }
            if (loc[0] + xOff < dp(4)) {
                xOff = dp(4) - loc[0];
            } else if (loc[0] + xOff + totalW > screenW - dp(4)) {
                xOff = (screenW - dp(4) - totalW) - loc[0];
            }

            int yOff = -(anchor.getHeight() + totalH + dp(4));

            if (previewPopup.isShowing()) {
                previewPopup.update(anchor, xOff, yOff, totalW, totalH);
            } else {
                previewPopup.setWidth(totalW);
                previewPopup.setHeight(totalH);
                previewPopup.showAsDropDown(anchor, xOff, yOff);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onHidePreview() {
        dismissPreview();
    }

    private void dismissPreview() {
        if (previewPopup != null) {
            try {
                if (previewPopup.isShowing()) previewPopup.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    private void sendCursorKey(int keyCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (selectionMode) {
            long now = SystemClock.uptimeMillis();
            int meta = KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta));
            ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta));
        } else {
            sendDownUpKeyEvents(keyCode);
        }
    }

    private void performContextAction(int id) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.performContextMenuAction(id);
    }

    private void performEnterAction() {
        if (translateModeActive) {
            exitTranslateMode(true);
            return;
        }

        // Apply dynamic correction right before Enter/Search too!
        InputConnection ic = getCurrentInputConnection();
        int fontStyle = GboardTheme.getTypingFontStyle(this);
        if (ic != null && fontStyle == FontStyler.FONT_NORMAL
                && (GboardTheme.isAutoCorrectEnabled(this) || GboardTheme.isStrictAntiErrorEnabled(this))) {
            String word = extractCurrentWordBeforeCursor();
            if (!word.isEmpty()) {
                String fix = PortugueseSuggestions.getAutoCorrection(word, true, GboardTheme.isStrictAntiErrorEnabled(this));
                if (fix != null && !fix.equals(word)) {
                    ic.beginBatchEdit();
                    ic.deleteSurroundingText(word.length(), 0);
                    ic.commitText(fix, 1);
                    ic.endBatchEdit();
                }
            }
        }

        clearAutoCorrectUndo();
        if (!sendDefaultEditorAction(true)) {
            commitRawText("\n");
        }
    }

    private void showInputMethodSelector() {
        Object service = getSystemService(INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager) {
            ((InputMethodManager) service).showInputMethodPicker();
        }
    }

    private void applyNavigationBarStyle() {
        try {
            Dialog dialog = getWindow();
            if (dialog == null) return;
            Window window = dialog.getWindow();
            if (window == null || theme == null) return;

            window.setNavigationBarColor(theme.boardBg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    int appearance = theme.isDark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(
                            appearance,
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                View decor = window.getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (theme.isDark) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decor.setSystemUiVisibility(flags);
            }
        } catch (Exception ignored) {
        }
    }

    private int getNavBottomInset(WindowInsets insets) {
        if (insets == null) return 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
        }
        return insets.getSystemWindowInsetBottom();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    /**
     * Miniature Gboard Theme Swatch Card (including Rainbow RGB Gamer preview!).
     */
    private static final class ThemeSwatchView extends View {
        private final GboardTheme.ThemeOption option;
        private final boolean selected;
        private final int labelColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF subRect = new RectF();
        private final Path checkPath = new Path();

        ThemeSwatchView(Context context, GboardTheme.ThemeOption option, boolean selected,
                        int labelColor, Runnable onSelect) {
            super(context);
            this.option = option;
            this.selected = selected;
            this.labelColor = labelColor;
            textPaint.setTextAlign(Paint.Align.CENTER);
            setOnClickListener(v -> onSelect.run());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float padH = dpF(6f);
            float cardH = h - dpF(22f);

            cardRect.set(padH, dpF(3f), w - padH, dpF(3f) + cardH);
            float r = dpF(10f);

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(option.previewBg);
            canvas.drawRoundRect(cardRect, r, r, paint);

            if (option.splitPreview) {
                canvas.save();
                canvas.clipRect(cardRect.centerX(), cardRect.top, cardRect.right, cardRect.bottom);
                paint.setColor(Color.parseColor("#1B1C1F"));
                canvas.drawRoundRect(cardRect, r, r, paint);
                canvas.restore();
            }

            float sbW = cardRect.width() * 0.42f;
            float sbH = dpF(6f);
            float sbLeft = cardRect.centerX() - sbW * 0.62f;
            float sbTop = cardRect.bottom - dpF(11f);
            subRect.set(sbLeft, sbTop, sbLeft + sbW, sbTop + sbH);
            paint.setColor(option.previewSpace);
            canvas.drawRoundRect(subRect, sbH / 2f, sbH / 2f, paint);

            float pillW = dpF(14f);
            subRect.set(cardRect.right - dpF(8f) - pillW, sbTop, cardRect.right - dpF(8f), sbTop + sbH);
            paint.setColor(option.previewEnter);
            canvas.drawRoundRect(subRect, sbH / 2f, sbH / 2f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(option.rgbPreview ? dpF(2.2f) : (selected ? dpF(2.2f) : dpF(1f)));
            if (option.rgbPreview) {
                paint.setShader(new LinearGradient(
                        cardRect.left, cardRect.top, cardRect.right, cardRect.bottom,
                        new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.MAGENTA},
                        null, Shader.TileMode.CLAMP
                ));
            } else {
                paint.setColor(selected ? Color.parseColor("#476298") : Color.parseColor("#88909E9E"));
            }
            canvas.drawRoundRect(cardRect, r, r, paint);
            paint.setShader(null);

            if (selected) {
                float cx = cardRect.centerX();
                float cy = cardRect.centerY() - dpF(2f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#425E91"));
                canvas.drawCircle(cx, cy, dpF(10f), paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(dpF(2f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                checkPath.reset();
                checkPath.moveTo(cx - dpF(4.2f), cy + dpF(0.2f));
                checkPath.lineTo(cx - dpF(1.0f), cy + dpF(3.4f));
                checkPath.lineTo(cx + dpF(4.5f), cy - dpF(2.8f));
                canvas.drawPath(checkPath, paint);
            }

            textPaint.setTextSize(spF(10.5f));
            textPaint.setColor(labelColor);
            canvas.drawText(option.name, w / 2f, h - dpF(4f), textPaint);
        }

        private float dpF(float v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
        }

        private float spF(float v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
        }
    }
}
