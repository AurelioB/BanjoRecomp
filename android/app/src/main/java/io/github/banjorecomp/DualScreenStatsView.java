package io.github.banjorecomp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public final class DualScreenStatsView extends View {
    public interface DebugAreaButtonHandler {
        boolean onDebugAreaButton(int direction);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Path jiggyPath = new Path();
    private final Path jiggyHolePath = new Path();
    private final GestureDetector gestureDetector;
    private final DebugAreaButtonHandler debugAreaButtonHandler;
    private final RectF debugPrevButtonRect = new RectF();
    private final RectF debugNextButtonRect = new RectF();

    private DualScreenStats stats;
    private boolean gameplayActive;
    private boolean userHidden;
    private int displayMode = DualScreenStats.DISPLAY_BLACK;
    private BanjoSpriteTheme theme = BanjoSpriteTheme.EMPTY;
    private LinearGradient caveGradient;
    private RadialGradient floorGlow;
    private int gradientWidth;
    private int gradientHeight;
    private long irisStartMs;
    private long irisDurationMs = IRIS_DURATION_IN_SLOW_MS;
    private int irisMode = IRIS_NONE;
    private int pendingDisplayMode = -1;
    private boolean pendingGameplayActive;
    private DualScreenStats pendingStats;
    private long pendingIrisInDurationMs = IRIS_DURATION_IN_SLOW_MS;
    private Bitmap cachedLevelBackdrop;
    private Bitmap cachedLevelBackdropSource;
    private int cachedLevelBackdropWidth;
    private int cachedLevelBackdropHeight;
    private float jiggyRotationBaseDegrees;
    private boolean startupBlackHold = true;
    private boolean startupRevealScheduled;
    private boolean gameTransitionHoldingBlack;
    private int lastGameTransitionPhase = DualScreenStats.GAME_TRANSITION_NONE;

    private static final int IRIS_NONE = 0;
    private static final int IRIS_IN = 1;
    private static final int IRIS_OUT = 2;
    private static final int IRIS_OUT_TO_PENDING = 3;
    private static final long IRIS_DURATION_IN_SLOW_MS = 2500L;
    private static final long IRIS_DURATION_OUT_FAST_MS = 900L;
    private static final long STARTUP_BLACK_HOLD_MS = 350L;
    private static final int IRIS_FRAME_DELAY_MS = 16;
    private static final int STEADY_REDRAW_DELAY_MS = 130;
    private static final int MENU_SKY_BLUE = Color.rgb(0x1F, 0x63, 0xC2);
    private static final int[] TITLE_LOGO_TEXTURES = {
            19, 18, 17, 16, 15, 14, 13,
            12, 11, 10, 9, 8, 7, 6, 5,
            25, 24, 23, 22, 21, 20,
            0, 1, 4, 3, 2
    };
    private static final int[] TITLE_LOGO_RECTS = {
            200, -50, 300, 50,
            100, -50, 200, 50,
            0, -50, 100, 50,
            -100, -50, 0, 50,
            -200, -50, -100, 50,
            -300, -50, -200, 50,
            -400, -50, -300, 50,
            300, 50, 400, 150,
            200, 50, 300, 150,
            100, 50, 200, 150,
            0, 50, 100, 150,
            -100, 50, 0, 150,
            -200, 50, -100, 150,
            -300, 50, -200, 150,
            -400, 50, -300, 150,
            200, -150, 300, -50,
            100, -150, 200, -50,
            0, -150, 100, -50,
            -100, -150, 0, -50,
            -200, -150, -100, -50,
            -300, -150, -200, -50,
            300, -50, 400, 50,
            300, -150, 400, -50,
            -100, 150, 0, 250,
            -200, 150, -100, 250,
            -300, 150, -200, 250
    };
    private static final float TITLE_LOGO_MIN_X = -400.0f;
    private static final float TITLE_LOGO_MAX_X = 400.0f;
    private static final float TITLE_LOGO_MIN_Y = -150.0f;
    private static final float TITLE_LOGO_MAX_Y = 250.0f;

    public DualScreenStatsView(Context context) {
        this(context, null);
    }

    public DualScreenStatsView(Context context, DebugAreaButtonHandler debugAreaButtonHandler) {
        super(context);
        this.debugAreaButtonHandler = debugAreaButtonHandler;
        numberPaint.setColor(Color.rgb(100, 220, 255));
        numberPaint.setTextAlign(Paint.Align.LEFT);
        numberPaint.setFakeBoldText(true);
        numberPaint.setShadowLayer(5.0f, 3.0f, 3.0f, Color.rgb(5, 20, 70));

        labelPaint.setColor(Color.rgb(238, 236, 255));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        labelPaint.setShadowLayer(4.0f, 2.0f, 3.0f, Color.BLACK);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        setBackgroundColor(Color.BLACK);
        setClickable(true);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleUserHidden();
                return true;
            }
        });
    }

    public void showBlank() {
        transitionToContent(DualScreenStats.DISPLAY_BLACK, stats, false, displayMode != DualScreenStats.DISPLAY_BLACK);
    }

    public void showLogo() {
        transitionToContent(DualScreenStats.DISPLAY_LOGO, stats, false, displayMode != DualScreenStats.DISPLAY_LOGO);
    }

    public void updateStats(DualScreenStats stats) {
        boolean changed = this.stats == null || !this.stats.sameValues(stats);
        boolean backgroundChanged = this.stats != null && this.stats.levelId != stats.levelId;
        boolean modeChanged = displayMode != stats.displayMode;
        boolean nextGameplayActive = stats.displayMode == DualScreenStats.DISPLAY_STATS;

        if (stats.gameTransitionPhase == DualScreenStats.GAME_TRANSITION_OUT) {
            if (!gameTransitionHoldingBlack && !userHidden) {
                startIris(IRIS_OUT, IRIS_DURATION_OUT_FAST_MS);
            }
            gameTransitionHoldingBlack = true;
            lastGameTransitionPhase = DualScreenStats.GAME_TRANSITION_OUT;
            invalidate();
            return;
        }

        if (gameTransitionHoldingBlack) {
            if (stats.displayMode == DualScreenStats.DISPLAY_STATS
                    && (stats.gameTransitionPhase == DualScreenStats.GAME_TRANSITION_IN
                    || stats.gameTransitionPhase == DualScreenStats.GAME_TRANSITION_NONE)) {
                gameTransitionHoldingBlack = false;
                applyContent(stats.displayMode, stats, nextGameplayActive);
                if (!userHidden) {
                    startIris(IRIS_IN, IRIS_DURATION_IN_SLOW_MS);
                }
                lastGameTransitionPhase = stats.gameTransitionPhase == DualScreenStats.GAME_TRANSITION_IN
                        ? DualScreenStats.GAME_TRANSITION_IN
                        : DualScreenStats.GAME_TRANSITION_NONE;
                invalidate();
                return;
            }

            lastGameTransitionPhase = DualScreenStats.GAME_TRANSITION_OUT;
            invalidate();
            return;
        }

        if (stats.gameTransitionPhase == DualScreenStats.GAME_TRANSITION_IN) {
            applyContent(stats.displayMode, stats, nextGameplayActive);
            if (!userHidden && lastGameTransitionPhase != DualScreenStats.GAME_TRANSITION_IN) {
                startIris(IRIS_IN, IRIS_DURATION_IN_SLOW_MS);
            }
            lastGameTransitionPhase = DualScreenStats.GAME_TRANSITION_IN;
            invalidate();
            return;
        }

        if (lastGameTransitionPhase != DualScreenStats.GAME_TRANSITION_NONE) {
            lastGameTransitionPhase = DualScreenStats.GAME_TRANSITION_NONE;
        }

        if (modeChanged || backgroundChanged) {
            transitionToContent(stats.displayMode, stats, nextGameplayActive, true);
            return;
        }

        applyContent(stats.displayMode, stats, nextGameplayActive);
        if (changed || irisMode != IRIS_NONE) {
            if (isStatsVisible() || irisMode != IRIS_NONE) {
                invalidate();
            }
        }
    }

    public void setTheme(BanjoSpriteTheme theme) {
        this.theme = theme == null ? BanjoSpriteTheme.EMPTY : theme;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (handleDebugAreaButtonTouch(event)) {
            return true;
        }
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    private boolean handleDebugAreaButtonTouch(MotionEvent event) {
        if (!BuildConfig.BANJO_DUAL_SCREEN_DEBUG || debugAreaButtonHandler == null || stats == null || event == null) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            ensureDebugButtonRects();
            if (debugPrevButtonRect.contains(event.getX(), event.getY())) {
                return debugAreaButtonHandler.onDebugAreaButton(-1);
            }
            if (debugNextButtonRect.contains(event.getX(), event.getY())) {
                return debugAreaButtonHandler.onDebugAreaButton(1);
            }
        }
        return false;
    }

    private void toggleUserHidden() {
        if (userHidden) {
            userHidden = false;
            if (displayMode != DualScreenStats.DISPLAY_BLACK) {
                startIris(IRIS_IN, IRIS_DURATION_IN_SLOW_MS);
            } else {
                irisMode = IRIS_NONE;
            }
        } else {
            userHidden = true;
            if (displayMode != DualScreenStats.DISPLAY_BLACK) {
                startIris(IRIS_OUT, IRIS_DURATION_OUT_FAST_MS);
            } else {
                irisMode = IRIS_NONE;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        long now = System.currentTimeMillis();

        if (irisMode != IRIS_NONE) {
            float progress = Math.min(1.0f, Math.max(0.0f, (now - irisStartMs) / (float) irisDurationMs));
            if (progress >= 1.0f) {
                finishIris(canvas, now, width, height);
                return;
            }

            drawCurrentContent(canvas, now, width, height);
            float visibleFraction = irisMode == IRIS_IN ? progress : 1.0f - progress;
            drawIrisMask(canvas, width, height, visibleFraction, progress);
            postInvalidateDelayed(IRIS_FRAME_DELAY_MS);
            return;
        }

        if (gameTransitionHoldingBlack && irisMode == IRIS_NONE) {
            drawBlack(canvas, width, height);
            return;
        }

        if (lastGameTransitionPhase == DualScreenStats.GAME_TRANSITION_OUT) {
            drawBlack(canvas, width, height);
            return;
        }

        if (stats == null) {
            drawCurrentContent(canvas, now, width, height);
            return;
        }

        if (!isStatsVisible()) {
            drawCurrentContent(canvas, now, width, height);
            return;
        }

        drawStatsContent(canvas, now);
        postInvalidateDelayed(STEADY_REDRAW_DELAY_MS);
    }

    private void finishIris(Canvas canvas, long now, int width, int height) {
        int finishedMode = irisMode;
        irisMode = IRIS_NONE;
        jiggyRotationBaseDegrees -= 90.0f;
        if (jiggyRotationBaseDegrees <= -360.0f) {
            jiggyRotationBaseDegrees += 360.0f;
        }

        if (finishedMode == IRIS_OUT_TO_PENDING) {
            applyPendingContent();
            drawBlack(canvas, width, height);
            if (displayMode != DualScreenStats.DISPLAY_BLACK && !userHidden) {
                startIris(IRIS_IN, pendingIrisInDurationMs);
                postInvalidateDelayed(IRIS_FRAME_DELAY_MS);
            }
            return;
        }

        if (finishedMode == IRIS_OUT) {
            drawBlack(canvas, width, height);
            return;
        }

        drawCurrentContent(canvas, now, width, height);
        if (isStatsVisible()) {
            postInvalidateDelayed(STEADY_REDRAW_DELAY_MS);
        }
    }

    private boolean isStatsVisible() {
        return displayMode == DualScreenStats.DISPLAY_STATS && gameplayActive && !userHidden;
    }

    private void applyContent(int newDisplayMode, DualScreenStats newStats, boolean newGameplayActive) {
        displayMode = newDisplayMode;
        stats = newStats;
        gameplayActive = newGameplayActive;
    }

    private void transitionToContent(int newDisplayMode, DualScreenStats newStats, boolean newGameplayActive, boolean animate) {
        if (userHidden) {
            applyContent(newDisplayMode, newStats, newGameplayActive);
            irisMode = IRIS_NONE;
            invalidate();
            return;
        }

        int oldMode = displayMode;
        boolean oldBlack = oldMode == DualScreenStats.DISPLAY_BLACK;
        boolean newBlack = newDisplayMode == DualScreenStats.DISPLAY_BLACK;
        if (!animate) {
            startupBlackHold = false;
            applyContent(newDisplayMode, newStats, newGameplayActive);
            invalidate();
            return;
        }

        if (startupBlackHold && oldBlack && !newBlack) {
            setPendingContent(newDisplayMode, newStats, newGameplayActive, IRIS_DURATION_IN_SLOW_MS);
            if (!startupRevealScheduled) {
                startupRevealScheduled = true;
                postDelayed(this::startStartupReveal, STARTUP_BLACK_HOLD_MS);
            }
            invalidate();
            return;
        }

        startupBlackHold = false;

        if (oldMode == newDisplayMode) {
            if (oldBlack) {
                applyContent(newDisplayMode, newStats, newGameplayActive);
                invalidate();
            } else {
                setPendingContent(newDisplayMode, newStats, newGameplayActive, IRIS_DURATION_IN_SLOW_MS);
                startIris(IRIS_OUT_TO_PENDING, IRIS_DURATION_OUT_FAST_MS);
                invalidate();
            }
            return;
        }

        if (oldBlack && !newBlack) {
            applyContent(newDisplayMode, newStats, newGameplayActive);
            startIris(IRIS_IN, IRIS_DURATION_IN_SLOW_MS);
        } else if (newBlack) {
            setPendingContent(newDisplayMode, newStats, newGameplayActive, 0);
            startIris(IRIS_OUT_TO_PENDING, IRIS_DURATION_OUT_FAST_MS);
        } else {
            setPendingContent(newDisplayMode, newStats, newGameplayActive, IRIS_DURATION_IN_SLOW_MS);
            startIris(IRIS_OUT_TO_PENDING, IRIS_DURATION_OUT_FAST_MS);
        }
        invalidate();
    }

    private void setPendingContent(int newDisplayMode, DualScreenStats newStats, boolean newGameplayActive, long irisInDurationMs) {
        pendingDisplayMode = newDisplayMode;
        pendingStats = newStats;
        pendingGameplayActive = newGameplayActive;
        pendingIrisInDurationMs = irisInDurationMs;
    }

    private void applyPendingContent() {
        if (pendingDisplayMode >= 0) {
            applyContent(pendingDisplayMode, pendingStats, pendingGameplayActive);
        }
        pendingDisplayMode = -1;
        pendingStats = null;
        pendingGameplayActive = false;
    }

    private void startStartupReveal() {
        startupRevealScheduled = false;
        startupBlackHold = false;
        if (userHidden || displayMode != DualScreenStats.DISPLAY_BLACK || irisMode != IRIS_NONE || pendingDisplayMode < 0) {
            return;
        }

        long irisInDurationMs = pendingIrisInDurationMs;
        applyPendingContent();
        startIris(IRIS_IN, irisInDurationMs);
        invalidate();
    }

    private void startIris(int mode, long durationMs) {
        irisMode = mode;
        irisDurationMs = Math.max(IRIS_FRAME_DELAY_MS, durationMs);
        irisStartMs = System.currentTimeMillis();
    }

    private void drawStatsContent(Canvas canvas, long now) {
        drawStartMenuBackdrop(canvas, getWidth(), getHeight());
        drawRowLayout(canvas, now);
        drawDebugAreaInfo(canvas);
    }

    private void drawCurrentContent(Canvas canvas, long now, int width, int height) {
        if (userHidden && irisMode != IRIS_OUT) {
            drawBlack(canvas, width, height);
        } else if (displayMode == DualScreenStats.DISPLAY_STATS && gameplayActive) {
            drawStatsContent(canvas, now);
        } else if (displayMode == DualScreenStats.DISPLAY_LOGO) {
            drawLogo(canvas, width, height);
        } else {
            drawBlack(canvas, width, height);
        }
    }

    private void drawLogo(Canvas canvas, int width, int height) {
        paint.setShader(null);
        paint.setColor(MENU_SKY_BLUE);
        paint.setAlpha(255);
        canvas.drawRect(0, 0, width, height, paint);

        if (drawTitleLogoFromRomTiles(canvas, width, height)) {
            return;
        }

        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        float titleHeight = scale(120);
        if (!drawSpriteTextCentered(canvas, "BANJO", centerX, centerY - scale(30), titleHeight, width * 0.86f)) {
            drawLogoText(canvas, "BANJO", centerX, centerY - scale(30), scale(124));
        }
        if (!drawSpriteTextCentered(canvas, "KAZOOIE", centerX, centerY + scale(105), titleHeight * 0.86f, width * 0.86f)) {
            drawLogoText(canvas, "KAZOOIE", centerX, centerY + scale(105), scale(104));
        }
    }

    private void drawLogoText(Canvas canvas, String text, float x, float baseline, float size) {
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        labelPaint.setTextSize(size);
        labelPaint.setStyle(Paint.Style.STROKE);
        labelPaint.setStrokeWidth(scale(8));
        labelPaint.setColor(Color.rgb(96, 46, 8));
        canvas.drawText(text, x, baseline, labelPaint);
        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(Color.rgb(255, 222, 45));
        canvas.drawText(text, x, baseline, labelPaint);
    }

    private boolean drawTitleLogoFromRomTiles(Canvas canvas, int width, int height) {
        if (theme.frame("title_logo_tile_0", 0, 0) == null) {
            return false;
        }
        float modelWidth = TITLE_LOGO_MAX_X - TITLE_LOGO_MIN_X;
        float modelHeight = TITLE_LOGO_MAX_Y - TITLE_LOGO_MIN_Y;
        float scaleFactor = Math.min(width * 0.88f / modelWidth, height * 0.58f / modelHeight);
        float logoWidth = modelWidth * scaleFactor;
        float logoHeight = modelHeight * scaleFactor;
        float left = (width - logoWidth) / 2.0f;
        float top = (height - logoHeight) / 2.0f;
        paint.setShader(null);
        paint.setAlpha(255);
        paint.setFilterBitmap(true);
        paint.setDither(true);

        for (int i = 0; i < TITLE_LOGO_TEXTURES.length; i++) {
            Bitmap tile = theme.frame("title_logo_tile_" + TITLE_LOGO_TEXTURES[i], 0, 0);
            if (tile == null) {
                return false;
            }
            int o = i * 4;
            float x0 = TITLE_LOGO_RECTS[o];
            float y0 = TITLE_LOGO_RECTS[o + 1];
            float x1 = TITLE_LOGO_RECTS[o + 2];
            float y1 = TITLE_LOGO_RECTS[o + 3];
            rect.set(
                    left + (x0 - TITLE_LOGO_MIN_X) * scaleFactor,
                    top + (TITLE_LOGO_MAX_Y - y1) * scaleFactor,
                    left + (x1 - TITLE_LOGO_MIN_X) * scaleFactor,
                    top + (TITLE_LOGO_MAX_Y - y0) * scaleFactor);
            canvas.save();
            canvas.scale(1.0f, -1.0f, rect.centerX(), rect.centerY());
            canvas.drawBitmap(tile, null, rect, paint);
            canvas.restore();
        }
        return true;
    }

    private void drawBlack(Canvas canvas, int width, int height) {
        paint.setShader(null);
        paint.setColor(Color.BLACK);
        paint.setAlpha(255);
        canvas.drawRect(0, 0, width, height, paint);
    }

    private void drawIrisMask(Canvas canvas, int width, int height, float visibleFraction, float progress) {
        int layer = canvas.saveLayer(0.0f, 0.0f, width, height, null);
        overlayPaint.setShader(null);
        overlayPaint.setStyle(Paint.Style.FILL);
        overlayPaint.setColor(Color.BLACK);
        overlayPaint.setAlpha(255);
        canvas.drawRect(0, 0, width, height, overlayPaint);

        if (visibleFraction > 0.001f) {
            float maxSize = (float) Math.hypot(width, height) * 4.0f;
            float size = maxSize * visibleFraction;
            buildJiggyTransitionPath();
            canvas.save();
            canvas.translate(width / 2.0f, height / 2.0f);
            canvas.rotate(jiggyRotationBaseDegrees - 90.0f * progress);
            canvas.scale(size, size);
            canvas.drawPath(jiggyPath, clearPaint);
            canvas.restore();
        }

        canvas.restoreToCount(layer);
    }

    private void buildJiggyTransitionPath() {
        jiggyPath.reset();
        jiggyPath.setFillType(Path.FillType.WINDING);
        jiggyPath.addRect(-0.36f, -0.25f, 0.24f, 0.50f, Path.Direction.CW);
        jiggyPath.addCircle(-0.07f, -0.38f, 0.17f, Path.Direction.CW);
        jiggyPath.addCircle(0.37f, 0.05f, 0.17f, Path.Direction.CW);

        jiggyHolePath.reset();
        jiggyHolePath.addCircle(-0.36f, 0.05f, 0.17f, Path.Direction.CW);
        jiggyHolePath.addCircle(-0.02f, 0.50f, 0.17f, Path.Direction.CW);
        jiggyPath.op(jiggyHolePath, Path.Op.DIFFERENCE);
    }


    private void drawStartMenuBackdrop(Canvas canvas, int width, int height) {
        Bitmap levelPortrait = stats == null ? null : theme.frame(levelPortraitKey(stats.levelId), 0, 0);
        if (levelPortrait != null) {
            drawLevelPortraitBackdrop(canvas, levelPortrait, width, height);
            return;
        }

        Bitmap fallbackPortrait = theme.frame("level_portrait_mm", 0, 0);
        if (fallbackPortrait != null) {
            drawLevelPortraitBackdrop(canvas, fallbackPortrait, width, height);
            return;
        }

        Bitmap grass = theme.frame("background_grass", 0, 0);
        if (grass != null) {
            drawGrassTextureBackdrop(canvas, grass, width, height);
            return;
        }

        if (caveGradient == null || gradientWidth != width || gradientHeight != height) {
            gradientWidth = width;
            gradientHeight = height;
            caveGradient = new LinearGradient(0, 0, width, height,
                    new int[] {
                            Color.rgb(22, 6, 5),
                            Color.rgb(108, 20, 12),
                            Color.rgb(39, 92, 38),
                            Color.rgb(72, 30, 7)
                    },
                    new float[] {0.0f, 0.38f, 0.63f, 1.0f},
                    Shader.TileMode.CLAMP);
            floorGlow = new RadialGradient(width * 0.52f, height * 0.72f, width * 0.48f,
                    Color.argb(160, 245, 126, 25), Color.argb(0, 40, 5, 0), Shader.TileMode.CLAMP);
        }

        paint.setShader(caveGradient);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        paint.setShader(floorGlow);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        // Coarse cave-wall shapes: enough to evoke the start menu without needing a captured game frame.
        overlayPaint.setStyle(Paint.Style.FILL);
        overlayPaint.setColor(Color.argb(120, 5, 5, 8));
        path.reset();
        path.moveTo(0, 0);
        path.lineTo(scale(260), 0);
        path.lineTo(scale(150), scale(1030));
        path.lineTo(0, getHeight());
        path.close();
        canvas.drawPath(path, overlayPaint);

        overlayPaint.setColor(Color.argb(130, 12, 9, 12));
        path.reset();
        path.moveTo(getWidth(), 0);
        path.lineTo(scale(970), 0);
        path.lineTo(scale(1080), getHeight());
        path.lineTo(getWidth(), getHeight());
        path.close();
        canvas.drawPath(path, overlayPaint);

        overlayPaint.setColor(Color.argb(85, 0, 180, 60));
        path.reset();
        path.moveTo(scale(660), scale(80));
        path.lineTo(scale(865), scale(45));
        path.lineTo(scale(805), scale(355));
        path.lineTo(scale(615), scale(350));
        path.close();
        canvas.drawPath(path, overlayPaint);

        overlayPaint.setColor(Color.argb(115, 0, 0, 0));
        canvas.drawRect(0, 0, width, height, overlayPaint);
    }

    private void drawLevelPortraitBackdrop(Canvas canvas, Bitmap portrait, int width, int height) {
        if (cachedLevelBackdrop == null
                || cachedLevelBackdropSource != portrait
                || cachedLevelBackdropWidth != width
                || cachedLevelBackdropHeight != height) {
            cachedLevelBackdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            cachedLevelBackdropSource = portrait;
            cachedLevelBackdropWidth = width;
            cachedLevelBackdropHeight = height;

            Canvas backdropCanvas = new Canvas(cachedLevelBackdrop);
            paint.setShader(null);
            paint.setAlpha(255);
            paint.setColor(Color.BLACK);
            backdropCanvas.drawRect(0, 0, width, height, paint);

            float scale = Math.max(width / (float) portrait.getWidth(), height / (float) portrait.getHeight());
            float drawWidth = portrait.getWidth() * scale;
            float drawHeight = portrait.getHeight() * scale;
            rect.set((width - drawWidth) / 2.0f,
                    (height - drawHeight) / 2.0f,
                    (width + drawWidth) / 2.0f,
                    (height + drawHeight) / 2.0f);
            backdropCanvas.drawBitmap(portrait, null, rect, paint);
        }

        paint.setShader(null);
        paint.setAlpha(255);
        canvas.drawBitmap(cachedLevelBackdrop, 0, 0, paint);

        // Match the old readability level: the level art is the backdrop, not the foreground.
        overlayPaint.setStyle(Paint.Style.FILL);
        overlayPaint.setColor(Color.argb(145, 0, 0, 0));
        canvas.drawRect(0, 0, width, height, overlayPaint);
    }

    private String levelPortraitKey(int mapId) {
        return DualScreenDebugAreas.backdropKey(mapId);
    }

    private void drawDebugAreaInfo(Canvas canvas) {
        if (!BuildConfig.BANJO_DUAL_SCREEN_DEBUG || stats == null) {
            return;
        }

        DualScreenDebugAreas.Area area = DualScreenDebugAreas.areaForMapId(stats.levelId);
        int index = DualScreenDebugAreas.indexForMapId(stats.levelId);
        String name = area == null ? "UNKNOWN/FALLBACK" : area.name;
        int assetId = area == null ? 0x14AA : area.assetId;
        int textureIndex = area == null ? 0 : area.textureIndex;
        String line1 = "DEBUG AREA " + (index >= 0 ? (index + 1) + "/" + DualScreenDebugAreas.AREAS.length : "?/"
                + DualScreenDebugAreas.AREAS.length) + "  map=0x" + Integer.toHexString(stats.levelId).toUpperCase();
        String line2 = name + "  asset=0x" + Integer.toHexString(assetId).toUpperCase()
                + "  tex=" + textureIndex;
        String line3 = "DPAD or touch arrows: previous/next area";

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(185, 0, 0, 0));
        rect.set(scale(22), scale(18), getWidth() - scale(22), scale(126));
        canvas.drawRoundRect(rect, scale(12), scale(12), paint);

        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setFakeBoldText(false);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(scale(28));
        labelPaint.setShadowLayer(3.0f, 1.0f, 1.0f, Color.BLACK);
        canvas.drawText(line1, scale(42), scale(52), labelPaint);
        canvas.drawText(line2, scale(42), scale(84), labelPaint);
        labelPaint.setTextSize(scale(22));
        labelPaint.setColor(Color.rgb(190, 230, 255));
        canvas.drawText(line3, scale(42), scale(112), labelPaint);

        drawDebugAreaButtons(canvas);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        labelPaint.setColor(Color.rgb(238, 236, 255));
        labelPaint.setShadowLayer(4.0f, 2.0f, 3.0f, Color.BLACK);
    }

    private void ensureDebugButtonRects() {
        float margin = scale(24);
        float width = scale(150);
        float height = scale(210);
        float centerY = getHeight() * 0.55f;
        debugPrevButtonRect.set(margin, centerY - height / 2.0f, margin + width, centerY + height / 2.0f);
        debugNextButtonRect.set(getWidth() - margin - width, centerY - height / 2.0f, getWidth() - margin, centerY + height / 2.0f);
    }

    private void drawDebugAreaButtons(Canvas canvas) {
        ensureDebugButtonRects();
        drawDebugAreaButton(canvas, debugPrevButtonRect, "‹");
        drawDebugAreaButton(canvas, debugNextButtonRect, "›");
    }

    private void drawDebugAreaButton(Canvas canvas, RectF bounds, String label) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 0, 0, 0));
        canvas.drawRoundRect(bounds, scale(18), scale(18), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(scale(4));
        paint.setColor(Color.argb(230, 190, 230, 255));
        canvas.drawRoundRect(bounds, scale(18), scale(18), paint);
        paint.setStyle(Paint.Style.FILL);

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        labelPaint.setTextSize(scale(128));
        labelPaint.setColor(Color.WHITE);
        labelPaint.setShadowLayer(5.0f, 2.0f, 2.0f, Color.BLACK);
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        float baseline = bounds.centerY() - (fm.ascent + fm.descent) / 2.0f;
        canvas.drawText(label, bounds.centerX(), baseline, labelPaint);
    }

    private void drawGrassTextureBackdrop(Canvas canvas, Bitmap grass, int width, int height) {
        float tile = scale(190);
        paint.setShader(null);
        paint.setAlpha(255);
        for (float y = 0; y < height + tile; y += tile) {
            for (float x = 0; x < width + tile; x += tile) {
                rect.set(x, y, x + tile, y + tile);
                canvas.drawBitmap(grass, null, rect, paint);
            }
        }
        paint.setAlpha(255);

        // Keep the stats readable over the high-contrast ROM texture.
        overlayPaint.setStyle(Paint.Style.FILL);
        overlayPaint.setColor(Color.argb(130, 0, 0, 0));
        canvas.drawRect(0, 0, width, height, overlayPaint);
        overlayPaint.setColor(Color.argb(90, 58, 16, 0));
        canvas.drawRect(0, 0, width, height, overlayPaint);
    }

    private void drawRowLayout(Canvas canvas, long now) {
        boolean hideEarlySpiralStats = shouldHideSpiralStatsUntilLair();
        boolean globalProgressRow = shouldDrawGlobalProgressRow();
        drawHealthRow(canvas, now, scale(112));
        if (!hideEarlySpiralStats) {
            drawTwoStatRow(canvas, now, scale(268), "note", stats.notes, scale(112), "egg", stats.eggs, scale(112));
            drawTwoStatRow(canvas, now, scale(418), "red_feather", stats.redFeathers, scale(116), "gold_feather", stats.goldFeathers, scale(112));
            drawTwoStatRow(canvas, now, scale(568), "jiggy", stats.jiggies, scale(118), "mumbo", stats.mumboTokens, scale(116));
            if (globalProgressRow) {
                drawGlobalProgressRow(canvas, now, scale(742));
            } else {
                drawJinjoRow(canvas, now, scale(742));
            }
        }
        drawFooter(canvas, scale(990));
    }

    private boolean shouldHideSpiralStatsUntilLair() {
        if (stats == null) {
            return false;
        }
        return isSpiralMountainMap(stats.levelId) && !stats.reachedGruntysLair;
    }

    private boolean shouldDrawGlobalProgressRow() {
        if (stats == null) {
            return false;
        }
        int mapId = stats.levelId;
        return mapId == 0x91 // save-file select
                || isSpiralMountainMap(mapId)
                || isGruntysLairMap(mapId);
    }

    private boolean isSpiralMountainMap(int mapId) {
        return mapId == 0x01 || mapId == 0x8C;
    }

    private boolean isGruntysLairMap(int mapId) {
        return (mapId >= 0x69 && mapId <= 0x7A) // Grunty's Lair rooms
                || mapId == 0x80 // Furnace Fun entrance
                || mapId == 0x8E // Furnace Fun
                || mapId == 0x90 // Battlements
                || mapId == 0x93; // Dingpot
    }

    private void drawGlobalProgressRow(Canvas canvas, long now, float y) {
        int completion = percent(stats.totalJiggies + stats.totalNotes + stats.totalHoneycombs, 100 + 900 + 24);
        drawCompletionText(canvas, completion, y + scale(30), scale(70));
    }

    private int percent(int value, int max) {
        if (max <= 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(value, max));
        return (clamped * 100 + max / 2) / max;
    }

    private void drawCompletionText(Canvas canvas, int value, float baseline, float textSize) {
        String digits = Integer.toString(Math.max(0, Math.min(value, 100)));
        String suffix = " COMPLETE";
        float digitsWidth = measureSpriteText(digits, textSize);
        if (digitsWidth <= 0.0f) {
            digitsWidth = digits.length() * textSize * 0.66f;
        }
        float percentWidth = textSize * 0.72f;
        float spaceWidth = textSize * 0.35f;
        float suffixWidth = measureSpriteText(suffix, textSize);
        if (suffixWidth <= 0.0f) {
            suffixWidth = suffix.length() * textSize * 0.55f;
        }
        float startX = (getWidth() - (digitsWidth + spaceWidth + percentWidth + suffixWidth)) / 2.0f;
        drawBlueNumber(canvas, digits, startX, baseline, textSize);
        float percentX = startX + digitsWidth + spaceWidth;
        if (!drawGamePercentSymbol(canvas, percentX, baseline, textSize)) {
            labelPaint.setTextAlign(Paint.Align.LEFT);
            labelPaint.setFakeBoldText(true);
            labelPaint.setTextSize(textSize * 0.65f);
            labelPaint.setColor(Color.rgb(255, 221, 34));
            labelPaint.setShadowLayer(4.0f, 2.0f, 3.0f, Color.BLACK);
            canvas.drawText("%", percentX, baseline - textSize * 0.12f, labelPaint);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setFakeBoldText(false);
            labelPaint.clearShadowLayer();
            labelPaint.setColor(Color.rgb(238, 236, 255));
        }
        drawSpriteText(canvas, suffix, percentX + percentWidth, baseline, textSize);
    }

    private boolean drawGamePercentSymbol(Canvas canvas, float x, float baseline, float textSize) {
        if (!theme.hasGlyphs() || theme.glyph('0') == null || theme.glyph('/') == null) {
            return false;
        }
        float small = textSize * 0.42f;
        float slash = textSize * 0.68f;
        boolean top = drawSpriteText(canvas, "0", x, baseline - textSize * 0.34f, small);
        boolean mid = drawSpriteText(canvas, "/", x + small * 0.28f, baseline - textSize * 0.02f, slash);
        boolean bottom = drawSpriteText(canvas, "0", x + small * 0.80f, baseline + textSize * 0.12f, small);
        return top && mid && bottom;
    }

    private void drawHealthRow(Canvas canvas, long now, float y) {
        Bitmap banjo = theme.frame("banjo", now, STEADY_REDRAW_DELAY_MS);
        if (banjo == null) {
            banjo = theme.frame("extra_life", now, STEADY_REDRAW_DELAY_MS);
        }
        drawBitmapCenter(canvas, banjo, scale(118), y, scale(154), 255, "banjo");
        drawBlueNumber(canvas, Integer.toString(stats == null ? 0 : stats.lives), scale(220), y + scale(26), scale(76));

        int max = stats == null ? 6 : Math.max(1, Math.min(stats.maxHealth, 12));
        int health = stats == null ? 0 : Math.max(0, Math.min(stats.health, max));
        float spacing = max > 8 ? scale(68) : scale(78);
        float size = max > 8 ? scale(74) : scale(82);
        float startX = scale(420);
        for (int i = 0; i < max; i++) {
            float x = startX + i * spacing;
            drawBitmapCenter(canvas, theme.frame("health", now + i * 20L, STEADY_REDRAW_DELAY_MS), x, y, size, i < health ? 255 : 70, "health");
        }
    }

    private void drawTwoStatRow(Canvas canvas, long now, float y,
                                String leftSprite, int leftValue, float leftSize,
                                String rightSprite, int rightValue, float rightSize) {
        drawMenuStat(canvas, leftSprite, leftValue, scale(280), scale(380), y, now, leftSize);
        drawMenuStat(canvas, rightSprite, rightValue, scale(760), scale(860), y, now, rightSize);
    }

    private void drawMenuStat(Canvas canvas, String spriteKey, int value, float iconX, float numberX, float y, long now, float iconSize) {
        drawBitmapCenter(canvas, theme.frame(spriteKey, now + animationPhaseOffset(spriteKey), STEADY_REDRAW_DELAY_MS), iconX, y, iconSize, 255, spriteKey);
        drawBlueNumber(canvas, Integer.toString(value), numberX, y + scale(27), scale(78));
    }

    private void drawJinjoRow(Canvas canvas, long now, float y) {
        String[] keys = {"jinjo_blue", "jinjo_green", "jinjo_orange", "jinjo_pink", "jinjo_yellow"};
        String[] fallback = {"jinjo_blue", "jinjo_green", "jinjo_orange", "jinjo_pink", "jinjo_yellow"};
        float startX = scale(245);
        float gap = scale(185);
        float size = scale(138);
        int mask = stats == null ? 0 : stats.jinjosMask;
        for (int i = 0; i < keys.length; i++) {
            boolean collected = (mask & (1 << i)) != 0;
            int alpha = collected ? 255 : 92;
            long frameTime = collected ? now + animationPhaseOffset(keys[i]) : 0L;
            drawBitmapCenter(canvas, theme.frame(keys[i], frameTime, STEADY_REDRAW_DELAY_MS), startX + i * gap, y, size, alpha, fallback[i]);
        }
    }

    private long animationPhaseOffset(String key) {
        if ("banjo".equals(key)) return 0L;
        if ("extra_life".equals(key)) return 0L;
        if ("note".equals(key)) return STEADY_REDRAW_DELAY_MS;
        if ("egg".equals(key)) return STEADY_REDRAW_DELAY_MS * 2L;
        if ("red_feather".equals(key)) return STEADY_REDRAW_DELAY_MS * 3L;
        if ("gold_feather".equals(key)) return STEADY_REDRAW_DELAY_MS * 4L;
        if ("jiggy".equals(key)) return STEADY_REDRAW_DELAY_MS * 5L;
        if ("mumbo".equals(key)) return STEADY_REDRAW_DELAY_MS * 6L;
        if ("jinjo_blue".equals(key)) return STEADY_REDRAW_DELAY_MS;
        if ("jinjo_green".equals(key)) return STEADY_REDRAW_DELAY_MS * 2L;
        if ("jinjo_orange".equals(key)) return STEADY_REDRAW_DELAY_MS * 3L;
        if ("jinjo_pink".equals(key)) return STEADY_REDRAW_DELAY_MS * 4L;
        if ("jinjo_yellow".equals(key)) return STEADY_REDRAW_DELAY_MS * 5L;
        return 0L;
    }

    private void drawFooter(Canvas canvas, float baseline) {
        String levelName = levelName(stats.levelId);
        if (stats.levelId == 0x91 && stats.selectedGameNumber >= 0 && stats.selectedGameNumber <= 2) {
            int displayedGameNumber = stats.selectedGameNumber == 1 ? 3 : (stats.selectedGameNumber == 2 ? 2 : 1);
            levelName = "GAME " + displayedGameNumber;
        }
        if (!drawSpriteTextCentered(canvas, levelName, getWidth() / 2.0f, baseline, scale(68), scale(1120))) {
            labelPaint.setTextSize(scale(62));
            labelPaint.setColor(Color.rgb(255, 221, 34));
            canvas.drawText(levelName, getWidth() / 2.0f, baseline, labelPaint);
        }
    }

    private void drawBitmapCenter(Canvas canvas, Bitmap bitmap, float cx, float cy, float size, int alpha, String fallbackKey) {
        if (bitmap != null) {
            paint.setAlpha(alpha);
            canvas.drawBitmap(bitmap, null, new RectF(cx - size / 2.0f, cy - size / 2.0f, cx + size / 2.0f, cy + size / 2.0f), paint);
            paint.setAlpha(255);
        } else {
            drawFallbackIcon(canvas, fallbackKey, cx, cy, size * 0.38f, alpha);
        }
    }

    private void drawBlueNumber(Canvas canvas, String value, float x, float baseline, float textSize) {
        if (drawSpriteText(canvas, value, x, baseline, textSize)) {
            return;
        }
        numberPaint.setTextSize(textSize);
        numberPaint.setStyle(Paint.Style.STROKE);
        numberPaint.setStrokeWidth(scale(5));
        numberPaint.setColor(Color.rgb(90, 45, 0));
        canvas.drawText(value, x, baseline, numberPaint);
        numberPaint.setStyle(Paint.Style.FILL);
        numberPaint.setColor(Color.rgb(255, 221, 34));
        canvas.drawText(value, x, baseline, numberPaint);
    }

    private boolean drawSpriteText(Canvas canvas, String value, float x, float baseline, float height) {
        if (value == null || value.isEmpty() || !theme.hasGlyphs()) {
            return false;
        }
        float cursor = x;
        boolean drewAny = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ') {
                cursor += height * 0.35f;
                continue;
            }
            BanjoSpriteTheme.FontGlyph glyph = theme.glyph(c);
            if (glyph == null || glyph.bitmap == null) {
                return false;
            }
            float glyphHeight = height * glyph.topScale;
            float width = glyphDrawWidth(glyph, height);
            float top = baseline - height + height * glyph.baselineOffset;
            canvas.drawBitmap(glyph.bitmap, null, new RectF(cursor, top, cursor + width, top + glyphHeight), paint);
            cursor += glyphAdvance(glyph, height, width);
            drewAny = true;
        }
        return drewAny;
    }

    private boolean drawSpriteTextCentered(Canvas canvas, String value, float centerX, float baseline, float height, float maxWidth) {
        if (value == null || value.isEmpty() || !theme.hasGlyphs()) {
            return false;
        }
        float width = measureSpriteText(value, height);
        if (width <= 0.0f) {
            return false;
        }
        float drawHeight = height;
        if (width > maxWidth) {
            drawHeight = height * maxWidth / width;
            width = maxWidth;
        }
        return drawSpriteText(canvas, value, centerX - width / 2.0f, baseline, drawHeight);
    }

    private float measureSpriteText(String value, float height) {
        float width = 0.0f;
        boolean drewAny = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ') {
                width += height * 0.35f;
                continue;
            }
            BanjoSpriteTheme.FontGlyph glyph = theme.glyph(c);
            if (glyph == null || glyph.bitmap == null) {
                return -1.0f;
            }
            float glyphWidth = glyphDrawWidth(glyph, height);
            width += glyphAdvance(glyph, height, glyphWidth);
            drewAny = true;
        }
        return drewAny ? width : 0.0f;
    }

    private float glyphDrawWidth(BanjoSpriteTheme.FontGlyph glyph, float height) {
        float glyphHeight = height * glyph.topScale;
        return glyphHeight * glyph.bitmap.getWidth() / (float) glyph.bitmap.getHeight();
    }

    private float glyphAdvance(BanjoSpriteTheme.FontGlyph glyph, float height, float drawWidth) {
        if (glyph.tightSpacing) {
            // Bold letter chunks include nominal side bearing. For narrow letters like I/J,
            // using that bearing leaves a visible blank strip before the next glyph; the
            // original menu font overlaps from the cropped visual body instead.
            float narrowThreshold = height * 0.46f;
            float overlap = drawWidth < narrowThreshold ? height * 0.22f : height * 0.13f;
            return Math.max(height * 0.08f, drawWidth - overlap);
        }
        float minimumAdvance = height * glyph.advance - height * 0.18f;
        float visualAdvance = drawWidth - height * 0.11f;
        return Math.max(visualAdvance, minimumAdvance * 0.82f);
    }

    private String levelName(int mapId) {
        switch (mapId) {
            case 0x02:
            case 0x0C:
            case 0x0E:
                return "MUMBO'S MOUNTAIN";
            case 0x05:
            case 0x06:
            case 0x07:
            case 0x0A:
            case 0x8F:
                return "TREASURE TROVE COVE";
            case 0x0B:
            case 0x21:
            case 0x22:
            case 0x23:
                return "CLANKER'S CAVERN";
            case 0x0D:
            case 0x10:
            case 0x11:
            case 0x47:
                return "BUBBLEGLOOP SWAMP";
            case 0x27:
            case 0x41:
            case 0x48:
            case 0x53:
            case 0x7F:
                return "FREEZEEZY PEAK";
            case 0x12:
            case 0x13:
            case 0x14:
            case 0x15:
            case 0x16:
            case 0x1A:
            case 0x92:
                return "GOBI'S VALLEY";
            case 0x40:
            case 0x43:
            case 0x44:
            case 0x45:
            case 0x46:
            case 0x4A:
            case 0x4B:
            case 0x4C:
            case 0x4D:
            case 0x5A:
            case 0x5B:
            case 0x5C:
            case 0x5E:
            case 0x5F:
            case 0x60:
            case 0x61:
            case 0x62:
            case 0x63:
            case 0x64:
            case 0x65:
            case 0x66:
            case 0x67:
            case 0x68:
                return "CLICK CLOCK WOOD";
            case 0x31:
            case 0x34:
            case 0x35:
            case 0x36:
            case 0x37:
            case 0x38:
            case 0x39:
            case 0x3A:
            case 0x3B:
            case 0x3C:
            case 0x3D:
            case 0x3E:
            case 0x3F:
            case 0x8B:
                return "RUSTY BUCKET BAY";
            case 0x1B:
            case 0x1C:
            case 0x1D:
            case 0x24:
            case 0x25:
            case 0x26:
            case 0x28:
            case 0x29:
            case 0x2A:
            case 0x2B:
            case 0x2C:
            case 0x2D:
            case 0x2E:
            case 0x2F:
            case 0x30:
            case 0x8D:
                return "MAD MONSTER MANSION";
            case 0x01:
            case 0x7D:
            case 0x7E:
            case 0x85:
            case 0x86:
            case 0x87:
            case 0x88:
            case 0x8C:
            case 0x94:
            case 0x98:
            case 0x99:
                return "SPIRAL MOUNTAIN";
            case 0x69:
            case 0x6A:
            case 0x6B:
            case 0x6C:
            case 0x6D:
            case 0x6E:
            case 0x6F:
            case 0x70:
            case 0x71:
            case 0x72:
            case 0x74:
            case 0x75:
            case 0x76:
            case 0x77:
            case 0x78:
            case 0x79:
            case 0x7A:
            case 0x80:
            case 0x8E:
            case 0x90:
            case 0x93:
                return "GRUNTILDA'S LAIR";
            default:
                return "BANJO KAZOOIE";
        }
    }

    private void drawFallbackIcon(Canvas canvas, String key, float cx, float cy, float radius, int alpha) {
        int color = Color.rgb(255, 214, 65);
        if ("health".equals(key)) color = Color.rgb(240, 60, 52);
        else if ("empty_honeycomb".equals(key)) color = Color.rgb(255, 220, 35);
        else if ("note".equals(key)) color = Color.rgb(255, 220, 35);
        else if ("egg".equals(key)) color = Color.rgb(100, 210, 255);
        else if ("red_feather".equals(key)) color = Color.rgb(220, 30, 35);
        else if ("gold_feather".equals(key)) color = Color.rgb(255, 230, 50);
        else if ("mumbo".equals(key)) color = Color.rgb(230, 230, 245);
        else if ("jinjo_blue".equals(key)) color = Color.rgb(40, 80, 255);
        else if ("jinjo_green".equals(key)) color = Color.rgb(55, 255, 65);
        else if ("jinjo_orange".equals(key)) color = Color.rgb(255, 128, 40);
        else if ("jinjo_pink".equals(key)) color = Color.rgb(255, 70, 230);
        paint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private float scale(float value) {
        return value * Math.min(getWidth() / 1240.0f, getHeight() / 1080.0f);
    }
}
