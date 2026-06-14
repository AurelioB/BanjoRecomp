package io.github.banjorecomp;

import android.app.Presentation;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Window;

public class DualScreenStatsPresentation extends Presentation {
    public interface DebugKeyHandler {
        boolean onDebugKey(int keyCode, KeyEvent event);
    }

    private DualScreenStatsView statsView;
    private final DebugKeyHandler debugKeyHandler;
    private final DualScreenStatsView.DebugAreaButtonHandler debugAreaButtonHandler;

    public DualScreenStatsPresentation(android.content.Context outerContext, Display display,
                                       DebugKeyHandler debugKeyHandler,
                                       DualScreenStatsView.DebugAreaButtonHandler debugAreaButtonHandler) {
        super(outerContext, display);
        this.debugKeyHandler = debugKeyHandler;
        this.debugAreaButtonHandler = debugAreaButtonHandler;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (debugKeyHandler != null && debugKeyHandler.onDebugKey(event.getKeyCode(), event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.black);
        }

        statsView = new DualScreenStatsView(getContext(), debugAreaButtonHandler);
        setContentView(statsView);
        showLogo();
    }

    public void showLogo() {
        if (statsView != null) {
            statsView.showLogo();
        }
    }

    public void showBlank() {
        if (statsView != null) {
            statsView.showBlank();
        }
    }

    public void updateStats(DualScreenStats stats) {
        if (statsView != null) {
            statsView.updateStats(stats);
        }
    }

    public void setTheme(BanjoSpriteTheme theme) {
        if (statsView != null) {
            statsView.setTheme(theme);
        }
    }
}
