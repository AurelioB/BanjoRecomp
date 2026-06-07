package io.github.banjorecomp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

public final class DualScreenStatsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private DualScreenStats stats;
    private boolean gameplayActive;
    private BanjoSpriteTheme theme = BanjoSpriteTheme.EMPTY;
    private LinearGradient caveGradient;
    private RadialGradient floorGlow;
    private int gradientWidth;
    private int gradientHeight;

    public DualScreenStatsView(Context context) {
        super(context);
        numberPaint.setColor(Color.rgb(100, 220, 255));
        numberPaint.setTextAlign(Paint.Align.LEFT);
        numberPaint.setFakeBoldText(true);
        numberPaint.setShadowLayer(5.0f, 3.0f, 3.0f, Color.rgb(5, 20, 70));

        labelPaint.setColor(Color.rgb(238, 236, 255));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        labelPaint.setShadowLayer(4.0f, 2.0f, 3.0f, Color.BLACK);

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
        drawStartMenuBackdrop(canvas, width, height);

        if (!gameplayActive || stats == null) {
            drawWaitingState(canvas, width, height);
            postInvalidateDelayed(250);
            return;
        }

        long now = System.currentTimeMillis();
        drawLivesAndHealth(canvas, now);
        drawRightColumn(canvas, now);
        drawJinjoRow(canvas, now);
        drawFooter(canvas);
        postInvalidateDelayed(130);
    }

    private void drawStartMenuBackdrop(Canvas canvas, int width, int height) {
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

    private void drawWaitingState(Canvas canvas, int width, int height) {
        drawLivesAndHealth(canvas, System.currentTimeMillis());
        labelPaint.setTextSize(scale(42));
        labelPaint.setColor(Color.rgb(238, 236, 255));
        canvas.drawText("WAITING FOR GAMEPLAY", width / 2.0f, height / 2.0f, labelPaint);
        labelPaint.setTextSize(scale(24));
        canvas.drawText("Stats will appear here like the start menu", width / 2.0f, height / 2.0f + scale(48), labelPaint);
    }

    private void drawLivesAndHealth(Canvas canvas, long now) {
        float top = scale(86);
        Bitmap banjo = theme.frame("banjo", now, 130);
        if (banjo == null) {
            banjo = theme.frame("extra_life", now, 130);
        }
        drawBitmapCenter(canvas, banjo, scale(145), top, scale(125), 255, "banjo");
        drawBlueNumber(canvas, Integer.toString(stats == null ? 0 : stats.lives), scale(270), top + scale(20), scale(64));

        int max = stats == null ? 6 : Math.max(1, Math.min(stats.maxHealth, 12));
        int health = stats == null ? 0 : Math.max(0, Math.min(stats.health, max));
        float startX = scale(360);
        float spacing = scale(57);
        float size = scale(58);
        for (int i = 0; i < max; i++) {
            float x = startX + (i % 6) * spacing;
            float y = top - scale(8) + (i / 6) * scale(58);
            drawBitmapCenter(canvas, theme.frame("health", now + i * 20L, 130), x, y, size, i < health ? 255 : 70, "health");
        }
    }

    private void drawRightColumn(Canvas canvas, long now) {
        float iconX = scale(918);
        float numberX = scale(1008);
        float y = scale(116);
        float gap = scale(132);
        drawMenuStat(canvas, "note", stats.notes, iconX, numberX, y, now, scale(92));
        drawMenuStat(canvas, "egg", stats.eggs, iconX, numberX, y + gap, now, scale(82));
        drawMenuStat(canvas, "red_feather", stats.redFeathers, iconX, numberX, y + gap * 2.0f, now, scale(86));
        drawMenuStat(canvas, "gold_feather", stats.goldFeathers, iconX, numberX, y + gap * 3.0f, now, scale(82));
        drawMenuStat(canvas, "jiggy", stats.jiggies, iconX, numberX, y + gap * 4.0f, now, scale(84));
        drawMenuStat(canvas, "mumbo", stats.mumboTokens, iconX, numberX, y + gap * 5.0f, now, scale(86));
    }

    private void drawMenuStat(Canvas canvas, String spriteKey, int value, float iconX, float numberX, float y, long now, float iconSize) {
        drawBitmapCenter(canvas, theme.frame(spriteKey, now, 120), iconX, y, iconSize, 255, spriteKey);
        drawBlueNumber(canvas, Integer.toString(value), numberX, y + scale(22), scale(66));
    }

    private void drawJinjoRow(Canvas canvas, long now) {
        String[] keys = {"jinjo_blue", "jinjo_green", "jinjo_orange", "jinjo_pink", "jinjo_yellow"};
        String[] fallback = {"jinjo_blue", "jinjo_green", "jinjo_orange", "jinjo_pink", "jinjo_yellow"};
        float startX = scale(180);
        float y = scale(895);
        float gap = scale(130);
        float size = scale(96);
        int mask = stats == null ? 0 : stats.jinjosMask;
        for (int i = 0; i < keys.length; i++) {
            int alpha = (mask & (1 << i)) != 0 ? 255 : 92;
            drawBitmapCenter(canvas, theme.frame(keys[i], now + i * 45L, 120), startX + i * gap, y, size, alpha, fallback[i]);
        }
    }

    private void drawFooter(Canvas canvas) {
        String levelName = levelName(stats.levelId);
        if (!drawSpriteTextCentered(canvas, levelName, getWidth() / 2.0f, scale(1030), scale(34), scale(960))) {
            labelPaint.setTextSize(scale(30));
            labelPaint.setColor(Color.rgb(255, 221, 34));
            canvas.drawText(levelName, getWidth() / 2.0f, scale(1030), labelPaint);
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
            Bitmap glyph = theme.glyph(c);
            if (glyph == null) {
                return false;
            }
            float width = height * glyph.getWidth() / (float) glyph.getHeight();
            canvas.drawBitmap(glyph, null, new RectF(cursor, baseline - height, cursor + width, baseline), paint);
            cursor += width + height * 0.08f;
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
            Bitmap glyph = theme.glyph(c);
            if (glyph == null) {
                return -1.0f;
            }
            width += height * glyph.getWidth() / (float) glyph.getHeight() + height * 0.08f;
            drewAny = true;
        }
        return drewAny ? width : 0.0f;
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
