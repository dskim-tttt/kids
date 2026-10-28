package com.example.kidstrip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Verified bundled logo when available; otherwise a neutral, visibly labelled logo slot. */
public final class CityBadgeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private final CityLogoCatalog catalog;
    private String initials = "?";
    private CityLogoCatalog.Logo logo;
    private CityLogoCatalog.Entry entry;
    public CityBadgeView(Context context) {
        super(context);
        catalog = CityLogoCatalog.get(context);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    /** Convenience for a preview or unknown key: names alone never select an official logo. */
    public void setCity(String city) { setCity(null, city); }
    public void setCity(String key, String city) {
        String name = city == null ? "" : city.trim();
        initials = name.isEmpty() ? "?" : name.substring(0,
                name.offsetByCodePoints(0, Math.min(2, name.codePointCount(0, name.length()))));
        entry = catalog.entryFor(key);
        logo = catalog.logoFor(key);
        invalidate();
    }
    public boolean hasLogo() { return logo != null; }
    public boolean isOfficialLogo() { return hasLogo() && "official".equals(entry.sourceKind); }
    public boolean isOriginalBadge() { return hasLogo() && "original".equals(entry.sourceKind); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        float left = (getWidth() - side) / 2f, top = (getHeight() - side) / 2f;
        if (logo != null) {
            // Keep the complete official mark and its proportions; do not crop it into a circle.
            float available = side * (isOriginalBadge() ? 1f : 0.88f);
            float scale = Math.min(available / logo.source.width(), available / logo.source.height());
            float width = logo.source.width() * scale, height = logo.source.height() * scale;
            destination.set(left + (side - width) / 2f, top + (side - height) / 2f,
                    left + (side + width) / 2f, top + (side + height) / 2f);
            paint.setAlpha(255);
            canvas.drawBitmap(logo.bitmap, logo.source, destination, paint);
            return;
        }
        float cx = left + side / 2, cy = top + side / 2;
        paint.setColor(0xffECEFEA);
        canvas.drawCircle(cx, cy, side * 0.46f, paint);
        paint.setColor(0xff718078);
        paint.setTextSize(side * 0.24f);
        canvas.drawText(initials, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint);
    }
}
