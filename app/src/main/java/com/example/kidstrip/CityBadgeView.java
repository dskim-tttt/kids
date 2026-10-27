package com.example.kidstrip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

/** App-owned city-name badge, not a municipality's official emblem. */
public final class CityBadgeView extends View {
    private static final int[] COLORS = {0xff246B64, 0xff326A91, 0xff865B8D, 0xff9A603D, 0xff53783D, 0xff7C6150};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path landscape = new Path();
    private String initials = "";
    private int color = COLORS[0];
    public CityBadgeView(Context context) {
        super(context);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setCity(String city) {
        String name = city == null ? "" : city.trim();
        initials = name.substring(0, name.offsetByCodePoints(0, Math.min(2, name.codePointCount(0, name.length()))));
        color = COLORS[Math.floorMod(name.hashCode(), COLORS.length)];
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        canvas.save();
        canvas.translate((getWidth() - side) / 2, (getHeight() - side) / 2);
        canvas.scale(side / 240f, side / 240f);
        paint.setColor(0xffFFCE69); canvas.drawCircle(120, 120, 115, paint);
        paint.setColor(color); canvas.drawCircle(120, 120, 107, paint);
        paint.setColor(0xffF6F3E9); canvas.drawCircle(120, 120, 97, paint);
        paint.setColor(0xffFFCE69); canvas.drawCircle(170, 67, 17, paint);
        landscape.reset(); landscape.moveTo(43, 163); landscape.lineTo(84, 119);
        landscape.lineTo(119, 157); landscape.lineTo(153, 126); landscape.lineTo(196, 165);
        landscape.quadTo(121, 218, 43, 163); landscape.close();
        paint.setColor(0xffA3BC78); canvas.drawPath(landscape, paint);
        paint.setColor(color); paint.setTextSize(58);
        canvas.drawText(initials, 120, 116, paint);
        paint.setColor(0xffFFFDF4); canvas.drawCircle(120, 177, 5, paint);
        canvas.restore();
    }
}
