package io.github.banjorecomp;

import android.app.Presentation;
import android.os.Bundle;
import android.view.Display;
import android.view.Window;

public class DualScreenStatsPresentation extends Presentation {
    private DualScreenStatsView statsView;

    public DualScreenStatsPresentation(android.content.Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.black);
        }

        statsView = new DualScreenStatsView(getContext());
        setContentView(statsView);
        showBlank();
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
