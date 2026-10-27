package com.example.kidstrip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small bundled illustrations. Decorative; no map service or downloaded assets. */
public final class SceneryView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int[] TREES = {28, 554};
    private final Path hills = new Path(), meadow = new Path(), river = new Path(),
            peak = new Path(), snow = new Path(), line = new Path();
    private int scene;
    public SceneryView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setScene(int scene) { this.scene = scene; invalidate(); }
    private void color(int color) { paint.setColor(color); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 600f, getHeight() / 260f);
        color(0xffB8E0EB); canvas.drawRect(0, 0, 600, 260, paint);
        color(0xffFFCE69); canvas.drawCircle(506, 47, 26, paint);
        color(0xffFFFDF4);
        canvas.drawRoundRect(62, 35, 167, 59, 12, 12, paint);
        canvas.drawCircle(101, 38, 21, paint);
        canvas.drawRoundRect(325, 66, 401, 83, 10, 10, paint);
        hills.reset();
        hills.moveTo(0, 170); hills.lineTo(0, 123);
        hills.cubicTo(100, 32, 163, 110, 240, 138);
        hills.cubicTo(340, 22, 410, 120, 600, 94);
        hills.lineTo(600, 260); hills.lineTo(0, 260); hills.close();
        color(0xff83ACA1); canvas.drawPath(hills, paint);
        meadow.reset();
        meadow.moveTo(0, 194); meadow.cubicTo(182, 103, 390, 212, 600, 148);
        meadow.lineTo(600, 260); meadow.lineTo(0, 260); meadow.close();
        color(scene == 4 ? 0xffDCC66E : 0xffA3BC78); canvas.drawPath(meadow, paint);
        if (scene == 0 || scene == 2) {
            river.reset();
            river.moveTo(360, 155); river.cubicTo(230, 193, 472, 204, 295, 260);
            river.lineTo(79, 260); river.cubicTo(380, 203, 157, 187, 338, 155); river.close();
            color(0xff69B5D0); canvas.drawPath(river, paint);
        }
        if (scene == 1) {
            peak.reset();
            peak.moveTo(90, 200); peak.lineTo(250, 65); peak.lineTo(410, 200); peak.close();
            color(0xff568A82); canvas.drawPath(peak, paint);
            snow.reset();
            snow.moveTo(205, 103); snow.lineTo(250, 65); snow.lineTo(293, 102);
            snow.lineTo(258, 94); snow.lineTo(237, 108); snow.close();
            color(0xffFFFDF4); canvas.drawPath(snow, paint);
        } else if (scene == 2) {
            color(0xffF6F3E9); canvas.drawRect(100, 177, 502, 191, paint);
            for (int x = 135; x < 500; x += 88) canvas.drawRect(x, 191, x + 15, 228, paint);
            color(0xff246B64); canvas.drawRect(100, 169, 502, 175, paint);
        } else if (scene == 3) {
            color(0xff568A82); canvas.drawOval(110, 74, 460, 350, paint);
            color(0xffF0DEB3); canvas.drawRoundRect(220, 133, 354, 284, 65, 65, paint);
            color(0xff17413E); canvas.drawRoundRect(235, 150, 339, 284, 49, 49, paint);
            color(0xffF6F3E9); canvas.drawRect(282, 218, 291, 245, paint);
        } else if (scene == 4) {
            color(0xffB89E4E);
            for (int x = -100; x < 700; x += 95) {
                line.reset(); line.moveTo(300, 172); line.lineTo(x, 260); line.lineTo(x + 9, 260); line.close();
                canvas.drawPath(line, paint);
            }
        } else if (scene == 5) {
            for (int i = 0; i < 5; i++) {
                int x = 82 + i * 86, top = 96 + (i % 3) * 18;
                color(i % 2 == 0 ? 0xffF5E8C9 : 0xff77A6AA); canvas.drawRect(x, top, x + 62, 232, paint);
                color(0xff246B64);
                for (int y = top + 13; y < 220; y += 25) {
                    canvas.drawRect(x + 12, y, x + 23, y + 10, paint);
                    canvas.drawRect(x + 39, y, x + 50, y + 10, paint);
                }
            }
        }
        for (int x : TREES) {
            color(0xff246B64); canvas.drawRoundRect(x, 201, x + 9, 250, 3, 3, paint);
            canvas.drawOval(x - 18, 153, x + 27, 224, paint);
        }
        canvas.restore();
    }
}
