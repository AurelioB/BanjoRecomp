package io.github.banjorecomp;

import android.graphics.Bitmap;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BanjoSpriteTheme {
    public static final BanjoSpriteTheme EMPTY = new BanjoSpriteTheme(Collections.emptyMap(), Collections.emptyMap(), false);

    public static final class FontGlyph {
        public final Bitmap bitmap;
        public final float advance;
        public final float baselineOffset;
        public final float topScale;

        public FontGlyph(Bitmap bitmap, float advance, float baselineOffset, float topScale) {
            this.bitmap = bitmap;
            this.advance = advance;
            this.baselineOffset = baselineOffset;
            this.topScale = topScale;
        }
    }

    private final Map<String, List<Bitmap>> sprites;
    private final Map<Character, FontGlyph> glyphs;
    private final boolean loadedFromRom;

    public BanjoSpriteTheme(Map<String, List<Bitmap>> sprites, Map<Character, FontGlyph> glyphs, boolean loadedFromRom) {
        this.sprites = Collections.unmodifiableMap(new HashMap<>(sprites));
        this.glyphs = Collections.unmodifiableMap(new HashMap<>(glyphs));
        this.loadedFromRom = loadedFromRom;
    }

    public boolean isLoadedFromRom() {
        return loadedFromRom;
    }

    public Bitmap frame(String key, long timeMillis, int frameMillis) {
        List<Bitmap> frames = sprites.get(key);
        if (frames == null || frames.isEmpty()) {
            return null;
        }
        if (frames.size() == 1 || frameMillis <= 0) {
            return frames.get(0);
        }
        int index = (int) ((timeMillis / frameMillis) % frames.size());
        return frames.get(index);
    }

    public FontGlyph glyph(char c) {
        return glyphs.get(c);
    }

    public boolean hasGlyphs() {
        return !glyphs.isEmpty();
    }
}
