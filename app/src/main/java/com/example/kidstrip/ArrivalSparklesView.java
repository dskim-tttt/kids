package com.example.kidstrip;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Brief confetti, with no flashing. Animates only while the arrival overlay is visible. */
public final class ArrivalSparklesView extends View {
    private static final int[] COLORS = {0xffFFCE69, 0xffA3BC78, 0xffB8E0EB, 0xffF6F3E9};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float progress = 1;
    public ArrivalSparklesView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void start() {
        stop();
        progress = 0;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1800);
        animator.addUpdateListener(value -> { progress = (float) value.getAnimatedValue(); invalidate(); });
        animator.start();
    }
    public void stop() {
        if (animator != null) { animator.cancel(); animator = null; }
        progress = 1;
        invalidate();
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress >= 1) return;
        for (int i = 0; i < 28; i++) {
            float x = getWidth() * ((i * 37) % 101) / 100f;
            x += (float) Math.sin(progress * 5 + i) * 15 * getResources().getDisplayMetrics().density;
            float y = progress * (getHeight() + 80) - 80 + (i * 19) % 70;
            paint.setColor(COLORS[i % COLORS.length]);
            paint.setAlpha(Math.round(255 * (1 - progress)));
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(i * 31 + progress * 150);
            float size = 4 * getResources().getDisplayMetrics().density;
            canvas.drawRoundRect(-size, -size / 2, size, size / 2, 2, 2, paint);
            canvas.restore();
        }
    }
}
