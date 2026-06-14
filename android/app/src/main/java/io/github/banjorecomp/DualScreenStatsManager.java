package io.github.banjorecomp;

import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DualScreenStatsManager implements DisplayManager.DisplayListener {
    private static final String TAG = "DualScreenStats";

    private final Context context;
    private final DisplayManager displayManager;

    private DualScreenStatsPresentation presentation;
    private DualScreenStats latestStats = DualScreenStats.probe();
    private BanjoSpriteTheme spriteTheme = BanjoSpriteTheme.EMPTY;
    private ExecutorService themeExecutor;
    private boolean started;
    private boolean appForeground;
    private boolean gameplayActive;
    private int displayMode = DualScreenStats.DISPLAY_LOGO;
    private boolean previewMode;
    private int debugAreaIndex;
    private int lastLoggedDisplayMode = -1;
    private int lastLoggedGameTransitionPhase = -1;

    public DualScreenStatsManager(Context context) {
        this.context = context;
        this.displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    }

    public void start() {
        if (started || displayManager == null) {
            return;
        }

        started = true;
        themeExecutor = Executors.newSingleThreadExecutor();
        displayManager.registerDisplayListener(this, null);
        logPresentationDisplays();
        refreshPresentation();
    }

    public void stop() {
        if (!started) {
            return;
        }

        dismissPresentation();
        if (themeExecutor != null) {
            themeExecutor.shutdownNow();
            themeExecutor = null;
        }
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(this);
        }
        started = false;
    }

    public void setAppForeground(boolean foreground) {
        if (appForeground == foreground) {
            return;
        }

        appForeground = foreground;
        refreshPresentation();
    }

    public void setGameplayActive(boolean active) {
        if (gameplayActive == active) {
            return;
        }

        gameplayActive = active;
        Log.i(TAG, "Gameplay active=" + active);
        if (!active && displayMode == DualScreenStats.DISPLAY_STATS) {
            displayMode = DualScreenStats.DISPLAY_LOGO;
        }
        refreshPresentation();
    }

    public void updateStats(DualScreenStats stats) {
        if (stats == null) {
            return;
        }

        if (previewMode) {
            return;
        }

        latestStats = stats;
        displayMode = stats.displayMode;
        gameplayActive = displayMode == DualScreenStats.DISPLAY_STATS;
        if (stats.displayMode != lastLoggedDisplayMode
                || stats.gameTransitionPhase != lastLoggedGameTransitionPhase) {
            lastLoggedDisplayMode = stats.displayMode;
            lastLoggedGameTransitionPhase = stats.gameTransitionPhase;
            Log.i(TAG, "Dual-screen mode=" + stats.displayMode
                    + " transition=" + stats.gameTransitionPhase
                    + " map=0x" + Integer.toHexString(stats.levelId));
        }
        if (presentation != null) {
            applyPresentationMode();
        }
    }

    public void previewStatsBackground(int mapId) {
        int index = DualScreenDebugAreas.indexForMapId(mapId);
        if (index >= 0) {
            debugAreaIndex = index;
        }
        previewMode = true;
        latestStats = new DualScreenStats(
                DualScreenStats.DISPLAY_STATS,
                6,
                8,
                3,
                42,
                12,
                5,
                3,
                7,
                12,
                mapId,
                0b10101,
                67,
                612,
                18,
                true,
                0,
                DualScreenStats.GAME_TRANSITION_NONE);
        displayMode = DualScreenStats.DISPLAY_STATS;
        gameplayActive = true;
        Log.i(TAG, "Previewing dual-screen stats background for map id=0x" + Integer.toHexString(mapId));
        refreshPresentation();
        if (presentation != null) {
            applyPresentationMode();
        }
    }

    public boolean debugStepPreviewArea(int direction) {
        if (!BuildConfig.BANJO_DUAL_SCREEN_DEBUG || DualScreenDebugAreas.AREAS.length == 0) {
            return false;
        }

        int currentIndex = previewMode ? debugAreaIndex : DualScreenDebugAreas.indexForMapId(latestStats.levelId);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        debugAreaIndex = (currentIndex + direction) % DualScreenDebugAreas.AREAS.length;
        if (debugAreaIndex < 0) {
            debugAreaIndex += DualScreenDebugAreas.AREAS.length;
        }
        DualScreenDebugAreas.Area area = DualScreenDebugAreas.AREAS[debugAreaIndex];
        previewStatsBackground(area.mapId);
        Log.i(TAG, "Debug preview area " + (debugAreaIndex + 1) + "/" + DualScreenDebugAreas.AREAS.length
                + ": map=0x" + Integer.toHexString(area.mapId)
                + " asset=0x" + Integer.toHexString(area.assetId)
                + " texture=" + area.textureIndex
                + " name=" + area.name);
        return true;
    }

    public void clearPreviewMode() {
        if (!previewMode) {
            return;
        }
        previewMode = false;
        Log.i(TAG, "Cleared dual-screen stats background preview mode");
        applyPresentationMode();
    }

    public void loadThemeFromRom(File romFile) {
        if (romFile == null || !romFile.isFile()) {
            return;
        }

        ExecutorService executor = themeExecutor;
        if (executor == null) {
            return;
        }

        executor.execute(() -> {
            try {
                BanjoSpriteTheme loadedTheme = BanjoSpriteThemeExtractor.extract(romFile);
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> applyTheme(loadedTheme));
                } else {
                    applyTheme(loadedTheme);
                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to build dual-screen sprite theme from selected ROM", e);
            }
        });
    }

    private void applyTheme(BanjoSpriteTheme theme) {
        spriteTheme = theme == null ? BanjoSpriteTheme.EMPTY : theme;
        if (presentation != null) {
            presentation.setTheme(spriteTheme);
        }
        Log.i(TAG, "Dual-screen sprite theme loaded=" + spriteTheme.isLoadedFromRom());
    }

    public void hideForExternalActivity() {
        dismissPresentation();
    }

    @Override
    public void onDisplayAdded(int displayId) {
        Log.i(TAG, "Display added: " + displayId);
        refreshPresentation();
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        Log.i(TAG, "Display removed: " + displayId);
        if (presentation != null && presentation.getDisplay() != null
                && presentation.getDisplay().getDisplayId() == displayId) {
            dismissPresentation();
        }
        refreshPresentation();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        Log.i(TAG, "Display changed: " + displayId);
        if (presentation != null && presentation.getDisplay() != null
                && presentation.getDisplay().getDisplayId() == displayId) {
            dismissPresentation();
        }
        refreshPresentation();
    }

    private void refreshPresentation() {
        if (!started) {
            return;
        }

        if (!appForeground) {
            dismissPresentation();
            return;
        }

        Display display = findSecondaryPresentationDisplay();
        if (display == null) {
            dismissPresentation();
            return;
        }

        if (presentation != null && presentation.getDisplay() != null
                && presentation.getDisplay().getDisplayId() == display.getDisplayId()) {
            applyPresentationMode();
            return;
        }

        dismissPresentation();
        presentation = new DualScreenStatsPresentation(context, display, this::handleDebugKey, this::handleDebugAreaButton);
        try {
            presentation.show();
            presentation.setTheme(spriteTheme);
            applyPresentationMode();
            Log.i(TAG, "Showing dual-screen stats surface on display "
                    + display.getDisplayId() + " / " + display.getName());
        } catch (WindowManager.InvalidDisplayException e) {
            Log.w(TAG, "Unable to show secondary display presentation", e);
            presentation = null;
        }
    }

    private boolean handleDebugAreaButton(int direction) {
        if (!BuildConfig.BANJO_DUAL_SCREEN_DEBUG || direction == 0) {
            return false;
        }
        return debugStepPreviewArea(direction > 0 ? 1 : -1);
    }

    private boolean handleDebugKey(int keyCode, KeyEvent event) {
        if (!BuildConfig.BANJO_DUAL_SCREEN_DEBUG || event == null
                || event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) {
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return debugStepPreviewArea(1);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return debugStepPreviewArea(-1);
        }
        return false;
    }

    private void applyPresentationMode() {
        if (presentation == null) {
            return;
        }
        presentation.updateStats(latestStats);
    }

    private void dismissPresentation() {
        if (presentation == null) {
            return;
        }

        try {
            presentation.dismiss();
        } catch (RuntimeException e) {
            Log.w(TAG, "Error dismissing secondary display presentation", e);
        } finally {
            presentation = null;
        }
    }

    private Display findSecondaryPresentationDisplay() {
        if (displayManager == null) {
            return null;
        }

        Display[] displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        for (Display display : displays) {
            if (display != null && display.isValid() && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                return display;
            }
        }

        return null;
    }

    private void logPresentationDisplays() {
        if (displayManager == null) {
            Log.i(TAG, "DisplayManager unavailable");
            return;
        }

        Display[] displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        Log.i(TAG, "Presentation display count: " + displays.length);
        for (Display display : displays) {
            if (display != null) {
                Log.i(TAG, "Presentation display: id=" + display.getDisplayId()
                        + " name=" + display.getName()
                        + " valid=" + display.isValid());
            }
        }
    }
}
