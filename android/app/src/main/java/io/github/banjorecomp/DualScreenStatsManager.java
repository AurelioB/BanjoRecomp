package io.github.banjorecomp;

import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public class DualScreenStatsManager implements DisplayManager.DisplayListener {
    private static final String TAG = "DualScreenStats";

    private final Context context;
    private final DisplayManager displayManager;

    private DualScreenStatsPresentation presentation;
    private DualScreenStats latestStats = DualScreenStats.probe();
    private boolean started;
    private boolean appForeground;
    private boolean gameplayActive;

    public DualScreenStatsManager(Context context) {
        this.context = context;
        this.displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    }

    public void start() {
        if (started || displayManager == null) {
            return;
        }

        started = true;
        displayManager.registerDisplayListener(this, null);
        logPresentationDisplays();
        refreshPresentation();
    }

    public void stop() {
        if (!started) {
            return;
        }

        dismissPresentation();
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
        refreshPresentation();
    }

    public void updateStats(DualScreenStats stats) {
        if (stats == null) {
            return;
        }

        latestStats = stats;
        if (presentation != null && gameplayActive) {
            presentation.updateStats(stats);
        }
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
            if (gameplayActive) {
                presentation.updateStats(latestStats);
            } else {
                presentation.showBlank();
            }
            return;
        }

        dismissPresentation();
        presentation = new DualScreenStatsPresentation(context, display);
        try {
            presentation.show();
            if (gameplayActive) {
                presentation.updateStats(latestStats);
            } else {
                presentation.showBlank();
            }
            Log.i(TAG, "Showing dual-screen stats surface on display "
                    + display.getDisplayId() + " / " + display.getName());
        } catch (WindowManager.InvalidDisplayException e) {
            Log.w(TAG, "Unable to show secondary display presentation", e);
            presentation = null;
        }
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
