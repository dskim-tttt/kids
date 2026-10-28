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
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0);
        audio.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0);
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
        assertEquals(0, sound.availabilityReason());
        assertEquals(0, sound.lastReason());
        assertEquals(R.raw.city_chime, shadowOf(player).getSourceResId());
        assertEquals(AudioAttributes.USAGE_MEDIA, shadowOf(player).getAudioAttributes().getUsage());
        sound.stop();
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(player).getState());
    }
    @Test public void silentAndVibrateModesAreNeverOverridden() {
        audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        sound.play();
        audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        sound.play();
        assertTrue(players.isEmpty());
        assertEquals(R.string.audio_reason_quiet, sound.lastReason());
    }
    @Test public void doNotDisturbAndZeroVolumeSuppressPlayback() {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        sound.play();
        assertTrue(players.isEmpty());
        assertEquals(R.string.audio_reason_dnd, sound.lastReason());
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
        sound.play();
        assertTrue(players.isEmpty());
        assertEquals(R.string.audio_reason_media_volume, sound.lastReason());
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
    @Test public void selectedSampleIsUsedAndUnknownSelectionFallsBackSafely() {
        for (String id : SoundCatalog.IDS) {
            sound.play(id);
            MediaPlayer latest = players.get(players.size() - 1);
            assertTrue(latest.isPlaying());
            assertEquals(SoundCatalog.rawFor(id), shadowOf(latest).getSourceResId());
            assertEquals(AudioAttributes.USAGE_MEDIA, shadowOf(latest).getAudioAttributes().getUsage());
        }
        sound.play("removed-sample");
        assertEquals(R.raw.city_chime, shadowOf(players.get(players.size() - 1)).getSourceResId());
    }
    @Test public void unavailableDndStateReportsAnHonestReason() {
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_UNKNOWN);
        assertEquals(R.string.audio_reason_dnd_unknown, sound.availabilityReason());
        sound.play();
        assertEquals(R.string.audio_reason_dnd_unknown, sound.lastReason());
        assertTrue(players.isEmpty());
    }
    @Test public void asynchronousPlaybackErrorIsVisibleAndReleasesThePlayer() {
        sound.play();
        MediaPlayer player = players.get(0);
        shadowOf(player).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        assertEquals(R.string.audio_reason_playback, sound.lastReason());
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(player).getState());
    }
}
