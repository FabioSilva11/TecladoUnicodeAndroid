package com.luisunicode.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

public class GboardKeyView extends View {

    public static final int STYLE_CHAR = 0;
    public static final int STYLE_FUNC = 1;
    public static final int STYLE_FUNC_PILL = 2;
    public static final int STYLE_ENTER_PILL = 3;
    public static final int STYLE_SPACE = 4;
    public static final int STYLE_TOOLBAR_ICON = 5;
    public static final int STYLE_TOOLBAR_CIRCLE = 6;
    public static final int STYLE_TOOL_CARD = 7;
    public static final int STYLE_TONAL_CIRCLE = 8;
    public static final int STYLE_FLAT_STRIP = 9;

    public static final int ICON_NONE = 0;
    public static final int ICON_BACKSPACE = 1;
    public static final int ICON_ENTER = 2;
    public static final int ICON_SEARCH = 3;
    public static final int ICON_SHIFT = 4;
    public static final int ICON_CAPS_LOCK = 5;
    public static final int ICON_GLOBE = 6;
    public static final int ICON_HIDE = 7;
    public static final int ICON_GRID_MENU = 8;
    public static final int ICON_CLOSE = 9;
    public static final int ICON_BACK_ARROW = 10;
    public static final int ICON_TEXT_EDIT = 11;
    public static final int ICON_CLIPBOARD = 12;
    public static final int ICON_ONE_HANDED = 13;
    public static final int ICON_EXPAND_FULL = 14;
    public static final int ICON_CHEVRON_LEFT = 15;
    public static final int ICON_CHEVRON_RIGHT = 16;
    public static final int ICON_CHEVRON_UP = 17;
    public static final int ICON_CHEVRON_DOWN = 18;
    public static final int ICON_JUMP_START = 19;
    public static final int ICON_JUMP_END = 20;
    public static final int ICON_NUMPAD = 21;
    public static final int ICON_SPACE_BAR = 22;
    public static final int ICON_EMOJI = 23;
    public static final int ICON_PALETTE = 24;
    public static final int ICON_SPELLCHECK = 25;
    public static final int ICON_TRANSLATE = 26;
    public static final int ICON_SETTINGS = 27;
    public static final int ICON_MIC = 28;
    public static final int ICON_SPEAKER_TTS = 29;
    public static final int ICON_HEART_SPARK = 30;
    public static final int ICON_FONTS = 31;
    public static final int ICON_LOCK_CLOSED = 32;
    public static final int ICON_LOCK_OPEN = 33;
    public static final int ICON_TRASH = 34;
    public static final int ICON_SWAP_HORIZ = 35;
    public static final int ICON_UNDO = 36;
    public static final int ICON_RESIZE = 37;
    public static final int ICON_CLOCK = 38;

    public interface PreviewListener {
        void onShowPreview(GboardKeyView anchor, String text);
        void onShowVariants(GboardKeyView anchor, String[] variants, int selectedIndex);
        void onHidePreview();
    }

    public interface VariantSelectListener {
        void onVariantSelected(String variant);
    }

    private GboardTheme theme;
    private final int style;
    private int iconType = ICON_NONE;
    private String label = "";
    private String subLabel = "";
    private String topRightHint = "";
    private String[] popupVariants = null;
    private float textSizeSp = 22f;
    private float cornerRadiusDp = 8f;
    private boolean activeState = false;
    private boolean isKeyPressed = false;
    private boolean repeatable = false;
    private long lastPressReleaseTime = 0L;

    private float insetH;
    private float insetV;

    private OnClickListener tapListener;
    private OnLongClickListener longPressListener;
    private PreviewListener previewListener;
    private VariantSelectListener variantListener;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF capTopRect = new RectF();
    private final RectF auxRect = new RectF();
    private final Path path = new Path();
    private final float[] hsvBuf = new float[]{0f, 0.92f, 1.0f};
    private final int[] winLoc = new int[2];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longPressTriggered = false;
    private boolean variantsPopupActive = false;
    private int selectedVariantIndex = 0;
    private float touchDownX = 0f;

    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (isKeyPressed && tapListener != null) {
                tapListener.onClick(GboardKeyView.this);
                handler.postDelayed(this, 55);
            }
        }
    };

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isKeyPressed) return;

            if (popupVariants != null && popupVariants.length > 0 && variantListener != null) {
                longPressTriggered = true;
                variantsPopupActive = true;
                selectedVariantIndex = 0;
                triggerHaptic(true);
                if (previewListener != null) {
                    previewListener.onShowVariants(GboardKeyView.this, popupVariants, selectedVariantIndex);
                }
            } else if (longPressListener != null) {
                longPressTriggered = true;
                if (previewListener != null) {
                    previewListener.onHidePreview();
                }
                triggerHaptic(true);
                longPressListener.onLongClick(GboardKeyView.this);
            }
        }
    };

    public GboardKeyView(Context context, GboardTheme theme, int style) {
        super(context);
        this.theme = theme;
        this.style = style;
        this.insetH = dp(3f);
        this.insetV = dp(4.5f);

        strokePaint.setStyle(Paint.Style.STROKE);
        updateTypefaces();

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    private void updateTypefaces() {
        boolean med = (style == STYLE_FUNC_PILL || style == STYLE_TOOL_CARD);
        int fontMode = (theme != null) ? theme.keycapFontMode : FontStyler.KEY_FONT_DEFAULT;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(FontStyler.resolveKeycapTypeface(fontMode, med));

        hintPaint.setTextAlign(Paint.Align.RIGHT);
        hintPaint.setTypeface(FontStyler.resolveKeycapTypeface(fontMode, true));
    }

    public void setTheme(GboardTheme theme) {
        this.theme = theme;
        updateTypefaces();
        invalidate();
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
        invalidate();
    }

    public String getLabel() {
        return label;
    }

    public void setStackedLabels(String line1, String line2) {
        this.label = line1 != null ? line1 : "";
        this.subLabel = line2 != null ? line2 : "";
        invalidate();
    }

    public void setTopRightHint(String hint) {
        this.topRightHint = hint != null ? hint : "";
        invalidate();
    }

    public void setPopupVariants(String[] variants, VariantSelectListener listener) {
        this.popupVariants = variants;
        this.variantListener = listener;
    }

    public void setTextSizeSp(float sp) {
        this.textSizeSp = sp;
        invalidate();
    }

    public void setIcon(int iconType) {
        this.iconType = iconType;
        invalidate();
    }

    public void setInsetsDp(float horizontalDp, float verticalDp) {
        this.insetH = dp(horizontalDp);
        this.insetV = dp(verticalDp);
        invalidate();
    }

    public void setActiveState(boolean active) {
        this.activeState = active;
        invalidate();
    }

    public void setRepeatable(boolean repeatable) {
        this.repeatable = repeatable;
    }

    public void setPreviewListener(PreviewListener listener) {
        this.previewListener = listener;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        this.tapListener = l;
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        this.longPressListener = l;
    }

    private void triggerHaptic(boolean longPress) {
        if (GboardTheme.isHapticEnabled(getContext())) {
            performHapticFeedback(longPress
                    ? HapticFeedbackConstants.LONG_PRESS
                    : HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void triggerSound() {
        if (!GboardTheme.isSoundEnabled(getContext())) return;
        try {
            Object svc = getContext().getSystemService(Context.AUDIO_SERVICE);
            if (svc instanceof AudioManager) {
                AudioManager am = (AudioManager) svc;
                int fx = (theme != null && theme.isGamerRgb)
                        ? AudioManager.FX_KEYPRESS_SPACEBAR
                        : AudioManager.FX_KEYPRESS_STANDARD;
                am.playSoundEffect(fx, (theme != null && theme.isGamerRgb) ? 0.85f : -1f);
                return;
            }
        } catch (Exception ignored) {
        }
        playSoundEffect(SoundEffectConstants.CLICK);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                isKeyPressed = true;
                longPressTriggered = false;
                variantsPopupActive = false;
                touchDownX = event.getX();
                invalidate();
                triggerHaptic(false);

                if (previewListener != null && style == STYLE_CHAR && !label.isEmpty() && subLabel.isEmpty()
                        && GboardTheme.isPopupEnabled(getContext())) {
                    previewListener.onShowPreview(this, label);
                }

                if (repeatable) {
                    if (tapListener != null) {
                        triggerSound();
                        tapListener.onClick(this);
                    }
                    handler.removeCallbacks(repeatRunnable);
                    handler.postDelayed(repeatRunnable, 350);
                } else if ((popupVariants != null && popupVariants.length > 0) || longPressListener != null) {
                    handler.removeCallbacks(longPressRunnable);
                    handler.postDelayed(longPressRunnable, 260);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (variantsPopupActive && popupVariants != null && popupVariants.length > 0) {
                    float stepPx = dp(34f);
                    int offset = Math.round((event.getX() - touchDownX) / stepPx);
                    int newIdx = Math.max(0, Math.min(popupVariants.length - 1, offset));
                    if (newIdx != selectedVariantIndex) {
                        selectedVariantIndex = newIdx;
                        triggerHaptic(false);
                        if (previewListener != null) {
                            previewListener.onShowVariants(this, popupVariants, selectedVariantIndex);
                        }
                    }
                    return true;
                }

                boolean inside = event.getX() >= -dp(12) && event.getX() <= getWidth() + dp(12)
                        && event.getY() >= -dp(12) && event.getY() <= getHeight() + dp(12);
                if (!inside && isKeyPressed) {
                    cancelPress();
                }
                return true;

            case MotionEvent.ACTION_UP:
                boolean wasPressed = isKeyPressed;
                boolean wasLongPressed = longPressTriggered;
                boolean wasVariantsActive = variantsPopupActive;
                int chosenIdx = selectedVariantIndex;
                lastPressReleaseTime = SystemClock.uptimeMillis();
                cancelPress();

                if (wasVariantsActive && popupVariants != null && chosenIdx >= 0 && chosenIdx < popupVariants.length && variantListener != null) {
                    triggerSound();
                    variantListener.onVariantSelected(popupVariants[chosenIdx]);
                } else if (wasPressed && !repeatable && !wasLongPressed && tapListener != null) {
                    triggerSound();
                    tapListener.onClick(this);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelPress();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void cancelPress() {
        isKeyPressed = false;
        variantsPopupActive = false;
        handler.removeCallbacks(repeatRunnable);
        handler.removeCallbacks(longPressRunnable);
        if (previewListener != null) {
            previewListener.onHidePreview();
        }
        invalidate();
    }

    private int computeGamerRgbColor() {
        getLocationInWindow(winLoc);
        long now = SystemClock.uptimeMillis();
        float hue = ((now * 0.095f) + (winLoc[0] * 0.23f) + (winLoc[1] * 0.14f)) % 360f;
        if (hue < 0f) hue += 360f;
        hsvBuf[0] = hue;
        long sinceRelease = now - lastPressReleaseTime;
        if (isKeyPressed) {
            hsvBuf[1] = 0.35f;
            hsvBuf[2] = 1.0f;
        } else if (sinceRelease >= 0 && sinceRelease < 260) {
            float t = sinceRelease / 260f;
            hsvBuf[1] = 0.35f + 0.57f * t;
            hsvBuf[2] = 1.0f;
        } else {
            hsvBuf[1] = 0.92f;
            hsvBuf[2] = 1.0f;
        }
        return Color.HSVToColor(hsvBuf);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        rect.set(insetH, insetV, w - insetH, h - insetV);
        float radius;
        if (style == STYLE_FUNC_PILL || style == STYLE_ENTER_PILL || style == STYLE_TOOLBAR_CIRCLE
                || style == STYLE_TONAL_CIRCLE || style == STYLE_TOOL_CARD || style == STYLE_TOOLBAR_ICON) {
            radius = Math.min(rect.width(), rect.height()) / 2f;
        } else {
            radius = dp(cornerRadiusDp);
        }

        if (theme != null && theme.isGamerRgb) {
            if (style == STYLE_TOOLBAR_ICON || style == STYLE_FLAT_STRIP) {
                int rgb = computeGamerRgbColor();
                if (isKeyPressed || activeState) {
                    fillPaint.setColor(Color.argb(65, Color.red(rgb), Color.green(rgb), Color.blue(rgb)));
                    canvas.drawRoundRect(rect, radius, radius, fillPaint);
                }
                drawKeyForeground(canvas, rect, isKeyPressed ? Color.WHITE : rgb, rgb);
                return;
            }
            drawMechanicalGamerKey(canvas, radius);
            return;
        }

        int bgColor = resolveBgColor();
        if (bgColor != 0) {
            fillPaint.setColor(bgColor);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
        }

        if (style == STYLE_TOOL_CARD) {
            drawToolCard(canvas, resolveContentColor());
            return;
        }

        drawKeyForeground(canvas, rect, resolveContentColor(), theme.hintText);
    }

    private void drawMechanicalGamerKey(Canvas canvas, float radius) {
        int rgb = computeGamerRgbColor();

        // 1. RGB underglow halo at switch base
        fillPaint.setColor(Color.argb(isKeyPressed ? 115 : 52, Color.red(rgb), Color.green(rgb), Color.blue(rgb)));
        auxRect.set(rect.left - dp(1.2f), rect.top - dp(0.8f), rect.right + dp(1.2f), rect.bottom + dp(1.6f));
        canvas.drawRoundRect(auxRect, radius + dp(1.5f), radius + dp(1.5f), fillPaint);

        // 2. Dark switch housing skirt (3D mechanical keycap depth)
        fillPaint.setColor(Color.parseColor("#0F121B"));
        canvas.drawRoundRect(rect, radius, radius, fillPaint);

        // 3. Glowing per-key LED RGB border ring
        strokePaint.setStrokeWidth(dp(isKeyPressed ? 2.1f : 1.35f));
        strokePaint.setColor(rgb);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);

        // 4. Sculpted inner keycap top face that physically depresses downward on press!
        float travel = isKeyPressed ? dp(2.1f) : 0f;
        float bezel = dp(2.2f);
        capTopRect.set(
                rect.left + bezel,
                rect.top + dp(1.2f) + travel,
                rect.right - bezel,
                rect.bottom - dp(3.0f) + travel
        );
        float innerRad = Math.max(dp(4f), radius - bezel);
        fillPaint.setColor(isKeyPressed ? Color.parseColor("#282F42") : (activeState ? Color.parseColor("#253348") : theme.keyBg));
        canvas.drawRoundRect(capTopRect, innerRad, innerRad, fillPaint);

        int legendColor = isKeyPressed ? Color.WHITE : rgb;
        if (style == STYLE_TOOL_CARD) {
            drawToolCard(canvas, legendColor);
        } else {
            drawKeyForeground(canvas, capTopRect, legendColor, Color.argb(190, Color.red(rgb), Color.green(rgb), Color.blue(rgb)));
        }
    }

    private void drawKeyForeground(Canvas canvas, RectF bounds, int contentColor, int hintColor) {
        if (!topRightHint.isEmpty()) {
            hintPaint.setTextSize(sp(10.2f));
            hintPaint.setColor(hintColor);
            canvas.drawText(topRightHint, bounds.right - dp(4.2f), bounds.top + dp(11.8f), hintPaint);
        }

        float cx = bounds.centerX();
        float cy = bounds.centerY();

        if (iconType != ICON_NONE) {
            drawVectorIcon(canvas, cx, cy, contentColor);
        } else if (!subLabel.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(11.5f));
            textPaint.setColor(contentColor);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float lineOffset = dp(6.2f);
            float base1 = (cy - lineOffset) - (fm.ascent + fm.descent) / 2f;
            float base2 = (cy + lineOffset) - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, cx, base1, textPaint);
            canvas.drawText(subLabel, cx, base2, textPaint);
        } else if (!label.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(textSizeSp));
            textPaint.setColor(contentColor);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, cx, baseline, textPaint);
        }
    }

    private void drawToolCard(Canvas canvas, int contentColor) {
        float iconCenterX = rect.left + dp(25f);
        float cy = rect.centerY();
        if (iconType != ICON_NONE) {
            drawVectorIcon(canvas, iconCenterX, cy, contentColor);
        }
        if (!label.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTextSize(sp(12.8f));
            textPaint.setColor(theme.isGamerRgb ? Color.WHITE : contentColor);
            float textLeft = rect.left + dp(45f);
            float maxWidth = Math.max(0f, rect.right - dp(10f) - textLeft);
            CharSequence ellipsized = TextUtils.ellipsize(
                    label,
                    new android.text.TextPaint(textPaint),
                    maxWidth,
                    TextUtils.TruncateAt.END
            );
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(ellipsized, 0, ellipsized.length(), textLeft, baseline, textPaint);
        }
    }

    private int resolveBgColor() {
        switch (style) {
            case STYLE_CHAR:
                if (isKeyPressed) return theme.keyPressedBg;
                return theme.keyBorders ? theme.keyBg : 0;
            case STYLE_SPACE:
            case STYLE_TOOL_CARD:
            case STYLE_TOOLBAR_CIRCLE:
                return isKeyPressed ? theme.keyPressedBg : theme.keyBg;
            case STYLE_FUNC:
            case STYLE_FUNC_PILL:
            case STYLE_TONAL_CIRCLE:
                if (isKeyPressed) return theme.funcKeyPressedBg;
                if (activeState) return theme.funcKeyActiveBg;
                return (style == STYLE_FUNC && !theme.keyBorders) ? 0 : theme.funcKeyBg;
            case STYLE_ENTER_PILL:
                return isKeyPressed ? theme.enterPressedBg : theme.enterBg;
            case STYLE_TOOLBAR_ICON:
                if (activeState) return theme.funcKeyBg;
                return isKeyPressed ? theme.funcKeyBg : 0;
            case STYLE_FLAT_STRIP:
                return isKeyPressed ? theme.funcKeyPressedBg : 0;
            default:
                return theme.keyBg;
        }
    }

    private int resolveContentColor() {
        switch (style) {
            case STYLE_FUNC:
            case STYLE_FUNC_PILL:
            case STYLE_TONAL_CIRCLE:
                return activeState ? theme.funcKeyActiveText : theme.funcKeyText;
            case STYLE_ENTER_PILL:
                return theme.enterIcon;
            case STYLE_TOOLBAR_ICON:
            case STYLE_TOOLBAR_CIRCLE:
                return theme.toolbarIcon;
            default:
                return theme.keyText;
        }
    }

    private void drawVectorIcon(Canvas canvas, float cx, float cy, int color) {
        iconPaint.setColor(color);
        iconPaint.setPathEffect(null);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(1.85f));

        switch (iconType) {
            case ICON_BACKSPACE: {
                iconPaint.setPathEffect(new CornerPathEffect(dp(1.8f)));
                path.reset();
                path.moveTo(cx - dp(9.5f), cy);
                path.lineTo(cx - dp(4.2f), cy - dp(6.5f));
                path.lineTo(cx + dp(9.2f), cy - dp(6.5f));
                path.lineTo(cx + dp(9.2f), cy + dp(6.5f));
                path.lineTo(cx - dp(4.2f), cy + dp(6.5f));
                path.close();
                canvas.drawPath(path, iconPaint);

                iconPaint.setPathEffect(null);
                iconPaint.setStrokeWidth(dp(1.75f));
                canvas.drawLine(cx - dp(0.6f), cy - dp(2.8f), cx + dp(5.0f), cy + dp(2.8f), iconPaint);
                canvas.drawLine(cx + dp(5.0f), cy - dp(2.8f), cx - dp(0.6f), cy + dp(2.8f), iconPaint);
                break;
            }
            case ICON_ENTER: {
                iconPaint.setPathEffect(new CornerPathEffect(dp(2.4f)));
                iconPaint.setStrokeWidth(dp(2.0f));
                path.reset();
                path.moveTo(cx + dp(6.2f), cy - dp(4.8f));
                path.lineTo(cx + dp(6.2f), cy + dp(1.6f));
                path.lineTo(cx - dp(6.2f), cy + dp(1.6f));
                canvas.drawPath(path, iconPaint);

                iconPaint.setPathEffect(null);
                path.reset();
                path.moveTo(cx - dp(2.2f), cy - dp(2.4f));
                path.lineTo(cx - dp(6.4f), cy + dp(1.6f));
                path.lineTo(cx - dp(2.2f), cy + dp(5.6f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_SEARCH: {
                iconPaint.setStrokeWidth(dp(2.0f));
                float r = dp(5.6f);
                canvas.drawCircle(cx - dp(1.6f), cy - dp(1.6f), r, iconPaint);
                canvas.drawLine(cx + dp(2.5f), cy + dp(2.5f), cx + dp(7.5f), cy + dp(7.5f), iconPaint);
                break;
            }
            case ICON_SHIFT:
            case ICON_CAPS_LOCK: {
                float yShift = (iconType == ICON_CAPS_LOCK) ? -dp(1.6f) : 0f;
                iconPaint.setPathEffect(new CornerPathEffect(dp(1.2f)));
                path.reset();
                path.moveTo(cx, cy - dp(7.2f) + yShift);
                path.lineTo(cx + dp(7.2f), cy + dp(0.3f) + yShift);
                path.lineTo(cx + dp(3.1f), cy + dp(0.3f) + yShift);
                path.lineTo(cx + dp(3.1f), cy + dp(5.8f) + yShift);
                path.lineTo(cx - dp(3.1f), cy + dp(5.8f) + yShift);
                path.lineTo(cx - dp(3.1f), cy + dp(0.3f) + yShift);
                path.lineTo(cx - dp(7.2f), cy + dp(0.3f) + yShift);
                path.close();
                if (activeState || iconType == ICON_CAPS_LOCK) {
                    iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                }
                canvas.drawPath(path, iconPaint);
                if (iconType == ICON_CAPS_LOCK) {
                    iconPaint.setPathEffect(null);
                    iconPaint.setStyle(Paint.Style.STROKE);
                    iconPaint.setStrokeWidth(dp(2.1f));
                    canvas.drawLine(cx - dp(3.8f), cy + dp(7.5f), cx + dp(3.8f), cy + dp(7.5f), iconPaint);
                }
                break;
            }
            case ICON_GLOBE: {
                iconPaint.setStrokeWidth(dp(1.65f));
                float r = dp(8.2f);
                canvas.drawCircle(cx, cy, r, iconPaint);
                canvas.drawLine(cx - r, cy, cx + r, cy, iconPaint);
                auxRect.set(cx - dp(4.1f), cy - r, cx + dp(4.1f), cy + r);
                canvas.drawOval(auxRect, iconPaint);
                break;
            }
            case ICON_EMOJI: {
                iconPaint.setStrokeWidth(dp(1.8f));
                float r = dp(8.6f);
                canvas.drawCircle(cx, cy, r, iconPaint);
                iconPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - dp(3.0f), cy - dp(2.0f), dp(1.35f), iconPaint);
                canvas.drawCircle(cx + dp(3.0f), cy - dp(2.0f), dp(1.35f), iconPaint);
                iconPaint.setStyle(Paint.Style.STROKE);
                auxRect.set(cx - dp(4.6f), cy - dp(2.6f), cx + dp(4.6f), cy + dp(4.8f));
                canvas.drawArc(auxRect, 20f, 140f, false, iconPaint);
                break;
            }
            case ICON_PALETTE: {
                iconPaint.setStrokeWidth(dp(1.8f));
                float r = dp(8.5f);
                path.reset();
                auxRect.set(cx - r, cy - r, cx + r, cy + r);
                path.addArc(auxRect, 35f, 305f);
                path.quadTo(cx + dp(3f), cy + dp(2f), cx + dp(2.5f), cy + dp(5.5f));
                path.close();
                canvas.drawPath(path, iconPaint);

                iconPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - dp(3.6f), cy - dp(1.8f), dp(1.4f), iconPaint);
                canvas.drawCircle(cx - dp(0.8f), cy - dp(4.6f), dp(1.4f), iconPaint);
                canvas.drawCircle(cx + dp(3.4f), cy - dp(3.0f), dp(1.4f), iconPaint);
                canvas.drawCircle(cx - dp(2.2f), cy + dp(3.2f), dp(1.5f), iconPaint);
                break;
            }
            case ICON_TRANSLATE: {
                // Exact Gboard dual-card G-Translate icon
                iconPaint.setStrokeWidth(dp(1.65f));
                auxRect.set(cx - dp(8.5f), cy - dp(7.8f), cx + dp(1.8f), cy + dp(2.2f));
                iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                canvas.drawRoundRect(auxRect, dp(2f), dp(2f), iconPaint);

                iconPaint.setStyle(Paint.Style.STROKE);
                auxRect.set(cx - dp(1.2f), cy - dp(2.0f), cx + dp(8.5f), cy + dp(7.8f));
                canvas.drawRoundRect(auxRect, dp(2f), dp(2f), iconPaint);

                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(sp(8.8f));
                textPaint.setColor(theme.isDark ? Color.BLACK : Color.WHITE);
                canvas.drawText("G", cx - dp(3.3f), cy - dp(0.2f), textPaint);

                textPaint.setColor(color);
                textPaint.setTextSize(sp(8.5f));
                canvas.drawText("A", cx + dp(3.8f), cy + dp(5.6f), textPaint);
                break;
            }
            case ICON_SETTINGS: {
                // Crisp Material 8-tooth gear icon
                iconPaint.setStrokeWidth(dp(1.9f));
                canvas.drawCircle(cx, cy, dp(3.1f), iconPaint);
                iconPaint.setStrokeWidth(dp(2.3f));
                float rIn = dp(5.5f);
                float rOut = dp(8.4f);
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4.0;
                    float cos = (float) Math.cos(ang);
                    float sin = (float) Math.sin(ang);
                    canvas.drawLine(cx + cos * rIn, cy + sin * rIn, cx + cos * rOut, cy + sin * rOut, iconPaint);
                }
                iconPaint.setStrokeWidth(dp(1.8f));
                canvas.drawCircle(cx, cy, dp(6.3f), iconPaint);
                break;
            }
            case ICON_MIC: {
                iconPaint.setStrokeWidth(dp(1.85f));
                auxRect.set(cx - dp(2.8f), cy - dp(8.2f), cx + dp(2.8f), cy + dp(1.2f));
                canvas.drawRoundRect(auxRect, dp(2.8f), dp(2.8f), iconPaint);
                auxRect.set(cx - dp(6.0f), cy - dp(4.8f), cx + dp(6.0f), cy + dp(4.5f));
                canvas.drawArc(auxRect, 0f, 180f, false, iconPaint);
                canvas.drawLine(cx, cy + dp(4.5f), cx, cy + dp(8.0f), iconPaint);
                canvas.drawLine(cx - dp(3.2f), cy + dp(8.0f), cx + dp(3.2f), cy + dp(8.0f), iconPaint);
                break;
            }
            case ICON_SPEAKER_TTS: {
                iconPaint.setPathEffect(new CornerPathEffect(dp(1.2f)));
                iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                iconPaint.setStrokeWidth(dp(1.5f));
                path.reset();
                path.moveTo(cx - dp(7.8f), cy - dp(2.8f));
                path.lineTo(cx - dp(4.2f), cy - dp(2.8f));
                path.lineTo(cx - dp(0.2f), cy - dp(6.5f));
                path.lineTo(cx - dp(0.2f), cy + dp(6.5f));
                path.lineTo(cx - dp(4.2f), cy + dp(2.8f));
                path.lineTo(cx - dp(7.8f), cy + dp(2.8f));
                path.close();
                canvas.drawPath(path, iconPaint);

                iconPaint.setPathEffect(null);
                iconPaint.setStyle(Paint.Style.STROKE);
                iconPaint.setStrokeWidth(dp(1.8f));
                auxRect.set(cx - dp(1.5f), cy - dp(4.2f), cx + dp(4.2f), cy + dp(4.2f));
                canvas.drawArc(auxRect, -48f, 96f, false, iconPaint);
                auxRect.set(cx - dp(2.5f), cy - dp(7.4f), cx + dp(7.6f), cy + dp(7.4f));
                canvas.drawArc(auxRect, -50f, 100f, false, iconPaint);
                break;
            }
            case ICON_HEART_SPARK: {
                iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                iconPaint.setStrokeWidth(dp(1.4f));
                path.reset();
                path.moveTo(cx, cy + dp(6.8f));
                path.cubicTo(cx - dp(9.5f), cy + dp(0.5f), cx - dp(8.2f), cy - dp(7.2f), cx - dp(3.5f), cy - dp(7.2f));
                path.cubicTo(cx - dp(1.2f), cy - dp(7.2f), cx, cy - dp(5.0f), cx, cy - dp(4.2f));
                path.cubicTo(cx, cy - dp(5.0f), cx + dp(1.2f), cy - dp(7.2f), cx + dp(3.5f), cy - dp(7.2f));
                path.cubicTo(cx + dp(8.2f), cy - dp(7.2f), cx + dp(9.5f), cy + dp(0.5f), cx, cy + dp(6.8f));
                path.close();
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_FONTS: {
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(sp(14.5f));
                textPaint.setColor(color);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                canvas.drawText("Aa", cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
                break;
            }
            case ICON_LOCK_CLOSED: {
                iconPaint.setStrokeWidth(dp(1.75f));
                auxRect.set(cx - dp(5.8f), cy - dp(1.2f), cx + dp(5.8f), cy + dp(7.5f));
                iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                canvas.drawRoundRect(auxRect, dp(1.8f), dp(1.8f), iconPaint);
                iconPaint.setStyle(Paint.Style.STROKE);
                auxRect.set(cx - dp(3.6f), cy - dp(7.2f), cx + dp(3.6f), cy + dp(0.5f));
                canvas.drawArc(auxRect, 180f, 180f, false, iconPaint);
                break;
            }
            case ICON_LOCK_OPEN: {
                iconPaint.setStrokeWidth(dp(1.75f));
                auxRect.set(cx - dp(5.8f), cy - dp(1.2f), cx + dp(5.8f), cy + dp(7.5f));
                canvas.drawRoundRect(auxRect, dp(1.8f), dp(1.8f), iconPaint);
                auxRect.set(cx - dp(1.0f), cy - dp(7.4f), cx + dp(5.8f), cy + dp(0.2f));
                canvas.drawArc(auxRect, 180f, 180f, false, iconPaint);
                break;
            }
            case ICON_TRASH: {
                iconPaint.setStrokeWidth(dp(1.7f));
                canvas.drawLine(cx - dp(6.8f), cy - dp(4.5f), cx + dp(6.8f), cy - dp(4.5f), iconPaint);
                canvas.drawLine(cx - dp(2.5f), cy - dp(6.8f), cx + dp(2.5f), cy - dp(6.8f), iconPaint);
                auxRect.set(cx - dp(5.0f), cy - dp(4.5f), cx + dp(5.0f), cy + dp(7.2f));
                canvas.drawRoundRect(auxRect, dp(1.6f), dp(1.6f), iconPaint);
                canvas.drawLine(cx - dp(1.8f), cy - dp(1.5f), cx - dp(1.8f), cy + dp(4.2f), iconPaint);
                canvas.drawLine(cx + dp(1.8f), cy - dp(1.5f), cx + dp(1.8f), cy + dp(4.2f), iconPaint);
                break;
            }
            case ICON_SWAP_HORIZ: {
                iconPaint.setStrokeWidth(dp(1.85f));
                canvas.drawLine(cx - dp(6.5f), cy - dp(2.6f), cx + dp(6.5f), cy - dp(2.6f), iconPaint);
                canvas.drawLine(cx + dp(3.0f), cy - dp(5.6f), cx + dp(6.6f), cy - dp(2.6f), iconPaint);
                canvas.drawLine(cx + dp(3.0f), cy + dp(0.4f), cx + dp(6.6f), cy - dp(2.6f), iconPaint);

                canvas.drawLine(cx + dp(6.5f), cy + dp(2.8f), cx - dp(6.5f), cy + dp(2.8f), iconPaint);
                canvas.drawLine(cx - dp(3.0f), cy - dp(0.2f), cx - dp(6.6f), cy + dp(2.8f), iconPaint);
                canvas.drawLine(cx - dp(3.0f), cy + dp(5.8f), cx - dp(6.6f), cy + dp(2.8f), iconPaint);
                break;
            }
            case ICON_UNDO: {
                iconPaint.setStrokeWidth(dp(1.85f));
                auxRect.set(cx - dp(6.5f), cy - dp(3.2f), cx + dp(6.8f), cy + dp(6.5f));
                canvas.drawArc(auxRect, 195f, 155f, false, iconPaint);
                canvas.drawLine(cx - dp(6.6f), cy - dp(1.2f), cx - dp(2.5f), cy - dp(4.8f), iconPaint);
                canvas.drawLine(cx - dp(6.6f), cy - dp(1.2f), cx - dp(2.2f), cy + dp(2.0f), iconPaint);
                break;
            }
            case ICON_RESIZE: {
                iconPaint.setStrokeWidth(dp(1.8f));
                float d = dp(6.8f);
                float s = dp(2.8f);
                canvas.drawLine(cx - d, cy - d, cx - d + s, cy - d, iconPaint);
                canvas.drawLine(cx - d, cy - d, cx - d, cy - d + s, iconPaint);
                canvas.drawLine(cx + d, cy - d, cx + d - s, cy - d, iconPaint);
                canvas.drawLine(cx + d, cy - d, cx + d, cy - d + s, iconPaint);
                canvas.drawLine(cx - d, cy + d, cx - d + s, cy + d, iconPaint);
                canvas.drawLine(cx - d, cy + d, cx - d, cy + d - s, iconPaint);
                canvas.drawLine(cx + d, cy + d, cx + d - s, cy + d, iconPaint);
                canvas.drawLine(cx + d, cy + d, cx + d, cy + d - s, iconPaint);
                break;
            }
            case ICON_CLOCK: {
                iconPaint.setStrokeWidth(dp(1.75f));
                canvas.drawCircle(cx, cy, dp(7.6f), iconPaint);
                canvas.drawLine(cx, cy - dp(4.2f), cx, cy + dp(0.5f), iconPaint);
                canvas.drawLine(cx, cy + dp(0.5f), cx + dp(3.4f), cy + dp(2.6f), iconPaint);
                break;
            }
            case ICON_SPELLCHECK: {
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(sp(13f));
                textPaint.setColor(color);
                canvas.drawText("A", cx - dp(3.5f), cy + dp(2.5f), textPaint);
                iconPaint.setStrokeWidth(dp(1.8f));
                path.reset();
                path.moveTo(cx + dp(1.2f), cy + dp(2.5f));
                path.lineTo(cx + dp(3.8f), cy + dp(5.2f));
                path.lineTo(cx + dp(8.5f), cy - dp(0.5f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_HIDE: {
                iconPaint.setStrokeWidth(dp(1.95f));
                path.reset();
                path.moveTo(cx - dp(5.5f), cy - dp(2.2f));
                path.lineTo(cx, cy + dp(3.2f));
                path.lineTo(cx + dp(5.5f), cy - dp(2.2f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_GRID_MENU: {
                iconPaint.setStyle(Paint.Style.FILL);
                float size = dp(6.4f);
                float gap = dp(1.8f);
                float rad = dp(1.9f);
                auxRect.set(cx - gap - size, cy - gap - size, cx - gap, cy - gap);
                canvas.drawRoundRect(auxRect, rad, rad, iconPaint);
                auxRect.set(cx + gap, cy - gap - size, cx + gap + size, cy - gap);
                canvas.drawRoundRect(auxRect, rad, rad, iconPaint);
                auxRect.set(cx - gap - size, cy + gap, cx - gap, cy + gap + size);
                canvas.drawRoundRect(auxRect, rad, rad, iconPaint);
                auxRect.set(cx + gap, cy + gap, cx + gap + size, cy + gap + size);
                canvas.drawRoundRect(auxRect, rad, rad, iconPaint);
                break;
            }
            case ICON_CLOSE: {
                iconPaint.setStrokeWidth(dp(2.0f));
                float d = dp(5.5f);
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, iconPaint);
                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, iconPaint);
                break;
            }
            case ICON_BACK_ARROW: {
                iconPaint.setStrokeWidth(dp(2.0f));
                canvas.drawLine(cx - dp(6.5f), cy, cx + dp(6.5f), cy, iconPaint);
                path.reset();
                path.moveTo(cx - dp(1.2f), cy - dp(5.5f));
                path.lineTo(cx - dp(6.8f), cy);
                path.lineTo(cx - dp(1.2f), cy + dp(5.5f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_TEXT_EDIT: {
                iconPaint.setStrokeWidth(dp(1.8f));
                canvas.drawLine(cx, cy - dp(7f), cx, cy + dp(7f), iconPaint);
                canvas.drawLine(cx - dp(3f), cy - dp(7f), cx + dp(3f), cy - dp(7f), iconPaint);
                canvas.drawLine(cx - dp(3f), cy + dp(7f), cx + dp(3f), cy + dp(7f), iconPaint);
                iconPaint.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(cx - dp(8.8f), cy);
                path.lineTo(cx - dp(4.8f), cy - dp(3.3f));
                path.lineTo(cx - dp(4.8f), cy + dp(3.3f));
                path.close();
                canvas.drawPath(path, iconPaint);

                path.reset();
                path.moveTo(cx + dp(8.8f), cy);
                path.lineTo(cx + dp(4.8f), cy - dp(3.3f));
                path.lineTo(cx + dp(4.8f), cy + dp(3.3f));
                path.close();
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_CLIPBOARD: {
                iconPaint.setStrokeWidth(dp(1.75f));
                auxRect.set(cx - dp(6.5f), cy - dp(6.2f), cx + dp(6.5f), cy + dp(8f));
                canvas.drawRoundRect(auxRect, dp(1.8f), dp(1.8f), iconPaint);
                auxRect.set(cx - dp(3.2f), cy - dp(8.2f), cx + dp(3.2f), cy - dp(4.8f));
                iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                canvas.drawRoundRect(auxRect, dp(1.2f), dp(1.2f), iconPaint);
                iconPaint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx - dp(3.5f), cy - dp(0.8f), cx + dp(3.5f), cy - dp(0.8f), iconPaint);
                canvas.drawLine(cx - dp(3.5f), cy + dp(3.2f), cx + dp(2.2f), cy + dp(3.2f), iconPaint);
                break;
            }
            case ICON_ONE_HANDED: {
                iconPaint.setStrokeWidth(dp(1.75f));
                auxRect.set(cx - dp(5.5f), cy - dp(8f), cx + dp(5.5f), cy + dp(8f));
                canvas.drawRoundRect(auxRect, dp(1.8f), dp(1.8f), iconPaint);
                iconPaint.setStyle(Paint.Style.FILL);
                auxRect.set(cx - dp(3.5f), cy + dp(0.5f), cx + dp(3.5f), cy + dp(6f));
                canvas.drawRoundRect(auxRect, dp(1f), dp(1f), iconPaint);
                break;
            }
            case ICON_EXPAND_FULL: {
                iconPaint.setStrokeWidth(dp(1.85f));
                float d = dp(6.2f);
                float a = dp(3.2f);
                canvas.drawLine(cx - d, cy - d, cx - dp(1.8f), cy - dp(1.8f), iconPaint);
                canvas.drawLine(cx - d, cy - d, cx - d + a, cy - d, iconPaint);
                canvas.drawLine(cx - d, cy - d, cx - d, cy - d + a, iconPaint);
                canvas.drawLine(cx + d, cy - d, cx + dp(1.8f), cy - dp(1.8f), iconPaint);
                canvas.drawLine(cx + d, cy - d, cx + d - a, cy - d, iconPaint);
                canvas.drawLine(cx + d, cy - d, cx + d, cy - d + a, iconPaint);
                canvas.drawLine(cx - d, cy + d, cx - dp(1.8f), cy + dp(1.8f), iconPaint);
                canvas.drawLine(cx - d, cy + d, cx - d + a, cy + d, iconPaint);
                canvas.drawLine(cx - d, cy + d, cx - d, cy + d - a, iconPaint);
                canvas.drawLine(cx + d, cy + d, cx + dp(1.8f), cy + dp(1.8f), iconPaint);
                canvas.drawLine(cx + d, cy + d, cx + d - a, cy + d, iconPaint);
                canvas.drawLine(cx + d, cy + d, cx + d, cy + d - a, iconPaint);
                break;
            }
            case ICON_CHEVRON_LEFT: {
                path.reset();
                path.moveTo(cx + dp(2.5f), cy - dp(5f));
                path.lineTo(cx - dp(2.8f), cy);
                path.lineTo(cx + dp(2.5f), cy + dp(5f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_CHEVRON_RIGHT: {
                path.reset();
                path.moveTo(cx - dp(2.5f), cy - dp(5f));
                path.lineTo(cx + dp(2.8f), cy);
                path.lineTo(cx - dp(2.5f), cy + dp(5f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_CHEVRON_UP: {
                path.reset();
                path.moveTo(cx - dp(5f), cy + dp(2.5f));
                path.lineTo(cx, cy - dp(2.8f));
                path.lineTo(cx + dp(5f), cy + dp(2.5f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_CHEVRON_DOWN: {
                path.reset();
                path.moveTo(cx - dp(5f), cy - dp(2.5f));
                path.lineTo(cx, cy + dp(2.8f));
                path.lineTo(cx + dp(5f), cy - dp(2.5f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_JUMP_START: {
                canvas.drawLine(cx - dp(5.5f), cy - dp(5.5f), cx - dp(5.5f), cy + dp(5.5f), iconPaint);
                path.reset();
                path.moveTo(cx + dp(4.5f), cy - dp(5f));
                path.lineTo(cx - dp(1.5f), cy);
                path.lineTo(cx + dp(4.5f), cy + dp(5f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_JUMP_END: {
                canvas.drawLine(cx + dp(5.5f), cy - dp(5.5f), cx + dp(5.5f), cy + dp(5.5f), iconPaint);
                path.reset();
                path.moveTo(cx - dp(4.5f), cy - dp(5f));
                path.lineTo(cx + dp(1.5f), cy);
                path.lineTo(cx - dp(4.5f), cy + dp(5f));
                canvas.drawPath(path, iconPaint);
                break;
            }
            case ICON_NUMPAD: {
                iconPaint.setStyle(Paint.Style.FILL);
                float r = dp(1.7f);
                float step = dp(5.2f);
                for (int row = -1; row <= 1; row++) {
                    for (int col = -1; col <= 1; col++) {
                        canvas.drawCircle(cx + col * step, cy + row * step, r, iconPaint);
                    }
                }
                break;
            }
            case ICON_SPACE_BAR: {
                iconPaint.setStrokeWidth(dp(2.0f));
                path.reset();
                path.moveTo(cx - dp(7f), cy - dp(1.5f));
                path.lineTo(cx - dp(7f), cy + dp(2.2f));
                path.lineTo(cx + dp(7f), cy + dp(2.2f));
                path.lineTo(cx + dp(7f), cy - dp(1.5f));
                canvas.drawPath(path, iconPaint);
                break;
            }
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
