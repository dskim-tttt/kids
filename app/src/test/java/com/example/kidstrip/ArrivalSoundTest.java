package com.example.kidstrip;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaPlayer;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ArrivalSoundTest {
    private final List<MediaPlayer> players = new ArrayList<>();
    private AudioManager audio;
    private NotificationManager notifications;
    private ArrivalSound sound;
    @Before public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        shadowOf(notifications).setNotificationPolicyAccessGranted(true);
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        audio.setStreamVolume(AudioManager.STREAM_SYSTEM, 5, 0);
        ShadowMediaPlayer.setCreateListener((player, shadow) -> players.add(player));
        ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(750, 0));
        sound = new ArrivalSound(context);
    }
    @After public void teardown() { sound.stop(); }
    @Test public void playsBundledShortSoundAndReleasesOnStop() {
        sound.play();
        assertEquals(1, players.size());
        MediaPlayer player = players.get(0);
        assertTrue(player.isPlaying());
        assertEquals(R.raw.city_chime, shadowOf(player).getSourceResId());
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, shadowOf(player).getAudioAttributes().getUsage());
        sound.stop();
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(player).getState());
    }
    @Test public void silentAndVibrateModesAreNeverOverridden() {
        audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        sound.play();
        audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        sound.play();
        assertTrue(players.isEmpty());
    }
    @Test public void doNotDisturbAndZeroVolumeSuppressPlayback() {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        sound.play();
        assertTrue(players.isEmpty());
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        audio.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
        sound.play();
        assertTrue(players.isEmpty());
    }
    @Test public void completionReleasesResourcesAndNextPlaybackDoesNotOverlap() {
        sound.play();
        MediaPlayer first = players.get(0);
        shadowOf(first).invokeCompletionListener();
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(first).getState());
        sound.play();
        MediaPlayer second = players.get(1);
        assertTrue(second.isPlaying());
        sound.play();
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(second).getState());
        assertTrue(players.get(2).isPlaying());
    }
}
