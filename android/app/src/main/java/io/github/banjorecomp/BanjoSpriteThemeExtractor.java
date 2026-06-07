package io.github.banjorecomp;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class BanjoSpriteThemeExtractor {
    private static final String TAG = "BanjoSpriteExtractor";
    private static final int ASSETS_ROM_START = 0x00005E90;
    private static final int ASSETS_ROM_END = 0x00D846C0;

    private static final int FMT_CI4 = 0x0001;
    private static final int FMT_CI8 = 0x0004;
    private static final int FMT_I4 = 0x0020;
    private static final int FMT_I8 = 0x0040;
    private static final int FMT_RGBA16 = 0x0400;
    private static final int FMT_RGBA32 = 0x0800;

    private BanjoSpriteThemeExtractor() {
    }

    public static BanjoSpriteTheme extract(File romFile) throws IOException {
        Map<String, List<Bitmap>> sprites = new HashMap<>();
        Map<Character, BanjoSpriteTheme.FontGlyph> glyphs = new HashMap<>();
        try (RandomAccessFile rom = new RandomAccessFile(romFile, "r")) {
            int byteOrder = detectByteOrder(rom);
            putSprite(rom, byteOrder, sprites, "health", 0x7DD);
            putSprite(rom, byteOrder, sprites, "banjo", 0x7EF);
            putSprite(rom, byteOrder, sprites, "extra_life", 0x80E);
            putSprite(rom, byteOrder, sprites, "egg", 0x81E);
            putSprite(rom, byteOrder, sprites, "red_feather", 0x820);
            putSprite(rom, byteOrder, sprites, "gold_feather", 0x81F);
            putSprite(rom, byteOrder, sprites, "note", 0x81B);
            putSprite(rom, byteOrder, sprites, "jiggy", 0x80D);
            putSprite(rom, byteOrder, sprites, "mumbo", 0x808);
            putSprite(rom, byteOrder, sprites, "jinjo_yellow", 0x802);
            putSprite(rom, byteOrder, sprites, "jinjo_green", 0x803);
            putSprite(rom, byteOrder, sprites, "jinjo_blue", 0x804);
            putSprite(rom, byteOrder, sprites, "jinjo_pink", 0x805);
            putSprite(rom, byteOrder, sprites, "jinjo_orange", 0x806);
            putNumberGlyphs(rom, byteOrder, glyphs);
            putLetterGlyphs(rom, byteOrder, glyphs);
        }
        Log.i(TAG, "Loaded dual-screen sprite theme from ROM: " + sprites.keySet() + ", glyphs=" + glyphs.keySet());
        return new BanjoSpriteTheme(sprites, glyphs, true);
    }

    private static void putSprite(RandomAccessFile rom, int byteOrder, Map<String, List<Bitmap>> sprites, String key, int assetId) {
        try {
            List<Bitmap> frames = decodeSprite(readAsset(rom, byteOrder, assetId));
            if (!frames.isEmpty()) {
                sprites.put(key, frames);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode sprite asset 0x" + Integer.toHexString(assetId) + " for " + key, e);
        }
    }

    private static void putNumberGlyphs(RandomAccessFile rom, int byteOrder, Map<Character, BanjoSpriteTheme.FontGlyph> glyphs) {
        try {
            List<GlyphChunk> glyphChunks = decodeSpriteGlyphChunks(readAsset(rom, byteOrder, 0x6ED));
            for (int i = 0; i < glyphChunks.size() && i < 10; i++) {
                glyphs.put((char) ('0' + i), makeGlyph(glyphChunks.get(i), 20.0f, false));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode bold number font sprite", e);
        }
    }

    private static void putLetterGlyphs(RandomAccessFile rom, int byteOrder, Map<Character, BanjoSpriteTheme.FontGlyph> glyphs) {
        try {
            List<GlyphChunk> glyphChunks = decodeSpriteGlyphChunks(readAsset(rom, byteOrder, 0x6EC));
            for (int i = 1; i <= 26 && i < glyphChunks.size(); i++) {
                glyphs.put((char) ('A' + i - 1), makeGlyph(glyphChunks.get(i), 23.0f, false));
            }
            if (glyphChunks.size() > 40) {
                glyphs.put('\'', makeGlyph(glyphChunks.get(40), 23.0f, true));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to decode bold letter font sprite", e);
        }
    }

    private static BanjoSpriteTheme.FontGlyph makeGlyph(GlyphChunk chunk, float nominalHeight, boolean apostrophe) {
        Bitmap bitmap = tintHoney(cropTransparent(chunk.bitmap));
        float advance = Math.max(1.0f, chunk.advance / nominalHeight);
        float baselineOffset = 0.0f;
        float topScale = apostrophe ? 0.55f : 1.0f;
        return new BanjoSpriteTheme.FontGlyph(bitmap, advance, baselineOffset, topScale);
    }

    private static Bitmap tintHoney(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            float vertical = height <= 1 ? 0.0f : y / (float) (height - 1);
            int topR = 255, topG = 241, topB = 89;
            int midR = 238, midG = 167, midB = 31;
            int botR = 132, botG = 67, botB = 5;
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                int alpha = (pixels[i] >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                float shade = alpha / 255.0f;
                int r;
                int g;
                int b;
                if (vertical < 0.55f) {
                    float t = vertical / 0.55f;
                    r = lerp(topR, midR, t);
                    g = lerp(topG, midG, t);
                    b = lerp(topB, midB, t);
                } else {
                    float t = (vertical - 0.55f) / 0.45f;
                    r = lerp(midR, botR, t);
                    g = lerp(midG, botG, t);
                    b = lerp(midB, botB, t);
                }
                // Keep the source alpha-mask detail instead of flattening every pixel to one color.
                float highlight = 0.62f + shade * 0.38f;
                pixels[i] = Color.argb(alpha,
                        Math.min(255, (int) (r * highlight)),
                        Math.min(255, (int) (g * highlight)),
                        Math.min(255, (int) (b * highlight)));
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static byte[] readAsset(RandomAccessFile rom, int byteOrder, int assetId) throws IOException, DataFormatException {
        if (rom.length() < ASSETS_ROM_END) {
            throw new IOException("ROM is too small for Banjo-Kazooie asset table");
        }

        int count = readLogicalInt(rom, byteOrder, ASSETS_ROM_START);
        if (assetId < 0 || assetId + 1 >= count) {
            throw new IOException("Asset id 0x" + Integer.toHexString(assetId) + " out of table bounds " + count);
        }

        long tableBase = ASSETS_ROM_START + 8L;
        long dataBase = tableBase + count * 8L;
        byte[] meta = readLogicalBytes(rom, byteOrder, tableBase + assetId * 8L, 16);
        int relativeStart = readU32(meta, 0);
        int compressedFlag = readU16(meta, 4);
        int relativeEnd = readU32(meta, 8);

        int size = relativeEnd - relativeStart;
        if (size <= 0 || dataBase + relativeEnd > ASSETS_ROM_END) {
            throw new IOException("Invalid asset range for 0x" + Integer.toHexString(assetId));
        }

        byte[] data = readLogicalBytes(rom, byteOrder, dataBase + relativeStart, size);
        if (compressedFlag == 0) {
            return data;
        }
        return decompressBkZip(data);
    }

    private static byte[] decompressBkZip(byte[] data) throws IOException, DataFormatException {
        if (data.length < 6 || data[0] != 0x11 || data[1] != 0x72) {
            throw new IOException("Compressed asset is missing BK zip header");
        }
        int expected = readU32(data, 2);
        Inflater inflater = new Inflater(true);
        inflater.setInput(data, 6, data.length - 6);
        ByteArrayOutputStream out = new ByteArrayOutputStream(expected);
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) {
            int read = inflater.inflate(buffer);
            if (read == 0) {
                if (inflater.needsInput()) {
                    break;
                }
                if (inflater.needsDictionary()) {
                    throw new IOException("Compressed asset needs an unsupported dictionary");
                }
            } else {
                out.write(buffer, 0, read);
            }
        }
        inflater.end();
        byte[] result = out.toByteArray();
        if (result.length != expected) {
            throw new IOException("Unexpected decompressed size " + result.length + ", expected " + expected);
        }
        return result;
    }

    private static final class GlyphChunk {
        final Bitmap bitmap;
        final int advance;

        GlyphChunk(Bitmap bitmap, int advance) {
            this.bitmap = bitmap;
            this.advance = advance;
        }
    }

    private static List<GlyphChunk> decodeSpriteGlyphChunks(byte[] data) throws IOException {
        int frameCount = readU16(data, 0);
        int format = readU16(data, 2);
        if (frameCount <= 0 || frameCount > 0x100) {
            throw new IOException("Unsupported sprite frame count " + frameCount);
        }
        if (!isSupportedFormat(format)) {
            throw new IOException("Unsupported sprite format 0x" + Integer.toHexString(format));
        }

        int relative = readU32(data, 0x10);
        int frameOffset = 0x10 + relative + 4 * frameCount;
        return decodeFrameGlyphChunks(data, frameOffset, format);
    }

    private static List<Bitmap> decodeSpriteChunks(byte[] data) throws IOException {
        List<GlyphChunk> chunks = decodeSpriteGlyphChunks(data);
        List<Bitmap> bitmaps = new ArrayList<>(chunks.size());
        for (GlyphChunk chunk : chunks) {
            bitmaps.add(chunk.bitmap);
        }
        return bitmaps;
    }

    private static List<GlyphChunk> decodeFrameGlyphChunks(byte[] data, int frameOffset, int format) throws IOException {
        int width = readU16(data, frameOffset + 4);
        int height = readU16(data, frameOffset + 6);
        int chunkCount = readU16(data, frameOffset + 8);
        if (width <= 0 || height <= 0 || width > 1024 || height > 512 || chunkCount <= 0 || chunkCount > 256) {
            throw new IOException("Invalid sprite frame dimensions/chunks");
        }

        List<GlyphChunk> chunks = new ArrayList<>(chunkCount);
        int offset = frameOffset + 0x14;
        byte[] palette = null;
        if (format == FMT_CI4 || format == FMT_CI8) {
            offset = align8(offset);
            int paletteBytes = format == FMT_CI4 ? 0x20 : 0x200;
            palette = slice(data, offset, paletteBytes);
            offset += paletteBytes;
        }

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int advance = readU16(data, offset);
            int chunkWidth = readU16(data, offset + 4);
            int chunkHeight = readU16(data, offset + 6);
            offset += 8;
            offset = align8(offset);
            int rawSize = chunkWidth * chunkHeight * bitsPerPixel(format) / 8;
            byte[] raw = slice(data, offset, rawSize);
            offset += rawSize;
            int[] chunkPixels = decodePixels(raw, palette, format, chunkWidth, chunkHeight);
            chunks.add(new GlyphChunk(Bitmap.createBitmap(chunkPixels, chunkWidth, chunkHeight, Bitmap.Config.ARGB_8888), advance));
        }
        return chunks;
    }

    private static List<Bitmap> decodeFrameChunks(byte[] data, int frameOffset, int format) throws IOException {
        int width = readU16(data, frameOffset + 4);
        int height = readU16(data, frameOffset + 6);
        int chunkCount = readU16(data, frameOffset + 8);
        if (width <= 0 || height <= 0 || width > 1024 || height > 512 || chunkCount <= 0 || chunkCount > 256) {
            throw new IOException("Invalid sprite frame dimensions/chunks");
        }

        List<Bitmap> chunks = new ArrayList<>(chunkCount);
        int offset = frameOffset + 0x14;
        byte[] palette = null;
        if (format == FMT_CI4 || format == FMT_CI8) {
            offset = align8(offset);
            int paletteBytes = format == FMT_CI4 ? 0x20 : 0x200;
            palette = slice(data, offset, paletteBytes);
            offset += paletteBytes;
        }

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int chunkWidth = readU16(data, offset + 4);
            int chunkHeight = readU16(data, offset + 6);
            offset += 8;
            offset = align8(offset);
            int rawSize = chunkWidth * chunkHeight * bitsPerPixel(format) / 8;
            byte[] raw = slice(data, offset, rawSize);
            offset += rawSize;
            int[] chunkPixels = decodePixels(raw, palette, format, chunkWidth, chunkHeight);
            chunks.add(Bitmap.createBitmap(chunkPixels, chunkWidth, chunkHeight, Bitmap.Config.ARGB_8888));
        }
        return chunks;
    }

    private static Bitmap cropTransparent(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int left = bitmap.getWidth();
        int top = bitmap.getHeight();
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (((bitmap.getPixel(x, y) >>> 24) & 0xFF) != 0) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < left || bottom < top) {
            return bitmap;
        }
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
    }

    private static List<Bitmap> decodeSprite(byte[] data) throws IOException {
        int frameCount = readU16(data, 0);
        int format = readU16(data, 2);
        if (frameCount <= 0 || frameCount > 0x100) {
            throw new IOException("Unsupported sprite frame count " + frameCount);
        }
        if (!isSupportedFormat(format)) {
            throw new IOException("Unsupported sprite format 0x" + Integer.toHexString(format));
        }

        List<Bitmap> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int relative = readU32(data, 0x10 + i * 4);
            int frameOffset = 0x10 + relative + 4 * frameCount;
            frames.add(decodeFrame(data, frameOffset, format));
        }
        return frames;
    }

    private static Bitmap decodeFrame(byte[] data, int frameOffset, int format) throws IOException {
        int width = readU16(data, frameOffset + 4);
        int height = readU16(data, frameOffset + 6);
        int chunkCount = readU16(data, frameOffset + 8);
        if (width <= 0 || height <= 0 || width > 512 || height > 512 || chunkCount <= 0 || chunkCount > 256) {
            throw new IOException("Invalid sprite frame dimensions/chunks");
        }

        int[] pixels = new int[width * height];
        int offset = frameOffset + 0x14;
        byte[] palette = null;
        if (format == FMT_CI4 || format == FMT_CI8) {
            offset = align8(offset);
            int paletteBytes = format == FMT_CI4 ? 0x20 : 0x200;
            palette = slice(data, offset, paletteBytes);
            offset += paletteBytes;
        }

        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int x = readS16(data, offset);
            int y = readS16(data, offset + 2);
            int chunkWidth = readU16(data, offset + 4);
            int chunkHeight = readU16(data, offset + 6);
            offset += 8;
            offset = align8(offset);
            int rawSize = chunkWidth * chunkHeight * bitsPerPixel(format) / 8;
            byte[] raw = slice(data, offset, rawSize);
            offset += rawSize;
            int[] chunkPixels = decodePixels(raw, palette, format, chunkWidth, chunkHeight);
            blit(chunkPixels, chunkWidth, chunkHeight, pixels, width, height, chunkCount == 1 ? 0 : x, chunkCount == 1 ? 0 : y);
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static int[] decodePixels(byte[] raw, byte[] palette, int format, int width, int height) throws IOException {
        int pixelCount = width * height;
        int[] out = new int[pixelCount];
        switch (format) {
            case FMT_CI4:
                for (int i = 0, p = 0; i < raw.length && p < pixelCount; i++) {
                    out[p++] = rgba16ToArgb(palette, (raw[i] >> 4) & 0x0F);
                    if (p < pixelCount) {
                        out[p++] = rgba16ToArgb(palette, raw[i] & 0x0F);
                    }
                }
                return out;
            case FMT_CI8:
                for (int i = 0; i < pixelCount; i++) {
                    out[i] = rgba16ToArgb(palette, raw[i] & 0xFF);
                }
                return out;
            case FMT_I4:
                for (int i = 0, p = 0; i < raw.length && p < pixelCount; i++) {
                    int hi = (raw[i] >> 4) & 0x0F;
                    int lo = raw[i] & 0x0F;
                    out[p++] = Color.argb(255, hi * 17, hi * 17, hi * 17);
                    if (p < pixelCount) {
                        out[p++] = Color.argb(255, lo * 17, lo * 17, lo * 17);
                    }
                }
                return out;
            case FMT_I8:
                for (int i = 0; i < pixelCount; i++) {
                    int v = raw[i] & 0xFF;
                    out[i] = Color.argb(255, v, v, v);
                }
                return out;
            case FMT_RGBA16:
                for (int i = 0; i < pixelCount; i++) {
                    out[i] = rgba16ToArgb(raw, i);
                }
                return out;
            case FMT_RGBA32:
                for (int i = 0, o = 0; i < pixelCount; i++, o += 4) {
                    out[i] = Color.argb(raw[o + 3] & 0xFF, raw[o] & 0xFF, raw[o + 1] & 0xFF, raw[o + 2] & 0xFF);
                }
                return out;
            default:
                throw new IOException("Unsupported sprite pixel format");
        }
    }

    private static void blit(int[] src, int sw, int sh, int[] dst, int dw, int dh, int x, int y) {
        for (int sy = 0; sy < sh; sy++) {
            int dy = y + sy;
            if (dy < 0 || dy >= dh) {
                continue;
            }
            for (int sx = 0; sx < sw; sx++) {
                int dx = x + sx;
                if (dx >= 0 && dx < dw) {
                    dst[dy * dw + dx] = src[sy * sw + sx];
                }
            }
        }
    }

    private static int rgba16ToArgb(byte[] data, int index) {
        int off = index * 2;
        int value = ((data[off] & 0xFF) << 8) | (data[off + 1] & 0xFF);
        int r5 = (value >> 11) & 0x1F;
        int g5 = (value >> 6) & 0x1F;
        int b5 = (value >> 1) & 0x1F;
        int a = (value & 1) == 0 ? 0 : 255;
        int r = (r5 << 3) | (r5 >> 2);
        int g = (g5 << 3) | (g5 >> 2);
        int b = (b5 << 3) | (b5 >> 2);
        return Color.argb(a, r, g, b);
    }

    private static boolean isSupportedFormat(int format) {
        return format == FMT_CI4 || format == FMT_CI8 || format == FMT_I4 || format == FMT_I8
                || format == FMT_RGBA16 || format == FMT_RGBA32;
    }

    private static int bitsPerPixel(int format) throws IOException {
        switch (format) {
            case FMT_CI4:
            case FMT_I4:
                return 4;
            case FMT_CI8:
            case FMT_I8:
                return 8;
            case FMT_RGBA16:
                return 16;
            case FMT_RGBA32:
                return 32;
            default:
                throw new IOException("Unsupported bits per pixel format");
        }
    }

    private static byte[] slice(byte[] data, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IOException("Sprite decode overread");
        }
        byte[] out = new byte[length];
        System.arraycopy(data, offset, out, 0, length);
        return out;
    }

    private static int align8(int value) {
        return (value + 7) & ~7;
    }

    private static int detectByteOrder(RandomAccessFile rom) throws IOException {
        byte[] header = new byte[4];
        rom.seek(0);
        rom.readFully(header);
        if ((header[0] & 0xFF) == 0x80 && (header[1] & 0xFF) == 0x37
                && (header[2] & 0xFF) == 0x12 && (header[3] & 0xFF) == 0x40) {
            return 0;
        }
        if ((header[0] & 0xFF) == 0x37 && (header[1] & 0xFF) == 0x80
                && (header[2] & 0xFF) == 0x40 && (header[3] & 0xFF) == 0x12) {
            return 1;
        }
        if ((header[0] & 0xFF) == 0x40 && (header[1] & 0xFF) == 0x12
                && (header[2] & 0xFF) == 0x37 && (header[3] & 0xFF) == 0x80) {
            return 2;
        }
        throw new IOException("Unsupported ROM byte order");
    }

    private static int readLogicalInt(RandomAccessFile rom, int byteOrder, long offset) throws IOException {
        return readU32(readLogicalBytes(rom, byteOrder, offset, 4), 0);
    }

    private static byte[] readLogicalBytes(RandomAccessFile rom, int byteOrder, long offset, int length) throws IOException {
        byte[] raw = new byte[length];
        rom.seek(offset);
        rom.readFully(raw);
        if (byteOrder == 0) {
            return raw;
        }
        byte[] out = new byte[length];
        if (byteOrder == 1) {
            for (int i = 0; i < length; i++) {
                long absolute = offset + i;
                int pairOffset = (absolute & 1L) == 0L ? 1 : -1;
                int rawIndex = i + pairOffset;
                out[i] = rawIndex >= 0 && rawIndex < length ? raw[rawIndex] : readRawByte(rom, absolute + pairOffset);
            }
            return out;
        }
        for (int i = 0; i < length; i++) {
            long absolute = offset + i;
            int wordOffset = 3 - (int) (absolute & 3L) - (int) (absolute & 3L);
            int rawIndex = i + wordOffset;
            out[i] = rawIndex >= 0 && rawIndex < length ? raw[rawIndex] : readRawByte(rom, absolute + wordOffset);
        }
        return out;
    }

    private static byte readRawByte(RandomAccessFile rom, long offset) throws IOException {
        rom.seek(offset);
        return rom.readByte();
    }

    private static int readU16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int readS16(byte[] data, int offset) {
        int value = readU16(data, offset);
        return value >= 0x8000 ? value - 0x10000 : value;
    }

    private static int readU32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}
