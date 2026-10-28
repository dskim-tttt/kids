package com.example.kidstrip;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;

/** Short local samples use the media volume, while respecting quiet device modes. */
public final class ArrivalSound {
    private final Context context;
    private MediaPlayer current;
    private int lastReason;
    public ArrivalSound(Context context) { this.context = context.getApplicationContext(); }

    /** Zero means the device currently allows playback. No global audio setting is changed. */
    public int availabilityReason() {
        try {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            NotificationManager notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (audio == null) return R.string.audio_reason_unavailable;
            if (audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return R.string.audio_reason_quiet;
            if (notifications != null) {
                int filter = notifications.getCurrentInterruptionFilter();
                if (filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return R.string.audio_reason_dnd_unknown;
                if (filter != NotificationManager.INTERRUPTION_FILTER_ALL) return R.string.audio_reason_dnd;
            }
            if (audio.isStreamMute(AudioManager.STREAM_MUSIC)
                    || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) return R.string.audio_reason_media_volume;
            return 0;
        } catch (RuntimeException unavailable) {
            return R.string.audio_reason_unavailable;
        }
    }
    /** Reason for the most recent playback attempt, including audio decoder/device failure. */
    public int lastReason() { return lastReason; }
    public void play() { play(SoundCatalog.DEFAULT_ID); }
    public void play(String soundId) {
        stop();
        lastReason = availabilityReason();
        if (lastReason != 0) return;
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            MediaPlayer player = MediaPlayer.create(context, SoundCatalog.rawFor(soundId), attributes, 0);
            if (player == null) { lastReason = R.string.audio_reason_playback; return; }
            current = player;
            player.setVolume(0.7f, 0.7f);
            player.setOnCompletionListener(done -> {
                if (current == done) current = null;
                done.release();
            });
            player.setOnErrorListener((failed, what, extra) -> {
                if (current == failed) current = null;
                lastReason = R.string.audio_reason_playback;
                failed.release();
                return true;
            });
            player.start();
        } catch (RuntimeException failed) {
            // Audio failure must never prevent displaying the badge.
            lastReason = R.string.audio_reason_playback;
            stop();
        }
    }
    public void stop() {
        MediaPlayer old = current;
        current = null;
        if (old != null) old.release();
    }
}
