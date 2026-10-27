package com.example.kidstrip;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Foreground-only arrival presentation; it never creates stamps or saves a city. */
public final class CityArrivalOverlay {
    public enum Kind { FIRST_VISIT, RETURN_VISIT, PREVIEW }
    private final Activity activity;
    private final View content;
    private final FrameLayout overlay;
    private final LinearLayout panel;
    private final CityBadgeView badge;
    private final TextView title, city, subtitle;
    private final ArrivalSparklesView sparkles;
    private final ArrivalSound sound;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoHide = this::hide;
    public CityArrivalOverlay(Activity activity, FrameLayout host, View content) {
        this.activity = activity;
        this.content = content;
        sound = new ArrivalSound(activity);
        overlay = new FrameLayout(activity);
        overlay.setId(R.id.city_arrival_overlay);
        overlay.setBackgroundColor(0xe617413e);
        overlay.setClickable(true);
        overlay.setVisibility(View.GONE);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout centered = new LinearLayout(activity);
        centered.setOrientation(LinearLayout.VERTICAL);
        centered.setGravity(Gravity.CENTER);
        centered.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel = new LinearLayout(activity);
        panel.setId(R.id.arrival_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xffF6F3E9);
        background.setCornerRadius(dp(28));
        panel.setBackground(background);
        title = text(21);
        panel.addView(title);
        badge = new CityBadgeView(activity);
        panel.addView(badge, new LinearLayout.LayoutParams(dp(156), dp(156)));
        city = text(32);
        city.setId(R.id.arrival_city);
        city.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(city);
        subtitle = text(18);
        panel.addView(subtitle);
        Button close = new Button(activity);
        close.setId(R.id.arrival_close);
        close.setText(R.string.arrival_close);
        close.setAllCaps(false);
        close.setTextSize(17);
        close.setMinHeight(dp(52));
        close.setOnClickListener(view -> hide());
        panel.addView(close, new LinearLayout.LayoutParams(-1, -2));
        centered.addView(panel, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(centered);
        overlay.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        sparkles = new ArrivalSparklesView(activity);
        sparkles.setId(R.id.arrival_sparkles);
        overlay.addView(sparkles, new FrameLayout.LayoutParams(-1, -1));
        host.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private TextView text(int size) {
        TextView view = new TextView(activity);
        view.setTextSize(size);
        view.setTextColor(0xff17413E);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }
    public void show(String cityName, Kind kind, boolean playSound, boolean effects) {
        hide();
        badge.setCity(cityName);
        city.setText(cityName);
        boolean preview = kind == Kind.PREVIEW;
        boolean revisit = kind == Kind.RETURN_VISIT;
        title.setText(preview ? R.string.arrival_preview_title
                : revisit ? R.string.arrival_revisit_title : R.string.arrival_title);
        subtitle.setText(preview ? R.string.arrival_preview_note
                : revisit ? R.string.arrival_revisit_subtitle : R.string.arrival_subtitle);
        overlay.setVisibility(View.VISIBLE);
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 28) overlay.setAccessibilityPaneTitle(title.getText());
        panel.setScaleX(1); panel.setScaleY(1); panel.setAlpha(1);
        boolean animate = effects && ValueAnimator.areAnimatorsEnabled();
        sparkles.setVisibility(animate ? View.VISIBLE : View.GONE);
        if (animate) {
            sparkles.start();
            panel.setScaleX(0.88f); panel.setScaleY(0.88f); panel.setAlpha(0);
            panel.animate().scaleX(1).scaleY(1).alpha(1).setDuration(420)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        if (playSound) sound.play();
        city.announceForAccessibility(activity.getString(preview ? R.string.arrival_preview_announcement
                : revisit ? R.string.arrival_revisit_announcement : R.string.stamp_announcement, cityName));
        AccessibilityManager accessibility = (AccessibilityManager) activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        // Screen-reader users can read at their own pace and use the close button.
        if (accessibility == null || !accessibility.isTouchExplorationEnabled()) {
            int timeout = 4500;
            if (Build.VERSION.SDK_INT >= 29 && accessibility != null) {
                timeout = accessibility.getRecommendedTimeoutMillis(timeout,
                        AccessibilityManager.FLAG_CONTENT_TEXT | AccessibilityManager.FLAG_CONTENT_CONTROLS);
            }
            handler.postDelayed(autoHide, Math.max(timeout, 4500));
        }
    }
    public void hide() {
        handler.removeCallbacks(autoHide);
        panel.animate().cancel();
        sparkles.stop();
        sound.stop();
        overlay.setVisibility(View.GONE);
        content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
}
