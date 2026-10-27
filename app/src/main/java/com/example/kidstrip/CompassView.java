package com.example.kidstrip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/** Red needle and north mark both rotate toward magnetic north. Readout handles accessibility. */
public final class CompassView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path needle = new Path(), marker = new Path();
    private final String[] labels;
    private float heading = Float.NaN;
    public CompassView(Context context) {
        super(context);
        labels = context.getResources().getStringArray(R.array.compass_cardinals);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setHeading(float value) { heading = CompassMath.normalize(value); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        canvas.save();
        canvas.translate((getWidth() - side) / 2, (getHeight() - side) / 2);
        canvas.scale(side / 200f, side / 200f);
        paint.setColor(0xffF6F3E9); canvas.drawCircle(100, 102, 88, paint);
        paint.setStrokeWidth(2);
        paint.setColor(0xff17413E);
        marker.reset(); marker.moveTo(94, 3); marker.lineTo(106, 3); marker.lineTo(100, 13); marker.close();
        canvas.drawPath(marker, paint); // Fixed pointer = upper edge of the display.
        if (!Float.isFinite(heading)) {
            paint.setTextSize(40); canvas.drawText("—", 100, 115, paint);
            canvas.restore();
            return;
        }
        canvas.save();
        canvas.rotate(-heading, 100, 102);
        paint.setColor(0xffA1B4AD);
        for (int i = 0; i < 16; i++) {
            canvas.drawLine(100, 20, 100, i % 4 == 0 ? 26 : 24, paint);
            canvas.rotate(22.5f, 100, 102);
        }
        needle.reset(); needle.moveTo(100, 54); needle.lineTo(89, 110); needle.lineTo(111, 110); needle.close();
        paint.setColor(0xffB74737); canvas.drawPath(needle, paint);
        needle.reset(); needle.moveTo(100, 150); needle.lineTo(89, 94); needle.lineTo(111, 94); needle.close();
        paint.setColor(0xff6D998A); canvas.drawPath(needle, paint);
        canvas.restore();
        paint.setTextSize(23);
        for (int i = 0; i < labels.length; i++) {
            double radians = Math.toRadians(i * 90f - heading);
            paint.setColor(i == 0 ? 0xffB74737 : 0xff17413E);
            canvas.drawText(labels[i], 100 + (float) Math.sin(radians) * 65,
                    110 - (float) Math.cos(radians) * 65, paint);
        }
        paint.setColor(0xff17413E); canvas.drawCircle(100, 102, 5, paint);
        canvas.restore();
    }
}
