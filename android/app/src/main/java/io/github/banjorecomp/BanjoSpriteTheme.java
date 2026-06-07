package io.github.banjorecomp;

import android.graphics.Bitmap;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BanjoSpriteTheme {
    public static final BanjoSpriteTheme EMPTY = new BanjoSpriteTheme(Collections.emptyMap(), false);

    private final Map<String, List<Bitmap>> sprites;
    private final boolean loadedFromRom;

    public BanjoSpriteTheme(Map<String, List<Bitmap>> sprites, boolean loadedFromRom) {
        this.sprites = Collections.unmodifiableMap(new HashMap<>(sprites));
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
}
