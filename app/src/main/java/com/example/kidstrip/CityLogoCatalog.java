package com.example.kidstrip;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.LruCache;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Bundled, exact-city-key logo registry. It never fetches images or infers a city's emblem. */
public final class CityLogoCatalog {
    private static final String MANIFEST = "city_logos/manifest.json";
    private static CityLogoCatalog shared;
    private final AssetManager assets;
    private final Map<String, Entry> entries;
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    public static final class Cell {
        public final int column, row, columns, rows;
        private Cell(int column, int row, int columns, int rows) {
            this.column = column; this.row = row; this.columns = columns; this.rows = rows;
        }
    }
    private static final Cell FULL_IMAGE = new Cell(0, 0, 1, 1);
    public static final class Entry {
        public final String key, asset, sourceUrl, license, sourceKind;
        public final Cell cell;
        private Entry(String key, String asset, String sourceUrl, String license, String sourceKind, Cell cell) {
            this.key = key;
            this.asset = asset;
            this.sourceUrl = sourceUrl;
            this.license = license;
            this.sourceKind = sourceKind;
            this.cell = cell;
        }
    }
    public static final class Logo {
        public final Bitmap bitmap;
        public final Rect source;
        public final Entry entry;
        private Logo(Bitmap bitmap, Rect source, Entry entry) {
            this.bitmap = bitmap; this.source = source; this.entry = entry;
        }
    }

    public static synchronized CityLogoCatalog get(Context context) {
        AssetManager assets = context.getApplicationContext().getAssets();
        if (shared == null || shared.assets != assets) shared = new CityLogoCatalog(assets);
        return shared;
    }
    private CityLogoCatalog(AssetManager assets) {
        this.assets = assets;
        Map<String, Entry> loaded = Collections.emptyMap();
        try (InputStream input = assets.open(MANIFEST)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                // An accidental oversized or corrupt registry must not exhaust the app heap.
                if (bytes.size() + count > 1024 * 1024) throw new IOException("Oversized logo manifest");
                bytes.write(buffer, 0, count);
            }
            loaded = parse(bytes.toString(java.nio.charset.StandardCharsets.UTF_8.name()));
        } catch (IOException ignored) {
            // No verified registry: the UI shows a clearly marked empty logo slot.
        }
        entries = loaded;
    }
    public Entry entryFor(String key) { return key == null ? null : entries.get(key); }

    public Logo logoFor(String key) {
        Entry entry = entryFor(key);
        if (entry == null) return null;
        Bitmap bitmap = images.get(entry.asset);
        if (bitmap == null) {
            bitmap = decode(entry.asset);
            if (bitmap == null) return null;
            images.put(entry.asset, bitmap);
        }
        Rect source = sourceFor(entry, bitmap.getWidth(), bitmap.getHeight());
        return source == null ? null : new Logo(bitmap, source, entry);
    }
    private Bitmap decode(String asset) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream input = assets.open(asset)) {
                BitmapFactory.decodeStream(input, null, options);
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1024
                    || options.outHeight / options.inSampleSize > 1024) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            try (InputStream input = assets.open(asset)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
    static Rect sourceFor(Entry entry, int width, int height) {
        if (width <= 0 || height <= 0) return null;
        Cell cell = entry.cell;
        int left = (int) ((long) cell.column * width / cell.columns);
        int right = (int) ((long) (cell.column + 1) * width / cell.columns);
        int top = (int) ((long) cell.row * height / cell.rows);
        int bottom = (int) ((long) (cell.row + 1) * height / cell.rows);
        return right <= left || bottom <= top ? null : new Rect(left, top, right, bottom);
    }
    private static Cell parseCell(JSONArray array) {
        if (array == null || array.length() != 4) return null;
        int[] values = new int[4];
        for (int i = 0; i < values.length; i++) {
            Object value = array.opt(i);
            if (!(value instanceof Number)) return null;
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || number != Math.rint(number) || number < 0 || number > 16) return null;
            values[i] = (int) number;
        }
        if (values[2] < 1 || values[3] < 1 || values[0] >= values[2] || values[1] >= values[3]) return null;
        return new Cell(values[0], values[1], values[2], values[3]);
    }

    static boolean isSafeAssetPath(String path) {
        return path != null && !path.contains("..")
                && path.matches("city_logos/[A-Za-z0-9][A-Za-z0-9._-]*\\.(png|webp)");
    }
    static Map<String, Entry> parse(String json) {
        Map<String, Entry> result = new HashMap<>();
        Set<String> duplicateKeys = new HashSet<>();
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("version", 0) != 1) return Collections.emptyMap();
            JSONArray records = root.optJSONArray("entries");
            if (records == null) return Collections.emptyMap();
            for (int i = 0; i < records.length(); i++) {
                JSONObject item = records.optJSONObject(i);
                if (item == null) continue;
                String key = item.optString("key", "").trim();
                String asset = item.optString("asset", "");
                String source = item.optString("sourceUrl", "").trim();
                String license = item.optString("license", "").trim();
                String kind = item.optString("sourceKind", "official");
                boolean official = "official".equals(kind);
                boolean original = "original".equals(kind);
                if (key.isEmpty() || !key.equals(item.optString("key")) || key.length() > 512
                        || !isSafeAssetPath(asset) || license.isEmpty() || (!official && !original)) continue;
                if (official && (!item.optBoolean("verified", false) || !isHttpsSource(source))) continue;
                if (original && !source.isEmpty() && !isHttpsSource(source)) continue;
                Cell cell = item.has("cell") ? parseCell(item.optJSONArray("cell")) : FULL_IMAGE;
                if (cell == null) continue;
                // Conflicting mappings are safer to omit than to choose an arbitrary emblem.
                if (result.containsKey(key) || duplicateKeys.contains(key)) {
                    result.remove(key);
                    duplicateKeys.add(key);
                    continue;
                }
                result.put(key, new Entry(key, asset, source, license, kind, cell));
            }
        } catch (JSONException ignored) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(result);
    }
    private static boolean isHttpsSource(String source) {
        try {
            URI uri = new URI(source);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && !uri.getHost().isEmpty() && uri.getUserInfo() == null;
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}
