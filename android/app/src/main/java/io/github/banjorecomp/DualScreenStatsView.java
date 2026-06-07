package io.github.banjorecomp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

public final class DualScreenStatsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private DualScreenStats stats;
    private boolean gameplayActive;
    private BanjoSpriteTheme theme = BanjoSpriteTheme.EMPTY;
    private LinearGradient backgroundGradient;
    private int gradientWidth;
    private int gradientHeight;

    public DualScreenStatsView(Context context) {
        super(context);
        textPaint.setColor(Color.rgb(255, 226, 71));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(5.0f, 2.0f, 4.0f, Color.rgb(70, 38, 0));

        smallTextPaint.setColor(Color.rgb(255, 246, 190));
        smallTextPaint.setTextAlign(Paint.Align.CENTER);
        smallTextPaint.setFakeBoldText(true);
        smallTextPaint.setShadowLayer(4.0f, 2.0f, 3.0f, Color.BLACK);

        cardPaint.setColor(Color.rgb(76, 42, 18));
        setBackgroundColor(Color.BLACK);
    }

    public void showBlank() {
        gameplayActive = false;
        invalidate();
    }

    public void updateStats(DualScreenStats stats) {
        this.stats = stats;
        gameplayActive = true;
        invalidate();
    }

    public void setTheme(BanjoSpriteTheme theme) {
        this.theme = theme == null ? BanjoSpriteTheme.EMPTY : theme;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        drawBackground(canvas, width, height);

        textPaint.setTextSize(scale(42));
        canvas.drawText("BANJORECOMP", width / 2.0f, scale(80), textPaint);
        textPaint.setTextSize(scale(34));
        canvas.drawText("STATS", width / 2.0f, scale(124), textPaint);

        if (!gameplayActive || stats == null) {
            smallTextPaint.setTextSize(scale(28));
            canvas.drawText("Waiting for gameplay", width / 2.0f, height / 2.0f, smallTextPaint);
            drawThemeStatus(canvas, width, height);
            postInvalidateDelayed(250);
            return;
        }

        float top = scale(165);
        float rowHeight = scale(150);
        float colWidth = width / 2.0f;
        drawStat(canvas, "health", "HEALTH", stats.health + " / " + stats.maxHealth, 0, top, colWidth, rowHeight);
        drawStat(canvas, "note", "NOTES", Integer.toString(stats.notes), colWidth, top, colWidth, rowHeight);
        drawStat(canvas, "jiggy", "JIGGIES", Integer.toString(stats.jiggies), 0, top + rowHeight, colWidth, rowHeight);
        drawStat(canvas, "mumbo", "MUMBO", Integer.toString(stats.mumboTokens), colWidth, top + rowHeight, colWidth, rowHeight);
        drawJinjos(canvas, 0, top + rowHeight * 2.0f, width, rowHeight * 1.15f);

        smallTextPaint.setTextSize(scale(20));
        canvas.drawText("Level ID " + stats.levelId, width / 2.0f, height - scale(34), smallTextPaint);
        drawThemeStatus(canvas, width, height);
        postInvalidateDelayed(160);
    }

    private void drawBackground(Canvas canvas, int width, int height) {
        if (backgroundGradient == null || gradientWidth != width || gradientHeight != height) {
            gradientWidth = width;
            gradientHeight = height;
            backgroundGradient = new LinearGradient(0, 0, width, height,
                    Color.rgb(32, 15, 9), Color.rgb(5, 27, 52), Shader.TileMode.CLAMP);
        }
        paint.setShader(backgroundGradient);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        paint.setColor(Color.argb(95, 255, 180, 40));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(scale(6));
        rect.set(scale(18), scale(18), width - scale(18), height - scale(18));
        canvas.drawRoundRect(rect, scale(28), scale(28), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawStat(Canvas canvas, String spriteKey, String label, String value,
                          float left, float top, float width, float height) {
        rect.set(left + scale(30), top + scale(12), left + width - scale(30), top + height - scale(16));
        cardPaint.setColor(Color.argb(190, 74, 45, 22));
        canvas.drawRoundRect(rect, scale(22), scale(22), cardPaint);
        paint.setColor(Color.argb(140, 255, 210, 80));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(scale(3));
        canvas.drawRoundRect(rect, scale(22), scale(22), paint);
        paint.setStyle(Paint.Style.FILL);

        Bitmap icon = theme.frame(spriteKey, System.currentTimeMillis(), 110);
        float iconSize = scale(74);
        if (icon != null) {
            float ix = left + width / 2.0f - iconSize / 2.0f;
            canvas.drawBitmap(icon, null, new RectF(ix, top + scale(18), ix + iconSize, top + scale(18) + iconSize), paint);
        } else {
            drawFallbackIcon(canvas, spriteKey, left + width / 2.0f, top + scale(55), iconSize * 0.55f);
        }

        smallTextPaint.setTextSize(scale(22));
        smallTextPaint.setColor(Color.rgb(255, 236, 178));
        canvas.drawText(label, left + width / 2.0f, top + scale(105), smallTextPaint);
        textPaint.setTextSize(scale(38));
        canvas.drawText(value, left + width / 2.0f, top + scale(142), textPaint);
    }

    private void drawJinjos(Canvas canvas, float left, float top, float width, float height) {
        rect.set(left + scale(30), top + scale(12), left + width - scale(30), top + height - scale(12));
        cardPaint.setColor(Color.argb(180, 54, 32, 65));
        canvas.drawRoundRect(rect, scale(24), scale(24), cardPaint);

        smallTextPaint.setTextSize(scale(24));
        smallTextPaint.setColor(Color.rgb(255, 236, 178));
        canvas.drawText("JINJOS", left + width / 2.0f, top + scale(42), smallTextPaint);

        String[] keys = {"jinjo_yellow", "jinjo_green", "jinjo_blue", "jinjo_pink", "jinjo_orange"};
        int[] colors = {Color.YELLOW, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.rgb(255, 132, 0)};
        float spacing = width / 6.0f;
        float size = scale(72);
        int mask = stats == null ? 0 : stats.jinjosMask;
        for (int i = 0; i < keys.length; i++) {
            float cx = spacing * (i + 1);
            float cy = top + scale(93);
            Bitmap icon = theme.frame(keys[i], System.currentTimeMillis() + i * 47L, 120);
            if (icon != null) {
                paint.setAlpha((mask & (1 << i)) != 0 ? 255 : 75);
                canvas.drawBitmap(icon, null, new RectF(cx - size / 2.0f, cy - size / 2.0f, cx + size / 2.0f, cy + size / 2.0f), paint);
                paint.setAlpha(255);
            } else {
                paint.setColor((mask & (1 << i)) != 0 ? colors[i] : Color.argb(75, Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i])));
                canvas.drawCircle(cx, cy, size * 0.34f, paint);
            }
        }

        textPaint.setTextSize(scale(32));
        canvas.drawText(Integer.bitCount(mask) + " / 5", left + width / 2.0f, top + height - scale(18), textPaint);
    }

    private void drawThemeStatus(Canvas canvas, int width, int height) {
        smallTextPaint.setTextSize(scale(16));
        smallTextPaint.setColor(theme.isLoadedFromRom() ? Color.rgb(170, 255, 170) : Color.rgb(255, 200, 140));
        canvas.drawText(theme.isLoadedFromRom() ? "ROM sprite theme loaded" : "Using fallback theme until ROM sprites load",
                width / 2.0f, height - scale(12), smallTextPaint);
    }

    private void drawFallbackIcon(Canvas canvas, String key, float cx, float cy, float radius) {
        int color = Color.rgb(255, 214, 65);
        if ("health".equals(key)) color = Color.rgb(240, 60, 52);
        else if ("note".equals(key)) color = Color.rgb(55, 185, 255);
        else if ("mumbo".equals(key)) color = Color.rgb(205, 120, 255);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private float scale(float value) {
        return value * Math.min(getWidth() / 1240.0f, getHeight() / 1080.0f);
    }
}
