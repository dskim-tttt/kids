package com.example.kidstrip;

/** Stable identifiers are persisted; UI labels may change without losing a selection. */
public final class SoundCatalog {
    public static final String DEFAULT_ID = "melody";
    public static final String[] IDS = {"melody", "bell", "sparkle"};
    public static final int[] TITLE_RES = {R.string.audio_sample_melody, R.string.audio_sample_bell, R.string.audio_sample_sparkle};
    private SoundCatalog() { }
    public static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }
    public static String normalize(String id) { return IDS[indexOf(id)]; }
    public static int rawFor(String id) {
        switch (normalize(id)) {
            case "bell": return R.raw.city_bell;
            case "sparkle": return R.raw.city_sparkle;
            default: return R.raw.city_chime;
        }
    }
}
