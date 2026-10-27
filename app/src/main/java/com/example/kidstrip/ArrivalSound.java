package com.example.kidstrip;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;

/** One short bundled sound; never overrides mute, volume or Do Not Disturb. */
public final class ArrivalSound {
    private final Context context;
    private MediaPlayer current;
    public ArrivalSound(Context context) { this.context = context.getApplicationContext(); }
    public void play() {
        stop();
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        NotificationManager notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (audio == null || audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL
                || audio.isStreamMute(AudioManager.STREAM_SYSTEM)
                || audio.getStreamVolume(AudioManager.STREAM_SYSTEM) == 0
                || (notifications != null && notifications.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL)) return;
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            MediaPlayer player = MediaPlayer.create(context, R.raw.city_chime, attributes, 0);
            if (player == null) return;
            current = player;
            player.setVolume(0.7f, 0.7f);
            player.setOnCompletionListener(done -> {
                if (current == done) current = null;
                done.release();
            });
            player.setOnErrorListener((failed, what, extra) -> {
                if (current == failed) current = null;
                failed.release();
                return true;
            });
            player.start();
        } catch (RuntimeException ignored) {
            // Device audio failure must not prevent displaying the badge.
            stop();
        }
    }
    public void stop() {
        MediaPlayer old = current;
        current = null;
        if (old != null) old.release();
    }
}
