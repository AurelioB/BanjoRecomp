package io.github.banjorecomp;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DualScreenStatsPresentation extends Presentation {
    private TextView levelView;
    private TextView healthView;
    private TextView livesView;
    private TextView notesView;
    private TextView jiggiesView;
    private TextView mumboView;
    private TextView jinjosView;

    public DualScreenStatsPresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.black);
        }

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(56, 44, 56, 44);
        root.setBackgroundColor(Color.rgb(12, 18, 30));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView titleView = new TextView(getContext());
        titleView.setText("BanjoRecomp Stats");
        titleView.setTextColor(Color.rgb(255, 221, 87));
        titleView.setTextSize(38);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);

        levelView = new TextView(getContext());
        levelView.setTextColor(Color.rgb(170, 196, 255));
        levelView.setTextSize(20);
        levelView.setGravity(Gravity.CENTER);
        levelView.setPadding(0, 8, 0, 24);

        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(true);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        healthView = addStat(grid, "Health", "-- / --");
        livesView = addStat(grid, "Lives", "--");
        notesView = addStat(grid, "Notes", "--");
        jiggiesView = addStat(grid, "Jiggies", "--");
        mumboView = addStat(grid, "Mumbo", "--");
        jinjosView = addStat(grid, "Jinjos", "-- / 5");

        root.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(levelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        showBlank();
    }

    public void showBlank() {
        if (levelView == null) {
            return;
        }

        levelView.setText("Waiting for gameplay");
        healthView.setText("-- / --");
        livesView.setText("--");
        notesView.setText("--");
        jiggiesView.setText("--");
        mumboView.setText("--");
        jinjosView.setText("-- / 5");
    }

    public void updateStats(DualScreenStats stats) {
        if (stats == null || levelView == null) {
            return;
        }

        levelView.setText("Level ID " + stats.levelId + " · gameplay active");
        healthView.setText(stats.health + " / " + stats.maxHealth);
        livesView.setText(Integer.toString(stats.lives));
        notesView.setText(Integer.toString(stats.notes));
        jiggiesView.setText(Integer.toString(stats.jiggies));
        mumboView.setText(Integer.toString(stats.mumboTokens));
        jinjosView.setText(Integer.toString(Integer.bitCount(stats.jinjosMask)) + " / 5");
    }

    private TextView addStat(GridLayout grid, String label, String value) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(32, 24, 32, 24);
        card.setBackgroundColor(Color.rgb(28, 39, 60));

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setTextColor(Color.rgb(170, 196, 255));
        labelView.setTextSize(18);
        labelView.setGravity(Gravity.CENTER);

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(34);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setGravity(Gravity.CENTER);

        card.addView(labelView, new LinearLayout.LayoutParams(260, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(valueView, new LinearLayout.LayoutParams(260, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid.addView(card);
        return valueView;
    }
}
